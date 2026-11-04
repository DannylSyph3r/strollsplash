package dev.slethware.strollsplash.service.splash;

import dev.slethware.strollsplash.dto.request.UpdateSplashRequest;
import dev.slethware.strollsplash.dto.response.AdminSplashResponse;
import dev.slethware.strollsplash.enums.SplashKind;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

public interface AdminSplashService {

    AdminSplashResponse uploadSplash(MultipartFile file, String title, SplashKind kind, Instant startsAt, Instant endsAt);
    Page<AdminSplashResponse> getAllSplashes(int page, int size);
    AdminSplashResponse updateSplash(UUID id, UpdateSplashRequest request);
}
