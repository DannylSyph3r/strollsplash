package dev.slethware.strollsplash.service.splash;

import dev.slethware.strollsplash.dto.request.UpdateSplashRequest;
import dev.slethware.strollsplash.dto.response.AdminSplashResponse;
import dev.slethware.strollsplash.entity.SplashVideo;
import dev.slethware.strollsplash.enums.SplashKind;
import dev.slethware.strollsplash.exception.BadRequestException;
import dev.slethware.strollsplash.exception.ResourceNotFoundException;
import dev.slethware.strollsplash.repository.SplashVideoRepository;
import dev.slethware.strollsplash.service.storage.StorageService;
import dev.slethware.strollsplash.utility.ChecksumUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSplashServiceImpl implements AdminSplashService {

    private static final String VIDEO_MP4 = "video/mp4";
    private static final int MAX_PAGE_SIZE = 100;
    // The id breaks ties on createdAt, so a row can never repeat or vanish between pages
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final SplashVideoRepository splashVideoRepository;
    private final SplashValidator splashValidator;
    private final SplashPoolCache splashPoolCache;
    private final StorageService storageService;

    @Override
    public AdminSplashResponse uploadSplash(
            MultipartFile file, String title, SplashKind kind, Instant startsAt, Instant endsAt) {
        List<String> violations = new ArrayList<>(splashValidator.validate(title, kind, startsAt, endsAt));
        if (file == null || file.isEmpty()) {
            violations.add("file is required");
        } else if (!VIDEO_MP4.equals(file.getContentType())) {
            violations.add("Only video/mp4 files are accepted");
        }
        if (!violations.isEmpty()) {
            throw new BadRequestException(String.join("; ", violations));
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read uploaded file: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to read uploaded file");
        }

        String key = "splashes/" + UUID.randomUUID() + ".mp4";

        // Storage first, so a failed upload leaves no row pointing at a missing object
        storageService.upload(key, VIDEO_MP4, data);

        // A new row is inactive, and the cache only holds active rows, so there is nothing to invalidate
        SplashVideo saved = splashVideoRepository.save(SplashVideo.builder()
                .title(title)
                .kind(kind)
                .objectKey(key)
                .checksum(ChecksumUtil.sha256Hex(data))
                .sizeBytes((long) data.length)
                .active(false)
                .pinned(false)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build());

        log.info("splash.upload.done id={} key={} sizeBytes={}", saved.getId(), key, data.length);
        return toResponse(saved);
    }

    @Override
    public Page<AdminSplashResponse> getAllSplashes(int page, int size) {
        List<String> violations = new ArrayList<>();
        if (page < 0) {
            violations.add("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            violations.add("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (!violations.isEmpty()) {
            throw new BadRequestException(String.join("; ", violations));
        }
        return splashVideoRepository.findAll(PageRequest.of(page, size, NEWEST_FIRST)).map(this::toResponse);
    }

    @Override
    public AdminSplashResponse updateSplash(UUID id, UpdateSplashRequest request) {
        if (isEmpty(request)) {
            throw new BadRequestException("At least one field must be provided");
        }

        SplashVideo video = splashVideoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Splash video not found"));

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

        log.info("splash.update.done id={} kind={} active={} pinned={}", id, saved.getKind(), saved.isActive(), saved.isPinned());
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
