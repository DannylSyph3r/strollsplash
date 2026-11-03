package dev.slethware.strollsplash.service.splash;

import dev.slethware.strollsplash.dto.request.UpdateSplashRequest;
import dev.slethware.strollsplash.dto.response.AdminSplashResponse;

import java.util.List;
import java.util.UUID;

public interface AdminSplashService {

    List<AdminSplashResponse> getAllSplashes();

    AdminSplashResponse updateSplash(UUID id, UpdateSplashRequest request);
}
