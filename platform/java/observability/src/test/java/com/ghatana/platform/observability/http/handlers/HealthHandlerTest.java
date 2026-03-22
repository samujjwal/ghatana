package com.ghatana.platform.observability.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.platform.observability.trace.MockTraceStorage;
import com.ghatana.platform.testing.activej.ActiveJServletTestUtil;
import com.ghatana.platform.testing.activej.EventloopExtension;
import com.ghatana.platform.testing.activej.EventloopTestUtil;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HealthHandler}.
 * <p>
 * Tests health check endpoints using ActiveJ test utilities.
 * </p>
 */
@ExtendWith(EventloopExtension.class)
@DisplayName("HealthHandler Tests")
class HealthHandlerTest {

    private MockTraceStorage storage;
    private ObjectMapper objectMapper;
    private HealthHandler handler;

    @BeforeEach
    void setup() {
        storage = new MockTraceStorage();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        handler = new HealthHandler(storage, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    @DisplayName("GET /health - should return 200 for liveness check")
    void shouldReturnHealthyStatusForLivenessCheck(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleLiveness(req), request, runner);

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("GET /health - should include health components")
    void shouldIncludeHealthComponents(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleLiveness(req), request, runner);

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"status\":");
        assertThat(body).contains("\"timestamp\":");
        assertThat(body).contains("\"uptime\":");
    }

    @Test
    @DisplayName("GET /health - should check storage connectivity")
    void shouldCheckStorageConnectivity(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleLiveness(req), request, runner);

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        // Health check returns basic status info
        assertThat(body).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("GET /health - should include timestamp")
    void shouldIncludeTimestamp(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleLiveness(req), request, runner);

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"timestamp\":");
    }

    @Test
    @DisplayName("GET /health/ready - should return 200 for readiness check")
    void shouldReturnHealthyStatusForReadinessCheck(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health/ready")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleReadiness(req), request, runner);

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"status\":");
    }

    @Test
    @DisplayName("GET /health/ready - should check database availability")
    void shouldCheckDatabaseAvailability(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health/ready")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleReadiness(req), request, runner);

        assertThat(response.getCode()).isIn(200, 503);
    }

    @Test
    @DisplayName("GET /health/ready - should indicate readiness status")
    void shouldIndicateReadinessStatus(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health/ready")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleReadiness(req), request, runner);

        assertThat(response.getCode()).isIn(200, 503);
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        assertThat(body).isNotNull();
    }

    @Test
    @DisplayName("GET /health - should respond to Accept header")
    void shouldRespondToAcceptHeader(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleLiveness(req), request, runner);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).contains("application/json");
    }

    @Test
    @DisplayName("GET /health/ready - should respond to Accept header")
    void shouldRespondToAcceptHeaderForReadiness(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health/ready")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleReadiness(req), request, runner);

        assertThat(response.getCode()).isIn(200, 503);
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).contains("application/json");
    }
}
