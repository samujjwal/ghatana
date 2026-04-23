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

@ExtendWith(EventloopExtension.class) // GH-90000
@DisplayName("IngestHandler Tests")
class IngestHandlerTest {

    private MockTraceStorage storage;
    private ObjectMapper objectMapper;
    private IngestHandler handler;

    @BeforeEach
    void setup() { // GH-90000
        storage = new MockTraceStorage(); // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
        objectMapper.registerModule(new JavaTimeModule()); // GH-90000
        handler = new IngestHandler(storage, objectMapper); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (storage != null) { // GH-90000
            storage.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("Should ingest single span successfully")
    void shouldIngestSingleSpan(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
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
        HttpRequest req = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/traces/spans") // GH-90000
                .withBody(jsonBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse resp = ActiveJServletTestUtil.serve( // GH-90000
                request -> handler.handleSingleSpan(request), req, runner); // GH-90000

        assertThat(resp.getCode()).isEqualTo(201); // GH-90000
        assertThat(storage.getTotalSpanCount()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Should ingest batch spans successfully")
    void shouldIngestBatchSpans(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        String jsonBody = """
                {
                    "spans": [
                        {
                            "spanId": "span-1",
                            "traceId": "trace-1",
                            "operationName": "op1",
                            "serviceName": "service1",
                            "startTime": "2024-01-01T00:00:00Z",
                            "endTime": "2024-01-01T00:00:01Z",
                            "status": "OK"
                        },
                        {
                            "spanId": "span-2",
                            "traceId": "trace-1",
                            "parentSpanId": "span-1",
                            "operationName": "op2",
                            "serviceName": "service1",
                            "startTime": "2024-01-01T00:00:01Z",
                            "endTime": "2024-01-01T00:00:02Z",
                            "status": "OK"
                        }
                    ]
                }
                """;
        HttpRequest req = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/traces/spans/batch") // GH-90000
                .withBody(jsonBody.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse resp = ActiveJServletTestUtil.serve( // GH-90000
                request -> handler.handleBatchSpans(request), req, runner); // GH-90000

        assertThat(resp.getCode()).isEqualTo(201); // GH-90000
        String responseBody = resp.getBody().asString(StandardCharsets.UTF_8); // GH-90000
        assertThat(responseBody).contains("\"successCount\":2"); // GH-90000
        assertThat(responseBody).contains("\"failureCount\":0"); // GH-90000
        assertThat(storage.getTotalSpanCount()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("Should return 400 for invalid JSON")
    void shouldReturn400ForInvalidJson(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest req = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/traces/spans") // GH-90000
                .withBody("{ invalid }".getBytes(StandardCharsets.UTF_8)) // GH-90000
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse resp = ActiveJServletTestUtil.serve( // GH-90000
                request -> handler.handleSingleSpan(request), req, runner); // GH-90000

        assertThat(resp.getCode()).isEqualTo(400); // GH-90000
    }

    @Test
    @DisplayName("Should return 400 for empty body")
    void shouldReturn400ForEmptyBody(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        HttpRequest req = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/traces/spans") // GH-90000
                .withBody(new byte[0]) // GH-90000
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse resp = ActiveJServletTestUtil.serve( // GH-90000
                request -> handler.handleSingleSpan(request), req, runner); // GH-90000

        assertThat(resp.getCode()).isEqualTo(400); // GH-90000
    }

    @Test
    @DisplayName("Should handle large batch of 50 spans")
    void shouldHandleLargeBatch(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) { // GH-90000
        StringBuilder spansJson = new StringBuilder("[");
        for (int i = 0; i < 50; i++) { // GH-90000
            if (i > 0) spansJson.append(",");
            spansJson.append(String.format( // GH-90000
                    "{\"spanId\":\"span-%d\",\"traceId\":\"trace-1\",\"operationName\":\"op-%d\"," +
                    "\"serviceName\":\"service1\",\"startTime\":\"2024-01-01T00:00:00Z\"," +
                    "\"endTime\":\"2024-01-01T00:00:01Z\",\"status\":\"OK\"}", i, i));
        }
        spansJson.append("]");

        HttpRequest req = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/traces/spans/batch") // GH-90000
                .withBody(("{\"spans\":" + spansJson + "}").getBytes(StandardCharsets.UTF_8)) // GH-90000
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json") // GH-90000
                .build(); // GH-90000

        HttpResponse resp = ActiveJServletTestUtil.serve( // GH-90000
                request -> handler.handleBatchSpans(request), req, runner); // GH-90000

        assertThat(resp.getCode()).isEqualTo(201); // GH-90000
        assertThat(storage.getTotalSpanCount()).isEqualTo(50); // GH-90000
    }
}
