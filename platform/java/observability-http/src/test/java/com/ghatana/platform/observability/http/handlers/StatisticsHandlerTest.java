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
 * Unit tests for {@link StatisticsHandler}.
 * <p>
 * Tests statistics computation and retrieval using ActiveJ test utilities.
 * </p>
 */
@ExtendWith(EventloopExtension.class)
@DisplayName("StatisticsHandler Tests")
class StatisticsHandlerTest {

    private MockTraceStorage storage;
    private ObjectMapper objectMapper;
    private StatisticsHandler handler;

    @BeforeEach
    void setup() {
        storage = new MockTraceStorage();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        handler = new StatisticsHandler(storage, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should return statistics without filters")
    void shouldReturnStatisticsWithoutFilters(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleGetStatistics(req), request, runner);

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"totalTraces\":");
        assertThat(body).contains("\"totalSpans\":");
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should filter by serviceName")
    void shouldFilterStatisticsByServiceName(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats?serviceName=test-service")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleGetStatistics(req), request, runner);

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        assertThat(body).contains("totalTraces");
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should filter by status")
    void shouldFilterStatisticsByStatus(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats?status=OK")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleGetStatistics(req), request, runner);

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should include error count")
    void shouldIncludeErrorCount(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleGetStatistics(req), request, runner);

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"errorCount\":");
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should include duration statistics")
    void shouldIncludeDurationStatistics(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleGetStatistics(req), request, runner);

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"minDurationMs\":");
        assertThat(body).contains("\"maxDurationMs\":");
        assertThat(body).contains("\"avgDurationMs\":");
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should include percentile statistics")
    void shouldIncludePercentileStatistics(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleGetStatistics(req), request, runner);

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"p50DurationMs\":");
        assertThat(body).contains("\"p95DurationMs\":");
        assertThat(body).contains("\"p99DurationMs\":");
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should accept startTime filter")
    void shouldAcceptStartTimeFilter(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats?startTime=2024-01-01T00:00:00Z")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleGetStatistics(req), request, runner);

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should accept endTime filter")
    void shouldAcceptEndTimeFilter(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats?endTime=2024-12-31T23:59:59Z")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleGetStatistics(req), request, runner);

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should accept combined filters")
    void shouldAcceptCombinedFilters(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, 
                "http://localhost/api/v1/traces/stats?serviceName=service&status=ERROR&startTime=2024-01-01T00:00:00Z")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleGetStatistics(req), request, runner);

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should return zero values for empty storage")
    void shouldReturnZeroValuesForEmptyStorage(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleGetStatistics(req), request, runner);

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"totalTraces\":0");
        assertThat(body).contains("\"totalSpans\":0");
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should include most common service and operation")
    void shouldIncludeMostCommonServiceAndOperation(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        HttpResponse response = ActiveJServletTestUtil.serve(
                req -> handler.handleGetStatistics(req), request, runner);

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        // Should include most common service and operation
        assertThat(body).contains("\"mostCommonService\":");
        assertThat(body).contains("\"mostCommonOperation\":");
    }
}
