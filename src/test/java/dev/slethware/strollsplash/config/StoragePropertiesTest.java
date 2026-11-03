package dev.slethware.strollsplash.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class StoragePropertiesTest {

    private static final String[] REQUIRED = {
            "storage.access-key-id=key",
            "storage.secret-access-key=secret",
            "storage.bucket-name=bucket",
            "storage.public-url=https://cdn.test"
    };

    @Configuration
    @EnableConfigurationProperties(StorageProperties.class)
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void bindsValuesFromTestProperties() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    StorageProperties properties = context.getBean(StorageProperties.class);

                    assertThat(properties.getAccessKeyId()).isEqualTo("test-access-key");
                    assertThat(properties.getSecretAccessKey()).isEqualTo("test-secret-key");
                    assertThat(properties.getBucketName()).isEqualTo("test-bucket");
                    assertThat(properties.getPublicUrl()).isEqualTo("https://cdn.test");
                });
    }

    @Test
    void regionDefaultsToUsEast1AndEndpointIsOptional() {
        runner.withPropertyValues(REQUIRED).run(context -> {
            StorageProperties properties = context.getBean(StorageProperties.class);

            assertThat(properties.getRegion()).isEqualTo("us-east-1");
            assertThat(properties.getEndpoint()).isEmpty();
        });
    }

    @Test
    void endpointAndRegionCanBeSet() {
        runner.withPropertyValues(REQUIRED)
                .withPropertyValues("storage.endpoint=https://acc.r2.cloudflarestorage.com", "storage.region=auto")
                .run(context -> {
                    StorageProperties properties = context.getBean(StorageProperties.class);

                    assertThat(properties.getEndpoint()).isEqualTo("https://acc.r2.cloudflarestorage.com");
                    assertThat(properties.getRegion()).isEqualTo("auto");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"storage.access-key-id", "storage.secret-access-key", "storage.bucket-name", "storage.public-url"})
    void startupFailsWhenARequiredValueIsMissing(String property) {
        String[] withoutIt = Arrays.stream(REQUIRED)
                .filter(entry -> !entry.startsWith(property + "="))
                .toArray(String[]::new);

        runner.withPropertyValues(withoutIt).run(context -> assertThat(context).getFailure()
                .hasRootCauseInstanceOf(BindValidationException.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"storage.access-key-id", "storage.secret-access-key", "storage.bucket-name", "storage.public-url"})
    void startupFailsWhenARequiredValueIsBlank(String property) {
        runner.withPropertyValues(REQUIRED).withPropertyValues(property + "=")
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class));
    }
}
