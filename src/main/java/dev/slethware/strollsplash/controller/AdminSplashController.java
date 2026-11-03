package dev.slethware.strollsplash.controller;

import dev.slethware.strollsplash.dto.request.UpdateSplashRequest;
import dev.slethware.strollsplash.dto.response.AdminSplashResponse;
import dev.slethware.strollsplash.dto.response.ApiResponse;
import dev.slethware.strollsplash.enums.SplashKind;
import dev.slethware.strollsplash.service.splash.AdminSplashService;
import dev.slethware.strollsplash.utility.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/splashes")
@RequiredArgsConstructor
@SecurityRequirement(name = "AdminKey")
@Tag(name = "Splash Admin", description = "Endpoints for the design team to upload, list and update splash videos, guarded by the X-Admin-Key header")
public class AdminSplashController {

    private final AdminSplashService adminSplashService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a splash video",
            description = "Uploads an mp4 to object storage and creates the row as inactive and unpinned, so it only reaches the app once switched on with PATCH. The server computes the SHA-256 and size and picks the storage key. kind defaults to STANDARD, and an EVENT needs startsAt and endsAt as ISO-8601 timestamps with an offset. Every violation is reported together in one message."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Splash video uploaded successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing file, not video/mp4, blank or over-long title, bad kind or timestamps, all violations are joined in one message"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or wrong X-Admin-Key"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "File exceeds the maximum upload size"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Failed to upload video to storage")
    })
    public ResponseEntity<ApiResponse<AdminSplashResponse>> uploadSplash(
            @Parameter(description = "The mp4 video, content type video/mp4", required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam(value = "file", required = false) MultipartFile file,
            @Parameter(description = "Admin label, at most 120 characters", required = true)
            @RequestParam(value = "title", required = false) String title,
            @Parameter(description = "STANDARD (default) or EVENT")
            @RequestParam(value = "kind", defaultValue = "STANDARD") SplashKind kind,
            @Parameter(description = "Event start, ISO-8601 with an offset, required for EVENT")
            @RequestParam(value = "startsAt", required = false) Instant startsAt,
            @Parameter(description = "Event end, ISO-8601 with an offset, required for EVENT")
            @RequestParam(value = "endsAt", required = false) Instant endsAt) {

        // Every part is optional at the framework level so the service can report all violations together
        AdminSplashResponse response = adminSplashService.uploadSplash(file, title, kind, startsAt, endsAt);

        return new ResponseEntity<>(
                ApiResponseUtil.created("Splash video uploaded successfully", response),
                HttpStatus.CREATED
        );
    }

    @GetMapping
    @Operation(
            summary = "List all splash videos",
            description = "Returns every splash video, active or not, newest first. There is no pagination."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Splash videos retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or wrong X-Admin-Key")
    })
    public ResponseEntity<ApiResponse<List<AdminSplashResponse>>> getAllSplashes() {
        List<AdminSplashResponse> splashes = adminSplashService.getAllSplashes();

        return new ResponseEntity<>(
                ApiResponseUtil.successFull("Splash videos retrieved successfully", splashes),
                HttpStatus.OK
        );
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Update a splash video",
            description = "Partial update of title, kind, startsAt, endsAt, active and pinned. A field that is left out or null is not changed, so {\"active\": false} switches a video off and {\"pinned\": true} is a complete request. At least one field is required and unknown fields are rejected. Timestamps are ISO-8601 with an offset and are stored as UTC. Switching to STANDARD clears the event window, and switching to EVENT needs both timestamps in the same request."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Splash video updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "No fields given, unknown field, bad value or a violated rule, all violations are joined in one message"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or wrong X-Admin-Key"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Splash video not found")
    })
    public ResponseEntity<ApiResponse<AdminSplashResponse>> updateSplash(
            @Parameter(description = "Splash video ID", required = true)
            @PathVariable UUID id,
            @RequestBody UpdateSplashRequest request) {

        AdminSplashResponse response = adminSplashService.updateSplash(id, request);

        return new ResponseEntity<>(
                ApiResponseUtil.successFull("Splash video updated successfully", response),
                HttpStatus.OK
        );
    }
}
