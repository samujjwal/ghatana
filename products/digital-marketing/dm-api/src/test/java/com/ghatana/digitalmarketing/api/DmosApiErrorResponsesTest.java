package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.bytebuf.ByteBufStrings;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DmosApiErrorResponses")
class DmosApiErrorResponsesTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("renders canonical envelope for status matrix")
    void shouldRenderCanonicalEnvelopeForStatusMatrix() throws Exception {
        assertEnvelope(400, "BAD_REQUEST");
        assertEnvelope(401, "UNAUTHORIZED");
        assertEnvelope(403, "FORBIDDEN");
        assertEnvelope(404, "NOT_FOUND");
        assertEnvelope(409, "CONFLICT");
        assertEnvelope(422, "UNPROCESSABLE_ENTITY");
        assertEnvelope(423, "LOCKED");
        assertEnvelope(429, "RATE_LIMITED");
        assertEnvelope(500, "INTERNAL_ERROR");
    }

    @Test
    @DisplayName("uses request correlation header and propagates details")
    void shouldUseRequestCorrelationIdAndDetails() throws Exception {
        HttpRequest request = HttpRequest.get("http://localhost/v1/test")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-123")
            .build();

        HttpResponse response = DmosApiErrorResponses.error(
            422,
            "Validation failed",
            DmosApiHeaderValidator.getCorrelationId(request),
            Map.of("field", "must not be blank")
        );

        JsonNode body = parseBody(response);

        assertThat(response.getCode()).isEqualTo(422);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("corr-123");
        assertThat(body.get("error").asText()).isEqualTo("UNPROCESSABLE_ENTITY");
        assertThat(body.get("message").asText()).isEqualTo("Validation failed");
        assertThat(body.get("status").asInt()).isEqualTo(422);
        assertThat(body.get("correlationId").asText()).isEqualTo("corr-123");
        assertThat(body.get("details").get("field").asText()).isEqualTo("must not be blank");
    }

    private void assertEnvelope(int status, String errorCode) throws Exception {
        HttpResponse response = DmosApiErrorResponses.error(status, "sample", "corr-matrix");
        JsonNode body = parseBody(response);

        assertThat(response.getCode()).isEqualTo(status);
        assertThat(response.getHeader(HttpHeaders.of("Content-Type"))).isEqualTo("application/json");
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("corr-matrix");
        assertThat(body.get("error").asText()).isEqualTo(errorCode);
        assertThat(body.get("message").asText()).isEqualTo("sample");
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("correlationId").asText()).isEqualTo("corr-matrix");
    }

    private JsonNode parseBody(HttpResponse response) throws Exception {
        String body = ByteBufStrings.decodeUtf8(response.getBody());
        return OBJECT_MAPPER.readTree(body);
    }
}
