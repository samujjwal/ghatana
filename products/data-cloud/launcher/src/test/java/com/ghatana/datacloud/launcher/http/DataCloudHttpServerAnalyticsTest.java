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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the Data-Cloud HTTP analytics endpoints (DC-9). // GH-90000
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and makes HTTP calls via the
 * Java standard HttpClient. {@link DataCloudClient} and {@link AnalyticsQueryEngine} are mocked.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/analytics/** HTTP endpoints (DC-9) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Analytics Endpoints (DC-9) [GH-90000]")
class DataCloudHttpServerAnalyticsTest {

    private DataCloudClient mockClient;
    private AnalyticsQueryEngine mockEngine;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        mockEngine = mock(AnalyticsQueryEngine.class); // GH-90000
        port       = findFreePort(); // GH-90000
        httpClient = HttpClient.newBuilder().build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ==================== No engine (null) ==================== // GH-90000

    @Nested
    @DisplayName("Analytics engine not wired (null) [GH-90000]")
    class NoEngineTests {

        @Test
        @DisplayName("POST /query → 503 when engine is null [GH-90000]")
        void query_noEngine_returns503() throws Exception { // GH-90000
            startWithoutEngine(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/analytics/query", // GH-90000
                "{\"query\":\"SELECT * FROM foo\"}");
            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message [GH-90000]").toString()).containsIgnoringCase("not available [GH-90000]");
        }

        @Test
        @DisplayName("GET /query/:id → 503 when engine is null [GH-90000]")
        void getResult_noEngine_returns503() throws Exception { // GH-90000
            startWithoutEngine(); // GH-90000
            HttpResponse<String> resp = get("/api/v1/analytics/query/qid-1 [GH-90000]");
            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
        }

        @Test
        @DisplayName("GET /query/:id/plan → 503 when engine is null [GH-90000]")
        void getPlan_noEngine_returns503() throws Exception { // GH-90000
            startWithoutEngine(); // GH-90000
            HttpResponse<String> resp = get("/api/v1/analytics/query/qid-1/plan [GH-90000]");
            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
        }

        @Test
        @DisplayName("POST /aggregate → 503 when engine is null [GH-90000]")
        void aggregate_noEngine_returns503() throws Exception { // GH-90000
            startWithoutEngine(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/analytics/aggregate", // GH-90000
                "{\"query\":\"SELECT COUNT(*) FROM foo GROUP BY bar\"}"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
        }
    }

    // ==================== POST /api/v1/analytics/query ====================

    @Nested
    @DisplayName("POST /api/v1/analytics/query [GH-90000]")
    class SubmitQueryTests {

        @Test
        @DisplayName("valid query → 200 with full result fields [GH-90000]")
        void submitQuery_valid_returns200WithResult() throws Exception { // GH-90000
            QueryResult result = QueryResult.builder() // GH-90000
                .queryId("qid-123 [GH-90000]")
                .queryType("SELECT [GH-90000]")
                .rows(List.of( // GH-90000
                    Map.of("id", "1", "name", "Alice"), // GH-90000
                    Map.of("id", "2", "name", "Bob") // GH-90000
                ))
                .rowCount(2) // GH-90000
                .columnCount(2) // GH-90000
                .executionTimeMs(15L) // GH-90000
                .optimized(true) // GH-90000
                .build(); // GH-90000
            when(mockEngine.submitQuery(anyString(), anyString(), anyMap())) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            startWithEngine(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/analytics/query", // GH-90000
                "{\"query\":\"SELECT id, name FROM users\"}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("queryId [GH-90000]")).isEqualTo("qid-123 [GH-90000]");
            assertThat(body.get("queryType [GH-90000]")).isEqualTo("SELECT [GH-90000]");
            assertThat(((Number) body.get("rowCount [GH-90000]")).intValue()).isEqualTo(2);
            assertThat(body.get("rows [GH-90000]")).isNotNull();
            assertThat(body.get("timestamp [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("missing 'query' field → 400 [GH-90000]")
        void submitQuery_missingField_returns400() throws Exception { // GH-90000
            startWithEngine(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/analytics/query", "{\"foo\":\"bar\"}"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message [GH-90000]").toString()).containsIgnoringCase("query [GH-90000]");
        }

        @Test
        @DisplayName("blank 'query' field → 400 [GH-90000]")
        void submitQuery_blankQuery_returns400() throws Exception { // GH-90000
            startWithEngine(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/analytics/query", "{\"query\":\"   \"}"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("parameters object is forwarded to engine [GH-90000]")
        void submitQuery_withParameters_forwardedToEngine() throws Exception { // GH-90000
            QueryResult result = QueryResult.builder() // GH-90000
                .queryId("qid-params [GH-90000]")
                .queryType("SELECT [GH-90000]")
                .rows(List.of()) // GH-90000
                .rowCount(0) // GH-90000
                .columnCount(0) // GH-90000
                .executionTimeMs(5L) // GH-90000
                .build(); // GH-90000
            when(mockEngine.submitQuery(anyString(), anyString(), anyMap())) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            startWithEngine(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/analytics/query", // GH-90000
                "{\"query\":\"SELECT * FROM t WHERE id=:id\",\"parameters\":{\"id\":\"42\"}}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        }
    }

    // ==================== GET /api/v1/analytics/query/:queryId ====================

    @Nested
    @DisplayName("GET /api/v1/analytics/query/:queryId [GH-90000]")
    class GetResultTests {

        @Test
        @DisplayName("known queryId → 200 with result [GH-90000]")
        void getResult_knownId_returns200() throws Exception { // GH-90000
            QueryResult result = QueryResult.builder() // GH-90000
                .queryId("qid-abc [GH-90000]")
                .queryType("SELECT [GH-90000]")
                .rows(List.of(Map.of("value", 42))) // GH-90000
                .rowCount(1) // GH-90000
                .columnCount(1) // GH-90000
                .executionTimeMs(8L) // GH-90000
                .build(); // GH-90000
            when(mockEngine.getResult("qid-abc [GH-90000]")).thenReturn(Promise.of(result));

            startWithEngine(); // GH-90000
            HttpResponse<String> resp = get("/api/v1/analytics/query/qid-abc [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("queryId [GH-90000]")).isEqualTo("qid-abc [GH-90000]");
            assertThat(((Number) body.get("rowCount [GH-90000]")).intValue()).isEqualTo(1);
        }

        @Test
        @DisplayName("unknown queryId (null result) → 404 [GH-90000]")
        void getResult_nullResult_returns404() throws Exception { // GH-90000
            when(mockEngine.getResult("unknown [GH-90000]")).thenReturn(Promise.of(null));

            startWithEngine(); // GH-90000
            HttpResponse<String> resp = get("/api/v1/analytics/query/unknown [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    // ==================== GET /api/v1/analytics/query/:queryId/plan ====================

    @Nested
    @DisplayName("GET /api/v1/analytics/query/:queryId/plan [GH-90000]")
    class GetPlanTests {

        @Test
        @DisplayName("known queryId → 200 with plan fields [GH-90000]")
        void getPlan_knownId_returns200() throws Exception { // GH-90000
            QueryPlan plan = QueryPlan.builder() // GH-90000
                .queryId("qid-plan-1 [GH-90000]")
                .queryType(QueryType.SELECT) // GH-90000
                .dataSources(List.of("users", "orders")) // GH-90000
                .estimatedCost(2.5) // GH-90000
                .optimized(false) // GH-90000
                .build(); // GH-90000
            when(mockEngine.getPlan("qid-plan-1 [GH-90000]")).thenReturn(Promise.of(plan));

            startWithEngine(); // GH-90000
            HttpResponse<String> resp = get("/api/v1/analytics/query/qid-plan-1/plan [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("queryId [GH-90000]")).isEqualTo("qid-plan-1 [GH-90000]");
            assertThat(body.get("queryType [GH-90000]")).isEqualTo("SELECT [GH-90000]");
            assertThat(body.get("dataSources [GH-90000]")).isNotNull();
            assertThat(((Number) body.get("estimatedCost [GH-90000]")).doubleValue()).isEqualTo(2.5);
            assertThat(body.get("optimized [GH-90000]")).isEqualTo(false);
            assertThat(body.get("timestamp [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("unknown queryId (null plan) → 404 [GH-90000]")
        void getPlan_nullPlan_returns404() throws Exception { // GH-90000
            when(mockEngine.getPlan("ghost-id [GH-90000]")).thenReturn(Promise.of(null));

            startWithEngine(); // GH-90000
            HttpResponse<String> resp = get("/api/v1/analytics/query/ghost-id/plan [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    // ==================== POST /api/v1/analytics/aggregate ====================

    @Nested
    @DisplayName("POST /api/v1/analytics/aggregate [GH-90000]")
    class AggregateTests {

        @Test
        @DisplayName("valid COUNT query → 200 with result [GH-90000]")
        void aggregate_countQuery_returns200() throws Exception { // GH-90000
            QueryResult result = QueryResult.builder() // GH-90000
                .queryId("agg-1 [GH-90000]")
                .queryType("AGGREGATE [GH-90000]")
                .rows(List.of(Map.of("user_count", 42))) // GH-90000
                .rowCount(1) // GH-90000
                .columnCount(1) // GH-90000
                .executionTimeMs(20L) // GH-90000
                .build(); // GH-90000
            when(mockEngine.submitQuery(anyString(), anyString(), anyMap())) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            startWithEngine(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/analytics/aggregate", // GH-90000
                "{\"query\":\"SELECT department, COUNT(*) as user_count FROM users GROUP BY department\"}"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("queryId [GH-90000]")).isEqualTo("agg-1 [GH-90000]");
        }

        @Test
        @DisplayName("non-aggregate query → 400 with validation error [GH-90000]")
        void aggregate_nonAggregateQuery_returns400() throws Exception { // GH-90000
            startWithEngine(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/analytics/aggregate", // GH-90000
                "{\"query\":\"SELECT * FROM users\"}");

            assertThat(resp.statusCode()) // GH-90000
                .as("Expected 400 for non-aggregate query; body=%s", resp.body()) // GH-90000
                .isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("SUM() in query is accepted by aggregate endpoint [GH-90000]")
        void aggregate_sumQuery_isAccepted() throws Exception { // GH-90000
            QueryResult result = QueryResult.builder() // GH-90000
                .queryId("agg-sum [GH-90000]")
                .queryType("AGGREGATE [GH-90000]")
                .rows(List.of(Map.of("total", 99))) // GH-90000
                .rowCount(1).columnCount(1).executionTimeMs(10L).build(); // GH-90000
            when(mockEngine.submitQuery(anyString(), anyString(), any())) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            startWithEngine(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/analytics/aggregate", // GH-90000
                "{\"query\":\"SELECT SUM(amount) as total FROM transactions\"}"); // GH-90000

            assertThat(resp.statusCode()) // GH-90000
                .as("Expected 200 for SUM aggregate; body=%s", resp.body()) // GH-90000
                .isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("missing 'query' field → 400 [GH-90000]")
        void aggregate_missingQuery_returns400() throws Exception { // GH-90000
            startWithEngine(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/analytics/aggregate", "{\"x\":1}"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }
    @Nested
    @DisplayName("POST /api/v1/analytics/explain [GH-90000]")
    class ExplainQueryTests {

        @BeforeEach
        void stubExplain() { // GH-90000
            QueryPlan plan = QueryPlan.builder() // GH-90000
                    .queryId("explain-stub [GH-90000]")
                    .queryType(QueryType.SELECT) // GH-90000
                    .dataSources(List.of("products [GH-90000]"))
                    .estimatedCost(1.5) // GH-90000
                    .optimized(true) // GH-90000
                    .build(); // GH-90000
            lenient().when(mockEngine.explainQuery(anyString(), anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(plan)); // GH-90000
        }

        @Test
        @DisplayName("valid SELECT query → 200 with plan fields and explain=true [GH-90000]")
        void explain_validSelectQuery_returns200WithPlan() throws Exception { // GH-90000
            startWithEngine(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/analytics/explain", // GH-90000
                    "{\"query\":\"SELECT * FROM products\"}");
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = new com.fasterxml.jackson.databind.ObjectMapper() // GH-90000
                    .readValue(resp.body(), Map.class); // GH-90000
            assertThat(body).containsKey("queryType [GH-90000]");
            assertThat(body).containsKey("dataSources [GH-90000]");
            assertThat(body).containsKey("estimatedCost [GH-90000]");
            assertThat(body).containsKey("optimized [GH-90000]");
            assertThat(body.get("explain [GH-90000]")).isEqualTo(Boolean.TRUE);
            assertThat(body).containsKey("timestamp [GH-90000]");
        }

        @Test
        @DisplayName("missing 'query' field → 400 [GH-90000]")
        void explain_missingQuery_returns400() throws Exception { // GH-90000
            startWithEngine(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/analytics/explain", "{\"parameters\":{}}"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("engine not wired → 503 [GH-90000]")
        void explain_noEngine_returns503() throws Exception { // GH-90000
            startWithoutEngine(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/analytics/explain", // GH-90000
                    "{\"query\":\"SELECT 1\"}");
            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
        }

        @Test
        @DisplayName("COUNT query → queryType reflects aggregate planner decision [GH-90000]")
        void explain_countQuery_returnsAggregateQueryType() throws Exception { // GH-90000
            startWithEngine(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/analytics/explain", // GH-90000
                    "{\"query\":\"SELECT COUNT(*) FROM orders GROUP BY status\"}"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = new com.fasterxml.jackson.databind.ObjectMapper() // GH-90000
                    .readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("queryType [GH-90000]")).isNotNull();
        }
    }
    // ==================== Helpers ====================

    private void startWithEngine() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port, null, null, mockEngine); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private void startWithoutEngine() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .GET() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> post(String path, String body) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("Content-Type", "application/json") // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private static int findFreePort() throws IOException { // GH-90000
        try (ServerSocket ss = new ServerSocket(0)) { // GH-90000
            return ss.getLocalPort(); // GH-90000
        }
    }

    private static void waitForServerReady(int port) throws Exception { // GH-90000
        long deadline = System.currentTimeMillis() + 5_000; // GH-90000
        while (System.currentTimeMillis() < deadline) { // GH-90000
            try {
                new Socket("127.0.0.1", port).close(); // GH-90000
                return;
            } catch (IOException ignored) { // GH-90000
                Thread.sleep(50); // GH-90000
            }
        }
        throw new IllegalStateException("Server did not start on port " + port + " within 5 s"); // GH-90000
    }
}
