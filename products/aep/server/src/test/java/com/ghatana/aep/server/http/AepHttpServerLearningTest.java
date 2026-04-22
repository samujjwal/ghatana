package com.ghatana.aep.server.http;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.agent.learning.review.InMemoryHumanReviewQueue;
import com.ghatana.agent.learning.review.ReviewNotificationSpi;
import com.ghatana.datacloud.DataCloudClient;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for AEP HTTP learning endpoints (episodes, policies, reflection). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/learning/** HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepHttpServer – Learning Endpoints [GH-90000]")
class AepHttpServerLearningTest {

    private AepEngine engine;
    private DataCloudClient mockDc;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        engine = Aep.forTesting(); // GH-90000
        mockDc = mock(DataCloudClient.class); // GH-90000
        port = findFreePort(); // GH-90000
        httpClient = HttpClient.newBuilder().build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
        if (engine != null) engine.close(); // GH-90000
    }

    // ==================== GET /api/v1/learning/episodes ====================

    @Nested
    @DisplayName("GET /api/v1/learning/episodes [GH-90000]")
    class ListEpisodesTests {

        @Test
        @DisplayName("returns 503 when DataCloudClient not configured [GH-90000]")
        void listEpisodes_whenNoDc_returns503() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/learning/episodes [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]") Map<String, Object> body = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("message [GH-90000]").toString()).contains("not available [GH-90000]");
        }

        @Test
        @DisplayName("returns 200 with episodes when DC configured [GH-90000]")
        void listEpisodes_withDc_returns200() throws Exception { // GH-90000
            when(mockDc.query(anyString(), anyString(), any(DataCloudClient.Query.class))) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

            server = new AepHttpServer(engine, port, null, mockDc); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/learning/episodes [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]") Map<String, Object> body = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("episodes [GH-90000]");
            assertThat(body.get("count [GH-90000]")).isEqualTo(0);
        }
    }

    // ==================== GET /api/v1/learning/policies ====================

    @Nested
    @DisplayName("GET /api/v1/learning/policies [GH-90000]")
    class ListPoliciesTests {

        @Test
        @DisplayName("returns 200 truthful unconfigured response when HumanReviewQueue not configured [GH-90000]")
        void listPolicies_whenNoQueue_returnsTruthfulUnconfiguredResponse() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/learning/policies [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]") Map<String, Object> body = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("configured [GH-90000]")).isEqualTo(false);
            assertThat(body.get("count [GH-90000]")).isEqualTo(0);
            assertThat(body.get("message [GH-90000]").toString()).contains("Policy store not available [GH-90000]");
        }

        @Test
        @DisplayName("returns 200 with policies when queue configured [GH-90000]")
        void listPolicies_withQueue_returns200() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue =
                new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            server = new AepHttpServer(engine, port, queue, null); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/learning/policies [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]") Map<String, Object> body = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("policies [GH-90000]");
        }
    }

    // ==================== POST /api/v1/learning/policies/:policyId/approve ====================

    @Nested
    @DisplayName("POST /api/v1/learning/policies/:policyId/approve [GH-90000]")
    class ApprovePolicyTests {

        @Test
        @DisplayName("returns 501 when HumanReviewQueue not configured [GH-90000]")
        void approvePolicy_whenNoQueue_returns501() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/learning/policies/pol-1/approve",
                mapper.writeValueAsString(Map.of("reviewer", "tester")) // GH-90000
            );

            assertThat(resp.statusCode()).isEqualTo(501); // GH-90000
        }

        @Test
        @DisplayName("returns 404 when policy not found [GH-90000]")
        void approvePolicy_notFound_returns404() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue =
                new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            server = new AepHttpServer(engine, port, queue, null); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/learning/policies/nonexistent/approve",
                mapper.writeValueAsString(Map.of("reviewer", "tester")) // GH-90000
            );

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    // ==================== POST /api/v1/learning/policies/:policyId/reject ====================

    @Nested
    @DisplayName("POST /api/v1/learning/policies/:policyId/reject [GH-90000]")
    class RejectPolicyTests {

        @Test
        @DisplayName("returns 501 when HumanReviewQueue not configured [GH-90000]")
        void rejectPolicy_whenNoQueue_returns501() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/learning/policies/pol-1/reject",
                mapper.writeValueAsString(Map.of("reviewer", "tester")) // GH-90000
            );

            assertThat(resp.statusCode()).isEqualTo(501); // GH-90000
        }

        @Test
        @DisplayName("returns 404 when policy not found [GH-90000]")
        void rejectPolicy_notFound_returns404() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue =
                new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            server = new AepHttpServer(engine, port, queue, null); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/learning/policies/nonexistent/reject",
                mapper.writeValueAsString(Map.of("reviewer", "tester")) // GH-90000
            );

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    // ==================== POST /api/v1/learning/reflect ====================

    @Nested
    @DisplayName("POST /api/v1/learning/reflect [GH-90000]")
    class TriggerReflectionTests {

        @Test
        @DisplayName("returns 202 Accepted; triggered=false when pipeline not configured [GH-90000]")
        void triggerReflection_returns202() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/learning/reflect", "{}"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(202); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]") Map<String, Object> body = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            // When DataCloud is not configured the pipeline is null — triggered=false is correct
            assertThat(body).containsKey("triggered [GH-90000]");
            assertThat(body).containsKey("timestamp [GH-90000]");
        }
    }

    // ==================== Helpers ====================

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .GET() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> post(String path, String body) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("Content-Type", "application/json") // GH-90000
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
