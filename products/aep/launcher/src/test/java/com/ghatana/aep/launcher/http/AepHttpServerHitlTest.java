package com.ghatana.aep.launcher.http;

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
 * Integration tests for AEP HTTP HITL (Human-In-The-Loop) endpoints (AEP-P7).
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
@DisplayName("AepHttpServer – HITL Endpoints")
class AepHttpServerHitlTest {

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

    // ==================== GET /api/v1/hitl/pending ====================

    @Nested
    @DisplayName("GET /api/v1/hitl/pending")
    class ListPendingTests {

        @Test
        @DisplayName("returns 501 when humanReviewQueue is not configured")
        void listPending_whenQueueNull_returns501() throws Exception {
            server = new AepHttpServer(engine, port);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/api/v1/hitl/pending");

            assertThat(resp.statusCode()).isEqualTo(501);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("error").toString()).contains("HITL queue not configured");
        }

        @Test
        @DisplayName("returns 200 with empty list when queue has no pending items")
        void listPending_whenQueueEmpty_returns200WithEmptyList() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/api/v1/hitl/pending");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("count")).isEqualTo(0);
            assertThat((List<?>) body.get("pending")).isEmpty();
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("returns 200 with pending items and correct field mapping")
        void listPending_withEnqueuedItem_returns200WithItemSummary() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            ReviewItem item = ReviewItem.builder()
                .reviewId("review-xyz")
                .tenantId("tenant-a")
                .skillId("agent-skill-1")
                .proposedVersion("v2.0")
                .itemType(ReviewItemType.POLICY)
                .confidenceScore(0.55)
                .build();
            queue.enqueue(item); // synchronous — ConcurrentHashMap populated immediately

            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/api/v1/hitl/pending?tenantId=tenant-a");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("count")).isEqualTo(1);

            List<?> pending = (List<?>) body.get("pending");
            assertThat(pending).hasSize(1);

            Map<?, ?> itemMap = (Map<?, ?>) pending.get(0);
            assertThat(itemMap.get("reviewId")).isEqualTo("review-xyz");
            assertThat(itemMap.get("agentId")).isEqualTo("agent-skill-1");
            assertThat(itemMap.get("type")).isEqualTo("POLICY");
            assertThat(itemMap.get("status")).isEqualTo("PENDING");
            assertThat(itemMap.get("createdAt")).isNotNull();
        }

        @Test
        @DisplayName("returns only PENDING items (approved items excluded from list)")
        void listPending_afterApproval_excludesApprovedItems() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            ReviewItem pending = ReviewItem.builder()
                .reviewId("review-pending")
                .tenantId("t1")
                .skillId("skill-a")
                .proposedVersion("v1")
                .itemType(ReviewItemType.POLICY)
                .build();
            ReviewItem toApprove = ReviewItem.builder()
                .reviewId("review-approved")
                .tenantId("t1")
                .skillId("skill-b")
                .proposedVersion("v1")
                .itemType(ReviewItemType.POLICY)
                .build();
            queue.enqueue(pending); // synchronous — ConcurrentHashMap populated immediately
            queue.enqueue(toApprove);

            // Approve the second item via the queue directly (synchronous)
            com.ghatana.agent.learning.review.ReviewDecision decision =
                new com.ghatana.agent.learning.review.ReviewDecision(
                    "tester", "approved-in-test", java.time.Instant.now(), null);
            queue.approve("review-approved", decision);

            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/api/v1/hitl/pending?tenantId=t1");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            // Only the PENDING item should appear in the list
            assertThat(body.get("count")).isEqualTo(1);
            List<?> items = (List<?>) body.get("pending");
            assertThat(items).hasSize(1);
            Map<?, ?> itemMap = (Map<?, ?>) items.get(0);
            assertThat(itemMap.get("reviewId")).isEqualTo("review-pending");
        }
    }

    // ==================== POST /api/v1/hitl/:reviewId/approve ====================

    @Nested
    @DisplayName("POST /api/v1/hitl/:reviewId/approve")
    class ApproveTests {

        @Test
        @DisplayName("returns 501 when humanReviewQueue is not configured")
        void approve_whenQueueNull_returns501() throws Exception {
            server = new AepHttpServer(engine, port);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post(
                "/api/v1/hitl/review-42/approve",
                mapper.writeValueAsString(Map.of("reviewer", "alice")));

            assertThat(resp.statusCode()).isEqualTo(501);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("error").toString()).contains("HITL queue not configured");
        }

        @Test
        @DisplayName("returns 200 with APPROVED status on successful approve")
        void approve_withValidReviewId_returns200WithApprovedStatus() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            ReviewItem item = ReviewItem.builder()
                .reviewId("review-42")
                .tenantId("tenant-1")
                .skillId("skill-x")
                .proposedVersion("v1")
                .itemType(ReviewItemType.POLICY)
                .build();
            queue.enqueue(item); // synchronous — ConcurrentHashMap populated immediately

            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "reviewer", "alice",
                "rationale", "Looks correct",
                "notes", "Approved after review"
            ));
            HttpResponse<String> resp = post("/api/v1/hitl/review-42/approve", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody.get("reviewId")).isEqualTo("review-42");
            assertThat(respBody.get("status")).isEqualTo("APPROVED");
            assertThat(respBody.get("decidedAt")).isNotNull();
        }

        @Test
        @DisplayName("returns 200 with defaults when body is empty JSON object")
        void approve_withEmptyBody_returns200WithDefaults() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            ReviewItem item = ReviewItem.builder()
                .reviewId("review-empty")
                .tenantId("tenant-1")
                .skillId("skill-y")
                .proposedVersion("v1")
                .itemType(ReviewItemType.POLICY)
                .build();
            queue.enqueue(item); // synchronous — ConcurrentHashMap populated immediately

            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post("/api/v1/hitl/review-empty/approve", "{}");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody.get("status")).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("returns 404 when reviewId is not in the queue")
        void approve_whenReviewIdNotFound_returns404() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);

            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post(
                "/api/v1/hitl/does-not-exist/approve",
                mapper.writeValueAsString(Map.of("reviewer", "bob")));

            assertThat(resp.statusCode()).isEqualTo(404);
            Map<?, ?> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody.get("error").toString()).contains("not found");
        }

        @Test
        @DisplayName("returns 400 on malformed JSON body")
        void approve_withMalformedJson_returns400() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            ReviewItem item = ReviewItem.builder()
                .reviewId("review-bad-json")
                .tenantId("tenant-1")
                .skillId("skill-z")
                .proposedVersion("v1")
                .itemType(ReviewItemType.POLICY)
                .build();
            queue.enqueue(item); // synchronous — ConcurrentHashMap populated immediately

            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post("/api/v1/hitl/review-bad-json/approve", "{not valid json");

            assertThat(resp.statusCode()).isEqualTo(400);
        }
    }

    // ==================== POST /api/v1/hitl/:reviewId/reject ====================

    @Nested
    @DisplayName("POST /api/v1/hitl/:reviewId/reject")
    class RejectTests {

        @Test
        @DisplayName("returns 501 when humanReviewQueue is not configured")
        void reject_whenQueueNull_returns501() throws Exception {
            server = new AepHttpServer(engine, port);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post(
                "/api/v1/hitl/review-99/reject",
                mapper.writeValueAsString(Map.of("reviewer", "bob")));

            assertThat(resp.statusCode()).isEqualTo(501);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("error").toString()).contains("HITL queue not configured");
        }

        @Test
        @DisplayName("returns 200 with REJECTED status on successful reject")
        void reject_withValidReviewId_returns200WithRejectedStatus() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            ReviewItem item = ReviewItem.builder()
                .reviewId("review-rej-1")
                .tenantId("tenant-2")
                .skillId("skill-r")
                .proposedVersion("v1")
                .itemType(ReviewItemType.POLICY)
                .build();
            queue.enqueue(item); // synchronous — ConcurrentHashMap populated immediately

            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "reviewer", "bob",
                "rationale", "Confidence too low",
                "notes", "Needs more training data"
            ));
            HttpResponse<String> resp = post("/api/v1/hitl/review-rej-1/reject", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody.get("reviewId")).isEqualTo("review-rej-1");
            assertThat(respBody.get("status")).isEqualTo("REJECTED");
            assertThat(respBody.get("decidedAt")).isNotNull();
        }

        @Test
        @DisplayName("returns 200 with defaults when body is empty JSON object")
        void reject_withEmptyBody_returns200WithDefaults() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            ReviewItem item = ReviewItem.builder()
                .reviewId("review-rej-empty")
                .tenantId("tenant-2")
                .skillId("skill-s")
                .proposedVersion("v1")
                .itemType(ReviewItemType.POLICY)
                .build();
            queue.enqueue(item); // synchronous — ConcurrentHashMap populated immediately

            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post("/api/v1/hitl/review-rej-empty/reject", "{}");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody.get("status")).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("returns 404 when reviewId is not in the queue")
        void reject_whenReviewIdNotFound_returns404() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);

            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post(
                "/api/v1/hitl/no-such-review/reject",
                mapper.writeValueAsString(Map.of("reviewer", "carol")));

            assertThat(resp.statusCode()).isEqualTo(404);
            Map<?, ?> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody.get("error").toString()).contains("not found");
        }

        @Test
        @DisplayName("returns 400 on malformed JSON body")
        void reject_withMalformedJson_returns400() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            ReviewItem item = ReviewItem.builder()
                .reviewId("review-rej-bad")
                .tenantId("tenant-2")
                .skillId("skill-t")
                .proposedVersion("v1")
                .itemType(ReviewItemType.POLICY)
                .build();
            queue.enqueue(item); // synchronous — ConcurrentHashMap populated immediately

            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post("/api/v1/hitl/review-rej-bad/reject", "[invalid");

            assertThat(resp.statusCode()).isEqualTo(400);
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
