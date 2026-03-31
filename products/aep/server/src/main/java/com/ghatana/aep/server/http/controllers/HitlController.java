/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.agent.learning.review.HumanReviewQueue;
import com.ghatana.agent.learning.review.ReviewDecision;
import com.ghatana.agent.learning.review.ReviewFilter;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Controller for Human-in-the-Loop (HITL) review endpoints.
 *
 * @doc.type class
 * @doc.purpose HITL review queue management
 * @doc.layer product
 * @doc.pattern Service
 */
public class HitlController {

    private static final Logger log = LoggerFactory.getLogger(HitlController.class);

    /** Default HITL review SLA: 30 minutes. */
    public static final long DEFAULT_ESCALATION_TIMEOUT_SECONDS = 1800L;

    @Nullable
    private final HumanReviewQueue humanReviewQueue;
    /** Callback to publish SSE events: (tenantId, payload). */
    private final BiConsumer<String, Map<String, Object>> ssePublisher;
    private final long escalationTimeoutSeconds;
    @Nullable
    private ScheduledExecutorService scheduler;

    /**
     * @param humanReviewQueue        HITL queue; may be null if not configured
     * @param ssePublisher            callback invoked as {@code (tenantId, payload)} to publish SSE events
     * @param escalationTimeoutSeconds seconds before a PENDING/IN_REVIEW item is auto-escalated
     */
    public HitlController(@Nullable HumanReviewQueue humanReviewQueue,
                           BiConsumer<String, Map<String, Object>> ssePublisher,
                           long escalationTimeoutSeconds) {
        this.humanReviewQueue = humanReviewQueue;
        this.ssePublisher = ssePublisher;
        this.escalationTimeoutSeconds = escalationTimeoutSeconds;
        if (humanReviewQueue != null) {
            startEscalationScheduler();
        }
    }

    /**
     * @param humanReviewQueue   HITL queue; may be null if not configured
     * @param ssePublisher       callback invoked as {@code (tenantId, payload)} to publish SSE events
     */
    public HitlController(@Nullable HumanReviewQueue humanReviewQueue,
                           BiConsumer<String, Map<String, Object>> ssePublisher) {
        this(humanReviewQueue, ssePublisher, DEFAULT_ESCALATION_TIMEOUT_SECONDS);
    }

    /**
     * Starts the background scheduler that polls for overdue review items and auto-escalates them.
     * Runs every 60 seconds. Safe to call only once per instance.
     */
    private void startEscalationScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "hitl-escalation-scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::runEscalationCheck, 60, 60, TimeUnit.SECONDS);
        log.info("[hitl] auto-escalation scheduler started; timeout={}s", escalationTimeoutSeconds);
    }

    /** Polls the queue for overdue items and escalates them, firing hitl_escalated SSE events. */
    private void runEscalationCheck() {
        if (humanReviewQueue == null) return;
        try {
            humanReviewQueue.findOverdue(escalationTimeoutSeconds, null)
                .whenComplete((items, e) -> {
                    if (e != null) {
                        log.warn("[hitl] escalation check failed: {}", e.getMessage());
                        return;
                    }
                    for (var item : items) {
                        humanReviewQueue.escalate(item.getReviewId())
                            .whenComplete((escalated, err) -> {
                                if (err != null) {
                                    log.warn("[hitl] escalate failed reviewId={}: {}", item.getReviewId(), err.getMessage());
                                    return;
                                }
                                Map<String, Object> event = new HashMap<>();
                                event.put("reviewId", escalated.getReviewId());
                                event.put("status", escalated.getStatus().name());
                                event.put("agentId", escalated.getSkillId());
                                event.put("escalatedAt", Instant.now().toString());
                                event.put("reason", "sla_breach");
                                ssePublisher.accept(escalated.getTenantId(), event);
                                log.info("[hitl] auto-escalated reviewId={} tenantId={}", escalated.getReviewId(), escalated.getTenantId());
                            });
                    }
                });
        } catch (Exception ex) {
            log.warn("[hitl] escalation check error: {}", ex.getMessage());
        }
    }

    /** Stops the escalation scheduler. Call on server shutdown to avoid thread leaks. */
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    public Promise<HttpResponse> handleListPending(HttpRequest request) {
        if (humanReviewQueue == null) {
            return Promise.of(HttpHelper.errorResponse(501,
                "HITL queue not configured — start AEP with AepLearningModule"));
        }
        String tenantId = HttpHelper.resolveTenantId(request);
        ReviewFilter filter = ReviewFilter.forTenant(tenantId);
        return humanReviewQueue.getPending(filter)
            .map(items -> {
                var summaries = items.stream()
                    .map(item -> {
                        Map<String, Object> m = new java.util.HashMap<>();
                        m.put("reviewId", item.getReviewId());
                        m.put("agentId", item.getSkillId());
                        m.put("type", item.getItemType().name());
                        m.put("status", item.getStatus().name());
                        m.put("confidence", item.getConfidenceScore());
                        m.put("summary", item.getEvaluationSummary() != null
                            ? item.getEvaluationSummary() : "");
                        m.put("createdAt", item.getCreatedAt().toString());
                        return m;
                    })
                    .collect(java.util.stream.Collectors.toList());
                return HttpHelper.jsonResponse(Map.of(
                    "pending", summaries,
                    "count", summaries.size(),
                    "tenantId", tenantId,
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[hitl] getPending failed for tenant={}: {}", tenantId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed to list pending reviews: " + e.getMessage()));
            });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleApprove(HttpRequest request) {
        if (humanReviewQueue == null) {
            return Promise.of(HttpHelper.errorResponse(501, "HITL queue not configured"));
        }
        String reviewId = request.getPathParameter("reviewId");
        if (reviewId == null || reviewId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "reviewId path parameter is required"));
        }
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = body.isBlank()
                    ? Map.of() : HttpHelper.mapper().readValue(body, Map.class);
                String reviewer = (String) data.getOrDefault("reviewer", "unknown");
                String rationale = (String) data.getOrDefault("rationale", "Approved via API");
                String notes = (String) data.get("notes");
                ReviewDecision decision = new ReviewDecision(
                    reviewer, rationale, Instant.now(), notes);
                return humanReviewQueue.approve(reviewId, decision)
                    .map(item -> {
                        Map<String, Object> resp = Map.of(
                            "reviewId", item.getReviewId(),
                            "status", item.getStatus().name(),
                            "decidedAt", Instant.now().toString()
                        );
                        ssePublisher.accept(HttpHelper.resolveTenantId(request), resp);
                        return HttpHelper.jsonResponse(resp);
                    })
                    .then(Promise::of, e -> {
                        log.warn("HITL approve failed for reviewId={}: {}", reviewId, e.getMessage());
                        return Promise.of(HttpHelper.errorResponse(404,
                            "Review item not found: " + reviewId));
                    });
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        }, e -> Promise.of(HttpHelper.errorResponse(400, "Failed to read request body")));
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleReject(HttpRequest request) {
        if (humanReviewQueue == null) {
            return Promise.of(HttpHelper.errorResponse(501, "HITL queue not configured"));
        }
        String reviewId = request.getPathParameter("reviewId");
        if (reviewId == null || reviewId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "reviewId path parameter is required"));
        }
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = body.isBlank()
                    ? Map.of() : HttpHelper.mapper().readValue(body, Map.class);
                String reviewer = (String) data.getOrDefault("reviewer", "unknown");
                String rationale = (String) data.getOrDefault("rationale", "Rejected via API");
                String notes = (String) data.get("notes");
                ReviewDecision decision = new ReviewDecision(
                    reviewer, rationale, Instant.now(), notes);
                return humanReviewQueue.reject(reviewId, decision)
                    .map(item -> {
                        Map<String, Object> resp = Map.of(
                            "reviewId", item.getReviewId(),
                            "status", item.getStatus().name(),
                            "decidedAt", Instant.now().toString()
                        );
                        ssePublisher.accept(HttpHelper.resolveTenantId(request), resp);
                        return HttpHelper.jsonResponse(resp);
                    })
                    .then(Promise::of, e -> {
                        log.warn("HITL reject failed for reviewId={}: {}", reviewId, e.getMessage());
                        return Promise.of(HttpHelper.errorResponse(404,
                            "Review item not found: " + reviewId));
                    });
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        }, e -> Promise.of(HttpHelper.errorResponse(400, "Failed to read request body")));
    }

    /**
     * POST /api/v1/hitl/:reviewId/escalate — manually escalate a review item.
     * Fires a {@code hitl_escalated} SSE event on success.
     */
    public Promise<HttpResponse> handleEscalate(HttpRequest request) {
        if (humanReviewQueue == null) {
            return Promise.of(HttpHelper.errorResponse(501, "HITL queue not configured"));
        }
        String reviewId = request.getPathParameter("reviewId");
        if (reviewId == null || reviewId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "reviewId path parameter is required"));
        }
        return humanReviewQueue.escalate(reviewId)
            .map(escalated -> {
                Map<String, Object> resp = new HashMap<>();
                resp.put("reviewId", escalated.getReviewId());
                resp.put("status", escalated.getStatus().name());
                resp.put("escalatedAt", Instant.now().toString());
                resp.put("reason", "manual");

                Map<String, Object> ssePayload = new HashMap<>(resp);
                ssePayload.put("agentId", escalated.getSkillId());
                ssePublisher.accept(escalated.getTenantId(), ssePayload);
                log.info("[hitl] manually escalated reviewId={}", reviewId);

                return HttpHelper.jsonResponse(resp);
            })
            .then(Promise::of, e -> {
                log.warn("[hitl] escalate failed for reviewId={}: {}", reviewId, e.getMessage());
                return Promise.of(HttpHelper.errorResponse(404,
                    "Review item not found or cannot be escalated: " + reviewId));
            });
    }
}
