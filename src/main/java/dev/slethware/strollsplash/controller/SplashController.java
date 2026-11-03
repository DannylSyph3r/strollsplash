package dev.slethware.strollsplash.controller;

import dev.slethware.strollsplash.config.SplashProperties;
import dev.slethware.strollsplash.dto.response.ApiResponse;
import dev.slethware.strollsplash.dto.response.SplashResponse;
import dev.slethware.strollsplash.service.splash.SplashService;
import dev.slethware.strollsplash.utility.ApiResponseUtil;
import dev.slethware.strollsplash.utility.ChecksumUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/splashes")
@RequiredArgsConstructor
@Tag(name = "Splash Screens", description = "Public endpoint that serves the pool of splash videos the client may play")
public class SplashController {

    private final SplashService splashService;
    private final SplashProperties splashProperties;

    @GetMapping
    @Operation(
            summary = "Get the splash video pool",
            description = "Returns the videos the client may play right now. A live event replaces the normal rotation, and pinned videos narrow it. The client picks one at random. The response is the same for every caller, so it carries Cache-Control and an ETag, and a matching If-None-Match gets 304 Not Modified. No authentication."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Splashes retrieved successfully, an empty list means the client plays its bundled default"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "304", description = "The pool has not changed since the ETag in If-None-Match, no body")
    })
    public ResponseEntity<ApiResponse<List<SplashResponse>>> getSplashes() {
        List<SplashResponse> pool = splashService.getPool();

        // Spring turns a matching If-None-Match on this ETag into a 304 and keeps these headers
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(splashProperties.getHttp().getMaxAge()).cachePublic())
                .eTag(ChecksumUtil.etagFor(pool))
                .body(ApiResponseUtil.successFull("Splashes retrieved successfully", pool));
    }
}
