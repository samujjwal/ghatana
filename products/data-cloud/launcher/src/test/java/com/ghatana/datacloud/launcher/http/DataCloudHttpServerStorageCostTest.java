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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the storage cost HTTP endpoints (B11). // GH-90000
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and issues HTTP requests via the
 * Java standard HttpClient. {@link AnalyticsQueryEngine} is mocked.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/queries/estimate and /api/v1/collections/:id/cost-report (B11) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Storage Cost Endpoints (B11) [GH-90000]")
class DataCloudHttpServerStorageCostTest {

    private DataCloudClient mockClient;
    private AnalyticsQueryEngine mockEngine;
    private MetricsCollector mockMetrics;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient  = mock(DataCloudClient.class); // GH-90000
        mockEngine  = mock(AnalyticsQueryEngine.class); // GH-90000
        mockMetrics = mock(MetricsCollector.class); // GH-90000
        port        = findFreePort(); // GH-90000
        httpClient  = HttpClient.newBuilder().build(); // GH-90000
        lenient().doNothing().when(mockMetrics).incrementCounter(anyString(), anyString(), anyString()); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port, null, null, mockEngine) // GH-90000
                .withMetricsCollector(mockMetrics); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
                .uri(URI.create("http://localhost:" + port + path)) // GH-90000
                .header("X-Tenant-Id", "test-tenant") // GH-90000
                .GET() // GH-90000
                .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private static int findFreePort() throws IOException { // GH-90000
        try (ServerSocket s = new ServerSocket(0)) { // GH-90000
            return s.getLocalPort(); // GH-90000
        }
    }

    private static void waitForServerReady(int port) throws Exception { // GH-90000
        for (int i = 0; i < 50; i++) { // GH-90000
            try (Socket s = new Socket("localhost", port)) { // GH-90000
                return;
            } catch (IOException e) { // GH-90000
                Thread.sleep(100); // GH-90000
            }
        }
        throw new AssertionError("Server did not start on port " + port); // GH-90000
    }

    private QueryResult buildQueryResult(String queryId) { // GH-90000
        return QueryResult.builder() // GH-90000
                .queryId(queryId) // GH-90000
                .rows(List.of()) // GH-90000
                .rowCount(0) // GH-90000
                .columnCount(0) // GH-90000
                .executionTimeMs(5L) // GH-90000
                .optimized(true) // GH-90000
                .build(); // GH-90000
    }

    private QueryPlan buildQueryPlan(String queryId, double cost) { // GH-90000
        return QueryPlan.builder() // GH-90000
                .queryId(queryId) // GH-90000
                .estimatedCost(cost) // GH-90000
                .optimized(true) // GH-90000
                .dataSources(List.of("events [GH-90000]"))
                .build(); // GH-90000
    }

    // ─── tests ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/queries/estimate [GH-90000]")
    class EstimateQueryTests {

        @Test
        @DisplayName("returns 200 with cost estimate for a valid sql query [GH-90000]")
        void estimateQuery_validSql_returns200WithCostFields() throws Exception { // GH-90000
            QueryResult fakeResult = buildQueryResult("q-est-1 [GH-90000]");
            QueryPlan fakePlan = buildQueryPlan("q-est-1", 5.0); // GH-90000

            when(mockEngine.submitQuery(anyString(), anyString(), anyMap())) // GH-90000
                    .thenReturn(Promise.of(fakeResult)); // GH-90000
            when(mockEngine.getPlan(anyString())) // GH-90000
                    .thenReturn(Promise.of(fakePlan)); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> response = get("/api/v1/queries/estimate?sql=SELECT+1 [GH-90000]");

            assertThat(response.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
            assertThat(body).containsKeys("queryId", "estimatedCostDcc", "currency", "breakdown"); // GH-90000
            assertThat(body.get("currency [GH-90000]")).isEqualTo("DCC [GH-90000]");
        }

        @Test
        @DisplayName("returns 400 when sql query parameter is missing [GH-90000]")
        void estimateQuery_missingSql_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> response = get("/api/v1/queries/estimate [GH-90000]");

            assertThat(response.statusCode()).isEqualTo(400); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
            assertThat(body).containsKey("error [GH-90000]");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/collections/:id/cost-report [GH-90000]")
    class CollectionCostReportTests {

        @Test
        @DisplayName("returns 200 with HOT/WARM/COLD tier breakdown [GH-90000]")
        void costReport_returns200WithTierBreakdown() throws Exception { // GH-90000
            QueryResult fakeResult = buildQueryResult("q-cost-1 [GH-90000]");
            QueryPlan fakePlan = buildQueryPlan("q-cost-1", 10.0); // GH-90000

            when(mockEngine.submitQuery(anyString(), anyString(), anyMap())) // GH-90000
                    .thenReturn(Promise.of(fakeResult)); // GH-90000
            when(mockEngine.getPlan(anyString())) // GH-90000
                    .thenReturn(Promise.of(fakePlan)); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> response = get("/api/v1/collections/my-collection/cost-report [GH-90000]");

            assertThat(response.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
            assertThat(body).containsKeys("collectionId", "totalSizeGb", "totalCostDccPerDay", "tiers", "currency"); // GH-90000
            assertThat(body.get("collectionId [GH-90000]")).isEqualTo("my-collection [GH-90000]");
            assertThat(body.get("currency [GH-90000]")).isEqualTo("DCC [GH-90000]");

            @SuppressWarnings("unchecked [GH-90000]")
            List<Map<String, Object>> tiers = (List<Map<String, Object>>) body.get("tiers [GH-90000]");
            assertThat(tiers).hasSize(3); // GH-90000
            List<String> tierNames = tiers.stream() // GH-90000
                    .map(t -> (String) t.get("tier [GH-90000]"))
                    .toList(); // GH-90000
            assertThat(tierNames).containsExactlyInAnyOrder("HOT", "WARM", "COLD"); // GH-90000
        }
    }
}
