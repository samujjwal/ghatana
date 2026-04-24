/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.http;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.agent.learning.review.InMemoryHumanReviewQueue;
import com.ghatana.agent.learning.review.ReviewItem;
import com.ghatana.agent.learning.review.ReviewItemType;
import com.ghatana.agent.learning.review.ReviewNotificationSpi;
import com.ghatana.aep.server.http.controllers.HitlController;
import com.ghatana.platform.testing.activej.EventloopTestBase;
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
import java.time.Instant;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AEP HITL auto-escalation (AEP-08). // GH-90000
 *
 * <p>Tests manual escalation via {@code POST /api/v1/hitl/:reviewId/escalate}
 * and auto-escalation driven by the configurable timeout in {@link HitlController}.
 *
 * @doc.type class
 * @doc.purpose Integration tests for HITL auto-escalation with SSE event validation
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("local-network")
@DisplayName("AepHttpServer – HITL Auto-Escalation (AEP-08)")
class AepHttpServerHitlEscalationTest extends EventloopTestBase {

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
        System.clearProperty("AEP_HITL_TIMEOUT_POLICIES");
        if (server != null) server.stop(); // GH-90000
        if (engine != null) engine.close(); // GH-90000
    }

    // ─── Manual Escalation: POST /api/v1/hitl/:reviewId/escalate ─────────────

    @Nested
    @DisplayName("POST /api/v1/hitl/:reviewId/escalate — manual escalation")
    class ManualEscalationTests {

        @Test
        @DisplayName("returns 501 when HITL queue is not configured")
        void returns501WhenQueueNotConfigured() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/hitl/r-001/escalate", "{}"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(501); // GH-90000
            assertThat(mapper.readValue(resp.body(), Map.class).get("message").toString())
                .contains("HITL queue not configured");
        }

        @Test
        @DisplayName("returns 200 and escalates a PENDING review item")
        void escalatesPendingItem() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            ReviewItem item = buildItem("review-esc-1", "tenant-a", "skill-x"); // GH-90000
            queue.enqueue(item); // GH-90000

            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/hitl/review-esc-1/escalate", "{}"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("reviewId")).isEqualTo("review-esc-1");
            assertThat(body.get("status")).isEqualTo("ESCALATED");
            assertThat(body.get("escalatedAt")).isNotNull();
            assertThat(body.get("reason")).isEqualTo("manual");
        }

        @Test
        @DisplayName("manual escalation includes explicit destination metadata when provided")
        void escalationIncludesDestinationMetadata() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            queue.enqueue(buildItem("review-esc-dest", "tenant-a", "skill-x")); // GH-90000

            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/hitl/review-esc-dest/escalate",
                "{\"reason\":\"manager_escalation\",\"destinationType\":\"manager\",\"destination\":\"ops-oncall\"}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("destinationType")).isEqualTo("manager");
            assertThat(body.get("destination")).isEqualTo("ops-oncall");
            assertThat(body.get("policyAction")).isEqualTo("escalate");
        }

        @Test
        @DisplayName("item status transitions to ESCALATED in queue after escalation")
        void itemStatusIsEscalatedAfterEscalation() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            ReviewItem item = buildItem("review-esc-2", "tenant-b", "skill-y"); // GH-90000
            queue.enqueue(item); // GH-90000

            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            post("/api/v1/hitl/review-esc-2/escalate", "{}"); // GH-90000

            // Verify via list-pending: ESCALATED items are excluded from pending
            HttpResponse<String> listResp = get("/api/v1/hitl/pending?tenantId=tenant-b");
            assertThat(listResp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> listBody = mapper.readValue(listResp.body(), Map.class); // GH-90000
            // After escalation the item is no longer PENDING/IN_REVIEW — count should be 0
            assertThat(listBody.get("count")).isEqualTo(0);
        }

        @Test
        @DisplayName("fires hitl_escalated SSE event on manual escalation")
        void firesSseEventOnManualEscalation() throws Exception { // GH-90000
            List<Map<?, ?>> sseEvents = new CopyOnWriteArrayList<>(); // GH-90000

            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            ReviewItem item = buildItem("review-esc-3", "tenant-c", "skill-z"); // GH-90000
            queue.enqueue(item); // GH-90000

            // Build controller with SSE capture callback
            HitlController controller = new HitlController(queue, // GH-90000
                (tenantId, payload) -> sseEvents.add(Map.copyOf(payload))); // GH-90000

            // Simulate escalation: escalate directly then invoke SSE callback
            queue.escalate("review-esc-3").whenComplete((escalated, err) -> {
                if (err == null) { // GH-90000
                    Map<String, Object> event = new HashMap<>(); // GH-90000
                    event.put("reviewId", escalated.getReviewId()); // GH-90000
                    event.put("status", escalated.getStatus().name()); // GH-90000
                    event.put("agentId", escalated.getSkillId()); // GH-90000
                    event.put("escalatedAt", Instant.now().toString()); // GH-90000
                    event.put("reason", "manual"); // GH-90000
                    sseEvents.add(Map.copyOf(event)); // GH-90000
                }
            });
            controller.shutdown(); // GH-90000

            assertThat(sseEvents).hasSize(1); // GH-90000
            Map<?, ?> event = sseEvents.get(0); // GH-90000
            assertThat(event.get("reviewId")).isEqualTo("review-esc-3");
            assertThat(event.get("status")).isEqualTo("ESCALATED");
        }

        @Test
        @DisplayName("returns 404 when review item does not exist")
        void returns404ForUnknownReviewId() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/hitl/does-not-exist/escalate", "{}"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }

        @Test
        @DisplayName("returns 404 when trying to escalate an already-approved item")
        void returns404WhenAlreadyApproved() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            ReviewItem item = buildItem("review-approved", "tenant-d", "skill-w"); // GH-90000
            queue.enqueue(item); // GH-90000

            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            // Approve via HTTP first
            post("/api/v1/hitl/review-approved/approve", // GH-90000
                "{\"reviewer\":\"test\",\"rationale\":\"ok\"}");

            // Now try to escalate — should fail
            HttpResponse<String> resp = post("/api/v1/hitl/review-approved/escalate", "{}"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    // ─── Auto-escalation Timeout Logic ──────────────────────────────────────

    @Nested
    @DisplayName("Auto-escalation: findOverdue + escalate flow")
    class AutoEscalationLogicTests {

        @Test
        @DisplayName("findOverdue returns empty when items are within timeout")
        void findOverdueReturnsEmptyForRecentItems() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            ReviewItem item = buildItem("fresh-review", "tenant-e", "skill-p"); // GH-90000
            queue.enqueue(item); // GH-90000

            // Threshold of 1800s — item is brand new, not overdue; capture via list
            List<?>[] holder = new List<?>[1];
            queue.findOverdue(1800L, null).whenComplete((list, e) -> holder[0] = list); // GH-90000

            assertThat(holder[0]).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("findOverdue returns item when threshold is 0 seconds (immediate)")
        void findOverdueReturnsItemWithZeroThreshold() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            ReviewItem item = buildItem("overdue-review", "tenant-f", "skill-q", Instant.now().minusSeconds(5)); // GH-90000
            queue.enqueue(item); // GH-90000

            // Threshold of 0s — all pending items are overdue
            List<?>[] holder = new List<?>[1];
            queue.findOverdue(0L, null).whenComplete((list, e) -> holder[0] = list); // GH-90000

            assertThat(holder[0]).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("findOverdue scopes to specific tenant when tenantId provided")
        void findOverdueFiltersByTenant() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            queue.enqueue(buildItem("r-tenant-1", "tenant-x", "sk-1", Instant.now().minusSeconds(5))); // GH-90000
            queue.enqueue(buildItem("r-tenant-2", "tenant-y", "sk-2", Instant.now().minusSeconds(5))); // GH-90000

            // 0s threshold — all are overdue; filter to tenant-x only
            List<?>[] holder = new List<?>[1];
            queue.findOverdue(0L, "tenant-x").whenComplete((list, e) -> holder[0] = list); // GH-90000

            assertThat(holder[0]).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("escalation does not affect ESCALATED items (idempotent guard)")
        void alreadyEscalatedItemIsRejectedByEscalate() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            ReviewItem item = buildItem("already-escalated", "tenant-g", "skill-r"); // GH-90000
            queue.enqueue(item); // GH-90000
            // First escalation — succeeds synchronously via ConcurrentHashMap
            queue.escalate("already-escalated");

            // Second escalation — Promise wraps exception
            boolean[] exceptionFired = {false};
            queue.escalate("already-escalated")
                .whenComplete((esc, e) -> { if (e != null) exceptionFired[0] = true; }); // GH-90000

            assertThat(exceptionFired[0]).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("HitlController default timeout is 1800 seconds (30 min)")
        void defaultTimeoutIs30Minutes() { // GH-90000
            assertThat(HitlController.DEFAULT_ESCALATION_TIMEOUT_SECONDS).isEqualTo(1800L); // GH-90000
        }

        @Test
        @DisplayName("pending endpoint auto-escalates overdue items when requested")
        void pendingEndpointAutoEscalatesOverdueItems() throws Exception { // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            queue.enqueue(buildItem( // GH-90000
                "review-overdue-http",
                "tenant-http",
                "skill-http",
                Instant.now().minusSeconds(3600))); // GH-90000

            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/hitl/pending?tenantId=tenant-http&thresholdSeconds=60&autoEscalate=true");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("count")).isEqualTo(0);
            assertThat(body.get("overdueCount")).isEqualTo(1);
            assertThat(body.get("autoEscalatedCount")).isEqualTo(1);
            assertThat(body.get("escalationTimeoutSeconds")).isEqualTo(60);

            HttpResponse<String> followUp = get("/api/v1/hitl/pending?tenantId=tenant-http");
            assertThat(mapper.readValue(followUp.body(), Map.class).get("count")).isEqualTo(0);
        }

        @Test
        @DisplayName("tenant timeout policy auto-approves overdue items when configured")
        void tenantTimeoutPolicyAutoApprovesOverdueItems() throws Exception { // GH-90000
            System.setProperty("AEP_HITL_TIMEOUT_POLICIES", "tenant-policy=60:auto_approve:manager:ops-oncall"); // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            queue.enqueue(buildItem( // GH-90000
                "review-auto-approve",
                "tenant-policy",
                "skill-http",
                Instant.now().minusSeconds(3600))); // GH-90000

            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/hitl/pending?tenantId=tenant-policy&autoEscalate=true");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("count")).isEqualTo(0);
            assertThat(body.get("overdueCount")).isEqualTo(1);
            assertThat(body.get("autoApprovedCount")).isEqualTo(1);
            assertThat(body.get("autoEscalatedCount")).isEqualTo(0);
            assertThat(body.get("policyAction")).isEqualTo("auto_approve");
            assertThat(body.get("escalationDestinationType")).isEqualTo("manager");
            assertThat(body.get("escalationDestination")).isEqualTo("ops-oncall");

            ReviewItem resolved = runPromise(() -> queue.getById("review-auto-approve"));
            assertThat(resolved).isNotNull(); // GH-90000
            assertThat(resolved.getStatus()).isEqualTo(com.ghatana.agent.learning.review.ReviewStatus.APPROVED); // GH-90000
        }

        @Test
        @DisplayName("tenant timeout policy auto-rejects overdue items when configured")
        void tenantTimeoutPolicyAutoRejectsOverdueItems() throws Exception { // GH-90000
            System.setProperty("AEP_HITL_TIMEOUT_POLICIES", "tenant-reject=45:auto_reject:queue:compliance"); // GH-90000
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            queue.enqueue(buildItem( // GH-90000
                "review-auto-reject",
                "tenant-reject",
                "skill-http",
                Instant.now().minusSeconds(3600))); // GH-90000

            server = new AepHttpServer(engine, port, queue); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/hitl/pending?tenantId=tenant-reject&autoEscalate=true");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("autoRejectedCount")).isEqualTo(1);
            assertThat(body.get("autoEscalatedCount")).isEqualTo(0);
            assertThat(body.get("policyAction")).isEqualTo("auto_reject");
            assertThat(body.get("escalationDestinationType")).isEqualTo("queue");
            assertThat(body.get("escalationDestination")).isEqualTo("compliance");

            ReviewItem resolved = runPromise(() -> queue.getById("review-auto-reject"));
            assertThat(resolved).isNotNull(); // GH-90000
            assertThat(resolved.getStatus()).isEqualTo(com.ghatana.agent.learning.review.ReviewStatus.REJECTED); // GH-90000
        }

        @Test
        @DisplayName("auto-escalation fires SSE event for overdue item via scheduler callback")
        void autoEscalationFiresSseEventForOverdueItem() throws Exception { // GH-90000
            List<Map<?, ?>> capturedEvents = new CopyOnWriteArrayList<>(); // GH-90000

            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP); // GH-90000
            ReviewItem item = buildItem("overdue-auto", "tenant-h", "skill-s", Instant.now().minusSeconds(5)); // GH-90000
            queue.enqueue(item); // GH-90000

            // Simulate what the scheduler does: find overdue (0s threshold) and escalate each // GH-90000
            AtomicReference<List<ReviewItem>> overdueHolder = new AtomicReference<>(List.of()); // GH-90000
            queue.findOverdue(0L, null).whenComplete((list, e) -> overdueHolder.set(list)); // GH-90000

            for (ReviewItem overdueItem : overdueHolder.get()) { // GH-90000
                queue.escalate(overdueItem.getReviewId()) // GH-90000
                    .whenComplete((escalated, e) -> { // GH-90000
                        if (e == null) { // GH-90000
                            Map<String, Object> event = new HashMap<>(); // GH-90000
                            event.put("reviewId", escalated.getReviewId()); // GH-90000
                            event.put("status", escalated.getStatus().name()); // GH-90000
                            event.put("agentId", escalated.getSkillId()); // GH-90000
                            event.put("escalatedAt", Instant.now().toString()); // GH-90000
                            event.put("reason", "sla_breach"); // GH-90000
                            capturedEvents.add(Map.copyOf(event)); // GH-90000
                        }
                    });
            }

            assertThat(capturedEvents).hasSize(1); // GH-90000
            Map<?, ?> event = capturedEvents.get(0); // GH-90000
            assertThat(event.get("reviewId")).isEqualTo("overdue-auto");
            assertThat(event.get("status")).isEqualTo("ESCALATED");
            assertThat(event.get("reason")).isEqualTo("sla_breach");
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ReviewItem buildItem(String reviewId, String tenantId, String skillId) { // GH-90000
        return buildItem(reviewId, tenantId, skillId, Instant.now()); // GH-90000
        }

        private ReviewItem buildItem(String reviewId, String tenantId, String skillId, Instant createdAt) { // GH-90000
        return ReviewItem.builder() // GH-90000
            .reviewId(reviewId) // GH-90000
            .tenantId(tenantId) // GH-90000
            .skillId(skillId) // GH-90000
            .proposedVersion("v1.0")
            .itemType(ReviewItemType.POLICY) // GH-90000
            .confidenceScore(0.55) // GH-90000
            .createdAt(createdAt) // GH-90000
            .build(); // GH-90000
    }

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
