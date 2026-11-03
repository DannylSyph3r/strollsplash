package dev.slethware.strollsplash.interceptor;

import dev.slethware.strollsplash.config.SplashProperties;
import dev.slethware.strollsplash.config.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Boot's test type filter hides controllers nested in test classes from scanning, so import it
@WebMvcTest(controllers = AdminKeyInterceptorTest.PingController.class)
@Import({AdminKeyInterceptorTest.PingController.class, WebConfig.class, AdminKeyInterceptor.class})
@EnableConfigurationProperties(SplashProperties.class)
class AdminKeyInterceptorTest {

    private static final String ADMIN_PATH = "/api/v1/admin/ping";

    @RestController
    static class PingController {

        @GetMapping(ADMIN_PATH)
        String admin() {
            return "admin";
        }

        @GetMapping("/api/v1/splashes/ping")
        String publicPing() {
            return "public";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private void assertUnauthorized(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or missing admin key"))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.statusCode").value(401));
    }

    @Test
    void correctKeyPasses() throws Exception {
        mockMvc.perform(get(ADMIN_PATH).header("X-Admin-Key", "test-key"))
                .andExpect(status().isOk())
                .andExpect(content().string("admin"));
    }

    @Test
    void missingKeyGives401InTheErrorShape() throws Exception {
        assertUnauthorized(mockMvc.perform(get(ADMIN_PATH)));
    }

    @Test
    void wrongKeyGives401() throws Exception {
        assertUnauthorized(mockMvc.perform(get(ADMIN_PATH).header("X-Admin-Key", "wrong-key")));
    }

    @Test
    void emptyKeyGives401() throws Exception {
        assertUnauthorized(mockMvc.perform(get(ADMIN_PATH).header("X-Admin-Key", "")));
    }

    @Test
    void prefixOfTheRealKeyGives401() throws Exception {
        assertUnauthorized(mockMvc.perform(get(ADMIN_PATH).header("X-Admin-Key", "test-ke")));
    }

    @Test
    void realKeyWithExtraCharactersGives401() throws Exception {
        assertUnauthorized(mockMvc.perform(get(ADMIN_PATH).header("X-Admin-Key", "test-key-extra")));
    }

    @Test
    void unknownAdminRouteIsGuardedToo() throws Exception {
        assertUnauthorized(mockMvc.perform(get("/api/v1/admin/nowhere")));
    }

    @Test
    void publicSplashRouteNeedsNoKey() throws Exception {
        mockMvc.perform(get("/api/v1/splashes/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("public"));
    }

    @Test
    void routesOutsideTheAdminPathAreUnaffected() throws Exception {
        // 404 rather than 401 shows the interceptor never ran for the docs path
        mockMvc.perform(get("/docs")).andExpect(status().isNotFound());
    }
}
