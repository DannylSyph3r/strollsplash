package dev.slethware.strollsplash.dto.request;

import dev.slethware.strollsplash.enums.SplashKind;
import lombok.Data;

import java.time.Instant;

@Data
public class UpdateSplashRequest {
    private String title;
    private SplashKind kind;
    private Instant startsAt;
    private Instant endsAt;
    private Boolean active;
    private Boolean pinned;
}
