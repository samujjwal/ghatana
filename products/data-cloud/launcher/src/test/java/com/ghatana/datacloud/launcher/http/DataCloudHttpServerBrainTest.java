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
 * Integration tests for the Data-Cloud HTTP brain endpoints (DC-6).
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and makes HTTP calls
 * via the Java standard HttpClient. Both {@link DataCloudClient} and {@link DataCloudBrain}
 * are mocked so tests remain self-contained.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/brain/** HTTP endpoints (DC-6)
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
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(DataCloudClient.class);
        mockBrain  = mock(DataCloudBrain.class);
        port       = findFreePort();
        httpClient = HttpClient.newBuilder().build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    // ==================== Brain unavailable (no-brain constructor) ====================

    @Nested
    @DisplayName("Brain not wired (null brain)")
    class BrainUnavailableTests {

        @Test
        @DisplayName("GET /api/v1/brain/health → 503 when brain is null")
        void health_brainNull_returns503() throws Exception {
            startServerWithoutBrain();

            HttpResponse<String> resp = get("/api/v1/brain/health");

            assertThat(resp.statusCode()).isEqualTo(503);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("error").toString()).containsIgnoringCase("not available");
        }

        @Test
        @DisplayName("GET /api/v1/brain/config → 503 when brain is null")
        void config_brainNull_returns503() throws Exception {
            startServerWithoutBrain();

            HttpResponse<String> resp = get("/api/v1/brain/config");

            assertThat(resp.statusCode()).isEqualTo(503);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("error").toString()).containsIgnoringCase("not available");
        }

        @Test
        @DisplayName("GET /api/v1/brain/stats → 503 when brain is null")
        void stats_brainNull_returns503() throws Exception {
            startServerWithoutBrain();

            HttpResponse<String> resp = get("/api/v1/brain/stats");

            assertThat(resp.statusCode()).isEqualTo(503);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("error").toString()).containsIgnoringCase("not available");
        }

        @Test
        @DisplayName("GET /api/v1/brain/workspace → 503 when brain is null")
        void workspace_brainNull_returns503() throws Exception {
            startServerWithoutBrain();

            HttpResponse<String> resp = get("/api/v1/brain/workspace");

            assertThat(resp.statusCode()).isEqualTo(503);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("error").toString()).containsIgnoringCase("not available");
        }
    }

    // ==================== GET /api/v1/brain/health ====================

    @Nested
    @DisplayName("GET /api/v1/brain/health")
    class BrainHealthTests {

        @Test
        @DisplayName("healthy brain → 200 with HEALTHY status")
        void health_healthyBrain_returns200WithHealthyStatus() throws Exception {
            DataCloudBrain.HealthStatus healthStatus = DataCloudBrain.HealthStatus.builder()
                .status(DataCloudBrain.HealthStatus.Status.HEALTHY)
                .components(Map.of(
                    "attention", DataCloudBrain.HealthStatus.Status.HEALTHY,
                    "workspace", DataCloudBrain.HealthStatus.Status.HEALTHY
                ))
                .messages(List.of("Brain operational: brain-test"))
                .build();
            when(mockBrain.health()).thenReturn(Promise.of(healthStatus));

            startServerWithBrain();

            HttpResponse<String> resp = get("/api/v1/brain/health");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("status")).isEqualTo("HEALTHY");
            assertThat(body.get("components")).isNotNull();
            assertThat(body.get("messages")).isNotNull();
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("degraded brain → 200 with DEGRADED status")
        void health_degradedBrain_returns200WithDegradedStatus() throws Exception {
            DataCloudBrain.HealthStatus healthStatus = DataCloudBrain.HealthStatus.builder()
                .status(DataCloudBrain.HealthStatus.Status.DEGRADED)
                .components(Map.of(
                    "attention", DataCloudBrain.HealthStatus.Status.HEALTHY,
                    "reflexes",  DataCloudBrain.HealthStatus.Status.DEGRADED
                ))
                .messages(List.of("Reflex engine degraded"))
                .build();
            when(mockBrain.health()).thenReturn(Promise.of(healthStatus));

            startServerWithBrain();

            HttpResponse<String> resp = get("/api/v1/brain/health");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("status")).isEqualTo("DEGRADED");

            Map<?, ?> components = (Map<?, ?>) body.get("components");
            assertThat(components.get("reflexes")).isEqualTo("DEGRADED");
        }

        @Test
        @DisplayName("brain health check includes component map")
        void health_always_includesComponentMap() throws Exception {
            DataCloudBrain.HealthStatus healthStatus = DataCloudBrain.HealthStatus.builder()
                .status(DataCloudBrain.HealthStatus.Status.HEALTHY)
                .components(Map.of(
                    "attention", DataCloudBrain.HealthStatus.Status.HEALTHY,
                    "workspace", DataCloudBrain.HealthStatus.Status.HEALTHY,
                    "memory",    DataCloudBrain.HealthStatus.Status.HEALTHY,
                    "patterns",  DataCloudBrain.HealthStatus.Status.HEALTHY
                ))
                .messages(List.of())
                .build();
            when(mockBrain.health()).thenReturn(Promise.of(healthStatus));

            startServerWithBrain();

            HttpResponse<String> resp = get("/api/v1/brain/health");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
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
        void config_always_returns200WithConfigFields() throws Exception {
            BrainConfig cfg = BrainConfig.builder()
                .brainId("brain-test-01")
                .name("Test Brain")
                .learningEnabled(true)
                .reflexesEnabled(false)
                .salienceThreshold(0.75f)
                .build();
            when(mockBrain.getConfig()).thenReturn(cfg);

            startServerWithBrain();

            HttpResponse<String> resp = get("/api/v1/brain/config");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("brainId")).isEqualTo("brain-test-01");
            assertThat(body.get("name")).isEqualTo("Test Brain");
            assertThat(body.get("learningEnabled")).isEqualTo(true);
            assertThat(body.get("reflexesEnabled")).isEqualTo(false);
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("response contains salienceThreshold field")
        void config_includesSalienceThreshold() throws Exception {
            BrainConfig cfg = BrainConfig.builder()
                .salienceThreshold(0.5f)
                .build();
            when(mockBrain.getConfig()).thenReturn(cfg);

            startServerWithBrain();

            HttpResponse<String> resp = get("/api/v1/brain/config");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.containsKey("salienceThreshold")).isTrue();
        }
    }

    // ==================== GET /api/v1/brain/stats ====================

    @Nested
    @DisplayName("GET /api/v1/brain/stats")
    class BrainStatsTests {

        @Test
        @DisplayName("returns 200 with all stats fields")
        void stats_always_returns200WithStatsFields() throws Exception {
            DataCloudBrain.BrainStats stats = DataCloudBrain.BrainStats.builder()
                .totalRecordsProcessed(1000L)
                .activePatterns(42)
                .activeRules(15)
                .hotTierRecords(200)
                .warmTierRecords(800)
                .avgProcessingTimeMs(2.5)
                .uptimeSeconds(3600L)
                .build();
            when(mockBrain.getStats(any(BrainContext.class))).thenReturn(Promise.of(stats));

            startServerWithBrain();

            HttpResponse<String> resp = get("/api/v1/brain/stats");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
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
        void stats_noTenantHeader_defaultTenant() throws Exception {
            DataCloudBrain.BrainStats stats = DataCloudBrain.BrainStats.builder().build();
            when(mockBrain.getStats(any(BrainContext.class))).thenReturn(Promise.of(stats));

            startServerWithBrain();

            HttpResponse<String> resp = get("/api/v1/brain/stats");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("tenantId")).isEqualTo("default");
        }

        @Test
        @DisplayName("tenantId from X-Tenant-Id header is forwarded")
        void stats_withTenantHeader_forwardsTenantId() throws Exception {
            DataCloudBrain.BrainStats stats = DataCloudBrain.BrainStats.builder().build();
            when(mockBrain.getStats(any(BrainContext.class))).thenReturn(Promise.of(stats));

            startServerWithBrain();

            HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/brain/stats"))
                .header("X-Tenant-Id", "tenant-acme")
                .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("tenantId")).isEqualTo("tenant-acme");
        }
    }

    // ==================== GET /api/v1/brain/workspace ====================

    @Nested
    @DisplayName("GET /api/v1/brain/workspace")
    class BrainWorkspaceTests {

        @Test
        @DisplayName("returns 200 with workspace summary fields")
        void workspace_always_returns200WithSummaryFields() throws Exception {
            BrainConfig cfg = BrainConfig.builder()
                .brainId("brain-ws-01")
                .build();
            when(mockBrain.getConfig()).thenReturn(cfg);

            startServerWithBrain();

            HttpResponse<String> resp = get("/api/v1/brain/workspace");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("status")).isEqualTo("active");
            assertThat(body.get("brainId")).isEqualTo("brain-ws-01");
            assertThat(body.get("note")).isNotNull();
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("note field references /api/v1/brain/stats for details")
        void workspace_note_referencesStatsEndpoint() throws Exception {
            BrainConfig cfg = BrainConfig.builder().build();
            when(mockBrain.getConfig()).thenReturn(cfg);

            startServerWithBrain();

            HttpResponse<String> resp = get("/api/v1/brain/workspace");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("note").toString()).containsIgnoringCase("/api/v1/brain/stats");
        }
    }

    // ==================== Helpers ====================

    private void startServerWithBrain() throws Exception {
        server = new DataCloudHttpServer(mockClient, port, mockBrain);
        server.start();
        waitForServerReady(port);
    }

    private void startServerWithoutBrain() throws Exception {
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
                return; // port is accepting connections
            } catch (IOException ignored) {
                Thread.sleep(50);
            }
        }
        throw new IllegalStateException("Server did not start on port " + port + " within 5 s");
    }
}
