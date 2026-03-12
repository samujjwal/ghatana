package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.QueryPlan;
import com.ghatana.datacloud.analytics.QueryResult;
import com.ghatana.datacloud.analytics.QueryType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the Data-Cloud HTTP analytics endpoints (DC-9).
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and makes HTTP calls via the
 * Java standard HttpClient. {@link DataCloudClient} and {@link AnalyticsQueryEngine} are mocked.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/analytics/** HTTP endpoints (DC-9)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Analytics Endpoints (DC-9)")
class DataCloudHttpServerAnalyticsTest {

    private DataCloudClient mockClient;
    private AnalyticsQueryEngine mockEngine;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(DataCloudClient.class);
        mockEngine = mock(AnalyticsQueryEngine.class);
        port       = findFreePort();
        httpClient = HttpClient.newBuilder().build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    // ==================== No engine (null) ====================

    @Nested
    @DisplayName("Analytics engine not wired (null)")
    class NoEngineTests {

        @Test
        @DisplayName("POST /query → 503 when engine is null")
        void query_noEngine_returns503() throws Exception {
            startWithoutEngine();
            HttpResponse<String> resp = post("/api/v1/analytics/query",
                "{\"query\":\"SELECT * FROM foo\"}");
            assertThat(resp.statusCode()).isEqualTo(503);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("error").toString()).containsIgnoringCase("not available");
        }

        @Test
        @DisplayName("GET /query/:id → 503 when engine is null")
        void getResult_noEngine_returns503() throws Exception {
            startWithoutEngine();
            HttpResponse<String> resp = get("/api/v1/analytics/query/qid-1");
            assertThat(resp.statusCode()).isEqualTo(503);
        }

        @Test
        @DisplayName("GET /query/:id/plan → 503 when engine is null")
        void getPlan_noEngine_returns503() throws Exception {
            startWithoutEngine();
            HttpResponse<String> resp = get("/api/v1/analytics/query/qid-1/plan");
            assertThat(resp.statusCode()).isEqualTo(503);
        }

        @Test
        @DisplayName("POST /aggregate → 503 when engine is null")
        void aggregate_noEngine_returns503() throws Exception {
            startWithoutEngine();
            HttpResponse<String> resp = post("/api/v1/analytics/aggregate",
                "{\"query\":\"SELECT COUNT(*) FROM foo GROUP BY bar\"}");
            assertThat(resp.statusCode()).isEqualTo(503);
        }
    }

    // ==================== POST /api/v1/analytics/query ====================

    @Nested
    @DisplayName("POST /api/v1/analytics/query")
    class SubmitQueryTests {

        @Test
        @DisplayName("valid query → 200 with full result fields")
        void submitQuery_valid_returns200WithResult() throws Exception {
            QueryResult result = QueryResult.builder()
                .queryId("qid-123")
                .queryType("SELECT")
                .rows(List.of(
                    Map.of("id", "1", "name", "Alice"),
                    Map.of("id", "2", "name", "Bob")
                ))
                .rowCount(2)
                .columnCount(2)
                .executionTimeMs(15L)
                .optimized(true)
                .build();
            when(mockEngine.submitQuery(anyString(), anyString(), anyMap()))
                .thenReturn(Promise.of(result));

            startWithEngine();
            HttpResponse<String> resp = post("/api/v1/analytics/query",
                "{\"query\":\"SELECT id, name FROM users\"}");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("queryId")).isEqualTo("qid-123");
            assertThat(body.get("queryType")).isEqualTo("SELECT");
            assertThat(((Number) body.get("rowCount")).intValue()).isEqualTo(2);
            assertThat(body.get("rows")).isNotNull();
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("missing 'query' field → 400")
        void submitQuery_missingField_returns400() throws Exception {
            startWithEngine();
            HttpResponse<String> resp = post("/api/v1/analytics/query", "{\"foo\":\"bar\"}");
            assertThat(resp.statusCode()).isEqualTo(400);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("error").toString()).containsIgnoringCase("query");
        }

        @Test
        @DisplayName("blank 'query' field → 400")
        void submitQuery_blankQuery_returns400() throws Exception {
            startWithEngine();
            HttpResponse<String> resp = post("/api/v1/analytics/query", "{\"query\":\"   \"}");
            assertThat(resp.statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("parameters object is forwarded to engine")
        void submitQuery_withParameters_forwardedToEngine() throws Exception {
            QueryResult result = QueryResult.builder()
                .queryId("qid-params")
                .queryType("SELECT")
                .rows(List.of())
                .rowCount(0)
                .columnCount(0)
                .executionTimeMs(5L)
                .build();
            when(mockEngine.submitQuery(anyString(), anyString(), anyMap()))
                .thenReturn(Promise.of(result));

            startWithEngine();
            HttpResponse<String> resp = post("/api/v1/analytics/query",
                "{\"query\":\"SELECT * FROM t WHERE id=:id\",\"parameters\":{\"id\":\"42\"}}");

            assertThat(resp.statusCode()).isEqualTo(200);
        }
    }

    // ==================== GET /api/v1/analytics/query/:queryId ====================

    @Nested
    @DisplayName("GET /api/v1/analytics/query/:queryId")
    class GetResultTests {

        @Test
        @DisplayName("known queryId → 200 with result")
        void getResult_knownId_returns200() throws Exception {
            QueryResult result = QueryResult.builder()
                .queryId("qid-abc")
                .queryType("SELECT")
                .rows(List.of(Map.of("value", 42)))
                .rowCount(1)
                .columnCount(1)
                .executionTimeMs(8L)
                .build();
            when(mockEngine.getResult("qid-abc")).thenReturn(Promise.of(result));

            startWithEngine();
            HttpResponse<String> resp = get("/api/v1/analytics/query/qid-abc");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("queryId")).isEqualTo("qid-abc");
            assertThat(((Number) body.get("rowCount")).intValue()).isEqualTo(1);
        }

        @Test
        @DisplayName("unknown queryId (null result) → 404")
        void getResult_nullResult_returns404() throws Exception {
            when(mockEngine.getResult("unknown")).thenReturn(Promise.of(null));

            startWithEngine();
            HttpResponse<String> resp = get("/api/v1/analytics/query/unknown");

            assertThat(resp.statusCode()).isEqualTo(404);
        }
    }

    // ==================== GET /api/v1/analytics/query/:queryId/plan ====================

    @Nested
    @DisplayName("GET /api/v1/analytics/query/:queryId/plan")
    class GetPlanTests {

        @Test
        @DisplayName("known queryId → 200 with plan fields")
        void getPlan_knownId_returns200() throws Exception {
            QueryPlan plan = QueryPlan.builder()
                .queryId("qid-plan-1")
                .queryType(QueryType.SELECT)
                .dataSources(List.of("users", "orders"))
                .estimatedCost(2.5)
                .optimized(false)
                .build();
            when(mockEngine.getPlan("qid-plan-1")).thenReturn(Promise.of(plan));

            startWithEngine();
            HttpResponse<String> resp = get("/api/v1/analytics/query/qid-plan-1/plan");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("queryId")).isEqualTo("qid-plan-1");
            assertThat(body.get("queryType")).isEqualTo("SELECT");
            assertThat(body.get("dataSources")).isNotNull();
            assertThat(((Number) body.get("estimatedCost")).doubleValue()).isEqualTo(2.5);
            assertThat(body.get("optimized")).isEqualTo(false);
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("unknown queryId (null plan) → 404")
        void getPlan_nullPlan_returns404() throws Exception {
            when(mockEngine.getPlan("ghost-id")).thenReturn(Promise.of(null));

            startWithEngine();
            HttpResponse<String> resp = get("/api/v1/analytics/query/ghost-id/plan");

            assertThat(resp.statusCode()).isEqualTo(404);
        }
    }

    // ==================== POST /api/v1/analytics/aggregate ====================

    @Nested
    @DisplayName("POST /api/v1/analytics/aggregate")
    class AggregateTests {

        @Test
        @DisplayName("valid COUNT query → 200 with result")
        void aggregate_countQuery_returns200() throws Exception {
            QueryResult result = QueryResult.builder()
                .queryId("agg-1")
                .queryType("AGGREGATE")
                .rows(List.of(Map.of("user_count", 42)))
                .rowCount(1)
                .columnCount(1)
                .executionTimeMs(20L)
                .build();
            when(mockEngine.submitQuery(anyString(), anyString(), anyMap()))
                .thenReturn(Promise.of(result));

            startWithEngine();
            HttpResponse<String> resp = post("/api/v1/analytics/aggregate",
                "{\"query\":\"SELECT department, COUNT(*) as user_count FROM users GROUP BY department\"}");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("queryId")).isEqualTo("agg-1");
        }

        @Test
        @DisplayName("non-aggregate query → 400 with validation error")
        void aggregate_nonAggregateQuery_returns400() throws Exception {
            startWithEngine();
            HttpResponse<String> resp = post("/api/v1/analytics/aggregate",
                "{\"query\":\"SELECT * FROM users\"}");

            assertThat(resp.statusCode())
                .as("Expected 400 for non-aggregate query; body=%s", resp.body())
                .isEqualTo(400);
        }

        @Test
        @DisplayName("SUM() in query is accepted by aggregate endpoint")
        void aggregate_sumQuery_isAccepted() throws Exception {
            QueryResult result = QueryResult.builder()
                .queryId("agg-sum")
                .queryType("AGGREGATE")
                .rows(List.of(Map.of("total", 99)))
                .rowCount(1).columnCount(1).executionTimeMs(10L).build();
            when(mockEngine.submitQuery(anyString(), anyString(), any()))
                .thenReturn(Promise.of(result));

            startWithEngine();
            HttpResponse<String> resp = post("/api/v1/analytics/aggregate",
                "{\"query\":\"SELECT SUM(amount) as total FROM transactions\"}");

            assertThat(resp.statusCode())
                .as("Expected 200 for SUM aggregate; body=%s", resp.body())
                .isEqualTo(200);
        }

        @Test
        @DisplayName("missing 'query' field → 400")
        void aggregate_missingQuery_returns400() throws Exception {
            startWithEngine();
            HttpResponse<String> resp = post("/api/v1/analytics/aggregate", "{\"x\":1}");
            assertThat(resp.statusCode()).isEqualTo(400);
        }
    }

    // ==================== Helpers ====================

    private void startWithEngine() throws Exception {
        server = new DataCloudHttpServer(mockClient, port, null, null, mockEngine);
        server.start();
        waitForServerReady(port);
    }

    private void startWithoutEngine() throws Exception {
        server = new DataCloudHttpServer(mockClient, port);
        server.start();
        waitForServerReady(port);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json")
            .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static void waitForServerReady(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                new Socket("127.0.0.1", port).close();
                return;
            } catch (IOException ignored) {
                Thread.sleep(50);
            }
        }
        throw new IllegalStateException("Server did not start on port " + port + " within 5 s");
    }
}
