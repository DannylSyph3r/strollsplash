package dev.slethware.strollsplash.interceptor;

import dev.slethware.strollsplash.config.SplashProperties;
import dev.slethware.strollsplash.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@RequiredArgsConstructor
public class AdminKeyInterceptor implements HandlerInterceptor {

    private static final String HEADER = "X-Admin-Key";

    private final SplashProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String supplied = request.getHeader(HEADER);
        byte[] expected = properties.getAdminKey().getBytes(StandardCharsets.UTF_8);

        // Constant-time compare so response timing does not reveal how much of a guess matched
        if (supplied == null || !MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), expected)) {
            throw new UnauthorizedException("Invalid or missing admin key");
        }
        return true;
    }
}
