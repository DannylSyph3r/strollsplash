package dev.slethware.strollsplash.service.splash;

import dev.slethware.strollsplash.enums.SplashKind;
import dev.slethware.strollsplash.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplashValidatorTest {

    private static final Instant START = Instant.parse("2026-12-24T00:00:00Z");
    private static final Instant END = Instant.parse("2026-12-27T00:00:00Z");

    private final SplashValidator validator = new SplashValidator();

    @Test
    void blankTitleIsRejected() {
        assertThat(validator.validate("   ", SplashKind.STANDARD, null, null))
                .containsExactly("title must not be blank");
    }

    @Test
    void missingTitleIsRejected() {
        assertThat(validator.validate(null, SplashKind.STANDARD, null, null))
                .containsExactly("title must not be blank");
    }

    @Test
    void titleOverOneHundredTwentyCharactersIsRejected() {
        assertThat(validator.validate("x".repeat(121), SplashKind.STANDARD, null, null))
                .containsExactly("title must be at most 120 characters");
    }

    @Test
    void titleOfExactlyOneHundredTwentyCharactersPasses() {
        assertThat(validator.validate("x".repeat(120), SplashKind.STANDARD, null, null)).isEmpty();
    }

    @Test
    void missingKindIsRejected() {
        assertThat(validator.validate("Original", null, null, null)).containsExactly("kind is required");
    }

    @Test
    void eventWithoutStartsAtIsRejected() {
        assertThat(validator.validate("Christmas", SplashKind.EVENT, null, END))
                .containsExactly("startsAt is required when kind is EVENT");
    }

    @Test
    void eventWithoutEndsAtIsRejected() {
        assertThat(validator.validate("Christmas", SplashKind.EVENT, START, null))
                .containsExactly("endsAt is required when kind is EVENT");
    }

    @Test
    void eventWithoutEitherTimestampGetsBothRequiredMessages() {
        assertThat(validator.validate("Christmas", SplashKind.EVENT, null, null)).containsExactly(
                "startsAt is required when kind is EVENT",
                "endsAt is required when kind is EVENT");
    }

    @Test
    void eventEndingBeforeItStartsIsRejected() {
        assertThat(validator.validate("Christmas", SplashKind.EVENT, END, START))
                .containsExactly("endsAt must be after startsAt");
    }

    @Test
    void eventEndingAtTheInstantItStartsIsRejected() {
        assertThat(validator.validate("Christmas", SplashKind.EVENT, START, START))
                .containsExactly("endsAt must be after startsAt");
    }

    @Test
    void standardWithStartsAtIsRejected() {
        assertThat(validator.validate("Original", SplashKind.STANDARD, START, null))
                .containsExactly("startsAt and endsAt must not be set when kind is STANDARD");
    }

    @Test
    void standardWithEndsAtIsRejected() {
        assertThat(validator.validate("Original", SplashKind.STANDARD, null, END))
                .containsExactly("startsAt and endsAt must not be set when kind is STANDARD");
    }

    @Test
    void validEventPasses() {
        assertThat(validator.validate("Christmas", SplashKind.EVENT, START, END)).isEmpty();
    }

    @Test
    void validStandardPasses() {
        assertThat(validator.validate("Original", SplashKind.STANDARD, null, null)).isEmpty();
    }

    @Test
    void severalViolationsAreAllReported() {
        List<String> violations = validator.validate(" ", SplashKind.EVENT, END, START);

        assertThat(violations).containsExactly("title must not be blank", "endsAt must be after startsAt");
    }

    @Test
    void validateOrThrowJoinsEveryViolationIntoOneBadRequest() {
        assertThatThrownBy(() -> validator.validateOrThrow(" ", SplashKind.EVENT, END, START))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("title must not be blank; endsAt must be after startsAt");
    }

    @Test
    void validateOrThrowStaysQuietForAValidRow() {
        assertThatCode(() -> validator.validateOrThrow("Christmas", SplashKind.EVENT, START, END))
                .doesNotThrowAnyException();
    }
}
