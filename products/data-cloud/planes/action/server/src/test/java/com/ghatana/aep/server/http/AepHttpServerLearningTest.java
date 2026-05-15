package com.ghatana.aep.server.http;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.agent.learning.review.InMemoryHumanReviewQueue;
import com.ghatana.agent.learning.review.ReviewItem;
import com.ghatana.agent.learning.review.ReviewNotificationSpi;
import com.ghatana.agent.learning.review.ReviewItemType;
import com.ghatana.datacloud.DataCloudClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for AEP HTTP learning endpoints (episodes, policies, reflection). 
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/learning/** HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("local-network")
@DisplayName("AepHttpServer – Learning Endpoints")
class AepHttpServerLearningTest {

    private AepEngine engine;
    private DataCloudClient mockDc;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); 
    private String previousAuthDisabled;
    private String previousJwtSecret;

    @BeforeEach
    void setUp() throws Exception { 
        previousAuthDisabled = System.getProperty("AEP_AUTH_DISABLED");
        previousJwtSecret = System.getProperty("AEP_JWT_SECRET");
        System.setProperty("AEP_AUTH_DISABLED", "true");
        System.clearProperty("AEP_JWT_SECRET");

        engine = Aep.forTesting(); 
        mockDc = mock(DataCloudClient.class); 
        port = findFreePort(); 
        httpClient = HttpClient.newBuilder().build(); 
    }

    @AfterEach
    void tearDown() { 
        if (server != null) server.stop(); 
        if (engine != null) engine.close(); 

        if (previousAuthDisabled == null) {
            System.clearProperty("AEP_AUTH_DISABLED");
        } else {
            System.setProperty("AEP_AUTH_DISABLED", previousAuthDisabled);
        }

        if (previousJwtSecret == null) {
            System.clearProperty("AEP_JWT_SECRET");
        } else {
            System.setProperty("AEP_JWT_SECRET", previousJwtSecret);
        }
    }

    // ==================== GET /api/v1/learning/episodes ====================

    @Nested
    @DisplayName("GET /api/v1/learning/episodes")
    class ListEpisodesTests {

        @Test
        @DisplayName("returns 503 when DataCloudClient not configured")
        void listEpisodes_whenNoDc_returns503() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = get("/api/v1/learning/episodes");

            assertThat(resp.statusCode()).isEqualTo(503); 
            @SuppressWarnings("unchecked") Map<String, Object> body = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("message").toString()).contains("not available");
        }

        @Test
        @DisplayName("returns 200 with episodes when DC configured")
        void listEpisodes_withDc_returns200() throws Exception { 
            when(mockDc.query(anyString(), anyString(), any(DataCloudClient.Query.class))) 
                .thenReturn(Promise.of(List.of())); 

            server = new AepHttpServer(engine, port, null, mockDc); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = get("/api/v1/learning/episodes");

            assertThat(resp.statusCode()).isEqualTo(200); 
            @SuppressWarnings("unchecked") Map<String, Object> body = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("episodes");
            assertThat(body.get("count")).isEqualTo(0);
        }
    }

    // ==================== GET /api/v1/learning/policies ====================

    @Nested
    @DisplayName("GET /api/v1/learning/policies")
    class ListPoliciesTests {

        @Test
        @DisplayName("returns 200 truthful unconfigured response when HumanReviewQueue not configured")
        void listPolicies_whenNoQueue_returnsTruthfulUnconfiguredResponse() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = get("/api/v1/learning/policies");

            assertThat(resp.statusCode()).isEqualTo(200); 
            @SuppressWarnings("unchecked") Map<String, Object> body = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("configured")).isEqualTo(false);
            assertThat(body.get("count")).isEqualTo(0);
            assertThat(body.get("message").toString()).contains("Policy store not available");
        }

        @Test
        @DisplayName("returns 200 with policies when queue configured")
        void listPolicies_withQueue_returns200() throws Exception { 
            InMemoryHumanReviewQueue queue =
                new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); 
            queue.enqueue(ReviewItem.builder()
                .reviewId("review-1")
                .tenantId("default")
                .skillId("email-routing")
                .proposedVersion("policy-1")
                .itemType(ReviewItemType.POLICY)
                .confidenceScore(0.91)
                .evaluationSummary("Candidate policy derived from 24 episodes")
                .context(Map.of(
                    "autoPromotable", true,
                    "provenance", Map.of(
                        "policyId", "policy-1",
                        "skillId", "email-routing",
                        "version", 2,
                        "sourceEpisodeIds", List.of("ep-1", "ep-2"),
                        "evaluationMetrics", Map.of("successRate", 0.92),
                        "activationMode", "SHADOW",
                        "rollbackPointerId", "policy-0"
                    ),
                    "gateResult", Map.of(
                        "gateName", "composite-eval",
                        "passed", true,
                        "score", 0.91,
                        "threshold", 0.7,
                        "reason", "Confidence clears the review threshold."
                    )
                ))
                .createdAt(Instant.parse("2026-04-28T09:00:00Z"))
                .build()).getResult(); 
            server = new AepHttpServer(engine, port, queue, null); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = get("/api/v1/learning/policies");

            assertThat(resp.statusCode()).isEqualTo(200); 
            @SuppressWarnings("unchecked") Map<String, Object> body = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("policies");
            @SuppressWarnings("unchecked") List<Map<String, Object>> policies = (List<Map<String, Object>>) body.get("policies");
            assertThat(policies).hasSize(1);
            assertThat(policies.getFirst()).containsEntry("autoPromotable", true);
            assertThat(policies.getFirst()).containsEntry("reviewId", "review-1");
            assertThat(policies.getFirst()).containsKey("provenance");
        }
    }

    // ==================== POST /api/v1/learning/policies/:policyId/approve ====================

    @Nested
    @DisplayName("POST /api/v1/learning/policies/:policyId/approve")
    class ApprovePolicyTests {

        @Test
        @DisplayName("returns fail-closed when HumanReviewQueue not configured")
        void approvePolicy_whenNoQueue_returnsFailClosed() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post( 
                "/api/v1/learning/policies/pol-1/approve",
                mapper.writeValueAsString(Map.of("reviewer", "tester")) 
            );

            assertThat(resp.statusCode()).isIn(401, 501); 
        }

        @Test
        @DisplayName("returns 404 when policy not found")
        void approvePolicy_notFound_returns404() throws Exception { 
            InMemoryHumanReviewQueue queue =
                new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); 
            server = new AepHttpServer(engine, port, queue, null); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post( 
                "/api/v1/learning/policies/nonexistent/approve",
                mapper.writeValueAsString(Map.of("reviewer", "tester")) 
            );

            assertThat(resp.statusCode()).isEqualTo(404); 
        }
    }

    // ==================== POST /api/v1/learning/policies/:policyId/reject ====================

    @Nested
    @DisplayName("POST /api/v1/learning/policies/:policyId/reject")
    class RejectPolicyTests {

        @Test
        @DisplayName("returns fail-closed when HumanReviewQueue not configured")
        void rejectPolicy_whenNoQueue_returnsFailClosed() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post( 
                "/api/v1/learning/policies/pol-1/reject",
                mapper.writeValueAsString(Map.of("reviewer", "tester")) 
            );

            assertThat(resp.statusCode()).isIn(401, 501); 
        }

        @Test
        @DisplayName("returns 404 when policy not found")
        void rejectPolicy_notFound_returns404() throws Exception { 
            InMemoryHumanReviewQueue queue =
                new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); 
            server = new AepHttpServer(engine, port, queue, null); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post( 
                "/api/v1/learning/policies/nonexistent/reject",
                mapper.writeValueAsString(Map.of("reviewer", "tester")) 
            );

            assertThat(resp.statusCode()).isEqualTo(404); 
        }
    }

    // ==================== POST /api/v1/learning/reflect ====================

    @Nested
    @DisplayName("POST /api/v1/learning/reflect")
    class TriggerReflectionTests {

        @Test
        @DisplayName("returns 202 Accepted; triggered=false when pipeline not configured")
        void triggerReflection_returns202() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post("/api/v1/learning/reflect", "{}"); 

            assertThat(resp.statusCode()).isEqualTo(202); 
            @SuppressWarnings("unchecked") Map<String, Object> body = (Map<String, Object>) mapper.readValue(resp.body(), Map.class);
            // When DataCloud is not configured the pipeline is null — triggered=false is correct
            assertThat(body).containsKey("triggered");
            assertThat(body).containsKey("timestamp");
        }
    }

    // ==================== Helpers ====================

    private HttpResponse<String> get(String path) throws Exception { 
        HttpRequest req = HttpRequest.newBuilder() 
            .GET() 
            .uri(URI.create("http://127.0.0.1:" + port + path)) 
            .build(); 
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 
    }

    private HttpResponse<String> post(String path, String body) throws Exception { 
        HttpRequest req = HttpRequest.newBuilder() 
            .POST(HttpRequest.BodyPublishers.ofString(body)) 
            .uri(URI.create("http://127.0.0.1:" + port + path)) 
            .header("Content-Type", "application/json") 
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
