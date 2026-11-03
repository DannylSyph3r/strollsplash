package dev.slethware.strollsplash.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private String message;
    private Integer statusCode;
    // Wrapper type keeps the JSON key "isSuccessful"; a primitive would serialise as "successful"
    private Boolean isSuccessful;
    private T data;
}
