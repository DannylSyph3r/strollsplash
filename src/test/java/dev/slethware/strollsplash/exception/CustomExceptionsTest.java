package dev.slethware.strollsplash.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomExceptionsTest {

    @Test
    void badRequestCarriesItsMessage() {
        BadRequestException exception = new BadRequestException("title must not be blank");

        assertThat(exception).hasMessage("title must not be blank");
    }

    @Test
    void badRequestHasADefaultMessage() {
        assertThat(new BadRequestException()).hasMessage("Error Processing Request!");
    }

    @Test
    void resourceNotFoundCarriesItsMessage() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Splash video not found");

        assertThat(exception).hasMessage("Splash video not found");
    }

    @Test
    void unauthorizedCarriesItsMessage() {
        assertThat(new UnauthorizedException("Invalid or missing admin key"))
                .hasMessage("Invalid or missing admin key");
    }

    @Test
    void unauthorizedHasADefaultMessage() {
        assertThat(new UnauthorizedException()).hasMessage("Unauthorized access");
    }

    @Test
    void internalServerCarriesItsMessageAndCause() {
        RuntimeException cause = new RuntimeException("boom");
        InternalServerException exception = new InternalServerException("Failed to upload video to storage", cause);

        assertThat(exception).hasMessage("Failed to upload video to storage").hasCause(cause);
    }
}
