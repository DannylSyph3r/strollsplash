package dev.slethware.strollsplash.service.splash;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.slethware.strollsplash.config.SplashProperties;
import dev.slethware.strollsplash.entity.SplashVideo;
import dev.slethware.strollsplash.repository.SplashVideoRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SplashPoolCache {

    private static final String KEY = "active";

    private final SplashVideoRepository repository;
    private final Cache<String, List<SplashVideo>> cache;

    public SplashPoolCache(SplashVideoRepository repository, SplashProperties properties) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.getCache().getTtl())
                .maximumSize(1)
                .build();
    }

    // Only raw active rows are cached; the tier is resolved per request so event boundaries stay exact
    public List<SplashVideo> getActive() {
        return cache.get(KEY, k -> List.copyOf(repository.findAllByActiveTrue()));
    }

    public void invalidate() {
        cache.invalidateAll();
    }
}
