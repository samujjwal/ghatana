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
@DisplayName("DataCloudHttpServer – Brain Endpoints (DC-6)")
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
    @DisplayName("Brain not wired (null brain)")
    class BrainUnavailableTests {

        @Test
        @DisplayName("GET /api/v1/brain/health → 503 when brain is null")
        void health_brainNull_returns503() throws Exception { // GH-90000
            startServerWithoutBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/health");

            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message").toString()).containsIgnoringCase("not available");
        }

        @Test
        @DisplayName("GET /api/v1/brain/config → 503 when brain is null")
        void config_brainNull_returns503() throws Exception { // GH-90000
            startServerWithoutBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/config");

            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message").toString()).containsIgnoringCase("not available");
        }

        @Test
        @DisplayName("GET /api/v1/brain/stats → 503 when brain is null")
        void stats_brainNull_returns503() throws Exception { // GH-90000
            startServerWithoutBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/stats");

            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message").toString()).containsIgnoringCase("not available");
        }

        @Test
        @DisplayName("GET /api/v1/brain/workspace → 503 when brain is null")
        void workspace_brainNull_returns503() throws Exception { // GH-90000
            startServerWithoutBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/workspace");

            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message").toString()).containsIgnoringCase("not available");
        }
    }

    // ==================== GET /api/v1/brain/health ====================

    @Nested
    @DisplayName("GET /api/v1/brain/health")
    class BrainHealthTests {

        @Test
        @DisplayName("healthy brain → 200 with HEALTHY status")
        void health_healthyBrain_returns200WithHealthyStatus() throws Exception { // GH-90000
            DataCloudBrain.HealthStatus healthStatus = DataCloudBrain.HealthStatus.builder() // GH-90000
                .status(DataCloudBrain.HealthStatus.Status.HEALTHY) // GH-90000
                .components(Map.of( // GH-90000
                    "attention", DataCloudBrain.HealthStatus.Status.HEALTHY,
                    "workspace", DataCloudBrain.HealthStatus.Status.HEALTHY
                ))
                .messages(List.of("Brain operational: brain-test"))
                .build(); // GH-90000
            when(mockBrain.health()).thenReturn(Promise.of(healthStatus)); // GH-90000

            startServerWithBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/health");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("status")).isEqualTo("HEALTHY");
            assertThat(body.get("components")).isNotNull();
            assertThat(body.get("messages")).isNotNull();
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("degraded brain → 200 with DEGRADED status")
        void health_degradedBrain_returns200WithDegradedStatus() throws Exception { // GH-90000
            DataCloudBrain.HealthStatus healthStatus = DataCloudBrain.HealthStatus.builder() // GH-90000
                .status(DataCloudBrain.HealthStatus.Status.DEGRADED) // GH-90000
                .components(Map.of( // GH-90000
                    "attention", DataCloudBrain.HealthStatus.Status.HEALTHY,
                    "reflexes",  DataCloudBrain.HealthStatus.Status.DEGRADED
                ))
                .messages(List.of("Reflex engine degraded"))
                .build(); // GH-90000
            when(mockBrain.health()).thenReturn(Promise.of(healthStatus)); // GH-90000

            startServerWithBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/health");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("status")).isEqualTo("DEGRADED");

            Map<?, ?> components = (Map<?, ?>) body.get("components");
            assertThat(components.get("reflexes")).isEqualTo("DEGRADED");
        }

        @Test
        @DisplayName("brain health check includes component map")
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

            HttpResponse<String> resp = get("/api/v1/brain/health");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            Map<?, ?> components = (Map<?, ?>) body.get("components");
            assertThat(components.containsKey("attention")).isTrue();
            assertThat(components.containsKey("workspace")).isTrue();
            assertThat(components.containsKey("memory")).isTrue();
            assertThat(components.containsKey("patterns")).isTrue();
        }
    }

    // ==================== GET /api/v1/brain/config ====================

    @Nested
    @DisplayName("GET /api/v1/brain/config")
    class BrainConfigTests {

        @Test
        @DisplayName("returns 200 with brain configuration fields")
        void config_always_returns200WithConfigFields() throws Exception { // GH-90000
            BrainConfig cfg = BrainConfig.builder() // GH-90000
                .brainId("brain-test-01")
                .name("Test Brain")
                .learningEnabled(true) // GH-90000
                .reflexesEnabled(false) // GH-90000
                .salienceThreshold(0.75f) // GH-90000
                .build(); // GH-90000
            when(mockBrain.getConfig()).thenReturn(cfg); // GH-90000

            startServerWithBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/config");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("brainId")).isEqualTo("brain-test-01");
            assertThat(body.get("name")).isEqualTo("Test Brain");
            assertThat(body.get("learningEnabled")).isEqualTo(true);
            assertThat(body.get("reflexesEnabled")).isEqualTo(false);
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("response contains salienceThreshold field")
        void config_includesSalienceThreshold() throws Exception { // GH-90000
            BrainConfig cfg = BrainConfig.builder() // GH-90000
                .salienceThreshold(0.5f) // GH-90000
                .build(); // GH-90000
            when(mockBrain.getConfig()).thenReturn(cfg); // GH-90000

            startServerWithBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/config");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.containsKey("salienceThreshold")).isTrue();
        }
    }

    // ==================== GET /api/v1/brain/stats ====================

    @Nested
    @DisplayName("GET /api/v1/brain/stats")
    class BrainStatsTests {

        @Test
        @DisplayName("returns 200 with all stats fields")
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

            HttpResponse<String> resp = get("/api/v1/brain/stats");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(((Number) body.get("totalRecordsProcessed")).longValue()).isEqualTo(1000L);
            assertThat(((Number) body.get("activePatterns")).intValue()).isEqualTo(42);
            assertThat(((Number) body.get("activeRules")).intValue()).isEqualTo(15);
            assertThat(((Number) body.get("hotTierRecords")).intValue()).isEqualTo(200);
            assertThat(((Number) body.get("warmTierRecords")).intValue()).isEqualTo(800);
            assertThat(body.get("uptimeSeconds")).isNotNull();
            assertThat(body.get("tenantId")).isNotNull();
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("tenantId defaults to 'default' when header absent")
        void stats_noTenantHeader_defaultTenant() throws Exception { // GH-90000
            DataCloudBrain.BrainStats stats = DataCloudBrain.BrainStats.builder().build(); // GH-90000
            when(mockBrain.getStats(any(BrainContext.class))).thenReturn(Promise.of(stats)); // GH-90000

            startServerWithBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/stats");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("tenantId")).isEqualTo("default");
        }

        @Test
        @DisplayName("tenantId from X-Tenant-Id header is forwarded")
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
            assertThat(body.get("tenantId")).isEqualTo("tenant-acme");
        }
    }

    // ==================== GET /api/v1/brain/workspace ====================

    @Nested
    @DisplayName("GET /api/v1/brain/workspace")
    class BrainWorkspaceTests {

        @Test
        @DisplayName("returns 200 with workspace summary fields")
        void workspace_always_returns200WithSummaryFields() throws Exception { // GH-90000
            BrainConfig cfg = BrainConfig.builder() // GH-90000
                .brainId("brain-ws-01")
                .build(); // GH-90000
            when(mockBrain.getConfig()).thenReturn(cfg); // GH-90000

            startServerWithBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/workspace");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("status")).isEqualTo("active");
            assertThat(body.get("brainId")).isEqualTo("brain-ws-01");
            assertThat(body.get("note")).isNotNull();
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("note field references /api/v1/brain/stats for details")
        void workspace_note_referencesStatsEndpoint() throws Exception { // GH-90000
            BrainConfig cfg = BrainConfig.builder().build(); // GH-90000
            when(mockBrain.getConfig()).thenReturn(cfg); // GH-90000

            startServerWithBrain(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/brain/workspace");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("note").toString()).containsIgnoringCase("/api/v1/brain/stats");
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
