package com.ghatana.platform.http.server.activej;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Tests shared ActiveJ HTTP exchange helpers
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("ActiveJ HTTP exchange support")
class ActiveJHttpExchangeSupportTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("extracts caller correlation ID and generates one when absent")
    void extractsOrGeneratesCorrelationId() {
        HttpRequest supplied = HttpRequest.get("http://localhost/health")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-123")
            .build();
        HttpRequest absent = HttpRequest.get("http://localhost/health").build();

        assertThat(ActiveJHttpExchangeSupport.correlationId(supplied)).isEqualTo("corr-123");
        assertThat(ActiveJHttpExchangeSupport.correlationId(absent)).isNotBlank();
    }

    @Test
    @DisplayName("extracts and validates idempotency key")
    void extractsIdempotencyKey() {
        HttpRequest request = HttpRequest.post("http://localhost/consents")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "consent_12345678")
            .build();

        assertThat(ActiveJHttpExchangeSupport.idempotencyKey(request)).contains("consent_12345678");
        assertThat(ActiveJHttpExchangeSupport.isValidIdempotencyKey("abc-12345")).isTrue();
        assertThat(ActiveJHttpExchangeSupport.isValidIdempotencyKey("bad key")).isFalse();
    }

    @Test
    @DisplayName("rejects malformed idempotency key")
    void rejectsMalformedIdempotencyKey() {
        HttpRequest request = HttpRequest.post("http://localhost/consents")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "bad key")
            .build();

        assertThatThrownBy(() -> ActiveJHttpExchangeSupport.idempotencyKey(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Idempotency key");
    }

    @Test
    @DisplayName("writes JSON responses with correlation header")
    void writesJsonResponseWithCorrelationHeader() {
        HttpResponse response = ActiveJHttpExchangeSupport.jsonResponse(
            JSON,
            200,
            Map.of("status", "ok"),
            "corr-json"
        );

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("corr-json");
        assertThat(responseBody(response)).contains("\"status\":\"ok\"");
    }

    @Test
    @DisplayName("writes structured error envelope with safe details")
    void writesStructuredErrorEnvelope() {
        HttpResponse response = ActiveJHttpExchangeSupport.errorResponse(
            JSON,
            403,
            "POLICY_DENIED",
            "Access denied by policy",
            "corr-error",
            Map.of("policyId", "phr.records.read")
        );

        assertThat(response.getCode()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("corr-error");
        assertThat(responseBody(response))
            .contains("\"error\":\"POLICY_DENIED\"")
            .contains("\"policyId\":\"phr.records.read\"");
    }

    private static String responseBody(HttpResponse response) {
        return response.getBody().getString(StandardCharsets.UTF_8);
    }
}
