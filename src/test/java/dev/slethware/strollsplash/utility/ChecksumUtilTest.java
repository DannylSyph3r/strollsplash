package dev.slethware.strollsplash.utility;

import dev.slethware.strollsplash.dto.response.SplashResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChecksumUtilTest {

    private static final UUID ID = UUID.fromString("7d9c3f0e-5b1a-4c55-9d3e-0a1f6b2c8e11");

    private SplashResponse item(UUID id, String url, String checksum, long size) {
        return SplashResponse.builder().id(id).url(url).checksum(checksum).sizeBytes(size).build();
    }

    private SplashResponse baseItem() {
        return item(ID, "https://cdn.test/a.mp4", "abc", 100L);
    }

    @Test
    void sha256HexMatchesTheKnownVectorForAbc() {
        assertThat(ChecksumUtil.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sha256HexOfNothingMatchesTheKnownVector() {
        assertThat(ChecksumUtil.sha256Hex(new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void sameListAlwaysGivesTheSameEtag() {
        assertThat(ChecksumUtil.etagFor(List.of(baseItem()))).isEqualTo(ChecksumUtil.etagFor(List.of(baseItem())));
    }

    @Test
    void changingTheIdChangesTheEtag() {
        SplashResponse changed = item(UUID.randomUUID(), "https://cdn.test/a.mp4", "abc", 100L);

        assertThat(ChecksumUtil.etagFor(List.of(changed))).isNotEqualTo(ChecksumUtil.etagFor(List.of(baseItem())));
    }

    @Test
    void changingTheUrlChangesTheEtag() {
        SplashResponse changed = item(ID, "https://other.test/a.mp4", "abc", 100L);

        assertThat(ChecksumUtil.etagFor(List.of(changed))).isNotEqualTo(ChecksumUtil.etagFor(List.of(baseItem())));
    }

    @Test
    void changingTheChecksumChangesTheEtag() {
        SplashResponse changed = item(ID, "https://cdn.test/a.mp4", "abd", 100L);

        assertThat(ChecksumUtil.etagFor(List.of(changed))).isNotEqualTo(ChecksumUtil.etagFor(List.of(baseItem())));
    }

    @Test
    void changingTheSizeChangesTheEtag() {
        SplashResponse changed = item(ID, "https://cdn.test/a.mp4", "abc", 101L);

        assertThat(ChecksumUtil.etagFor(List.of(changed))).isNotEqualTo(ChecksumUtil.etagFor(List.of(baseItem())));
    }

    @Test
    void emptyListGivesAValidEtag() {
        assertThat(ChecksumUtil.etagFor(List.of())).matches("[0-9a-f]{64}");
    }
}
