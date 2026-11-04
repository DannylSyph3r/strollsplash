package dev.slethware.strollsplash.service.splash;

import dev.slethware.strollsplash.entity.SplashVideo;
import dev.slethware.strollsplash.enums.SplashKind;
import dev.slethware.strollsplash.exception.BadRequestException;
import dev.slethware.strollsplash.repository.SplashVideoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SplashSeederTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private SplashVideoRepository repository;

    @Mock
    private SplashValidator mockValidator;

    @Captor
    private ArgumentCaptor<List<SplashVideo>> saved;

    private SplashSeeder seederWith(SplashValidator validator) {
        return new SplashSeeder(repository, validator, jsonMapper);
    }

    @Test
    void emptyTableSavesAllEightRows() throws Exception {
        when(repository.count()).thenReturn(0L);

        seederWith(mockValidator).run();

        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(8);
        verify(mockValidator, times(8)).validateOrThrow(any(), any(), any(), any());
    }

    @Test
    void nonEmptyTableSavesNothing() throws Exception {
        when(repository.count()).thenReturn(3L);

        seederWith(mockValidator).run();

        verify(repository, never()).saveAll(any());
        verifyNoInteractions(mockValidator);
    }

    @Test
    void invalidRowAbortsStartupAndSavesNothing() {
        when(repository.count()).thenReturn(0L);
        lenient().doThrow(new BadRequestException("endsAt must be after startsAt"))
                .when(mockValidator).validateOrThrow(eq("Halloween 2026"), any(), any(), any());

        assertThatThrownBy(() -> seederWith(mockValidator).run())
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid seed row 'Halloween 2026': endsAt must be after startsAt");

        verify(repository, never()).saveAll(any());
    }

    @Test
    void seedFileHoldsTheSixStandardRowsAndTheTwoEventsFromTheNotes() throws Exception {
        when(repository.count()).thenReturn(0L);

        seederWith(new SplashValidator()).run();

        verify(repository).saveAll(saved.capture());
        List<SplashVideo> rows = saved.getValue();
        assertThat(rows).hasSize(8).allMatch(SplashVideo::isActive).noneMatch(SplashVideo::isPinned);

        List<SplashVideo> standard = rows.stream().filter(row -> row.getKind() == SplashKind.STANDARD).toList();
        assertThat(standard).hasSize(6).allSatisfy(row -> {
            assertThat(row.getStartsAt()).isNull();
            assertThat(row.getEndsAt()).isNull();
        });

        SplashVideo halloween = byTitle(rows, "Halloween 2026");
        assertThat(halloween.getKind()).isEqualTo(SplashKind.EVENT);
        assertThat(halloween.getStartsAt()).isEqualTo(Instant.parse("2026-10-30T00:00:00Z"));
        assertThat(halloween.getEndsAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));

        SplashVideo christmas = byTitle(rows, "Christmas 2026");
        assertThat(christmas.getKind()).isEqualTo(SplashKind.EVENT);
        assertThat(christmas.getStartsAt()).isEqualTo(Instant.parse("2026-12-24T00:00:00Z"));
        assertThat(christmas.getEndsAt()).isEqualTo(Instant.parse("2026-12-27T00:00:00Z"));
    }

    @Test
    void seedRowsHaveUniqueKeysAndChecksumsThatFitTheColumn() throws Exception {
        when(repository.count()).thenReturn(0L);

        seederWith(new SplashValidator()).run();

        verify(repository).saveAll(saved.capture());
        List<SplashVideo> rows = saved.getValue();
        assertThat(rows).extracting(SplashVideo::getObjectKey)
                .doesNotHaveDuplicates()
                .allMatch(key -> key.matches("splashes/seed-0[1-8]-[a-z-]+\\.mp4"));
        assertThat(rows).extracting(SplashVideo::getChecksum).allMatch(checksum -> checksum.length() <= 64);
        assertThat(rows).extracting(SplashVideo::getSizeBytes).allMatch(size -> size != null && size >= 0);
    }

    private SplashVideo byTitle(List<SplashVideo> rows, String title) {
        return rows.stream().filter(row -> title.equals(row.getTitle())).findFirst().orElseThrow();
    }
}
