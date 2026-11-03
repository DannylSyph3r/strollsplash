package dev.slethware.strollsplash.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class CustomExceptionsTest {

    @Test
    void badRequestCarriesItsMessageAndStatus() {
        BadRequestException exception = new BadRequestException("title must not be blank");

        assertThat(exception).hasMessage("title must not be blank");
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void badRequestHasADefaultMessage() {
        assertThat(new BadRequestException()).hasMessage("Error Processing Request!");
    }

    @Test
    void resourceNotFoundCarriesItsMessageAndStatus() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Splash video not found");

        assertThat(exception).hasMessage("Splash video not found");
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
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
    void internalServerCarriesItsMessageStatusAndCause() {
        RuntimeException cause = new RuntimeException("boom");
        InternalServerException exception = new InternalServerException("Failed to upload video to storage", cause);

        assertThat(exception).hasMessage("Failed to upload video to storage").hasCause(cause);
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
