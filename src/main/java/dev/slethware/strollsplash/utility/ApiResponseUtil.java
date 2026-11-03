package dev.slethware.strollsplash.utility;

import dev.slethware.strollsplash.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;

public class ApiResponseUtil {

    private ApiResponseUtil() {}

    public static <T> ApiResponse<T> created(String message, T data) {
        return ApiResponse.<T>builder()
                .message(message)
                .statusCode(HttpStatus.CREATED.value())
                .isSuccessful(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> successFull(String message, T data) {
        return ApiResponse.<T>builder()
                .message(message)
                .statusCode(HttpStatus.OK.value())
                .isSuccessful(true)
                .data(data)
                .build();
    }
}
