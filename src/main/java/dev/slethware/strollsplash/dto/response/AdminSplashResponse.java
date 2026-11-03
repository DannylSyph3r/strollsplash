package dev.slethware.strollsplash.dto.response;

import dev.slethware.strollsplash.enums.SplashKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSplashResponse {
    private UUID id;
    private String title;
    private SplashKind kind;
    private String url;
    private String checksum;
    private Long sizeBytes;
    private boolean active;
    private boolean pinned;
    private Instant startsAt;
    private Instant endsAt;
    private Instant createdAt;
    private Instant updatedAt;
}
