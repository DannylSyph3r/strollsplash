package dev.slethware.strollsplash.interceptor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import dev.slethware.strollsplash.config.SplashProperties;
import dev.slethware.strollsplash.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class UploadThrottleInterceptor implements HandlerInterceptor {

    private static final String WINDOW = "window";

    private final int maxPerMinute;
    private final Cache<String, AtomicInteger> uploads;

    @Autowired
    public UploadThrottleInterceptor(SplashProperties properties) {
        this(properties, Ticker.systemTicker());
    }

    UploadThrottleInterceptor(SplashProperties properties, Ticker ticker) {
        this.maxPerMinute = properties.getUpload().getMaxPerMinute();
        // The counter is only created, never rewritten, so each minute starts with the first upload
        this.uploads = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .ticker(ticker)
                .maximumSize(1)
                .build();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        if (uploads.get(WINDOW, k -> new AtomicInteger()).incrementAndGet() > maxPerMinute) {
            throw new TooManyRequestsException("Too many uploads, try again in a minute");
        }
        return true;
    }
}
