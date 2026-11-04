package dev.slethware.strollsplash.interceptor;

import com.github.benmanes.caffeine.cache.Ticker;
import dev.slethware.strollsplash.config.SplashProperties;
import dev.slethware.strollsplash.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadThrottleInterceptorTest {

    private static final int LIMIT = 3;

    private final AtomicLong nanos = new AtomicLong();
    private UploadThrottleInterceptor interceptor;

    @BeforeEach
    void setUp() {
        SplashProperties properties = new SplashProperties();
        properties.getUpload().setMaxPerMinute(LIMIT);
        Ticker ticker = nanos::get;
        interceptor = new UploadThrottleInterceptor(properties, ticker);
    }

    private boolean call(String method) {
        return interceptor.preHandle(new MockHttpServletRequest(method, "/api/v1/admin/splashes"),
                new MockHttpServletResponse(), new Object());
    }

    private void postTimes(int times) {
        for (int i = 0; i < times; i++) {
            assertThat(call("POST")).isTrue();
        }
    }

    private void pass(Duration time) {
        nanos.addAndGet(time.toNanos());
    }

    @Test
    void allowsUploadsUpToTheLimit() {
        postTimes(LIMIT);
    }

    @Test
    void refusesTheUploadAfterTheLimitWithAClearMessage() {
        postTimes(LIMIT);

        assertThatThrownBy(() -> call("POST"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("Too many uploads, try again in a minute");
    }

    @Test
    void keepsRefusingUntilTheMinuteIsOver() {
        postTimes(LIMIT);

        pass(Duration.ofSeconds(59));
        assertThatThrownBy(() -> call("POST")).isInstanceOf(TooManyRequestsException.class);

        pass(Duration.ofSeconds(2));
        assertThat(call("POST")).isTrue();
    }

    @Test
    void startsAFreshCountOnceTheMinuteIsOver() {
        postTimes(LIMIT);
        pass(Duration.ofSeconds(61));

        postTimes(LIMIT);
        assertThatThrownBy(() -> call("POST")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void theMinuteStartsWithTheFirstUploadNotWithStartup() {
        pass(Duration.ofMinutes(30));

        postTimes(LIMIT);
        assertThatThrownBy(() -> call("POST")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void onlyPostRequestsUseUpTheQuota() {
        for (int i = 0; i < 50; i++) {
            assertThat(call("GET")).isTrue();
            assertThat(call("PATCH")).isTrue();
        }

        postTimes(LIMIT);
    }
}
