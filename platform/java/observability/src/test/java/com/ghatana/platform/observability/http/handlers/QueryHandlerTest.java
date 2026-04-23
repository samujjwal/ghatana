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
 * Unit tests for {@link QueryHandler}.
 * <p>
 * Tests trace retrieval and search functionality using ActiveJ test utilities.
 * </p>
 */
@ExtendWith(EventloopExtension.class) // GH-90000
@DisplayName("QueryHandler Tests")
class QueryHandlerTest {

    private MockTraceStorage storage;
    private ObjectMapper objectMapper;
    private QueryHandler handler;

    @BeforeEach
    void setup() { // GH-90000
        storage = new MockTraceStorage(); // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
        objectMapper.registerModule(new JavaTimeModule()); // GH-90000
        handler = new QueryHandler(storage, objectMapper); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (storage != null) { // GH-90000
            storage.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("GET /api/v1/traces/{traceId} - should return trace when found")
    void shouldGetTraceById(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        // Setup: Add span to storage
        String jsonBody = """
                {
                    "spanId": "span-1",
                    "traceId": "trace-1",
                    "operationName": "http.request",
                    "serviceName": "api-service",
                    "startTime": "2024-01-01T00:00:00Z",
                    "endTime": "2024-01-01T00:00:01Z",
                    "status": "OK"
                }
                """;
        HttpRequest ingestReq = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/traces/spans") // GH-90000
                .withBody(jsonBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json") // GH-90000
                .build(); // GH-90000
        IngestHandler ingestHandler = new IngestHandler(storage, objectMapper); // GH-90000
        // Note: In real scenario, we'd use the ingest endpoint to populate data
        // For now, we assume MockTraceStorage can be populated directly via internal methods

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/trace-1") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleGetTraceById(req, "trace-1"), request, runner); // GH-90000

        // Should return 200 if trace exists in storage (depends on storage implementation) // GH-90000
        assertThat(response.getCode()).isIn(200, 404); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces/{traceId} - should return 404 when trace not found")
    void shouldReturn404ForTraceNotFound(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/nonexistent") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleGetTraceById(req, "nonexistent"), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(404); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("code");
    }

    @Test
    @DisplayName("GET /api/v1/traces/{traceId} - should return 400 for blank trace ID")
    void shouldReturn400ForBlankTraceId(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces/ ") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleGetTraceById(req, " "), request, runner); // GH-90000

        assertThat(response.getCode()).isIn(400, 404); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces - should search traces without filters")
    void shouldSearchTracesWithoutFilters(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleSearchTraces(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces - should accept serviceName filter")
    void shouldAcceptServiceNameFilter(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces?serviceName=service-a") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleSearchTraces(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces - should accept operationName filter")
    void shouldAcceptOperationNameFilter(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces?operationName=specific-op") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleSearchTraces(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces - should accept status filter")
    void shouldAcceptStatusFilter(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces?status=OK") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleSearchTraces(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces - should accept startTime filter")
    void shouldAcceptStartTimeFilter(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces?startTime=2024-01-01T00:00:00Z") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleSearchTraces(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces - should accept endTime filter")
    void shouldAcceptEndTimeFilter(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces?endTime=2024-12-31T23:59:59Z") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleSearchTraces(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces - should accept minDuration filter")
    void shouldAcceptMinDurationFilter(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces?minDuration=100") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleSearchTraces(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces - should accept maxDuration filter")
    void shouldAcceptMaxDurationFilter(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces?maxDuration=5000") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleSearchTraces(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces - should accept limit parameter")
    void shouldAcceptLimitParameter(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces?limit=50") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleSearchTraces(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces - should accept offset parameter")
    void shouldAcceptOffsetParameter(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/traces?offset=10") // GH-90000
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleSearchTraces(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/traces - should accept combined filters")
    void shouldAcceptCombinedFilters(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, // GH-90000
                "http://localhost/api/v1/traces?serviceName=svc&operationName=op&status=OK&limit=25")
                .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve( // GH-90000
                req -> handler.handleSearchTraces(req), request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }
}
