package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.client.autonomy.AutonomyController;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the autonomy control HTTP endpoints (B9). // GH-90000
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and issues HTTP requests via the
 * Java standard HttpClient. {@link AutonomyController} is mocked.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/autonomy/** HTTP endpoints (B9) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Autonomy Endpoints (B9) [GH-90000]")
class DataCloudHttpServerAutonomyTest {

    private DataCloudClient mockClient;
    private AutonomyController mockController;
    private MetricsCollector mockMetrics;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient     = mock(DataCloudClient.class); // GH-90000
        mockController = mock(AutonomyController.class); // GH-90000
        mockMetrics    = mock(MetricsCollector.class); // GH-90000
        port           = findFreePort(); // GH-90000
        httpClient     = HttpClient.newBuilder().build(); // GH-90000
        lenient().doNothing().when(mockMetrics).incrementCounter(anyString(), anyString(), anyString()); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void startServerWithController() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port) // GH-90000
                .withMetricsCollector(mockMetrics) // GH-90000
                .withAutonomyController(mockController); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private void startServerWithoutController() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port) // GH-90000
                .withMetricsCollector(mockMetrics); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private HttpResponse<String> putJson(String path, String body) throws IOException, InterruptedException { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
                .uri(URI.create("http://localhost:" + port + path)) // GH-90000
                .header("Content-Type", "application/json") // GH-90000
                .header("X-Tenant-Id", "test-tenant") // GH-90000
                .PUT(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
                .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
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

    // ─── tests ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/v1/autonomy/level [GH-90000]")
    class SetGlobalLevelTests {

        @Test
        @DisplayName("returns 200 with globalLevel when controller has no active domains [GH-90000]")
        void setGlobalLevel_noDomains_returns200() throws Exception { // GH-90000
            when(mockController.listAllStates(anyString())) // GH-90000
                    .thenReturn(Promise.of(Map.of())); // GH-90000

            startServerWithController(); // GH-90000

            HttpResponse<String> response = putJson( // GH-90000
                    "/api/v1/autonomy/level",
                    "{\"level\":\"SUGGEST\",\"reason\":\"operator-test\"}");

            assertThat(response.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
            assertThat(body.get("globalLevel [GH-90000]")).isEqualTo("SUGGEST [GH-90000]");
            assertThat(body).containsKey("affectedDomains [GH-90000]");
            assertThat(((Number) body.get("affectedDomains [GH-90000]")).intValue()).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 400 when level field is missing [GH-90000]")
        void setGlobalLevel_missingLevel_returns400() throws Exception { // GH-90000
            startServerWithController(); // GH-90000

            HttpResponse<String> response = putJson( // GH-90000
                    "/api/v1/autonomy/level",
                    "{\"reason\":\"test\"}");

            assertThat(response.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("returns 400 for unknown level value [GH-90000]")
        void setGlobalLevel_unknownLevel_returns400() throws Exception { // GH-90000
            startServerWithController(); // GH-90000

            HttpResponse<String> response = putJson( // GH-90000
                    "/api/v1/autonomy/level",
                    "{\"level\":\"TURBO\"}");

            assertThat(response.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("returns 503 when autonomy controller is not wired [GH-90000]")
        void setGlobalLevel_noController_returns503() throws Exception { // GH-90000
            startServerWithoutController(); // GH-90000

            HttpResponse<String> response = putJson( // GH-90000
                    "/api/v1/autonomy/level",
                    "{\"level\":\"SUGGEST\"}");

            assertThat(response.statusCode()).isEqualTo(503); // GH-90000
        }
    }

    @Nested
    @DisplayName("GET /api/v1/autonomy/level [GH-90000]")
    class GetGlobalLevelTests {

        @Test
        @DisplayName("returns 200 with current level info when controller is wired [GH-90000]")
        void getGlobalLevel_controllerWired_returns200() throws Exception { // GH-90000
            when(mockController.listAllStates(anyString())) // GH-90000
                    .thenReturn(Promise.of(Map.of())); // GH-90000

            startServerWithController(); // GH-90000

            HttpResponse<String> response = get("/api/v1/autonomy/level [GH-90000]");

            assertThat(response.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
            assertThat(body).containsKey("globalLevel [GH-90000]");
            assertThat(body).containsKey("shutoffActive [GH-90000]");
        }

        @Test
        @DisplayName("returns 503 when autonomy controller is not wired [GH-90000]")
        void getGlobalLevel_noController_returns503() throws Exception { // GH-90000
            startServerWithoutController(); // GH-90000

            HttpResponse<String> response = get("/api/v1/autonomy/level [GH-90000]");

            assertThat(response.statusCode()).isEqualTo(503); // GH-90000
        }
    }
}
