package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.StoragePlugin;
import com.ghatana.datacloud.spi.StoragePluginRegistry;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the plugin management HTTP endpoints (B6).
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and issues HTTP requests via the
 * Java standard HttpClient. {@link StoragePluginRegistry} is mocked.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/plugins/** HTTP endpoints (B6)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Plugin Management Endpoints (B6)")
class DataCloudHttpServerPluginInstallTest {

    private DataCloudClient mockClient;
    private StoragePluginRegistry mockRegistry;
    private MetricsCollector mockMetrics;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockClient   = mock(DataCloudClient.class);
        mockRegistry = mock(StoragePluginRegistry.class);
        mockMetrics  = mock(MetricsCollector.class);
        port         = findFreePort();
        httpClient   = HttpClient.newBuilder().build();
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
        // The built DataCloudHttpServer instantiates PluginInstallHandler with
        // StoragePluginRegistry.getInstance() in buildHandlers(). We cannot
        // inject our mock via the builder since no withPluginRegistry() method exists.
        // Tests therefore validate routing and basic contracts using the real registry
        // (which will be empty) and assert on response structure / status codes.
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
                .POST(HttpRequest.BodyPublishers.noBody())
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
    @DisplayName("GET /api/v1/plugins")
    class ListPluginsTests {

        @Test
        @DisplayName("returns 200 with plugins array and total count")
        void listPlugins_returns200WithPluginsArray() throws Exception {
            startServer();

            HttpResponse<String> response = get("/api/v1/plugins");

            assertThat(response.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            assertThat(body).containsKeys("plugins", "total");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/plugins/:id")
    class GetPluginTests {

        @Test
        @DisplayName("returns 404 for an unknown plugin id")
        void getPlugin_unknownId_returns404() throws Exception {
            startServer();

            HttpResponse<String> response = get("/api/v1/plugins/unknown-plugin-xyz");

            assertThat(response.statusCode()).isEqualTo(404);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            assertThat(body).containsKey("error");
        }
    }

    @Nested
    @DisplayName("POST /api/v1/plugins/:id/enable")
    class EnablePluginTests {

        @Test
        @DisplayName("returns 404 for an unknown plugin id")
        void enablePlugin_unknownId_returns404() throws Exception {
            startServer();

            HttpResponse<String> response = post("/api/v1/plugins/unknown-plugin-xyz/enable");

            assertThat(response.statusCode()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/plugins/:id/disable")
    class DisablePluginTests {

        @Test
        @DisplayName("returns 404 for an unknown plugin id")
        void disablePlugin_unknownId_returns404() throws Exception {
            startServer();

            HttpResponse<String> response = post("/api/v1/plugins/unknown-plugin-xyz/disable");

            assertThat(response.statusCode()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/plugins/:id/upgrade")
    class UpgradePluginTests {

        @Test
        @DisplayName("returns 404 for an unknown plugin id")
        void upgradePlugin_unknownId_returns404() throws Exception {
            startServer();

            HttpResponse<String> response = post("/api/v1/plugins/unknown-plugin-xyz/upgrade");

            assertThat(response.statusCode()).isEqualTo(404);
        }
    }
}
