package dev.slethware.strollsplash.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    // Blank means AWS S3, a value points the client at R2 or another S3-compatible store
    private String endpoint = "";

    // R2 ignores the region but the SDK requires one
    private String region = "us-east-1";

    @NotBlank
    private String accessKeyId;

    @NotBlank
    private String secretAccessKey;

    @NotBlank
    private String bucketName;

    @NotBlank
    private String publicUrl;
}
