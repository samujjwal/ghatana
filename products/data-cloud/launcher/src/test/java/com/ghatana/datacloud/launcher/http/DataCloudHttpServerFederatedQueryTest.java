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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the federated query HTTP endpoint (B13). // GH-90000
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and issues HTTP requests via the
 * Java standard HttpClient. External dependencies are mocked.
 *
 * @doc.type class
 * @doc.purpose Integration tests for POST /api/v1/queries/federated (B13) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Federated Query Endpoint (B13) [GH-90000]")
class DataCloudHttpServerFederatedQueryTest {

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

    private void startServerWithEngine() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port, null, null, mockEngine) // GH-90000
                .withMetricsCollector(mockMetrics); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private void startServerWithEngineAndTrino(String trinoUrl) throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port, null, null, mockEngine) // GH-90000
                .withMetricsCollector(mockMetrics) // GH-90000
                .withTrinoUrl(trinoUrl); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private HttpResponse<String> postJson(String path, String body) throws IOException, InterruptedException { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
                .uri(URI.create("http://localhost:" + port + path)) // GH-90000
                .header("Content-Type", "application/json") // GH-90000
                .header("X-Tenant-Id", "test-tenant") // GH-90000
                .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
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

    // ─── tests ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Without Trino (local engine fallback) [GH-90000]")
    class WithoutTrinoTests {

        @Test
        @DisplayName("returns 200 with FEDERATED_FALLBACK type and warning when Trino not configured [GH-90000]")
        void federatedQuery_localFallback_returns200WithWarning() throws Exception { // GH-90000
            QueryResult fakeResult = QueryResult.builder() // GH-90000
                    .queryId("q-001 [GH-90000]")
                    .rows(List.of(Map.of("id", "1"))) // GH-90000
                    .rowCount(1) // GH-90000
                    .columnCount(1) // GH-90000
                    .executionTimeMs(42L) // GH-90000
                    .optimized(false) // GH-90000
                    .build(); // GH-90000
            when(mockEngine.submitQuery(anyString(), anyString(), anyMap())) // GH-90000
                    .thenReturn(Promise.of(fakeResult)); // GH-90000

            startServerWithEngine(); // GH-90000

            HttpResponse<String> response = postJson( // GH-90000
                    "/api/v1/queries/federated",
                    "{\"sql\":\"SELECT * FROM events\"}");

            assertThat(response.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
            assertThat(body.get("queryType [GH-90000]")).isEqualTo("FEDERATED_FALLBACK [GH-90000]");
            assertThat(body).containsKey("warning [GH-90000]");
            assertThat(body.get("rowCount [GH-90000]")).isEqualTo(1);
        }

        @Test
        @DisplayName("returns 400 when sql field is missing [GH-90000]")
        void federatedQuery_missingSql_returns400() throws Exception { // GH-90000
            startServerWithEngine(); // GH-90000

            HttpResponse<String> response = postJson( // GH-90000
                    "/api/v1/queries/federated",
                    "{\"tenantId\":\"t1\"}");

            assertThat(response.statusCode()).isEqualTo(400); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
            assertThat(body).containsKey("message [GH-90000]");
        }

        @Test
        @DisplayName("returns 400 when body is invalid JSON [GH-90000]")
        void federatedQuery_invalidJson_returns400() throws Exception { // GH-90000
            startServerWithEngine(); // GH-90000

            HttpResponse<String> response = postJson( // GH-90000
                    "/api/v1/queries/federated",
                    "not-json");

            assertThat(response.statusCode()).isEqualTo(400); // GH-90000
        }
    }
}
