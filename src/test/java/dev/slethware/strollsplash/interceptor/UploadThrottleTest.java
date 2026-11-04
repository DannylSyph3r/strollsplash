package dev.slethware.strollsplash.interceptor;

import dev.slethware.strollsplash.config.SplashProperties;
import dev.slethware.strollsplash.config.WebConfig;
import dev.slethware.strollsplash.controller.AdminSplashController;
import dev.slethware.strollsplash.dto.response.AdminSplashResponse;
import dev.slethware.strollsplash.enums.SplashKind;
import dev.slethware.strollsplash.service.splash.AdminSplashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The throttle counts across the whole app, so each test gets a fresh context and a fresh count
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(controllers = AdminSplashController.class, properties = "splash.upload.max-per-minute=2")
@Import({WebConfig.class, AdminKeyInterceptor.class, UploadThrottleInterceptor.class})
@EnableConfigurationProperties(SplashProperties.class)
class UploadThrottleTest {

    private static final String PATH = "/api/v1/admin/splashes";
    private static final String KEY_HEADER = "X-Admin-Key";
    private static final String KEY = "test-key";
    private static final UUID ID = UUID.fromString("7d9c3f0e-5b1a-4c55-9d3e-0a1f6b2c8e11");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminSplashService adminSplashService;

    @BeforeEach
    void stubTheService() {
        AdminSplashResponse response = AdminSplashResponse.builder()
                .id(ID).title("Original").kind(SplashKind.STANDARD).url("https://cdn.test/splashes/a.mp4")
                .checksum("sum").sizeBytes(10L).active(false).pinned(false)
                .createdAt(Instant.parse("2026-11-03T08:00:00Z")).updatedAt(Instant.parse("2026-11-03T08:00:00Z"))
                .build();
        when(adminSplashService.uploadSplash(any(), any(), any(), any(), any())).thenReturn(response);
        when(adminSplashService.updateSplash(any(), any())).thenReturn(response);
        when(adminSplashService.getAllSplashes(0, 20)).thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));
    }

    private ResultActions upload(String key) throws Exception {
        MockMultipartFile video = new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[]{1});
        return mockMvc.perform(multipart(PATH).file(video).param("title", "Original").header(KEY_HEADER, key));
    }

    @Test
    void theUploadAfterTheLimitGetsA429InTheErrorShapeAndNeverReachesTheService() throws Exception {
        upload(KEY).andExpect(status().isCreated());
        upload(KEY).andExpect(status().isCreated());

        upload(KEY).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many uploads, try again in a minute"))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.statusCode").value(429));

        verify(adminSplashService, times(2)).uploadSplash(any(), any(), any(), any(), any());
    }

    @Test
    void uploadsWithAWrongKeyNeverUseUpTheQuota() throws Exception {
        for (int i = 0; i < 5; i++) {
            upload("wrong-key").andExpect(status().isUnauthorized());
        }

        upload(KEY).andExpect(status().isCreated());
        upload(KEY).andExpect(status().isCreated());
        upload(KEY).andExpect(status().isTooManyRequests());
    }

    @Test
    void listingAndPatchingAreNeverThrottled() throws Exception {
        upload(KEY);
        upload(KEY);
        upload(KEY).andExpect(status().isTooManyRequests());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get(PATH).header(KEY_HEADER, KEY)).andExpect(status().isOk());
            mockMvc.perform(patch(PATH + "/" + ID).header(KEY_HEADER, KEY)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"pinned\":true}")).andExpect(status().isOk());
        }
    }
}
