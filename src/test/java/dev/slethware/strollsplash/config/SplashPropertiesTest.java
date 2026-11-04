package dev.slethware.strollsplash.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SplashPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(SplashProperties.class)
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void bindsValuesFromTestProperties() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                // kebab-case in the file must reach the camelCase field
                .withPropertyValues("splash.http.max-age=90s")
                .run(context -> {
                    SplashProperties properties = context.getBean(SplashProperties.class);

                    assertThat(properties.getAdminKey()).isEqualTo("test-key");
                    assertThat(properties.getCache().getTtl()).isEqualTo(Duration.ofMinutes(2));
                    assertThat(properties.getHttp().getMaxAge()).isEqualTo(Duration.ofSeconds(90));
                });
    }

    @Test
    void cacheTtlAndHttpMaxAgeDefaultToFiveMinutes() {
        runner.withPropertyValues("splash.admin-key=any-key")
                .run(context -> {
                    SplashProperties properties = context.getBean(SplashProperties.class);

                    assertThat(properties.getCache().getTtl()).isEqualTo(Duration.ofMinutes(5));
                    assertThat(properties.getHttp().getMaxAge()).isEqualTo(Duration.ofMinutes(5));
                });
    }

    @Test
    void uploadLimitDefaultsToTenPerMinuteAndTakesAnOverride() {
        runner.withPropertyValues("splash.admin-key=any-key")
                .run(context -> assertThat(context.getBean(SplashProperties.class).getUpload().getMaxPerMinute()).isEqualTo(10));

        runner.withPropertyValues("splash.admin-key=any-key", "splash.upload.max-per-minute=25")
                .run(context -> assertThat(context.getBean(SplashProperties.class).getUpload().getMaxPerMinute()).isEqualTo(25));
    }

    @Test
    void startupFailsWhenTheUploadLimitIsBelowOne() {
        runner.withPropertyValues("splash.admin-key=any-key", "splash.upload.max-per-minute=0")
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class));
    }

    @Test
    void startupFailsWithoutAdminKey() {
        runner.run(context -> assertThat(context).getFailure()
                .hasRootCauseInstanceOf(BindValidationException.class));
    }

    @Test
    void startupFailsWithBlankAdminKey() {
        runner.withPropertyValues("splash.admin-key=")
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class));
    }
}
