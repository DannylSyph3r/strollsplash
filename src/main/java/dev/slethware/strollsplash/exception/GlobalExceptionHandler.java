package dev.slethware.strollsplash.exception;

import dev.slethware.strollsplash.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.util.Arrays;
import java.util.stream.Collectors;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String UNEXPECTED_ERROR = "An unexpected error occurred";

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Object> handleBadRequestException(BadRequestException e) {
        log.warn("Bad request: {}", e.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFoundException(ResourceNotFoundException e) {
        log.warn("Resource not found: {}", e.getMessage());
        return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Object> handleUnauthorizedException(UnauthorizedException e) {
        log.warn("Unauthorized: {}", e.getMessage());
        return errorResponse(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Object> handleTooManyRequestsException(TooManyRequestsException e) {
        log.warn("Too many requests: {}", e.getMessage());
        return errorResponse(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(InternalServerException.class)
    public ResponseEntity<Object> handleInternalServerException(InternalServerException e) {
        log.error("Internal server error: {}", e.getMessage(), e);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception e) {
        log.error("Unexpected error occurred: {}", e.getMessage(), e);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR);
    }

    // MaxUploadSizeExceededException is a subclass and keeps its own 413 handler below, the closest match wins
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Object> handleMultipartException(MultipartException e) {
        log.warn("Multipart request rejected: {}", e.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, "Request is not valid multipart/form-data");
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("Request body not readable: {}", ex.getMessage());

        String message = switch (ex.getCause()) {
            case UnrecognizedPropertyException e -> "Unknown field '" + e.getPropertyName() + "'";
            case InvalidFormatException e -> invalidValueMessage(e.getValue(), fieldName(e), e.getTargetType());
            case null, default -> "Request body is not valid JSON";
        };
        return errorResponse(HttpStatus.BAD_REQUEST, message);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("Type mismatch: {}", ex.getMessage());

        String name = ex instanceof MethodArgumentTypeMismatchException e ? e.getName() : ex.getPropertyName();
        return errorResponse(HttpStatus.BAD_REQUEST, invalidValueMessage(ex.getValue(), name, ex.getRequiredType()));
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("Upload too large: {}", ex.getMessage());
        // Spring 7 names 413 "Content Too Large", but the contract says "Payload Too Large"
        return errorResponse(status, headers, "Payload Too Large", "File exceeds the maximum upload size");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Framework error: {}", ex.getMessage(), ex);
            return errorResponse(statusCode, UNEXPECTED_ERROR);
        }
        log.warn("Request rejected: {}", ex.getMessage());
        // Headers carry what the status requires, such as Allow on a 405
        return errorResponse(statusCode, headers, reasonPhrase(statusCode), ex.getMessage());
    }

    // Jackson's own text names Java classes and parser internals, so the message is built here instead
    private String invalidValueMessage(Object value, String name, Class<?> type) {
        String message = "Invalid value '" + value + "' for " + name;
        if (type != null && type.isEnum()) {
            message += ". Allowed values: " + Arrays.stream(type.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
        }
        return message;
    }

    private String fieldName(InvalidFormatException e) {
        return e.getPath().isEmpty() ? "request body" : e.getPath().getLast().getPropertyName();
    }

    private ResponseEntity<Object> errorResponse(HttpStatusCode status, String message) {
        return errorResponse(status, HttpHeaders.EMPTY, reasonPhrase(status), message);
    }

    private ResponseEntity<Object> errorResponse(
            HttpStatusCode status, HttpHeaders headers, String error, String message) {
        ErrorResponse response = ErrorResponse.builder()
                .message(message)
                .error(error)
                .statusCode(status.value())
                .build();

        // Fixed content type so a client whose Accept header excludes JSON still gets the error shape
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    private String reasonPhrase(HttpStatusCode status) {
        return HttpStatus.valueOf(status.value()).getReasonPhrase();
    }
}
