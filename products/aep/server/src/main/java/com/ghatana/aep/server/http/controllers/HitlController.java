/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.agent.learning.review.HumanReviewQueue;
import com.ghatana.agent.learning.review.ReviewDecision;
import com.ghatana.agent.learning.review.ReviewFilter;
import com.ghatana.agent.learning.review.ReviewItem;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * HTTP controller for Human-in-the-Loop (HITL) review queue endpoints.
 *
 * <p>Provides REST API for:
 * <ul>
 *   <li>Listing pending review items</li>
 *   <li>Approving or rejecting items</li>
 *   <li>Escalating overdue items</li>
 * </ul>
 *
 * <p>All endpoints return 501 when the review queue is not configured.
 *
 * @doc.type class
 * @doc.purpose HITL review queue HTTP controller
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class HitlController {

    private static final Logger log = LoggerFactory.getLogger(HitlController.class);

    /** Default auto-escalation timeout: 30 minutes (1800 seconds). */
    public static final long DEFAULT_ESCALATION_TIMEOUT_SECONDS = 1800L;

    @Nullable
    private final HumanReviewQueue reviewQueue;
    private final BiConsumer<String, Map<String, Object>> ssePublisher;

    /**
     * @param reviewQueue  human review queue; {@code null} disables HITL (endpoints return 501)
     * @param ssePublisher SSE publisher for live HITL update notifications
     */
    public HitlController(
            @Nullable HumanReviewQueue reviewQueue,
            BiConsumer<String, Map<String, Object>> ssePublisher) {
        this.reviewQueue = reviewQueue;
        this.ssePublisher = ssePublisher;
    }

    /** GET /api/v1/hitl/pending — lists pending review items for a tenant. */
    public Promise<HttpResponse> handleListPending(HttpRequest request) {
        if (reviewQueue == null) {
            return Promise.of(HttpHelper.errorResponse(501, "HITL queue not configured"));
        }
        String tenantId = request.getQueryParameter("tenantId");
        ReviewFilter filter = tenantId != null ? ReviewFilter.forTenant(tenantId) : null;
        return reviewQueue.getPending(filter)
            .map(items -> HttpHelper.jsonResponse(Map.of(
                "pending", items.stream().map(this::toMap).collect(Collectors.toList()),
                "count", items.size(),
                "timestamp", Instant.now().toString()
            )))
            .then(Promise::of, e -> {
                log.error("[hitl] list-pending failed: {}", e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500, "Failed to list pending items: " + e.getMessage()));
            });
    }

    /** POST /api/v1/hitl/:reviewId/approve — approves a pending review item. */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleApprove(HttpRequest request) {
        if (reviewQueue == null) {
            return Promise.of(HttpHelper.errorResponse(501, "HITL queue not configured"));
        }
        String reviewId = request.getPathParameter("reviewId");
        if (reviewId == null || reviewId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "reviewId path parameter is required"));
        }
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = body.isBlank()
                    ? Map.of() : HttpHelper.mapper().readValue(body, Map.class);
                String reviewer = (String) data.getOrDefault("reviewer", "unknown");
                String rationale = (String) data.getOrDefault("rationale", "Approved via API");
                String notes = (String) data.get("notes");
                ReviewDecision decision = new ReviewDecision(reviewer, rationale, Instant.now(), notes);
                return reviewQueue.approve(reviewId, decision)
                    .map(item -> {
                        ssePublisher.accept(item.getTenantId(), Map.of(
                            "event", "hitl.approved",
                            "reviewId", item.getReviewId(),
                            "skillId", item.getSkillId(),
                            "status", item.getStatus().name()
                        ));
                        return HttpHelper.jsonResponse(toMap(item));
                    })
                    .then(Promise::of, e -> {
                        log.warn("[hitl] approve failed reviewId={}: {}", reviewId, e.getMessage());
                        return Promise.of(HttpHelper.errorResponse(404, "Review item not found: " + reviewId));
                    });
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        }, e -> Promise.of(HttpHelper.errorResponse(400, "Failed to read request body")));
    }

    /** POST /api/v1/hitl/:reviewId/reject — rejects a pending review item. */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleReject(HttpRequest request) {
        if (reviewQueue == null) {
            return Promise.of(HttpHelper.errorResponse(501, "HITL queue not configured"));
        }
        String reviewId = request.getPathParameter("reviewId");
        if (reviewId == null || reviewId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "reviewId path parameter is required"));
        }
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = body.isBlank()
                    ? Map.of() : HttpHelper.mapper().readValue(body, Map.class);
                String reviewer = (String) data.getOrDefault("reviewer", "unknown");
                String rationale = (String) data.getOrDefault("rationale", "Rejected via API");
                String notes = (String) data.get("notes");
                ReviewDecision decision = new ReviewDecision(reviewer, rationale, Instant.now(), notes);
                return reviewQueue.reject(reviewId, decision)
                    .map(item -> {
                        ssePublisher.accept(item.getTenantId(), Map.of(
                            "event", "hitl.rejected",
                            "reviewId", item.getReviewId(),
                            "skillId", item.getSkillId(),
                            "status", item.getStatus().name()
                        ));
                        return HttpHelper.jsonResponse(toMap(item));
                    })
                    .then(Promise::of, e -> {
                        log.warn("[hitl] reject failed reviewId={}: {}", reviewId, e.getMessage());
                        return Promise.of(HttpHelper.errorResponse(404, "Review item not found: " + reviewId));
                    });
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        }, e -> Promise.of(HttpHelper.errorResponse(400, "Failed to read request body")));
    }

    /** POST /api/v1/hitl/:reviewId/escalate — escalates a review item. */
    public Promise<HttpResponse> handleEscalate(HttpRequest request) {
        if (reviewQueue == null) {
            return Promise.of(HttpHelper.errorResponse(501, "HITL queue not configured"));
        }
        String reviewId = request.getPathParameter("reviewId");
        if (reviewId == null || reviewId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "reviewId path parameter is required"));
        }
        return reviewQueue.escalate(reviewId)
            .map(item -> {
                ssePublisher.accept(item.getTenantId(), Map.of(
                    "event", "hitl.escalated",
                    "reviewId", item.getReviewId(),
                    "skillId", item.getSkillId(),
                    "status", item.getStatus().name()
                ));
                return HttpHelper.jsonResponse(toEscalateMap(item));
            })
            .then(Promise::of, e -> {
                log.warn("[hitl] escalate failed reviewId={}: {}", reviewId, e.getMessage());
                return Promise.of(HttpHelper.errorResponse(404, "Review item not found or cannot be escalated: " + reviewId));
            });
    }

    /** Shuts down any background resources held by this controller. No-op in the default implementation. */
    public void shutdown() {
        // No background resources to release in the default in-memory implementation.
    }

    private Map<String, Object> toMap(ReviewItem item) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("reviewId", item.getReviewId());
        map.put("tenantId", item.getTenantId());
        map.put("agentId", item.getSkillId());
        map.put("proposedVersion", item.getProposedVersion());
        map.put("type", item.getItemType().name());
        map.put("confidenceScore", item.getConfidenceScore());
        map.put("status", item.getStatus().name());
        map.put("createdAt", item.getCreatedAt().toString());
        if (item.getDecidedAt() != null) {
            map.put("decidedAt", item.getDecidedAt().toString());
        }
        return Map.copyOf(map);
    }

    private Map<String, Object> toEscalateMap(ReviewItem item) {
        Map<String, Object> map = new java.util.HashMap<>(toMap(item));
        map.put("escalatedAt", Instant.now().toString());
        map.put("reason", "manual");
        return Map.copyOf(map);
    }
}
