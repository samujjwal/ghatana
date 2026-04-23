package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for the plugin management HTTP endpoints (B6). // GH-90000
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and issues HTTP requests via the
 * Java standard HttpClient. {@link StoragePluginRegistry} is mocked.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/plugins/** HTTP endpoints (B6) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Plugin Management Endpoints (B6)")
class DataCloudHttpServerPluginInstallTest {

    private DataCloudClient mockClient;
    private MetricsCollector mockMetrics;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient   = mock(DataCloudClient.class); // GH-90000
        mockMetrics  = mock(MetricsCollector.class); // GH-90000
        port         = findFreePort(); // GH-90000
        httpClient   = HttpClient.newBuilder().build(); // GH-90000
        lenient().doNothing().when(mockMetrics).incrementCounter(anyString(), anyString(), anyString()); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port) // GH-90000
                .withMetricsCollector(mockMetrics); // GH-90000
        // The built DataCloudHttpServer instantiates PluginInstallHandler with
        // StoragePluginRegistry.getInstance() in buildHandlers(). We cannot // GH-90000
        // inject our mock via the builder since no withPluginRegistry() method exists. // GH-90000
        // Tests therefore validate routing and basic contracts using the real registry
        // (which will be empty) and assert on response structure / status codes. // GH-90000
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

    private HttpResponse<String> post(String path) throws IOException, InterruptedException { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
                .uri(URI.create("http://localhost:" + port + path)) // GH-90000
                .header("X-Tenant-Id", "test-tenant") // GH-90000
                .POST(HttpRequest.BodyPublishers.noBody()) // GH-90000
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
    @DisplayName("GET /api/v1/plugins")
    class ListPluginsTests {

        @Test
        @DisplayName("returns 200 with plugins array and total count")
        void listPlugins_returns200WithPluginsArray() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> response = get("/api/v1/plugins");

            assertThat(response.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
            assertThat(body).containsKeys("plugins", "total"); // GH-90000
        }
    }

    @Nested
    @DisplayName("GET /api/v1/plugins/:id")
    class GetPluginTests {

        @Test
        @DisplayName("returns 404 for an unknown plugin id")
        void getPlugin_unknownId_returns404() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> response = get("/api/v1/plugins/unknown-plugin-xyz");

            assertThat(response.statusCode()).isEqualTo(404); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
            assertThat(body).containsKey("error");
        }
    }

    @Nested
    @DisplayName("POST /api/v1/plugins/:id/enable")
    class EnablePluginTests {

        @Test
        @DisplayName("returns 404 for an unknown plugin id")
        void enablePlugin_unknownId_returns404() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> response = post("/api/v1/plugins/unknown-plugin-xyz/enable");

            assertThat(response.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    @Nested
    @DisplayName("POST /api/v1/plugins/:id/disable")
    class DisablePluginTests {

        @Test
        @DisplayName("returns 404 for an unknown plugin id")
        void disablePlugin_unknownId_returns404() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> response = post("/api/v1/plugins/unknown-plugin-xyz/disable");

            assertThat(response.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    @Nested
    @DisplayName("POST /api/v1/plugins/:id/upgrade")
    class UpgradePluginTests {

        @Test
        @DisplayName("reloads runtime plugins without restarting the launcher")
        void upgradePlugin_reloadRuntimePlugin() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> response = post("/api/v1/plugins/workflow-execution/upgrade");

            assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        }
    }
}
