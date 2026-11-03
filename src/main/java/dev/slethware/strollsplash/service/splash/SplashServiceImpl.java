package dev.slethware.strollsplash.service.splash;

import dev.slethware.strollsplash.dto.response.SplashResponse;
import dev.slethware.strollsplash.entity.SplashVideo;
import dev.slethware.strollsplash.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SplashServiceImpl implements SplashService {

    private final SplashPoolCache splashPoolCache;
    private final SplashResolver splashResolver;
    private final StorageService storageService;

    @Override
    public List<SplashResponse> getPool() {
        return splashResolver.resolve(splashPoolCache.getActive(), Instant.now()).stream()
                // A fixed order keeps the same pool producing the same ETag
                .sorted(Comparator.comparing(video -> video.getId().toString()))
                .map(this::toResponse)
                .toList();
    }

    private SplashResponse toResponse(SplashVideo video) {
        return SplashResponse.builder()
                .id(video.getId())
                .url(storageService.buildPublicUrl(video.getObjectKey()))
                .checksum(video.getChecksum())
                .sizeBytes(video.getSizeBytes())
                .build();
    }
}
