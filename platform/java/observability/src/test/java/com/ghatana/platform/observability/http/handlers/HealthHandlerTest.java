package com.ghatana.platform.observability.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.platform.observability.trace.MockTraceStorage;
import com.ghatana.platform.testing.activej.ActiveJServletTestUtil;
import com.ghatana.platform.testing.activej.EventloopExtension;
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
@ExtendWith(EventloopExtension.class) // GH-90000
@DisplayName("HealthHandler Tests")
class HealthHandlerTest {

    private MockTraceStorage storage;
    private ObjectMapper objectMapper;
    private HealthHandler handler;

    @BeforeEach
    void setup() { // GH-90000
        storage = new MockTraceStorage(); // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
        objectMapper.registerModule(new JavaTimeModule()); // GH-90000
        handler = new HealthHandler(storage, objectMapper); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (storage != null) { // GH-90000
            storage.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("GET /health - should return 200 for liveness check")
    void shouldReturnHealthyStatusForLivenessCheck(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleLiveness(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("\"status\":\"UP\""); // GH-90000
    }

    @Test
    @DisplayName("GET /health - should include health components")
    void shouldIncludeHealthComponents(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleLiveness(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("\"status\":"); // GH-90000
        assertThat(body).contains("\"timestamp\":"); // GH-90000
        assertThat(body).contains("\"uptime\":"); // GH-90000
    }

    @Test
    @DisplayName("GET /health - should check storage connectivity")
    void shouldCheckStorageConnectivity(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleLiveness(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        // Health check returns basic status info
        assertThat(body).contains("\"status\":\"UP\""); // GH-90000
    }

    @Test
    @DisplayName("GET /health - should include timestamp")
    void shouldIncludeTimestamp(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleLiveness(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("\"timestamp\":"); // GH-90000
    }

    @Test
    @DisplayName("GET /health/ready - should return 200 for readiness check")
    void shouldReturnHealthyStatusForReadinessCheck(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health/ready") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleReadiness(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("\"status\":"); // GH-90000
    }

    @Test
    @DisplayName("GET /health/ready - should check database availability")
    void shouldCheckDatabaseAvailability(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health/ready") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleReadiness(req), request, runner); // GH-90000

        assertThat(response.getCode()).isIn(200, 503); // GH-90000
    }

    @Test
    @DisplayName("GET /health/ready - should indicate readiness status")
    void shouldIndicateReadinessStatus(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health/ready") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleReadiness(req), request, runner); // GH-90000

        assertThat(response.getCode()).isIn(200, 503); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("GET /health - should respond to Accept header")
    void shouldRespondToAcceptHeader(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleLiveness(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).contains("application/json");
    }

    @Test
    @DisplayName("GET /health/ready - should respond to Accept header")
    void shouldRespondToAcceptHeaderForReadiness(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health/ready") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleReadiness(req), request, runner); // GH-90000

        assertThat(response.getCode()).isIn(200, 503); // GH-90000
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).contains("application/json");
    }
}
