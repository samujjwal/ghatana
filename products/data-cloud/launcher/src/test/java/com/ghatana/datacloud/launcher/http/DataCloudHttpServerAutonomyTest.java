package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.client.autonomy.AutonomyController;
import com.ghatana.datacloud.client.autonomy.AutonomyLevel;
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
 * Integration tests for the autonomy control HTTP endpoints (B9).
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and issues HTTP requests via the
 * Java standard HttpClient. {@link AutonomyController} is mocked.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/autonomy/** HTTP endpoints (B9)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Autonomy Endpoints (B9)")
class DataCloudHttpServerAutonomyTest {

    private DataCloudClient mockClient;
    private AutonomyController mockController;
    private MetricsCollector mockMetrics;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockClient     = mock(DataCloudClient.class);
        mockController = mock(AutonomyController.class);
        mockMetrics    = mock(MetricsCollector.class);
        port           = findFreePort();
        httpClient     = HttpClient.newBuilder().build();
        lenient().doNothing().when(mockMetrics).incrementCounter(anyString(), anyString(), anyString());
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void startServerWithController() throws Exception {
        server = new DataCloudHttpServer(mockClient, port)
                .withMetricsCollector(mockMetrics)
                .withAutonomyController(mockController);
        server.start();
        waitForServerReady(port);
    }

    private void startServerWithoutController() throws Exception {
        server = new DataCloudHttpServer(mockClient, port)
                .withMetricsCollector(mockMetrics);
        server.start();
        waitForServerReady(port);
    }

    private HttpResponse<String> putJson(String path, String body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("X-Tenant-Id", "test-tenant")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("X-Tenant-Id", "test-tenant")
                .GET()
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
    @DisplayName("PUT /api/v1/autonomy/level")
    class SetGlobalLevelTests {

        @Test
        @DisplayName("returns 200 with globalLevel when controller has no active domains")
        void setGlobalLevel_noDomains_returns200() throws Exception {
            when(mockController.listAllStates(anyString()))
                    .thenReturn(Promise.of(Map.of()));

            startServerWithController();

            HttpResponse<String> response = putJson(
                    "/api/v1/autonomy/level",
                    "{\"level\":\"SUGGEST\",\"reason\":\"operator-test\"}");

            assertThat(response.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            assertThat(body.get("globalLevel")).isEqualTo("SUGGEST");
            assertThat(body).containsKey("affectedDomains");
            assertThat(((Number) body.get("affectedDomains")).intValue()).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 400 when level field is missing")
        void setGlobalLevel_missingLevel_returns400() throws Exception {
            startServerWithController();

            HttpResponse<String> response = putJson(
                    "/api/v1/autonomy/level",
                    "{\"reason\":\"test\"}");

            assertThat(response.statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("returns 400 for unknown level value")
        void setGlobalLevel_unknownLevel_returns400() throws Exception {
            startServerWithController();

            HttpResponse<String> response = putJson(
                    "/api/v1/autonomy/level",
                    "{\"level\":\"TURBO\"}");

            assertThat(response.statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("returns 503 when autonomy controller is not wired")
        void setGlobalLevel_noController_returns503() throws Exception {
            startServerWithoutController();

            HttpResponse<String> response = putJson(
                    "/api/v1/autonomy/level",
                    "{\"level\":\"SUGGEST\"}");

            assertThat(response.statusCode()).isEqualTo(503);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/autonomy/level")
    class GetGlobalLevelTests {

        @Test
        @DisplayName("returns 200 with current level info when controller is wired")
        void getGlobalLevel_controllerWired_returns200() throws Exception {
            when(mockController.listAllStates(anyString()))
                    .thenReturn(Promise.of(Map.of()));

            startServerWithController();

            HttpResponse<String> response = get("/api/v1/autonomy/level");

            assertThat(response.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            assertThat(body).containsKey("globalLevel");
            assertThat(body).containsKey("shutoffActive");
        }

        @Test
        @DisplayName("returns 503 when autonomy controller is not wired")
        void getGlobalLevel_noController_returns503() throws Exception {
            startServerWithoutController();

            HttpResponse<String> response = get("/api/v1/autonomy/level");

            assertThat(response.statusCode()).isEqualTo(503);
        }
    }
}
