package dev.slethware.strollsplash.service.splash;

import dev.slethware.strollsplash.enums.SplashKind;
import dev.slethware.strollsplash.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class SplashValidator {

    private static final int MAX_TITLE_LENGTH = 120;

    public List<String> validate(String title, SplashKind kind, Instant startsAt, Instant endsAt) {
        List<String> violations = new ArrayList<>();

        if (title == null || title.isBlank()) {
            violations.add("title must not be blank");
        } else if (title.length() > MAX_TITLE_LENGTH) {
            violations.add("title must be at most " + MAX_TITLE_LENGTH + " characters");
        }

        if (kind == null) {
            violations.add("kind is required");
        } else if (kind == SplashKind.EVENT) {
            if (startsAt == null) {
                violations.add("startsAt is required when kind is EVENT");
            }
            if (endsAt == null) {
                violations.add("endsAt is required when kind is EVENT");
            }
            if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
                violations.add("endsAt must be after startsAt");
            }
        } else if (startsAt != null || endsAt != null) {
            violations.add("startsAt and endsAt must not be set when kind is STANDARD");
        }

        return violations;
    }

    public void validateOrThrow(String title, SplashKind kind, Instant startsAt, Instant endsAt) {
        List<String> violations = validate(title, kind, startsAt, endsAt);
        if (!violations.isEmpty()) {
            throw new BadRequestException(String.join("; ", violations));
        }
    }
}
