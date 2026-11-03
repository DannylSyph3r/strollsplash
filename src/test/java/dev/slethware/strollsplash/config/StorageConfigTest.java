package dev.slethware.strollsplash.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class StorageConfigTest {

    private StorageProperties properties(String endpoint) {
        StorageProperties properties = new StorageProperties();
        properties.setEndpoint(endpoint);
        properties.setAccessKeyId("key");
        properties.setSecretAccessKey("secret");
        properties.setBucketName("bucket");
        properties.setPublicUrl("https://cdn.test");
        return properties;
    }

    @Test
    void blankEndpointLeavesTheDefaultAwsEndpoint() {
        try (S3Client client = new StorageConfig(properties("")).s3Client()) {
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
            assertThat(client.serviceClientConfiguration().region().id()).isEqualTo("us-east-1");
        }
    }

    @Test
    void endpointValueOverridesTheEndpoint() {
        try (S3Client client = new StorageConfig(properties("https://acc.r2.cloudflarestorage.com")).s3Client()) {
            assertThat(client.serviceClientConfiguration().endpointOverride())
                    .contains(URI.create("https://acc.r2.cloudflarestorage.com"));
        }
    }
}
