package dev.slethware.strollsplash.config;

import dev.slethware.strollsplash.interceptor.AdminKeyInterceptor;
import dev.slethware.strollsplash.interceptor.UploadThrottleInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AdminKeyInterceptor adminKeyInterceptor;
    private final UploadThrottleInterceptor uploadThrottleInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminKeyInterceptor).addPathPatterns("/api/v1/admin/**");
        // Registered after the key check, so only callers with the key can use up the quota
        registry.addInterceptor(uploadThrottleInterceptor).addPathPatterns("/api/v1/admin/splashes");
    }
}
