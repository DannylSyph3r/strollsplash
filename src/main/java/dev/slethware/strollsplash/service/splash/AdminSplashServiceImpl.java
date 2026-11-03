package dev.slethware.strollsplash.service.splash;

import dev.slethware.strollsplash.dto.request.UpdateSplashRequest;
import dev.slethware.strollsplash.dto.response.AdminSplashResponse;
import dev.slethware.strollsplash.entity.SplashVideo;
import dev.slethware.strollsplash.enums.SplashKind;
import dev.slethware.strollsplash.exception.BadRequestException;
import dev.slethware.strollsplash.exception.ResourceNotFoundException;
import dev.slethware.strollsplash.repository.SplashVideoRepository;
import dev.slethware.strollsplash.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSplashServiceImpl implements AdminSplashService {

    private final SplashVideoRepository splashVideoRepository;
    private final SplashValidator splashValidator;
    private final SplashPoolCache splashPoolCache;
    private final StorageService storageService;

    @Override
    public List<AdminSplashResponse> getAllSplashes() {
        return splashVideoRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public AdminSplashResponse updateSplash(UUID id, UpdateSplashRequest request) {
        if (isEmpty(request)) {
            throw new BadRequestException("At least one field must be provided");
        }

        SplashVideo video = splashVideoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Splash video not found"));

        // A field that is null in the request means no change, so each rule runs on the merged result
        SplashKind kind = request.getKind() != null ? request.getKind() : video.getKind();
        String title = request.getTitle() != null ? request.getTitle() : video.getTitle();

        Instant startsAt;
        Instant endsAt;
        if (kind == SplashKind.STANDARD) {
            // Switching to STANDARD drops the stored window; a window sent with it is a violation
            startsAt = request.getStartsAt();
            endsAt = request.getEndsAt();
        } else {
            startsAt = request.getStartsAt() != null ? request.getStartsAt() : video.getStartsAt();
            endsAt = request.getEndsAt() != null ? request.getEndsAt() : video.getEndsAt();
        }

        splashValidator.validateOrThrow(title, kind, startsAt, endsAt);

        video.setTitle(title);
        video.setKind(kind);
        video.setStartsAt(startsAt);
        video.setEndsAt(endsAt);
        if (request.getActive() != null) {
            video.setActive(request.getActive());
        }
        if (request.getPinned() != null) {
            video.setPinned(request.getPinned());
        }

        SplashVideo saved = splashVideoRepository.save(video);
        // Save first, then invalidate, so a concurrent reader cannot cache the old row
        splashPoolCache.invalidate();

        log.info("Splash video updated: id={}", id);
        return toResponse(saved);
    }

    private boolean isEmpty(UpdateSplashRequest request) {
        return request.getTitle() == null
                && request.getKind() == null
                && request.getStartsAt() == null
                && request.getEndsAt() == null
                && request.getActive() == null
                && request.getPinned() == null;
    }

    private AdminSplashResponse toResponse(SplashVideo video) {
        return AdminSplashResponse.builder()
                .id(video.getId())
                .title(video.getTitle())
                .kind(video.getKind())
                .url(storageService.buildPublicUrl(video.getObjectKey()))
                .checksum(video.getChecksum())
                .sizeBytes(video.getSizeBytes())
                .active(video.isActive())
                .pinned(video.isPinned())
                .startsAt(video.getStartsAt())
                .endsAt(video.getEndsAt())
                .createdAt(video.getCreatedAt())
                .updatedAt(video.getUpdatedAt())
                .build();
    }
}
