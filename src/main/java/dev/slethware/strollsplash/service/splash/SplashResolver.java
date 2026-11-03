package dev.slethware.strollsplash.service.splash;

import dev.slethware.strollsplash.entity.SplashVideo;
import dev.slethware.strollsplash.enums.SplashKind;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class SplashResolver {

    public List<SplashVideo> resolve(List<SplashVideo> rows, Instant now) {
        List<SplashVideo> live = rows.stream().filter(SplashVideo::isActive).toList();

        List<SplashVideo> events = live.stream()
                .filter(v -> v.getKind() == SplashKind.EVENT)
                // Event rows need both timestamps, so one with a gap is treated as not live
                .filter(v -> v.getStartsAt() != null && v.getEndsAt() != null)
                .filter(v -> !now.isBefore(v.getStartsAt()) && now.isBefore(v.getEndsAt()))
                .toList();

        List<SplashVideo> tier = !events.isEmpty()
                ? events
                : live.stream().filter(v -> v.getKind() == SplashKind.STANDARD).toList();

        // Pinned rows narrow the tier but never lift a standard video above a live event
        List<SplashVideo> pinned = tier.stream().filter(SplashVideo::isPinned).toList();
        return pinned.isEmpty() ? tier : pinned;
    }
}
