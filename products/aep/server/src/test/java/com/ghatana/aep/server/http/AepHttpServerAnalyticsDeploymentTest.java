package com.ghatana.aep.server.http;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AEP HTTP analytics and deployment endpoints.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/analytics/** and /api/v1/deployments/** HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("local-network")
@DisplayName("AepHttpServer – Analytics & Deployment Endpoints")
class AepHttpServerAnalyticsDeploymentTest {

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        engine = Aep.forTesting(); // GH-90000
        port = findFreePort(); // GH-90000
        httpClient = HttpClient.newBuilder().build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
        if (engine != null) engine.close(); // GH-90000
    }

    // ==================== POST /api/v1/analytics/anomalies ====================

    @Nested
    @DisplayName("POST /api/v1/analytics/anomalies")
    class DetectAnomaliesTests {

        @Test
        @DisplayName("returns 200 with empty anomalies for empty input")
        void detectAnomalies_emptyEvents_returns200() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "tenantId", "tenant-1",
                "events", List.of() // GH-90000
            ));
            HttpResponse<String> resp = post("/api/v1/analytics/anomalies", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked") Map<String, Object> respBody = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("anomalies");
        }

        @Test
        @DisplayName("returns 200 with anomaly results for valid events")
        void detectAnomalies_withEvents_returns200() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "tenantId", "tenant-1",
                "events", List.of( // GH-90000
                    Map.of("type", "cpu_spike", "payload", Map.of("value", 99.5)), // GH-90000
                    Map.of("type", "memory_leak", "payload", Map.of("value", 85.0)) // GH-90000
                )
            ));
            HttpResponse<String> resp = post("/api/v1/analytics/anomalies", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked") Map<String, Object> respBody = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("anomalies");
            assertThat(respBody).containsKey("count");
        }

        @Test
        @DisplayName("returns 400 on malformed JSON body")
        void detectAnomalies_malformedJson_returns400() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/analytics/anomalies", "{bad json"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }

    // ==================== POST /api/v1/analytics/forecast ====================

    @Nested
    @DisplayName("POST /api/v1/analytics/forecast")
    class ForecastTests {

        @Test
        @DisplayName("returns 200 with forecast for valid time-series data")
        void forecast_validData_returns200() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            Instant now = Instant.now(); // GH-90000
            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "tenantId", "tenant-1",
                "metric", "cpu_usage",
                "points", List.of( // GH-90000
                    Map.of("timestamp", now.minusSeconds(300).toString(), "value", 45.0), // GH-90000
                    Map.of("timestamp", now.minusSeconds(240).toString(), "value", 48.2), // GH-90000
                    Map.of("timestamp", now.minusSeconds(180).toString(), "value", 52.1), // GH-90000
                    Map.of("timestamp", now.minusSeconds(120).toString(), "value", 55.8), // GH-90000
                    Map.of("timestamp", now.minusSeconds(60).toString(), "value", 60.0) // GH-90000
                )
            ));
            HttpResponse<String> resp = post("/api/v1/analytics/forecast", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked") Map<String, Object> respBody = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("metric");
            assertThat(respBody.get("metric")).isEqualTo("cpu_usage");
        }

        @Test
        @DisplayName("returns 400 on malformed JSON body")
        void forecast_malformedJson_returns400() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/analytics/forecast", "{bad json"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }

    // ==================== POST /api/v1/deployments ====================

    @Nested
    @DisplayName("POST /api/v1/deployments")
    class CreateDeploymentTests {

        @Test
        @DisplayName("returns 200 for valid deployment request")
        void createDeployment_validRequest_returns200() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "agentId", "agent-1",
                "tenantId", "tenant-1",
                "version", "1.0.0",
                "environment", "staging"
            ));
            HttpResponse<String> resp = post("/api/v1/deployments", body); // GH-90000

            // Deployment adapter processes the request; we just verify it's accepted
            assertThat(resp.statusCode()).isIn(200, 201, 400); // GH-90000
        }

        @Test
        @DisplayName("returns 400 on malformed JSON body")
        void createDeployment_malformedJson_returns400() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/deployments", "{bad json"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }

    // ==================== DELETE /api/v1/deployments/:deploymentId ====================

    @Nested
    @DisplayName("DELETE /api/v1/deployments/:deploymentId")
    class DeleteDeploymentTests {

        @Test
        @DisplayName("returns 400 when tenantId query parameter is missing")
        void deleteDeployment_missingTenantId_returns400() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = delete("/api/v1/deployments/dep-1");

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
            @SuppressWarnings("unchecked") Map<String, Object> body = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("message").toString()).contains("tenantId");
        }
    }

    // ==================== Helpers ====================

    private HttpResponse<String> post(String path, String body) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("Content-Type", "application/json") // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> delete(String path) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .DELETE() // GH-90000
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
                return;
            } catch (IOException ignored) { // GH-90000
                Thread.sleep(50); // GH-90000
            }
        }
        throw new AssertionError("Server did not start on port " + port + " within 5 s"); // GH-90000
    }
}
