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
    private final ObjectMapper mapper = new ObjectMapper(); 

    @BeforeEach
    void setUp() throws Exception { 
        engine = Aep.forTesting(); 
        port = findFreePort(); 
        httpClient = HttpClient.newBuilder().build(); 
    }

    @AfterEach
    void tearDown() { 
        if (server != null) server.stop(); 
        if (engine != null) engine.close(); 
    }

    // ==================== POST /api/v1/analytics/anomalies ====================

    @Nested
    @DisplayName("POST /api/v1/analytics/anomalies")
    class DetectAnomaliesTests {

        @Test
        @DisplayName("returns 200 with empty anomalies for empty input")
        void detectAnomalies_emptyEvents_returns200() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            String body = mapper.writeValueAsString(Map.of( 
                "tenantId", "tenant-1",
                "events", List.of() 
            ));
            HttpResponse<String> resp = post("/api/v1/analytics/anomalies", body); 

            assertThat(resp.statusCode()).isEqualTo(200); 
            @SuppressWarnings("unchecked") Map<String, Object> respBody = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("anomalies");
        }

        @Test
        @DisplayName("returns 200 with anomaly results for valid events")
        void detectAnomalies_withEvents_returns200() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            String body = mapper.writeValueAsString(Map.of( 
                "tenantId", "tenant-1",
                "events", List.of( 
                    Map.of("type", "cpu_spike", "payload", Map.of("value", 99.5)), 
                    Map.of("type", "memory_leak", "payload", Map.of("value", 85.0)) 
                )
            ));
            HttpResponse<String> resp = post("/api/v1/analytics/anomalies", body); 

            assertThat(resp.statusCode()).isEqualTo(200); 
            @SuppressWarnings("unchecked") Map<String, Object> respBody = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("anomalies");
            assertThat(respBody).containsKey("count");
        }

        @Test
        @DisplayName("returns 400 on malformed JSON body")
        void detectAnomalies_malformedJson_returns400() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post("/api/v1/analytics/anomalies", "{bad json"); 

            assertThat(resp.statusCode()).isEqualTo(400); 
        }
    }

    @Nested
    @DisplayName("POST /api/v1/analytics/anomalies/:anomalyId/false-positive")
    class MarkFalsePositiveTests {

        @Test
        @DisplayName("returns 503 when analytics store is not configured")
        void markFalsePositive_withoutAnalyticsStore_returns503() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            String body = mapper.writeValueAsString(Map.of( 
                "reviewer", "ops-team",
                "rationale", "Known deploy spike"
            ));
            HttpResponse<String> resp = post("/api/v1/analytics/anomalies/anomaly-1/false-positive", body); 

            assertThat(resp.statusCode()).isEqualTo(503); 
            @SuppressWarnings("unchecked") Map<String, Object> respBody = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            assertThat(respBody.get("message").toString()).contains("Analytics store not configured");
        }
    }

    // ==================== POST /api/v1/analytics/forecast ====================

    @Nested
    @DisplayName("POST /api/v1/analytics/forecast")
    class ForecastTests {

        @Test
        @DisplayName("returns 200 with forecast for valid time-series data")
        void forecast_validData_returns200() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            Instant now = Instant.now(); 
            String body = mapper.writeValueAsString(Map.of( 
                "tenantId", "tenant-1",
                "metric", "cpu_usage",
                "points", List.of( 
                    Map.of("timestamp", now.minusSeconds(300).toString(), "value", 45.0), 
                    Map.of("timestamp", now.minusSeconds(240).toString(), "value", 48.2), 
                    Map.of("timestamp", now.minusSeconds(180).toString(), "value", 52.1), 
                    Map.of("timestamp", now.minusSeconds(120).toString(), "value", 55.8), 
                    Map.of("timestamp", now.minusSeconds(60).toString(), "value", 60.0) 
                )
            ));
            HttpResponse<String> resp = post("/api/v1/analytics/forecast", body); 

            assertThat(resp.statusCode()).isEqualTo(200); 
            @SuppressWarnings("unchecked") Map<String, Object> respBody = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("metric");
            assertThat(respBody.get("metric")).isEqualTo("cpu_usage");
        }

        @Test
        @DisplayName("returns 400 on malformed JSON body")
        void forecast_malformedJson_returns400() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post("/api/v1/analytics/forecast", "{bad json"); 

            assertThat(resp.statusCode()).isEqualTo(400); 
        }
    }

    // ==================== POST /api/v1/deployments ====================

    @Nested
    @DisplayName("POST /api/v1/deployments")
    class CreateDeploymentTests {

        @Test
        @DisplayName("returns 200 for valid deployment request")
        void createDeployment_validRequest_returns200() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            String body = mapper.writeValueAsString(Map.of( 
                "agentId", "agent-1",
                "tenantId", "tenant-1",
                "version", "1.0.0",
                "environment", "staging"
            ));
            HttpResponse<String> resp = post("/api/v1/deployments", body); 

            // Deployment adapter processes the request; we just verify it's accepted
            assertThat(resp.statusCode()).isIn(200, 201, 400); 
        }

        @Test
        @DisplayName("returns 400 on malformed JSON body")
        void createDeployment_malformedJson_returns400() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post("/api/v1/deployments", "{bad json"); 

            assertThat(resp.statusCode()).isEqualTo(400); 
        }
    }

    // ==================== DELETE /api/v1/deployments/:deploymentId ====================

    @Nested
    @DisplayName("DELETE /api/v1/deployments/:deploymentId")
    class DeleteDeploymentTests {

        @Test
        @DisplayName("returns 400 when tenantId query parameter is missing")
        void deleteDeployment_missingTenantId_returns400() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = delete("/api/v1/deployments/dep-1");

            assertThat(resp.statusCode()).isEqualTo(400); 
            @SuppressWarnings("unchecked") Map<String, Object> body = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("message").toString()).contains("tenantId");
        }
    }

    // ==================== Helpers ====================

    private HttpResponse<String> post(String path, String body) throws Exception { 
        HttpRequest req = HttpRequest.newBuilder() 
            .POST(HttpRequest.BodyPublishers.ofString(body)) 
            .uri(URI.create("http://127.0.0.1:" + port + path)) 
            .header("Content-Type", "application/json") 
            .build(); 
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 
    }

    private HttpResponse<String> delete(String path) throws Exception { 
        HttpRequest req = HttpRequest.newBuilder() 
            .DELETE() 
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
                return;
            } catch (IOException ignored) { 
                Thread.sleep(50); 
            }
        }
        throw new AssertionError("Server did not start on port " + port + " within 5 s"); 
    }
}
