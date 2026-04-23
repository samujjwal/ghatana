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
 * Unit tests for {@link StatisticsHandler}.
 * <p>
 * Tests statistics computation and retrieval using ActiveJ test utilities.
 * </p>
 */
@ExtendWith(EventloopExtension.class) // GH-90000
@DisplayName("StatisticsHandler Tests")
class StatisticsHandlerTest {

    private MockTraceStorage storage;
    private ObjectMapper objectMapper;
    private StatisticsHandler handler;

    @BeforeEach
    void setup() { // GH-90000
        storage = new MockTraceStorage(); // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
        objectMapper.registerModule(new JavaTimeModule()); // GH-90000
        handler = new StatisticsHandler(storage, objectMapper); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (storage != null) { // GH-90000
            storage.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should return statistics without filters")
    void shouldReturnStatisticsWithoutFilters(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleGetStatistics(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("\"totalTraces\":"); // GH-90000
        assertThat(body).contains("\"totalSpans\":"); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should filter by serviceName")
    void shouldFilterStatisticsByServiceName(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats?serviceName=test-service") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleGetStatistics(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("totalTraces");
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should filter by status")
    void shouldFilterStatisticsByStatus(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats?status=OK") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleGetStatistics(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should include error count")
    void shouldIncludeErrorCount(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleGetStatistics(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("\"errorCount\":"); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should include duration statistics")
    void shouldIncludeDurationStatistics(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleGetStatistics(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("\"minDurationMs\":"); // GH-90000
        assertThat(body).contains("\"maxDurationMs\":"); // GH-90000
        assertThat(body).contains("\"avgDurationMs\":"); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should include percentile statistics")
    void shouldIncludePercentileStatistics(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleGetStatistics(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("\"p50DurationMs\":"); // GH-90000
        assertThat(body).contains("\"p95DurationMs\":"); // GH-90000
        assertThat(body).contains("\"p99DurationMs\":"); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should accept startTime filter")
    void shouldAcceptStartTimeFilter(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats?startTime=2024-01-01T00:00:00Z") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleGetStatistics(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should accept endTime filter")
    void shouldAcceptEndTimeFilter(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats?endTime=2024-12-31T23:59:59Z") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleGetStatistics(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should accept combined filters")
    void shouldAcceptCombinedFilters(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, // GH-90000
                "http://localhost/api/v1/traces/stats?serviceName=service&status=ERROR&startTime=2024-01-01T00:00:00Z")
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleGetStatistics(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should return zero values for empty storage")
    void shouldReturnZeroValuesForEmptyStorage(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleGetStatistics(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("\"totalTraces\":0"); // GH-90000
        assertThat(body).contains("\"totalSpans\":0"); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces/stats - should include most common service and operation")
    void shouldIncludeMostCommonServiceAndOperation(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/stats") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleGetStatistics(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        // Should include most common service and operation
        assertThat(body).contains("\"mostCommonService\":"); // GH-90000
        assertThat(body).contains("\"mostCommonOperation\":"); // GH-90000
    }
}
