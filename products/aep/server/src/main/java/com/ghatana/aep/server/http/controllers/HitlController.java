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
import java.util.Map;
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

    @Nullable
    private final HumanReviewQueue humanReviewQueue;
    /** Callback to publish SSE events: (tenantId, eventType, payload). */
    private final BiConsumer<String, Map<String, Object>> ssePublisher;

    /**
     * @param humanReviewQueue   HITL queue; may be null if not configured
     * @param ssePublisher       callback invoked as {@code (tenantId, payload)} to publish SSE events
     */
    public HitlController(@Nullable HumanReviewQueue humanReviewQueue,
                           BiConsumer<String, Map<String, Object>> ssePublisher) {
        this.humanReviewQueue = humanReviewQueue;
        this.ssePublisher = ssePublisher;
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
}
