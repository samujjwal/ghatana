package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.observability.MetricsCollector;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Verifies that gated capabilities return the correct "not enabled" HTTP status when
 * the matching feature flag or optional dependency is absent.
 *
 * <p>Each test starts a real {@link DataCloudHttpServer} on a random port with the
 * capability under test in its disabled/unconfigured state and asserts on the response
 * status code.
 *
 *
 * @doc.type class
 * @doc.purpose Integration tests asserting gated capabilities return correct disabled status codes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Disabled Capability Gating")
class DataCloudHttpServerDisabledCapabilityTest {

    private DataCloudClient mockClient;
    private MetricsCollector mockMetrics;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() throws Exception {
        mockClient  = mock(DataCloudClient.class);
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
        server = new DataCloudHttpServer(mockClient, port)
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

    private HttpResponse<String> post(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("X-Tenant-Id", "test-tenant")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json")
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

    // ─── Workflow execution gating ────────────────────────────────────────────

    @Nested
    @DisplayName("Workflow execution gated (workflowExecutionEnabled=false)")
    class WorkflowExecutionDisabledTests {

        @Test
        @DisplayName("POST /api/v1/pipelines/:id/execute returns 501 when workflow execution capability is absent")
        void executePipeline_withoutWorkflowPlugin_returns501() throws Exception {
            server = new DataCloudHttpServer(mockClient, port)
                    .withMetricsCollector(mockMetrics);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> response = post("/api/v1/pipelines/my-pipeline/execute");

            assertThat(response.statusCode()).isEqualTo(501);
        }
    }

    // ─── OpenSearch not configured ────────────────────────────────────────────

    @Nested
    @DisplayName("Full-text search gated (OpenSearch not configured)")
    class OpenSearchNotConfiguredTests {

        @Test
        @DisplayName("GET /api/v1/entities/:collection/search returns 501 without OpenSearch")
        void searchEntities_withoutOpenSearch_returns501() throws Exception {
            startServer(); // no withOpenSearchConnector call

            HttpResponse<String> response = get("/api/v1/entities/orders/search?q=*");

            assertThat(response.statusCode()).isEqualTo(501);
        }
    }

    // ─── Plugin upgrade gating ────────────────────────────────────────────────

    @Nested
    @DisplayName("Plugin upgrade gated (pluginUpgradeEnabled=false by default)")
    class PluginUpgradeDisabledTests {

        @Test
        @DisplayName("POST /api/v1/plugins/:id/upgrade returns 501 when upgrade is disabled")
        void upgradePlugin_withoutFlag_returns501() throws Exception {
            startServer(); // pluginUpgradeEnabled defaults to false

            HttpResponse<String> response = post("/api/v1/plugins/some-plugin/upgrade");

            assertThat(response.statusCode()).isEqualTo(501);
        }
    }

    // ─── Analytics engine not configured ─────────────────────────────────────

    @Nested
    @DisplayName("Analytics engine not configured")
    class AnalyticsEngineNotConfiguredTests {

        @Test
        @DisplayName("POST /api/v1/analytics/query returns 503 when analytics engine absent")
        void analyticsQuery_withoutEngine_returns503() throws Exception {
            startServer(); // no withAnalyticsEngine call

            HttpResponse<String> response = post("/api/v1/analytics/query");

            assertThat(response.statusCode()).isEqualTo(503);
        }

        @Test
        @DisplayName("POST /api/v1/analytics/suggest uses heuristic fallback (200) when LLM absent — no hard gate")
        void analyticsSuggest_withoutLlm_usesFallbackHeuristics_returns200() throws Exception {
            // The /api/v1/analytics/suggest route is handled by AiAssistHandler, not AnalyticsHandler.
            // When completionService is null, the handler degrades gracefully to static heuristics
            // and returns 200 rather than a 503 gate — this is the documented production design.
            startServer();

            HttpResponse<String> response = post("/api/v1/analytics/suggest");

            assertThat(response.statusCode()).isEqualTo(200);
        }
    }

    // ─── AI model not configured ──────────────────────────────────────────────

    @Nested
    @DisplayName("AI/LLM capabilities not configured")
    class AiNotConfiguredTests {

        @Test
        @DisplayName("POST /api/v1/ai/suggestions uses heuristic fallback (200) when LLM absent — no hard gate")
        void aiSuggestions_withoutLlm_usesFallbackHeuristics_returns200() throws Exception {
            // AiAssistHandler is always wired; when completionService is null it degrades to
            // static-heuristic mode and returns 200.  Hard-gating (503) applies only to
            // capabilities backed by optional connectors (analytics engine, brain, OpenSearch).
            startServer(); // no withCompletionService call

            HttpResponse<String> response = post("/api/v1/ai/suggestions");

            assertThat(response.statusCode()).isEqualTo(200);
        }
    }

    // ─── Federated query not configured ──────────────────────────────────────

    @Nested
    @DisplayName("Federated query engine not configured")
    class FederatedQueryNotConfiguredTests {

        @Test
        @DisplayName("POST /api/v1/queries/federated returns 503 when Trino connector absent")
        void federatedQuery_withoutConnector_returns503() throws Exception {
            startServer(); // no Trino connector wired

            HttpResponse<String> response = post("/api/v1/queries/federated");

            assertThat(response.statusCode()).isEqualTo(503);
        }
    }

    // ─── SSE / streaming gate (P0-05) ─────────────────────────────────────────

    @Nested
    @DisplayName("SSE entity query-stream gated (OpenSearch not configured) — P0-05")
    class SseQueryStreamNotConfiguredTests {

        @Test
        @DisplayName("GET /api/v1/entities/:collection/query/stream returns 501 without OpenSearch (P0-05)")
        void entityQueryStream_withoutOpenSearch_returns501() throws Exception {
            startServer(); // no withOpenSearchConnector — SSE query-stream is gated

            HttpResponse<String> response = get("/api/v1/entities/orders/query/stream?q=status:open");

            assertThat(response.statusCode()).isEqualTo(501);
        }
    }

    @Nested
    @DisplayName("Brain SSE workspace-stream gated (brain absent) — P0-05")
    class BrainSseStreamNotConfiguredTests {

        @Test
        @DisplayName("GET /api/v1/brain/workspace/stream returns 503 when brain is not configured (P0-05)")
        void brainWorkspaceStream_withoutBrain_returns503() throws Exception {
            // Brain is null by default (no brain constructor argument supplied).
            startServer();

            HttpResponse<String> response = get("/api/v1/brain/workspace/stream");

            assertThat(response.statusCode()).isEqualTo(503);
        }
    }
}
