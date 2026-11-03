package dev.slethware.strollsplash.utility;

import dev.slethware.strollsplash.dto.response.ErrorResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseUtilTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void successResponseSerialisesWithTheLockedKeys() {
        JsonNode json = mapper.valueToTree(ApiResponseUtil.successFull("ok", List.of()));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder("message", "statusCode", "isSuccessful", "data");
        assertThat(json.has("successful")).isFalse();
        assertThat(json.get("message").asString()).isEqualTo("ok");
        assertThat(json.get("statusCode").asInt()).isEqualTo(200);
        assertThat(json.get("isSuccessful").asBoolean()).isTrue();
        assertThat(json.get("data").isArray()).isTrue();
    }

    @Test
    void createdResponseCarriesStatus201() {
        JsonNode json = mapper.valueToTree(ApiResponseUtil.created("made", "payload"));

        assertThat(json.get("statusCode").asInt()).isEqualTo(201);
        assertThat(json.get("isSuccessful").asBoolean()).isTrue();
        assertThat(json.get("data").asString()).isEqualTo("payload");
    }

    @Test
    void errorResponseSerialisesWithMessageErrorAndStatusCode() {
        ErrorResponse error = ErrorResponse.builder()
                .message("Invalid or missing admin key")
                .error("Unauthorized")
                .statusCode(401)
                .build();

        JsonNode json = mapper.valueToTree(error);

        assertThat(json.propertyNames()).containsExactlyInAnyOrder("message", "error", "statusCode");
        assertThat(json.get("error").asString()).isEqualTo("Unauthorized");
        assertThat(json.get("statusCode").asInt()).isEqualTo(401);
    }
}
