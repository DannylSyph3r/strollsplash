package dev.slethware.strollsplash.service.splash;

import dev.slethware.strollsplash.config.SplashProperties;
import dev.slethware.strollsplash.entity.SplashVideo;
import dev.slethware.strollsplash.repository.SplashVideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SplashPoolCacheTest {

    @Mock
    private SplashVideoRepository repository;

    private SplashProperties properties;
    private final SplashVideo video = SplashVideo.builder().title("a").active(true).build();

    @BeforeEach
    void setUp() {
        properties = new SplashProperties();
        when(repository.findAllByActiveTrue()).thenReturn(List.of(video));
    }

    @Test
    void secondReadIsServedFromTheCache() {
        SplashPoolCache cache = new SplashPoolCache(repository, properties);

        assertThat(cache.getActive()).containsExactly(video);
        assertThat(cache.getActive()).containsExactly(video);

        verify(repository, times(1)).findAllByActiveTrue();
    }

    @Test
    void invalidateForcesAReload() {
        SplashPoolCache cache = new SplashPoolCache(repository, properties);

        cache.getActive();
        cache.invalidate();
        cache.getActive();

        verify(repository, times(2)).findAllByActiveTrue();
    }

    @Test
    void entryExpiresAfterTheConfiguredTtl() {
        properties.getCache().setTtl(Duration.ofMillis(50));
        SplashPoolCache cache = new SplashPoolCache(repository, properties);

        cache.getActive();

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            cache.getActive();
            verify(repository, times(2)).findAllByActiveTrue();
        });
    }
}
