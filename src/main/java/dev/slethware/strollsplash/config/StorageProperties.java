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

    private String endpoint = "";

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
