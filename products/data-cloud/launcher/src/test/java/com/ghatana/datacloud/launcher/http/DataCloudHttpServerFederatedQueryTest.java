package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
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
 * Integration tests for the federated query HTTP endpoint (B13).
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and issues HTTP requests via the
 * Java standard HttpClient. External dependencies are mocked.
 *
 * @doc.type class
 * @doc.purpose Integration tests for POST /api/v1/queries/federated (B13)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Federated Query Endpoint (B13)")
class DataCloudHttpServerFederatedQueryTest {

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

    private void startServerWithEngine() throws Exception {
        server = new DataCloudHttpServer(mockClient, port, null, null, mockEngine)
                .withMetricsCollector(mockMetrics);
        server.start();
        waitForServerReady(port);
    }

    private void startServerWithEngineAndTrino(String trinoUrl) throws Exception {
        server = new DataCloudHttpServer(mockClient, port, null, null, mockEngine)
                .withMetricsCollector(mockMetrics)
                .withTrinoUrl(trinoUrl);
        server.start();
        waitForServerReady(port);
    }

    private HttpResponse<String> postJson(String path, String body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("X-Tenant-Id", "test-tenant")
                .POST(HttpRequest.BodyPublishers.ofString(body))
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

    // ─── tests ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Without Trino (local engine fallback)")
    class WithoutTrinoTests {

        @Test
        @DisplayName("returns 200 with FEDERATED_FALLBACK type and warning when Trino not configured")
        void federatedQuery_localFallback_returns200WithWarning() throws Exception {
            QueryResult fakeResult = QueryResult.builder()
                    .queryId("q-001")
                    .rows(List.of(Map.of("id", "1")))
                    .rowCount(1)
                    .columnCount(1)
                    .executionTimeMs(42L)
                    .optimized(false)
                    .build();
            when(mockEngine.submitQuery(anyString(), anyString(), anyMap()))
                    .thenReturn(Promise.of(fakeResult));

            startServerWithEngine();

            HttpResponse<String> response = postJson(
                    "/api/v1/queries/federated",
                    "{\"sql\":\"SELECT * FROM events\"}");

            assertThat(response.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            assertThat(body.get("queryType")).isEqualTo("FEDERATED_FALLBACK");
            assertThat(body).containsKey("warning");
            assertThat(body.get("rowCount")).isEqualTo(1);
        }

        @Test
        @DisplayName("returns 400 when sql field is missing")
        void federatedQuery_missingSql_returns400() throws Exception {
            startServerWithEngine();

            HttpResponse<String> response = postJson(
                    "/api/v1/queries/federated",
                    "{\"tenantId\":\"t1\"}");

            assertThat(response.statusCode()).isEqualTo(400);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            assertThat(body).containsKey("error");
        }

        @Test
        @DisplayName("returns 400 when body is invalid JSON")
        void federatedQuery_invalidJson_returns400() throws Exception {
            startServerWithEngine();

            HttpResponse<String> response = postJson(
                    "/api/v1/queries/federated",
                    "not-json");

            assertThat(response.statusCode()).isEqualTo(400);
        }
    }
}
