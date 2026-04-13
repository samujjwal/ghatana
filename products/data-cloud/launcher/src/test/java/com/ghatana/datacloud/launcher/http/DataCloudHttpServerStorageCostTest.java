package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.QueryPlan;
import com.ghatana.datacloud.analytics.QueryResult;
import com.ghatana.platform.observability.MetricsCollector;
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
 * Integration tests for the storage cost HTTP endpoints (B11).
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and issues HTTP requests via the
 * Java standard HttpClient. {@link AnalyticsQueryEngine} is mocked.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/queries/estimate and /api/v1/collections/:id/cost-report (B11)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Storage Cost Endpoints (B11)")
class DataCloudHttpServerStorageCostTest {

    private DataCloudClient mockClient;
    private AnalyticsQueryEngine mockEngine;
    private MetricsCollector mockMetrics;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockClient  = mock(DataCloudClient.class);
        mockEngine  = mock(AnalyticsQueryEngine.class);
        mockMetrics = mock(MetricsCollector.class);
        port        = findFreePort();
        httpClient  = HttpClient.newBuilder().build();
        lenient().doNothing().when(mockMetrics).incrementCounter(anyString(), anyString(), anyString());
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void startServer() throws Exception {
        server = new DataCloudHttpServer(mockClient, port, null, null, mockEngine)
                .withMetricsCollector(mockMetrics);
        server.start();
        waitForServerReady(port);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("X-Tenant-Id", "test-tenant")
                .GET()
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static void waitForServerReady(int port) throws Exception {
        for (int i = 0; i < 50; i++) {
            try (Socket s = new Socket("localhost", port)) {
                return;
            } catch (IOException e) {
                Thread.sleep(100);
            }
        }
        throw new AssertionError("Server did not start on port " + port);
    }

    private QueryResult buildQueryResult(String queryId) {
        return QueryResult.builder()
                .queryId(queryId)
                .rows(List.of())
                .rowCount(0)
                .columnCount(0)
                .executionTimeMs(5L)
                .optimized(true)
                .build();
    }

    private QueryPlan buildQueryPlan(String queryId, double cost) {
        return QueryPlan.builder()
                .queryId(queryId)
                .estimatedCost(cost)
                .optimized(true)
                .dataSources(List.of("events"))
                .build();
    }

    // ─── tests ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/queries/estimate")
    class EstimateQueryTests {

        @Test
        @DisplayName("returns 200 with cost estimate for a valid sql query")
        void estimateQuery_validSql_returns200WithCostFields() throws Exception {
            QueryResult fakeResult = buildQueryResult("q-est-1");
            QueryPlan fakePlan = buildQueryPlan("q-est-1", 5.0);

            when(mockEngine.submitQuery(anyString(), anyString(), anyMap()))
                    .thenReturn(Promise.of(fakeResult));
            when(mockEngine.getPlan(anyString()))
                    .thenReturn(Promise.of(fakePlan));

            startServer();

            HttpResponse<String> response = get("/api/v1/queries/estimate?sql=SELECT+1");

            assertThat(response.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            assertThat(body).containsKeys("queryId", "estimatedCostDcc", "currency", "breakdown");
            assertThat(body.get("currency")).isEqualTo("DCC");
        }

        @Test
        @DisplayName("returns 400 when sql query parameter is missing")
        void estimateQuery_missingSql_returns400() throws Exception {
            startServer();

            HttpResponse<String> response = get("/api/v1/queries/estimate");

            assertThat(response.statusCode()).isEqualTo(400);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            assertThat(body).containsKey("error");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/collections/:id/cost-report")
    class CollectionCostReportTests {

        @Test
        @DisplayName("returns 200 with HOT/WARM/COLD tier breakdown")
        void costReport_returns200WithTierBreakdown() throws Exception {
            QueryResult fakeResult = buildQueryResult("q-cost-1");
            QueryPlan fakePlan = buildQueryPlan("q-cost-1", 10.0);

            when(mockEngine.submitQuery(anyString(), anyString(), anyMap()))
                    .thenReturn(Promise.of(fakeResult));
            when(mockEngine.getPlan(anyString()))
                    .thenReturn(Promise.of(fakePlan));

            startServer();

            HttpResponse<String> response = get("/api/v1/collections/my-collection/cost-report");

            assertThat(response.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            assertThat(body).containsKeys("collectionId", "totalSizeGb", "totalCostDccPerDay", "tiers", "currency");
            assertThat(body.get("collectionId")).isEqualTo("my-collection");
            assertThat(body.get("currency")).isEqualTo("DCC");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tiers = (List<Map<String, Object>>) body.get("tiers");
            assertThat(tiers).hasSize(3);
            List<String> tierNames = tiers.stream()
                    .map(t -> (String) t.get("tier"))
                    .toList();
            assertThat(tierNames).containsExactlyInAnyOrder("HOT", "WARM", "COLD");
        }
    }
}
