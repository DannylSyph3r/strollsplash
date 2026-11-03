package dev.slethware.strollsplash.controller;

import dev.slethware.strollsplash.config.SplashProperties;
import dev.slethware.strollsplash.dto.response.SplashResponse;
import dev.slethware.strollsplash.service.splash.SplashService;
import dev.slethware.strollsplash.utility.ChecksumUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A value other than the 5 minute default, so the test proves the header reads the property
@WebMvcTest(controllers = SplashController.class, properties = "splash.http.max-age=90s")
@EnableConfigurationProperties(SplashProperties.class)
class SplashControllerTest {

    private static final String PATH = "/api/v1/splashes";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SplashService splashService;

    private SplashResponse item(String checksum) {
        return SplashResponse.builder()
                .id(UUID.fromString("7d9c3f0e-5b1a-4c55-9d3e-0a1f6b2c8e11"))
                .url("https://cdn.test/splashes/a.mp4")
                .checksum(checksum)
                .sizeBytes(412893L)
                .build();
    }

    private String quoted(List<SplashResponse> pool) {
        return "\"" + ChecksumUtil.etagFor(pool) + "\"";
    }

    @Test
    void returnsThePoolWithExactlyTheFourFieldsPerItem() throws Exception {
        when(splashService.getPool()).thenReturn(List.of(item("abc")));

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Splashes retrieved successfully"))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.isSuccessful").value(true))
                .andExpect(jsonPath("$.successful").doesNotExist())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value("7d9c3f0e-5b1a-4c55-9d3e-0a1f6b2c8e11"))
                .andExpect(jsonPath("$.data[0].url").value("https://cdn.test/splashes/a.mp4"))
                .andExpect(jsonPath("$.data[0].checksum").value("abc"))
                .andExpect(jsonPath("$.data[0].sizeBytes").value(412893))
                .andExpect(jsonPath("$.data[0].*", hasSize(4)));
    }

    @Test
    void carriesCacheControlAndEtagHeaders() throws Exception {
        List<SplashResponse> pool = List.of(item("abc"));
        when(splashService.getPool()).thenReturn(pool);

        mockMvc.perform(get(PATH))
                .andExpect(header().string("Cache-Control", "max-age=90, public"))
                .andExpect(header().string("ETag", quoted(pool)));
    }

    @Test
    void matchingIfNoneMatchGives304WithNoBodyAndBothHeaders() throws Exception {
        List<SplashResponse> pool = List.of(item("abc"));
        when(splashService.getPool()).thenReturn(pool);

        mockMvc.perform(get(PATH).header("If-None-Match", quoted(pool)))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""))
                .andExpect(header().string("Cache-Control", "max-age=90, public"))
                .andExpect(header().string("ETag", quoted(pool)));
    }

    @Test
    void weakIfNoneMatchAlsoGives304() throws Exception {
        List<SplashResponse> pool = List.of(item("abc"));
        when(splashService.getPool()).thenReturn(pool);

        mockMvc.perform(get(PATH).header("If-None-Match", "W/" + quoted(pool)))
                .andExpect(status().isNotModified());
    }

    @Test
    void staleIfNoneMatchGetsTheFullResponse() throws Exception {
        when(splashService.getPool()).thenReturn(List.of(item("abc")));
        String oldEtag = quoted(List.of(item("old")));

        mockMvc.perform(get(PATH).header("If-None-Match", oldEtag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void differentPoolsGiveDifferentEtags() throws Exception {
        when(splashService.getPool()).thenReturn(List.of(item("abc")));
        String first = mockMvc.perform(get(PATH)).andReturn().getResponse().getHeader("ETag");

        when(splashService.getPool()).thenReturn(List.of(item("abd")));
        String second = mockMvc.perform(get(PATH)).andReturn().getResponse().getHeader("ETag");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void emptyPoolGives200WithAnEmptyList() throws Exception {
        when(splashService.getPool()).thenReturn(List.of());

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(header().exists("ETag"));
    }

    @Test
    void needsNoAdminKey() throws Exception {
        when(splashService.getPool()).thenReturn(List.of());

        mockMvc.perform(get(PATH)).andExpect(status().isOk());
    }
}
