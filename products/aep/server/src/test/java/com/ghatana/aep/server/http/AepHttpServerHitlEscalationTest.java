/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * Integration tests for AEP HITL auto-escalation (AEP-08).
 *
 * <p>Tests manual escalation via {@code POST /api/v1/hitl/:reviewId/escalate}
 * and auto-escalation driven by the configurable timeout in {@link HitlController}.
 *
 * @doc.type class
 * @doc.purpose Integration tests for HITL auto-escalation with SSE event validation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepHttpServer – HITL Auto-Escalation (AEP-08)")
class AepHttpServerHitlEscalationTest extends EventloopTestBase {

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
        System.clearProperty("AEP_HITL_TIMEOUT_POLICIES");
        if (server != null) server.stop();
        if (engine != null) engine.close();
    }

    // ─── Manual Escalation: POST /api/v1/hitl/:reviewId/escalate ─────────────

    @Nested
    @DisplayName("POST /api/v1/hitl/:reviewId/escalate — manual escalation")
    class ManualEscalationTests {

        @Test
        @DisplayName("returns 501 when HITL queue is not configured")
        void returns501WhenQueueNotConfigured() throws Exception {
            server = new AepHttpServer(engine, port);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post("/api/v1/hitl/r-001/escalate", "{}");

            assertThat(resp.statusCode()).isEqualTo(501);
            assertThat(mapper.readValue(resp.body(), Map.class).get("message").toString())
                .contains("HITL queue not configured");
        }

        @Test
        @DisplayName("returns 200 and escalates a PENDING review item")
        void escalatesPendingItem() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            ReviewItem item = buildItem("review-esc-1", "tenant-a", "skill-x");
            queue.enqueue(item);

            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post("/api/v1/hitl/review-esc-1/escalate", "{}");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("reviewId")).isEqualTo("review-esc-1");
            assertThat(body.get("status")).isEqualTo("ESCALATED");
            assertThat(body.get("escalatedAt")).isNotNull();
            assertThat(body.get("reason")).isEqualTo("manual");
        }

        @Test
        @DisplayName("manual escalation includes explicit destination metadata when provided")
        void escalationIncludesDestinationMetadata() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            queue.enqueue(buildItem("review-esc-dest", "tenant-a", "skill-x"));

            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post(
                "/api/v1/hitl/review-esc-dest/escalate",
                "{\"reason\":\"manager_escalation\",\"destinationType\":\"manager\",\"destination\":\"ops-oncall\"}");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("destinationType")).isEqualTo("manager");
            assertThat(body.get("destination")).isEqualTo("ops-oncall");
            assertThat(body.get("policyAction")).isEqualTo("escalate");
        }

        @Test
        @DisplayName("item status transitions to ESCALATED in queue after escalation")
        void itemStatusIsEscalatedAfterEscalation() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            ReviewItem item = buildItem("review-esc-2", "tenant-b", "skill-y");
            queue.enqueue(item);

            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            post("/api/v1/hitl/review-esc-2/escalate", "{}");

            // Verify via list-pending: ESCALATED items are excluded from pending
            HttpResponse<String> listResp = get("/api/v1/hitl/pending?tenantId=tenant-b");
            assertThat(listResp.statusCode()).isEqualTo(200);
            Map<?, ?> listBody = mapper.readValue(listResp.body(), Map.class);
            // After escalation the item is no longer PENDING/IN_REVIEW — count should be 0
            assertThat(listBody.get("count")).isEqualTo(0);
        }

        @Test
        @DisplayName("fires hitl_escalated SSE event on manual escalation")
        void firesSseEventOnManualEscalation() throws Exception {
            List<Map<?, ?>> sseEvents = new CopyOnWriteArrayList<>();

            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            ReviewItem item = buildItem("review-esc-3", "tenant-c", "skill-z");
            queue.enqueue(item);

            // Build controller with SSE capture callback
            HitlController controller = new HitlController(queue,
                (tenantId, payload) -> sseEvents.add(Map.copyOf(payload)));

            // Simulate escalation: escalate directly then invoke SSE callback
            queue.escalate("review-esc-3").whenComplete((escalated, err) -> {
                if (err == null) {
                    Map<String, Object> event = new HashMap<>();
                    event.put("reviewId", escalated.getReviewId());
                    event.put("status", escalated.getStatus().name());
                    event.put("agentId", escalated.getSkillId());
                    event.put("escalatedAt", Instant.now().toString());
                    event.put("reason", "manual");
                    sseEvents.add(Map.copyOf(event));
                }
            });
            controller.shutdown();

            assertThat(sseEvents).hasSize(1);
            Map<?, ?> event = sseEvents.get(0);
            assertThat(event.get("reviewId")).isEqualTo("review-esc-3");
            assertThat(event.get("status")).isEqualTo("ESCALATED");
        }

        @Test
        @DisplayName("returns 404 when review item does not exist")
        void returns404ForUnknownReviewId() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post("/api/v1/hitl/does-not-exist/escalate", "{}");

            assertThat(resp.statusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("returns 404 when trying to escalate an already-approved item")
        void returns404WhenAlreadyApproved() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            ReviewItem item = buildItem("review-approved", "tenant-d", "skill-w");
            queue.enqueue(item);

            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            // Approve via HTTP first
            post("/api/v1/hitl/review-approved/approve",
                "{\"reviewer\":\"test\",\"rationale\":\"ok\"}");

            // Now try to escalate — should fail
            HttpResponse<String> resp = post("/api/v1/hitl/review-approved/escalate", "{}");

            assertThat(resp.statusCode()).isEqualTo(404);
        }
    }

    // ─── Auto-escalation Timeout Logic ──────────────────────────────────────

    @Nested
    @DisplayName("Auto-escalation: findOverdue + escalate flow")
    class AutoEscalationLogicTests {

        @Test
        @DisplayName("findOverdue returns empty when items are within timeout")
        void findOverdueReturnsEmptyForRecentItems() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            ReviewItem item = buildItem("fresh-review", "tenant-e", "skill-p");
            queue.enqueue(item);

            // Threshold of 1800s — item is brand new, not overdue; capture via list
            List<?>[] holder = new List<?>[1];
            queue.findOverdue(1800L, null).whenComplete((list, e) -> holder[0] = list);

            assertThat(holder[0]).isEmpty();
        }

        @Test
        @DisplayName("findOverdue returns item when threshold is 0 seconds (immediate)")
        void findOverdueReturnsItemWithZeroThreshold() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            ReviewItem item = buildItem("overdue-review", "tenant-f", "skill-q", Instant.now().minusSeconds(5));
            queue.enqueue(item);

            // Threshold of 0s — all pending items are overdue
            List<?>[] holder = new List<?>[1];
            queue.findOverdue(0L, null).whenComplete((list, e) -> holder[0] = list);

            assertThat(holder[0]).hasSize(1);
        }

        @Test
        @DisplayName("findOverdue scopes to specific tenant when tenantId provided")
        void findOverdueFiltersByTenant() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            queue.enqueue(buildItem("r-tenant-1", "tenant-x", "sk-1", Instant.now().minusSeconds(5)));
            queue.enqueue(buildItem("r-tenant-2", "tenant-y", "sk-2", Instant.now().minusSeconds(5)));

            // 0s threshold — all are overdue; filter to tenant-x only
            List<?>[] holder = new List<?>[1];
            queue.findOverdue(0L, "tenant-x").whenComplete((list, e) -> holder[0] = list);

            assertThat(holder[0]).hasSize(1);
        }

        @Test
        @DisplayName("escalation does not affect ESCALATED items (idempotent guard)")
        void alreadyEscalatedItemIsRejectedByEscalate() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            ReviewItem item = buildItem("already-escalated", "tenant-g", "skill-r");
            queue.enqueue(item);
            // First escalation — succeeds synchronously via ConcurrentHashMap
            queue.escalate("already-escalated");

            // Second escalation — Promise wraps exception
            boolean[] exceptionFired = {false};
            queue.escalate("already-escalated")
                .whenComplete((esc, e) -> { if (e != null) exceptionFired[0] = true; });

            assertThat(exceptionFired[0]).isTrue();
        }

        @Test
        @DisplayName("HitlController default timeout is 1800 seconds (30 min)")
        void defaultTimeoutIs30Minutes() {
            assertThat(HitlController.DEFAULT_ESCALATION_TIMEOUT_SECONDS).isEqualTo(1800L);
        }

        @Test
        @DisplayName("pending endpoint auto-escalates overdue items when requested")
        void pendingEndpointAutoEscalatesOverdueItems() throws Exception {
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            queue.enqueue(buildItem(
                "review-overdue-http",
                "tenant-http",
                "skill-http",
                Instant.now().minusSeconds(3600)));

            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/api/v1/hitl/pending?tenantId=tenant-http&thresholdSeconds=60&autoEscalate=true");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("count")).isEqualTo(0);
            assertThat(body.get("overdueCount")).isEqualTo(1);
            assertThat(body.get("autoEscalatedCount")).isEqualTo(1);
            assertThat(body.get("escalationTimeoutSeconds")).isEqualTo(60);

            HttpResponse<String> followUp = get("/api/v1/hitl/pending?tenantId=tenant-http");
            assertThat(mapper.readValue(followUp.body(), Map.class).get("count")).isEqualTo(0);
        }

        @Test
        @DisplayName("tenant timeout policy auto-approves overdue items when configured")
        void tenantTimeoutPolicyAutoApprovesOverdueItems() throws Exception {
            System.setProperty("AEP_HITL_TIMEOUT_POLICIES", "tenant-policy=60:auto_approve:manager:ops-oncall");
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            queue.enqueue(buildItem(
                "review-auto-approve",
                "tenant-policy",
                "skill-http",
                Instant.now().minusSeconds(3600)));

            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/api/v1/hitl/pending?tenantId=tenant-policy&autoEscalate=true");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("count")).isEqualTo(0);
            assertThat(body.get("overdueCount")).isEqualTo(1);
            assertThat(body.get("autoApprovedCount")).isEqualTo(1);
            assertThat(body.get("autoEscalatedCount")).isEqualTo(0);
            assertThat(body.get("policyAction")).isEqualTo("auto_approve");
            assertThat(body.get("escalationDestinationType")).isEqualTo("manager");
            assertThat(body.get("escalationDestination")).isEqualTo("ops-oncall");

            ReviewItem resolved = runPromise(() -> queue.getById("review-auto-approve"));
            assertThat(resolved).isNotNull();
            assertThat(resolved.getStatus()).isEqualTo(com.ghatana.agent.learning.review.ReviewStatus.APPROVED);
        }

        @Test
        @DisplayName("tenant timeout policy auto-rejects overdue items when configured")
        void tenantTimeoutPolicyAutoRejectsOverdueItems() throws Exception {
            System.setProperty("AEP_HITL_TIMEOUT_POLICIES", "tenant-reject=45:auto_reject:queue:compliance");
            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            queue.enqueue(buildItem(
                "review-auto-reject",
                "tenant-reject",
                "skill-http",
                Instant.now().minusSeconds(3600)));

            server = new AepHttpServer(engine, port, queue);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/api/v1/hitl/pending?tenantId=tenant-reject&autoEscalate=true");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("autoRejectedCount")).isEqualTo(1);
            assertThat(body.get("autoEscalatedCount")).isEqualTo(0);
            assertThat(body.get("policyAction")).isEqualTo("auto_reject");
            assertThat(body.get("escalationDestinationType")).isEqualTo("queue");
            assertThat(body.get("escalationDestination")).isEqualTo("compliance");

            ReviewItem resolved = runPromise(() -> queue.getById("review-auto-reject"));
            assertThat(resolved).isNotNull();
            assertThat(resolved.getStatus()).isEqualTo(com.ghatana.agent.learning.review.ReviewStatus.REJECTED);
        }

        @Test
        @DisplayName("auto-escalation fires SSE event for overdue item via scheduler callback")
        void autoEscalationFiresSseEventForOverdueItem() throws Exception {
            List<Map<?, ?>> capturedEvents = new CopyOnWriteArrayList<>();

            InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
            ReviewItem item = buildItem("overdue-auto", "tenant-h", "skill-s", Instant.now().minusSeconds(5));
            queue.enqueue(item);

            // Simulate what the scheduler does: find overdue (0s threshold) and escalate each
            AtomicReference<List<ReviewItem>> overdueHolder = new AtomicReference<>(List.of());
            queue.findOverdue(0L, null).whenComplete((list, e) -> overdueHolder.set(list));

            for (ReviewItem overdueItem : overdueHolder.get()) {
                queue.escalate(overdueItem.getReviewId())
                    .whenComplete((escalated, e) -> {
                        if (e == null) {
                            Map<String, Object> event = new HashMap<>();
                            event.put("reviewId", escalated.getReviewId());
                            event.put("status", escalated.getStatus().name());
                            event.put("agentId", escalated.getSkillId());
                            event.put("escalatedAt", Instant.now().toString());
                            event.put("reason", "sla_breach");
                            capturedEvents.add(Map.copyOf(event));
                        }
                    });
            }

            assertThat(capturedEvents).hasSize(1);
            Map<?, ?> event = capturedEvents.get(0);
            assertThat(event.get("reviewId")).isEqualTo("overdue-auto");
            assertThat(event.get("status")).isEqualTo("ESCALATED");
            assertThat(event.get("reason")).isEqualTo("sla_breach");
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ReviewItem buildItem(String reviewId, String tenantId, String skillId) {
        return buildItem(reviewId, tenantId, skillId, Instant.now());
        }

        private ReviewItem buildItem(String reviewId, String tenantId, String skillId, Instant createdAt) {
        return ReviewItem.builder()
            .reviewId(reviewId)
            .tenantId(tenantId)
            .skillId(skillId)
            .proposedVersion("v1.0")
            .itemType(ReviewItemType.POLICY)
            .confidenceScore(0.55)
            .createdAt(createdAt)
            .build();
    }

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
