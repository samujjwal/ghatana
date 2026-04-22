package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.brain.BrainConfig;
import com.ghatana.datacloud.brain.BrainContext;
import com.ghatana.datacloud.brain.DataCloudBrain;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the Data-Cloud HTTP brain endpoints (DC-6). // GH-90000
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and makes HTTP calls
 * via the Java standard HttpClient. Both {@link DataCloudClient} and {@link DataCloudBrain}
 * are mocked so tests remain self-contained.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/brain/** HTTP endpoints (DC-6) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Brain Endpoints (DC-6) [GH-90000]")
class DataCloudHttpServerBrainTest {

    private DataCloudClient mockClient;
    private DataCloudBrain mockBrain;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        mockBrain  = mock(DataCloudBrain.class); // GH-90000
        port       = findFreePort(); // GH-90000
        httpClient = HttpClient.newBuilder().build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ==================== Brain unavailable (no-brain constructor) ==================== // GH-90000

    @Nested
    @DisplayName("Brain not wired (null brain) [GH-90000]")
    class BrainUnavailableTests {

        @Test
        @DisplayName("GET /api/v1/brain/health → 503 when brain is null [GH-90000]")
        void health_brainNull_returns503() throws Exception { // GH-90000
            startServerWithoutBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/health [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message [GH-90000]").toString()).containsIgnoringCase("not available [GH-90000]");
        }

        @Test
        @DisplayName("GET /api/v1/brain/config → 503 when brain is null [GH-90000]")
        void config_brainNull_returns503() throws Exception { // GH-90000
            startServerWithoutBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/config [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message [GH-90000]").toString()).containsIgnoringCase("not available [GH-90000]");
        }

        @Test
        @DisplayName("GET /api/v1/brain/stats → 503 when brain is null [GH-90000]")
        void stats_brainNull_returns503() throws Exception { // GH-90000
            startServerWithoutBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/stats [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message [GH-90000]").toString()).containsIgnoringCase("not available [GH-90000]");
        }

        @Test
        @DisplayName("GET /api/v1/brain/workspace → 503 when brain is null [GH-90000]")
        void workspace_brainNull_returns503() throws Exception { // GH-90000
            startServerWithoutBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/workspace [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message [GH-90000]").toString()).containsIgnoringCase("not available [GH-90000]");
        }
    }

    // ==================== GET /api/v1/brain/health ====================

    @Nested
    @DisplayName("GET /api/v1/brain/health [GH-90000]")
    class BrainHealthTests {

        @Test
        @DisplayName("healthy brain → 200 with HEALTHY status [GH-90000]")
        void health_healthyBrain_returns200WithHealthyStatus() throws Exception { // GH-90000
            DataCloudBrain.HealthStatus healthStatus = DataCloudBrain.HealthStatus.builder() // GH-90000
                .status(DataCloudBrain.HealthStatus.Status.HEALTHY) // GH-90000
                .components(Map.of( // GH-90000
                    "attention", DataCloudBrain.HealthStatus.Status.HEALTHY,
                    "workspace", DataCloudBrain.HealthStatus.Status.HEALTHY
                ))
                .messages(List.of("Brain operational: brain-test [GH-90000]"))
                .build(); // GH-90000
            when(mockBrain.health()).thenReturn(Promise.of(healthStatus)); // GH-90000

            startServerWithBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/health [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("status [GH-90000]")).isEqualTo("HEALTHY [GH-90000]");
            assertThat(body.get("components [GH-90000]")).isNotNull();
            assertThat(body.get("messages [GH-90000]")).isNotNull();
            assertThat(body.get("timestamp [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("degraded brain → 200 with DEGRADED status [GH-90000]")
        void health_degradedBrain_returns200WithDegradedStatus() throws Exception { // GH-90000
            DataCloudBrain.HealthStatus healthStatus = DataCloudBrain.HealthStatus.builder() // GH-90000
                .status(DataCloudBrain.HealthStatus.Status.DEGRADED) // GH-90000
                .components(Map.of( // GH-90000
                    "attention", DataCloudBrain.HealthStatus.Status.HEALTHY,
                    "reflexes",  DataCloudBrain.HealthStatus.Status.DEGRADED
                ))
                .messages(List.of("Reflex engine degraded [GH-90000]"))
                .build(); // GH-90000
            when(mockBrain.health()).thenReturn(Promise.of(healthStatus)); // GH-90000

            startServerWithBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/health [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("status [GH-90000]")).isEqualTo("DEGRADED [GH-90000]");

            Map<?, ?> components = (Map<?, ?>) body.get("components [GH-90000]");
            assertThat(components.get("reflexes [GH-90000]")).isEqualTo("DEGRADED [GH-90000]");
        }

        @Test
        @DisplayName("brain health check includes component map [GH-90000]")
        void health_always_includesComponentMap() throws Exception { // GH-90000
            DataCloudBrain.HealthStatus healthStatus = DataCloudBrain.HealthStatus.builder() // GH-90000
                .status(DataCloudBrain.HealthStatus.Status.HEALTHY) // GH-90000
                .components(Map.of( // GH-90000
                    "attention", DataCloudBrain.HealthStatus.Status.HEALTHY,
                    "workspace", DataCloudBrain.HealthStatus.Status.HEALTHY,
                    "memory",    DataCloudBrain.HealthStatus.Status.HEALTHY,
                    "patterns",  DataCloudBrain.HealthStatus.Status.HEALTHY
                ))
                .messages(List.of()) // GH-90000
                .build(); // GH-90000
            when(mockBrain.health()).thenReturn(Promise.of(healthStatus)); // GH-90000

            startServerWithBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/health [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            Map<?, ?> components = (Map<?, ?>) body.get("components [GH-90000]");
            assertThat(components.containsKey("attention [GH-90000]")).isTrue();
            assertThat(components.containsKey("workspace [GH-90000]")).isTrue();
            assertThat(components.containsKey("memory [GH-90000]")).isTrue();
            assertThat(components.containsKey("patterns [GH-90000]")).isTrue();
        }
    }

    // ==================== GET /api/v1/brain/config ====================

    @Nested
    @DisplayName("GET /api/v1/brain/config [GH-90000]")
    class BrainConfigTests {

        @Test
        @DisplayName("returns 200 with brain configuration fields [GH-90000]")
        void config_always_returns200WithConfigFields() throws Exception { // GH-90000
            BrainConfig cfg = BrainConfig.builder() // GH-90000
                .brainId("brain-test-01 [GH-90000]")
                .name("Test Brain [GH-90000]")
                .learningEnabled(true) // GH-90000
                .reflexesEnabled(false) // GH-90000
                .salienceThreshold(0.75f) // GH-90000
                .build(); // GH-90000
            when(mockBrain.getConfig()).thenReturn(cfg); // GH-90000

            startServerWithBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/config [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("brainId [GH-90000]")).isEqualTo("brain-test-01 [GH-90000]");
            assertThat(body.get("name [GH-90000]")).isEqualTo("Test Brain [GH-90000]");
            assertThat(body.get("learningEnabled [GH-90000]")).isEqualTo(true);
            assertThat(body.get("reflexesEnabled [GH-90000]")).isEqualTo(false);
            assertThat(body.get("timestamp [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("response contains salienceThreshold field [GH-90000]")
        void config_includesSalienceThreshold() throws Exception { // GH-90000
            BrainConfig cfg = BrainConfig.builder() // GH-90000
                .salienceThreshold(0.5f) // GH-90000
                .build(); // GH-90000
            when(mockBrain.getConfig()).thenReturn(cfg); // GH-90000

            startServerWithBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/config [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.containsKey("salienceThreshold [GH-90000]")).isTrue();
        }
    }

    // ==================== GET /api/v1/brain/stats ====================

    @Nested
    @DisplayName("GET /api/v1/brain/stats [GH-90000]")
    class BrainStatsTests {

        @Test
        @DisplayName("returns 200 with all stats fields [GH-90000]")
        void stats_always_returns200WithStatsFields() throws Exception { // GH-90000
            DataCloudBrain.BrainStats stats = DataCloudBrain.BrainStats.builder() // GH-90000
                .totalRecordsProcessed(1000L) // GH-90000
                .activePatterns(42) // GH-90000
                .activeRules(15) // GH-90000
                .hotTierRecords(200) // GH-90000
                .warmTierRecords(800) // GH-90000
                .avgProcessingTimeMs(2.5) // GH-90000
                .uptimeSeconds(3600L) // GH-90000
                .build(); // GH-90000
            when(mockBrain.getStats(any(BrainContext.class))).thenReturn(Promise.of(stats)); // GH-90000

            startServerWithBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/stats [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(((Number) body.get("totalRecordsProcessed [GH-90000]")).longValue()).isEqualTo(1000L);
            assertThat(((Number) body.get("activePatterns [GH-90000]")).intValue()).isEqualTo(42);
            assertThat(((Number) body.get("activeRules [GH-90000]")).intValue()).isEqualTo(15);
            assertThat(((Number) body.get("hotTierRecords [GH-90000]")).intValue()).isEqualTo(200);
            assertThat(((Number) body.get("warmTierRecords [GH-90000]")).intValue()).isEqualTo(800);
            assertThat(body.get("uptimeSeconds [GH-90000]")).isNotNull();
            assertThat(body.get("tenantId [GH-90000]")).isNotNull();
            assertThat(body.get("timestamp [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("tenantId defaults to 'default' when header absent [GH-90000]")
        void stats_noTenantHeader_defaultTenant() throws Exception { // GH-90000
            DataCloudBrain.BrainStats stats = DataCloudBrain.BrainStats.builder().build(); // GH-90000
            when(mockBrain.getStats(any(BrainContext.class))).thenReturn(Promise.of(stats)); // GH-90000

            startServerWithBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/stats [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("tenantId [GH-90000]")).isEqualTo("default [GH-90000]");
        }

        @Test
        @DisplayName("tenantId from X-Tenant-Id header is forwarded [GH-90000]")
        void stats_withTenantHeader_forwardsTenantId() throws Exception { // GH-90000
            DataCloudBrain.BrainStats stats = DataCloudBrain.BrainStats.builder().build(); // GH-90000
            when(mockBrain.getStats(any(BrainContext.class))).thenReturn(Promise.of(stats)); // GH-90000

            startServerWithBrain(); // GH-90000

            HttpRequest req = HttpRequest.newBuilder() // GH-90000
                .GET() // GH-90000
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/brain/stats")) // GH-90000
                .header("X-Tenant-Id", "tenant-acme") // GH-90000
                .build(); // GH-90000
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("tenantId [GH-90000]")).isEqualTo("tenant-acme [GH-90000]");
        }
    }

    // ==================== GET /api/v1/brain/workspace ====================

    @Nested
    @DisplayName("GET /api/v1/brain/workspace [GH-90000]")
    class BrainWorkspaceTests {

        @Test
        @DisplayName("returns 200 with workspace summary fields [GH-90000]")
        void workspace_always_returns200WithSummaryFields() throws Exception { // GH-90000
            BrainConfig cfg = BrainConfig.builder() // GH-90000
                .brainId("brain-ws-01 [GH-90000]")
                .build(); // GH-90000
            when(mockBrain.getConfig()).thenReturn(cfg); // GH-90000

            startServerWithBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/workspace [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("status [GH-90000]")).isEqualTo("active [GH-90000]");
            assertThat(body.get("brainId [GH-90000]")).isEqualTo("brain-ws-01 [GH-90000]");
            assertThat(body.get("note [GH-90000]")).isNotNull();
            assertThat(body.get("timestamp [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("note field references /api/v1/brain/stats for details [GH-90000]")
        void workspace_note_referencesStatsEndpoint() throws Exception { // GH-90000
            BrainConfig cfg = BrainConfig.builder().build(); // GH-90000
            when(mockBrain.getConfig()).thenReturn(cfg); // GH-90000

            startServerWithBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/workspace [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("note [GH-90000]").toString()).containsIgnoringCase("/api/v1/brain/stats [GH-90000]");
        }
    }

    // ==================== Helpers ====================

    private void startServerWithBrain() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port, mockBrain); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private void startServerWithoutBrain() throws Exception { // GH-90000
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
                return; // port is accepting connections
            } catch (IOException ignored) { // GH-90000
                Thread.sleep(50); // GH-90000
            }
        }
        throw new IllegalStateException("Server did not start on port " + port + " within 5 s"); // GH-90000
    }
}
