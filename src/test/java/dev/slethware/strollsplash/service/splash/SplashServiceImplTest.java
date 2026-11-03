package dev.slethware.strollsplash.service.splash;

import dev.slethware.strollsplash.dto.response.SplashResponse;
import dev.slethware.strollsplash.entity.SplashVideo;
import dev.slethware.strollsplash.enums.SplashKind;
import dev.slethware.strollsplash.service.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SplashServiceImplTest {

    @Mock
    private SplashPoolCache splashPoolCache;

    @Mock
    private SplashResolver splashResolver;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private SplashServiceImpl splashService;

    private SplashVideo video(String id, String key) {
        return SplashVideo.builder()
                .id(UUID.fromString(id)).kind(SplashKind.STANDARD).objectKey(key)
                .checksum("sum-" + key).sizeBytes(123L).active(true).build();
    }

    @Test
    void poolIsSortedByIdString() {
        SplashVideo low = video("00000000-0000-0000-0000-000000000001", "splashes/low.mp4");
        SplashVideo mid = video("7d9c3f0e-5b1a-4c55-9d3e-0a1f6b2c8e11", "splashes/mid.mp4");
        SplashVideo high = video("ffffffff-0000-0000-0000-000000000001", "splashes/high.mp4");
        List<SplashVideo> active = List.of(high, low, mid);
        when(splashPoolCache.getActive()).thenReturn(active);
        when(splashResolver.resolve(any(), any(Instant.class))).thenReturn(active);
        when(storageService.buildPublicUrl(anyString())).thenAnswer(call -> "https://cdn.test/" + call.getArgument(0));

        List<SplashResponse> pool = splashService.getPool();

        assertThat(pool).extracting(SplashResponse::getId).containsExactly(low.getId(), mid.getId(), high.getId());
    }

    @Test
    void urlIsBuiltFromTheObjectKeyAndTheRestIsCopied() {
        SplashVideo video = video("7d9c3f0e-5b1a-4c55-9d3e-0a1f6b2c8e11", "splashes/mid.mp4");
        when(splashPoolCache.getActive()).thenReturn(List.of(video));
        when(splashResolver.resolve(any(), any(Instant.class))).thenReturn(List.of(video));
        when(storageService.buildPublicUrl("splashes/mid.mp4")).thenReturn("https://cdn.test/splashes/mid.mp4");

        SplashResponse response = splashService.getPool().getFirst();

        assertThat(response.getId()).isEqualTo(video.getId());
        assertThat(response.getUrl()).isEqualTo("https://cdn.test/splashes/mid.mp4");
        assertThat(response.getChecksum()).isEqualTo("sum-splashes/mid.mp4");
        assertThat(response.getSizeBytes()).isEqualTo(123L);
    }

    @Test
    void emptyPoolReturnsAnEmptyList() {
        when(splashPoolCache.getActive()).thenReturn(List.of());
        when(splashResolver.resolve(any(), any(Instant.class))).thenReturn(List.of());

        assertThat(splashService.getPool()).isEmpty();
    }

    @Test
    void poolIsResolvedAgainstTheCurrentTime() {
        when(splashPoolCache.getActive()).thenReturn(List.of());
        when(splashResolver.resolve(any(), any(Instant.class))).thenReturn(List.of());

        Instant before = Instant.now();
        splashService.getPool();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> resolvedAt = ArgumentCaptor.forClass(Instant.class);
        verify(splashResolver).resolve(any(), resolvedAt.capture());
        assertThat(resolvedAt.getValue()).isBetween(before, after);
    }
}
