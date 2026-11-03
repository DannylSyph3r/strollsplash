package dev.slethware.strollsplash.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {

    public BadRequestException() {
        this("Error Processing Request!");
    }

    public BadRequestException(String message) {
        super(message);
    }
}
