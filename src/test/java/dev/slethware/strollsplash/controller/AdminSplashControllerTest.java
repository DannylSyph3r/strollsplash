package dev.slethware.strollsplash.controller;

import dev.slethware.strollsplash.config.SplashProperties;
import dev.slethware.strollsplash.config.WebConfig;
import dev.slethware.strollsplash.dto.request.UpdateSplashRequest;
import dev.slethware.strollsplash.dto.response.AdminSplashResponse;
import dev.slethware.strollsplash.enums.SplashKind;
import dev.slethware.strollsplash.exception.BadRequestException;
import dev.slethware.strollsplash.exception.InternalServerException;
import dev.slethware.strollsplash.exception.ResourceNotFoundException;
import dev.slethware.strollsplash.interceptor.AdminKeyInterceptor;
import dev.slethware.strollsplash.service.splash.AdminSplashService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminSplashController.class)
@Import({WebConfig.class, AdminKeyInterceptor.class})
@EnableConfigurationProperties(SplashProperties.class)
class AdminSplashControllerTest {

    private static final String PATH = "/api/v1/admin/splashes";
    private static final UUID ID = UUID.fromString("7d9c3f0e-5b1a-4c55-9d3e-0a1f6b2c8e11");
    private static final String KEY_HEADER = "X-Admin-Key";
    private static final String KEY = "test-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminSplashService adminSplashService;

    private AdminSplashResponse response() {
        return AdminSplashResponse.builder()
                .id(ID).title("Halloween 2026").kind(SplashKind.EVENT)
                .url("https://cdn.test/splashes/h.mp4").checksum("sum").sizeBytes(412893L)
                .active(true).pinned(false)
                .startsAt(Instant.parse("2026-10-30T00:00:00Z")).endsAt(Instant.parse("2026-11-01T00:00:00Z"))
                .createdAt(Instant.parse("2026-11-03T08:00:00Z")).updatedAt(Instant.parse("2026-11-03T08:00:00Z"))
                .build();
    }

    private ResultActions patchJson(String json) throws Exception {
        return mockMvc.perform(patch(PATH + "/" + ID).header(KEY_HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void listWithoutTheKeyGives401() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or missing admin key"));

        verifyNoInteractions(adminSplashService);
    }

    @Test
    void patchWithoutTheKeyGives401() throws Exception {
        mockMvc.perform(patch(PATH + "/" + ID).contentType(MediaType.APPLICATION_JSON).content("{\"pinned\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or missing admin key"))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(adminSplashService);
    }

    @Test
    void listReturnsEveryAdminFieldWithIsoTimestamps() throws Exception {
        when(adminSplashService.getAllSplashes()).thenReturn(List.of(response()));

        mockMvc.perform(get(PATH).header(KEY_HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Splash videos retrieved successfully"))
                .andExpect(jsonPath("$.isSuccessful").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].*", hasSize(12)))
                .andExpect(jsonPath("$.data[0].kind").value("EVENT"))
                .andExpect(jsonPath("$.data[0].sizeBytes").value(412893))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[0].pinned").value(false))
                .andExpect(jsonPath("$.data[0].startsAt").value("2026-10-30T00:00:00Z"))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-11-03T08:00:00Z"));
    }

    @Test
    void patchReturnsTheUpdatedRow() throws Exception {
        when(adminSplashService.updateSplash(eq(ID), any(UpdateSplashRequest.class))).thenReturn(response());

        patchJson("{\"pinned\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Splash video updated successfully"))
                .andExpect(jsonPath("$.data.id").value(ID.toString()));
    }

    @Test
    void unknownFieldGives400NamingIt() throws Exception {
        patchJson("{\"pined\": true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field 'pined'"))
                .andExpect(jsonPath("$.error").value("Bad Request"));

        verifyNoInteractions(adminSplashService);
    }

    @Test
    void timestampWithAnOffsetIsReadAsTheMatchingUtcInstant() throws Exception {
        when(adminSplashService.updateSplash(eq(ID), any(UpdateSplashRequest.class))).thenReturn(response());

        patchJson("{\"startsAt\": \"2026-12-24T01:00:00+01:00\", \"endsAt\": \"2026-12-27T00:00:00Z\"}")
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateSplashRequest> captured = ArgumentCaptor.forClass(UpdateSplashRequest.class);
        verify(adminSplashService).updateSplash(eq(ID), captured.capture());
        assertThat(captured.getValue().getStartsAt()).isEqualTo(Instant.parse("2026-12-24T00:00:00Z"));
        assertThat(captured.getValue().getEndsAt()).isEqualTo(Instant.parse("2026-12-27T00:00:00Z"));
    }

    @Test
    void explicitNullIsReadTheSameAsAnAbsentField() throws Exception {
        when(adminSplashService.updateSplash(eq(ID), any(UpdateSplashRequest.class))).thenReturn(response());

        patchJson("{\"endsAt\": null, \"pinned\": true}").andExpect(status().isOk());

        ArgumentCaptor<UpdateSplashRequest> captured = ArgumentCaptor.forClass(UpdateSplashRequest.class);
        verify(adminSplashService).updateSplash(eq(ID), captured.capture());
        assertThat(captured.getValue().getEndsAt()).isNull();
        assertThat(captured.getValue().getPinned()).isTrue();
    }

    @Test
    void badKindValueGives400ListingTheAllowedValues() throws Exception {
        patchJson("{\"kind\": \"HOLIDAY\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'HOLIDAY' for kind. Allowed values: STANDARD, EVENT"));
    }

    @Test
    void badTimestampGives400() throws Exception {
        patchJson("{\"startsAt\": \"tomorrow\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'tomorrow' for startsAt"));
    }

    @Test
    void malformedIdGives400() throws Exception {
        mockMvc.perform(patch(PATH + "/not-a-uuid").header(KEY_HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pinned\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'not-a-uuid' for id"));
    }

    @Test
    void unknownIdGives404() throws Exception {
        when(adminSplashService.updateSplash(eq(ID), any(UpdateSplashRequest.class)))
                .thenThrow(new ResourceNotFoundException("Splash video not found"));

        patchJson("{\"pinned\": true}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Splash video not found"));
    }

    @Test
    void missingBodyGives400() throws Exception {
        mockMvc.perform(patch(PATH + "/" + ID).header(KEY_HEADER, KEY).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is not valid JSON"))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    private MockMultipartFile video() {
        return new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[]{1, 2, 3});
    }

    @Test
    void uploadWithoutTheKeyGives401() throws Exception {
        mockMvc.perform(multipart(PATH).file(video()).param("title", "Original"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or missing admin key"))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(adminSplashService);
    }

    @Test
    void uploadGives201WithTheRowAndDefaultsKindToStandard() throws Exception {
        when(adminSplashService.uploadSplash(any(), any(), any(), any(), any())).thenReturn(response());

        mockMvc.perform(multipart(PATH).file(video()).param("title", "Original").header(KEY_HEADER, KEY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Splash video uploaded successfully"))
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.data.id").value(ID.toString()));

        verify(adminSplashService).uploadSplash(any(), eq("Original"), eq(SplashKind.STANDARD), isNull(), isNull());
    }

    @Test
    void uploadPassesEventTimestampsAsUtcInstants() throws Exception {
        when(adminSplashService.uploadSplash(any(), any(), any(), any(), any())).thenReturn(response());

        mockMvc.perform(multipart(PATH).file(video()).header(KEY_HEADER, KEY)
                        .param("title", "Christmas").param("kind", "EVENT")
                        .param("startsAt", "2026-12-24T01:00:00+01:00").param("endsAt", "2026-12-27T00:00:00Z"))
                .andExpect(status().isCreated());

        verify(adminSplashService).uploadSplash(any(), eq("Christmas"), eq(SplashKind.EVENT),
                eq(Instant.parse("2026-12-24T00:00:00Z")), eq(Instant.parse("2026-12-27T00:00:00Z")));
    }

    @Test
    void uploadWithNoPartsReachesTheServiceSoEveryViolationCanBeReported() throws Exception {
        when(adminSplashService.uploadSplash(any(), any(), any(), any(), any()))
                .thenThrow(new BadRequestException("title must not be blank; file is required"));

        mockMvc.perform(multipart(PATH).header(KEY_HEADER, KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("title must not be blank; file is required"));

        verify(adminSplashService).uploadSplash(isNull(), isNull(), eq(SplashKind.STANDARD), isNull(), isNull());
    }

    @Test
    void uploadWithABadKindGives400ListingTheAllowedValues() throws Exception {
        mockMvc.perform(multipart(PATH).file(video()).header(KEY_HEADER, KEY)
                        .param("title", "Original").param("kind", "HOLIDAY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'HOLIDAY' for kind. Allowed values: STANDARD, EVENT"));
    }

    @Test
    void uploadWithABadTimestampGives400() throws Exception {
        mockMvc.perform(multipart(PATH).file(video()).header(KEY_HEADER, KEY)
                        .param("title", "Christmas").param("kind", "EVENT").param("startsAt", "tomorrow"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'tomorrow' for startsAt"));
    }

    @Test
    void oversizeUploadGives413InTheErrorShape() throws Exception {
        when(adminSplashService.uploadSplash(any(), any(), any(), any(), any()))
                .thenThrow(new MaxUploadSizeExceededException(10_485_760L));

        mockMvc.perform(multipart(PATH).file(video()).param("title", "Original").header(KEY_HEADER, KEY))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("File exceeds the maximum upload size"))
                .andExpect(jsonPath("$.error").value("Payload Too Large"))
                .andExpect(jsonPath("$.statusCode").value(413));
    }

    @Test
    void storageFailureGives500WithItsOwnMessage() throws Exception {
        when(adminSplashService.uploadSplash(any(), any(), any(), any(), any()))
                .thenThrow(new InternalServerException("Failed to upload video to storage"));

        mockMvc.perform(multipart(PATH).file(video()).param("title", "Original").header(KEY_HEADER, KEY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Failed to upload video to storage"));
    }
}
