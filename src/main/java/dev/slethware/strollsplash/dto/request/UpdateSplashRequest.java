package dev.slethware.strollsplash.dto.request;

import dev.slethware.strollsplash.enums.SplashKind;
import lombok.Data;

import java.time.Instant;

// Wrapper types so an absent field is never mistaken for false. Every rule is checked by SplashValidator on the merged row
@Data
public class UpdateSplashRequest {
    private String title;
    private SplashKind kind;
    private Instant startsAt;
    private Instant endsAt;
    private Boolean active;
    private Boolean pinned;
}
