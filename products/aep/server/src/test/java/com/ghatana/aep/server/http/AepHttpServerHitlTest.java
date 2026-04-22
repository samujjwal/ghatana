package com.ghatana.aep.server.http;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.agent.learning.review.InMemoryHumanReviewQueue;
import com.ghatana.agent.learning.review.ReviewItem;
import com.ghatana.agent.learning.review.ReviewItemType;
import com.ghatana.agent.learning.review.ReviewNotificationSpi;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Integration tests for AEP HTTP HITL (Human-In-The-Loop) endpoints (AEP-P7). // GH-90000
 *
 * <p>Starts a real {@link AepHttpServer} on a random port for each test and
 * makes HTTP requests via the Java standard HttpClient. Uses
 * {@link InMemoryHumanReviewQueue} for queue state; no external services required.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/hitl/** HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 * @doc.gaa.lifecycle act
 */
@DisplayName("AepHttpServer – HITL Endpoints [GH-90000]")
class AepHttpServerHitlTest {

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

    // ==================== GET /api/v1/hitl/pending ====================

    @Nested
    @DisplayName("GET /api/v1/hitl/pending [GH-90000]")
    class ListPendingTests {

        @Test
        @DisplayName("returns 200 truthful unconfigured response when humanReviewQueue is not configured [GH-90000]")
        void listPending_whenQueueNull_returnsTruthfulUnconfiguredResponse() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/hitl/pending [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("configured [GH-90000]")).isEqualTo(false);
            assertThat(body.get("count [GH-90000]")).isEqualTo(0);
            assertThat(body.get("message [GH-90000]").toString()).contains("HITL queue not configured [GH-90000]");
        }

        @Test
        @DisplayName("returns 200 with empty list when queue has no pending items [GH-90000]")
        void listPending_whenQueueEmpty_returns200WithEmptyList() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/hitl/pending [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("count [GH-90000]")).isEqualTo(0);
            assertThat((List<?>) body.get("pending [GH-90000]")).isEmpty();
            assertThat(body.get("timestamp [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("returns 200 with pending items and correct field mapping [GH-90000]")
        void listPending_withEnqueuedItem_returns200WithItemSummary() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            ReviewItem item = ReviewItem.builder() // GH-90000
                .reviewId("review-xyz [GH-90000]")
                .tenantId("tenant-a [GH-90000]")
                .skillId("agent-skill-1 [GH-90000]")
                .proposedVersion("v2.0 [GH-90000]")
                .itemType(ReviewItemType.POLICY) // GH-90000
                .confidenceScore(0.55) // GH-90000
                .build(); // GH-90000
            queue.enqueue(item); // synchronous — ConcurrentHashMap populated immediately // GH-90000

            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/hitl/pending?tenantId=tenant-a [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("count [GH-90000]")).isEqualTo(1);

            List<?> pending = (List<?>) body.get("pending [GH-90000]");
            assertThat(pending).hasSize(1); // GH-90000

            Map<?, ?> itemMap = (Map<?, ?>) pending.get(0); // GH-90000
            assertThat(itemMap.get("reviewId [GH-90000]")).isEqualTo("review-xyz [GH-90000]");
            assertThat(itemMap.get("agentId [GH-90000]")).isEqualTo("agent-skill-1 [GH-90000]");
            assertThat(itemMap.get("type [GH-90000]")).isEqualTo("POLICY [GH-90000]");
            assertThat(itemMap.get("status [GH-90000]")).isEqualTo("PENDING [GH-90000]");
            assertThat(itemMap.get("createdAt [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("returns only PENDING items (approved items excluded from list) [GH-90000]")
        void listPending_afterApproval_excludesApprovedItems() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            ReviewItem pending = ReviewItem.builder() // GH-90000
                .reviewId("review-pending [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .skillId("skill-a [GH-90000]")
                .proposedVersion("v1 [GH-90000]")
                .itemType(ReviewItemType.POLICY) // GH-90000
                .build(); // GH-90000
            ReviewItem toApprove = ReviewItem.builder() // GH-90000
                .reviewId("review-approved [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .skillId("skill-b [GH-90000]")
                .proposedVersion("v1 [GH-90000]")
                .itemType(ReviewItemType.POLICY) // GH-90000
                .build(); // GH-90000
            queue.enqueue(pending); // synchronous — ConcurrentHashMap populated immediately // GH-90000
            queue.enqueue(toApprove); // GH-90000

            // Approve the second item via the queue directly (synchronous) // GH-90000
            com.ghatana.agent.learning.review.ReviewDecision decision =
                new com.ghatana.agent.learning.review.ReviewDecision( // GH-90000
                    "tester", "approved-in-test", java.time.Instant.now(), null); // GH-90000
            queue.approve("review-approved", decision); // GH-90000

            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/hitl/pending?tenantId=t1 [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            // Only the PENDING item should appear in the list
            assertThat(body.get("count [GH-90000]")).isEqualTo(1);
            List<?> items = (List<?>) body.get("pending [GH-90000]");
            assertThat(items).hasSize(1); // GH-90000
            Map<?, ?> itemMap = (Map<?, ?>) items.get(0); // GH-90000
            assertThat(itemMap.get("reviewId [GH-90000]")).isEqualTo("review-pending [GH-90000]");
        }
    }

    // ==================== POST /api/v1/hitl/:reviewId/approve ====================

    @Nested
    @DisplayName("POST /api/v1/hitl/:reviewId/approve [GH-90000]")
    class ApproveTests {

        @Test
        @DisplayName("returns 501 when humanReviewQueue is not configured [GH-90000]")
        void approve_whenQueueNull_returns501() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/hitl/review-42/approve",
                mapper.writeValueAsString(Map.of("reviewer", "alice"))); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(501); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message [GH-90000]").toString()).contains("HITL queue not configured [GH-90000]");
        }

        @Test
        @DisplayName("returns 200 with APPROVED status on successful approve [GH-90000]")
        void approve_withValidReviewId_returns200WithApprovedStatus() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            ReviewItem item = ReviewItem.builder() // GH-90000
                .reviewId("review-42 [GH-90000]")
                .tenantId("tenant-1 [GH-90000]")
                .skillId("skill-x [GH-90000]")
                .proposedVersion("v1 [GH-90000]")
                .itemType(ReviewItemType.POLICY) // GH-90000
                .build(); // GH-90000
            queue.enqueue(item); // synchronous — ConcurrentHashMap populated immediately // GH-90000

            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "reviewer", "alice",
                "rationale", "Looks correct",
                "notes", "Approved after review"
            ));
            HttpResponse<String> resp = post("/api/v1/hitl/review-42/approve", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody.get("reviewId [GH-90000]")).isEqualTo("review-42 [GH-90000]");
            assertThat(respBody.get("status [GH-90000]")).isEqualTo("APPROVED [GH-90000]");
            assertThat(respBody.get("decidedAt [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("returns 200 with defaults when body is empty JSON object [GH-90000]")
        void approve_withEmptyBody_returns200WithDefaults() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            ReviewItem item = ReviewItem.builder() // GH-90000
                .reviewId("review-empty [GH-90000]")
                .tenantId("tenant-1 [GH-90000]")
                .skillId("skill-y [GH-90000]")
                .proposedVersion("v1 [GH-90000]")
                .itemType(ReviewItemType.POLICY) // GH-90000
                .build(); // GH-90000
            queue.enqueue(item); // synchronous — ConcurrentHashMap populated immediately // GH-90000

            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/hitl/review-empty/approve", "{}"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody.get("status [GH-90000]")).isEqualTo("APPROVED [GH-90000]");
        }

        @Test
        @DisplayName("returns 404 when reviewId is not in the queue [GH-90000]")
        void approve_whenReviewIdNotFound_returns404() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000

            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/hitl/does-not-exist/approve",
                mapper.writeValueAsString(Map.of("reviewer", "bob"))); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
            Map<?, ?> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody.get("message [GH-90000]").toString()).contains("not found [GH-90000]");
        }

        @Test
        @DisplayName("returns 400 on malformed JSON body [GH-90000]")
        void approve_withMalformedJson_returns400() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            ReviewItem item = ReviewItem.builder() // GH-90000
                .reviewId("review-bad-json [GH-90000]")
                .tenantId("tenant-1 [GH-90000]")
                .skillId("skill-z [GH-90000]")
                .proposedVersion("v1 [GH-90000]")
                .itemType(ReviewItemType.POLICY) // GH-90000
                .build(); // GH-90000
            queue.enqueue(item); // synchronous — ConcurrentHashMap populated immediately // GH-90000

            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/hitl/review-bad-json/approve", "{not valid json"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }

    // ==================== POST /api/v1/hitl/:reviewId/reject ====================

    @Nested
    @DisplayName("POST /api/v1/hitl/:reviewId/reject [GH-90000]")
    class RejectTests {

        @Test
        @DisplayName("returns 501 when humanReviewQueue is not configured [GH-90000]")
        void reject_whenQueueNull_returns501() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/hitl/review-99/reject",
                mapper.writeValueAsString(Map.of("reviewer", "bob"))); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(501); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message [GH-90000]").toString()).contains("HITL queue not configured [GH-90000]");
        }

        @Test
        @DisplayName("returns 200 with REJECTED status on successful reject [GH-90000]")
        void reject_withValidReviewId_returns200WithRejectedStatus() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            ReviewItem item = ReviewItem.builder() // GH-90000
                .reviewId("review-rej-1 [GH-90000]")
                .tenantId("tenant-2 [GH-90000]")
                .skillId("skill-r [GH-90000]")
                .proposedVersion("v1 [GH-90000]")
                .itemType(ReviewItemType.POLICY) // GH-90000
                .build(); // GH-90000
            queue.enqueue(item); // synchronous — ConcurrentHashMap populated immediately // GH-90000

            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "reviewer", "bob",
                "rationale", "Confidence too low",
                "notes", "Needs more training data"
            ));
            HttpResponse<String> resp = post("/api/v1/hitl/review-rej-1/reject", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody.get("reviewId [GH-90000]")).isEqualTo("review-rej-1 [GH-90000]");
            assertThat(respBody.get("status [GH-90000]")).isEqualTo("REJECTED [GH-90000]");
            assertThat(respBody.get("decidedAt [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("returns 200 with defaults when body is empty JSON object [GH-90000]")
        void reject_withEmptyBody_returns200WithDefaults() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            ReviewItem item = ReviewItem.builder() // GH-90000
                .reviewId("review-rej-empty [GH-90000]")
                .tenantId("tenant-2 [GH-90000]")
                .skillId("skill-s [GH-90000]")
                .proposedVersion("v1 [GH-90000]")
                .itemType(ReviewItemType.POLICY) // GH-90000
                .build(); // GH-90000
            queue.enqueue(item); // synchronous — ConcurrentHashMap populated immediately // GH-90000

            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/hitl/review-rej-empty/reject", "{}"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody.get("status [GH-90000]")).isEqualTo("REJECTED [GH-90000]");
        }

        @Test
        @DisplayName("returns 404 when reviewId is not in the queue [GH-90000]")
        void reject_whenReviewIdNotFound_returns404() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000

            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/hitl/no-such-review/reject",
                mapper.writeValueAsString(Map.of("reviewer", "carol"))); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
            Map<?, ?> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody.get("message [GH-90000]").toString()).contains("not found [GH-90000]");
        }

        @Test
        @DisplayName("returns 400 on malformed JSON body [GH-90000]")
        void reject_withMalformedJson_returns400() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            ReviewItem item = ReviewItem.builder() // GH-90000
                .reviewId("review-rej-bad [GH-90000]")
                .tenantId("tenant-2 [GH-90000]")
                .skillId("skill-t [GH-90000]")
                .proposedVersion("v1 [GH-90000]")
                .itemType(ReviewItemType.POLICY) // GH-90000
                .build(); // GH-90000
            queue.enqueue(item); // synchronous — ConcurrentHashMap populated immediately // GH-90000

            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/hitl/review-rej-bad/reject", "[invalid"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
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
