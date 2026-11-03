package dev.slethware.strollsplash.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Boot's test type filter hides controllers nested in test classes from scanning, so import it
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ThrowingController.class)
@Import(GlobalExceptionHandlerTest.ThrowingController.class)
class GlobalExceptionHandlerTest {

    enum Mode {
        STANDARD, EVENT
    }

    record Body(String name, Mode mode) {
    }

    @RestController
    @RequestMapping("/test")
    static class ThrowingController {

        @GetMapping("/bad-request")
        void badRequest() {
            throw new BadRequestException("title must not be blank");
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Splash video not found");
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new UnauthorizedException("Invalid or missing admin key");
        }

        @GetMapping("/storage-failure")
        void storageFailure() {
            throw new InternalServerException("Failed to upload video to storage");
        }

        @GetMapping("/upload-too-large")
        void uploadTooLarge() {
            throw new MaxUploadSizeExceededException(1024);
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new RuntimeException("database password is hunter2");
        }

        @PostMapping("/body")
        void body(@RequestBody Body body) {
        }

        @GetMapping("/mode")
        void mode(@RequestParam Mode mode) {
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void badRequestExceptionGives400WithItsMessage() throws Exception {
        assertError(mockMvc.perform(get("/test/bad-request")), 400, "Bad Request", "title must not be blank");
    }

    @Test
    void resourceNotFoundExceptionGives404() throws Exception {
        assertError(mockMvc.perform(get("/test/not-found")), 404, "Not Found", "Splash video not found");
    }

    @Test
    void unauthorizedExceptionGives401() throws Exception {
        assertError(mockMvc.perform(get("/test/unauthorized")),
                401, "Unauthorized", "Invalid or missing admin key");
    }

    @Test
    void internalServerExceptionGives500WithItsOwnMessage() throws Exception {
        assertError(mockMvc.perform(get("/test/storage-failure")),
                500, "Internal Server Error", "Failed to upload video to storage");
    }

    @Test
    void malformedJsonGives400() throws Exception {
        assertError(postJson("/test/body", "{\"name\": "), 400, "Bad Request", "Request body is not valid JSON");
    }

    @Test
    void unknownJsonFieldGives400NamingTheField() throws Exception {
        assertError(postJson("/test/body", "{\"nam\": \"x\"}"), 400, "Bad Request", "Unknown field 'nam'");
    }

    @Test
    void badEnumValueInBodyGives400WithAReadableMessage() throws Exception {
        postJson("/test/body", "{\"mode\": \"HOLIDAY\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("HOLIDAY"),
                        org.hamcrest.Matchers.containsString("STANDARD"),
                        org.hamcrest.Matchers.containsString("EVENT"))));
    }

    @Test
    void badEnumRequestParamGives400ListingTheAllowedValues() throws Exception {
        assertError(mockMvc.perform(get("/test/mode").param("mode", "HOLIDAY")), 400, "Bad Request",
                "Invalid value 'HOLIDAY' for mode. Allowed values: STANDARD, EVENT");
    }

    @Test
    void unknownRouteGives404NotA500() throws Exception {
        mockMvc.perform(get("/test/nowhere"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.statusCode").value(404));
    }

    @Test
    void wrongHttpMethodGives405NotA500() throws Exception {
        mockMvc.perform(post("/test/bad-request"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.statusCode").value(405));
    }

    @Test
    void oversizeUploadGives413() throws Exception {
        assertError(mockMvc.perform(get("/test/upload-too-large")),
                413, "Payload Too Large", "File exceeds the maximum upload size");
    }

    @Test
    void unexpectedExceptionGives500WithAGenericMessage() throws Exception {
        assertError(mockMvc.perform(get("/test/unexpected")),
                500, "Internal Server Error", "An unexpected error occurred");
    }

    private ResultActions postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void assertError(ResultActions result, int statusCode, String error, String message) throws Exception {
        result.andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.statusCode").value(statusCode));
    }
}
