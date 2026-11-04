package dev.slethware.strollsplash.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "splash")
public class SplashProperties {

    @NotBlank
    private String adminKey;

    private CacheConfig cache = new CacheConfig();
    private HttpConfig http = new HttpConfig();
    @Valid
    private UploadConfig upload = new UploadConfig();

    @Data
    public static class CacheConfig {
        private Duration ttl = Duration.ofMinutes(5);
    }

    @Data
    public static class HttpConfig {
        private Duration maxAge = Duration.ofMinutes(5);
    }

    @Data
    public static class UploadConfig {
        @Min(1)
        private int maxPerMinute = 10;
    }
}
