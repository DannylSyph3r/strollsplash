package dev.slethware.strollsplash.service.splash;

import dev.slethware.strollsplash.dto.request.UpdateSplashRequest;
import dev.slethware.strollsplash.dto.response.AdminSplashResponse;
import dev.slethware.strollsplash.enums.SplashKind;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AdminSplashService {

    AdminSplashResponse uploadSplash(MultipartFile file, String title, SplashKind kind, Instant startsAt, Instant endsAt);

    List<AdminSplashResponse> getAllSplashes();

    AdminSplashResponse updateSplash(UUID id, UpdateSplashRequest request);
}
