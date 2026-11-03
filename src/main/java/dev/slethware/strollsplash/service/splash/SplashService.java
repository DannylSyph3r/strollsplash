package dev.slethware.strollsplash.service.splash;

import dev.slethware.strollsplash.dto.response.SplashResponse;

import java.util.List;

public interface SplashService {

    List<SplashResponse> getPool();
}
