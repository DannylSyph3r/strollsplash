package dev.slethware.strollsplash.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplashResponse {
    private UUID id;
    private String url;
    private String checksum;
    private Long sizeBytes;
}
