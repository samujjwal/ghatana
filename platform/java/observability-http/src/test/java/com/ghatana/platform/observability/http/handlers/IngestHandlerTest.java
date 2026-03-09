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

@ExtendWith(EventloopExtension.class)
@DisplayName("IngestHandler Tests")
class IngestHandlerTest {

    private MockTraceStorage storage;
    private ObjectMapper objectMapper;
    private IngestHandler handler;

    @BeforeEach
    void setup() {
        storage = new MockTraceStorage();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        handler = new IngestHandler(storage, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    @DisplayName("Should ingest single span successfully")
    void shouldIngestSingleSpan(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
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
        HttpRequest req = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/traces/spans")
                .withBody(jsonBody.getBytes(StandardCharsets.UTF_8))
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();

        HttpResponse resp = ActiveJServletTestUtil.serve(
                request -> handler.handleSingleSpan(request), req, runner);

        assertThat(resp.getCode()).isEqualTo(201);
        assertThat(storage.getTotalSpanCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should ingest batch spans successfully")
    void shouldIngestBatchSpans(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
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
        HttpRequest req = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/traces/spans/batch")
                .withBody(jsonBody.getBytes(StandardCharsets.UTF_8))
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();

        HttpResponse resp = ActiveJServletTestUtil.serve(
                request -> handler.handleBatchSpans(request), req, runner);

        assertThat(resp.getCode()).isEqualTo(201);
        String responseBody = resp.getBody().asString(StandardCharsets.UTF_8);
        assertThat(responseBody).contains("\"successCount\":2");
        assertThat(responseBody).contains("\"failureCount\":0");
        assertThat(storage.getTotalSpanCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should return 400 for invalid JSON")
    void shouldReturn400ForInvalidJson(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest req = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/traces/spans")
                .withBody("{ invalid }".getBytes(StandardCharsets.UTF_8))
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();

        HttpResponse resp = ActiveJServletTestUtil.serve(
                request -> handler.handleSingleSpan(request), req, runner);

        assertThat(resp.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Should return 400 for empty body")
    void shouldReturn400ForEmptyBody(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        HttpRequest req = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/traces/spans")
                .withBody(new byte[0])
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();

        HttpResponse resp = ActiveJServletTestUtil.serve(
                request -> handler.handleSingleSpan(request), req, runner);

        assertThat(resp.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Should handle large batch of 50 spans")
    void shouldHandleLargeBatch(com.ghatana.platform.testing.activej.EventloopTestUtil.EventloopRunner runner) {
        StringBuilder spansJson = new StringBuilder("[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) spansJson.append(",");
            spansJson.append(String.format(
                    "{\"spanId\":\"span-%d\",\"traceId\":\"trace-1\",\"operationName\":\"op-%d\"," +
                    "\"serviceName\":\"service1\",\"startTime\":\"2024-01-01T00:00:00Z\"," +
                    "\"endTime\":\"2024-01-01T00:00:01Z\",\"status\":\"OK\"}", i, i));
        }
        spansJson.append("]");

        HttpRequest req = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/traces/spans/batch")
                .withBody(("{\"spans\":" + spansJson + "}").getBytes(StandardCharsets.UTF_8))
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();

        HttpResponse resp = ActiveJServletTestUtil.serve(
                request -> handler.handleBatchSpans(request), req, runner);

        assertThat(resp.getCode()).isEqualTo(201);
        assertThat(storage.getTotalSpanCount()).isEqualTo(50);
    }
}