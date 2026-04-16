package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles learning HTTP endpoints (DC-8).
 *
 * <p>Covers trigger, status, and review-queue operations.
 * The SSE streaming endpoint ({@code /api/v1/learning/stream}) remains in
 * {@code DataCloudHttpServer} due to its tight coupling with SSE infrastructure.
 *
 * @doc.type class
 * @doc.purpose Learning bridge HTTP handlers (DC-8)
 * @doc.layer product
 * @doc.pattern Handler
 * @doc.gaa.lifecycle reflect
 */
public class LearningHandler {

    private static final Logger log = LoggerFactory.getLogger(LearningHandler.class);

    private final DataCloudLearningBridge learningBridge;
    private final HttpHandlerSupport http;

    public LearningHandler(DataCloudLearningBridge learningBridge, HttpHandlerSupport http) {
        this.learningBridge = learningBridge;
        this.http = http;
    }

    public Promise<HttpResponse> handleLearningTrigger(HttpRequest request) {
        if (learningBridge == null) {
            return Promise.of(http.errorResponse(503, "Learning bridge not available in this deployment"));
        }
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String correlationId = http.resolveCorrelationId(request);
        return Promise.ofBlocking(http.blockingExecutor(), () -> {
            try {
                Map<String, Object> result = learningBridge.runLearning(tenantId, true);
                Map<String, Object> resp = new LinkedHashMap<>(result);
                resp.put("timestamp", Instant.now().toString());
                return http.jsonResponse(Map.copyOf(resp), correlationId);
            } catch (Exception e) {
                log.error("[DC-8] learning trigger failed for tenant={}: {}", tenantId, e.getMessage(), e);
                return http.errorResponse(500, "Learning trigger failed: " + e.getMessage());
            }
        });
    }

    public Promise<HttpResponse> handleLearningStatus(HttpRequest request) {
        if (learningBridge == null) {
            return Promise.of(http.errorResponse(503, "Learning bridge not available in this deployment"));
        }
        Map<String, Object> status = learningBridge.getStatus();
        Map<String, Object> resp = new LinkedHashMap<>(status);
        resp.put("timestamp", Instant.now().toString());
        return Promise.of(http.jsonResponse(Map.copyOf(resp)));
    }

    public Promise<HttpResponse> handleLearningReviewQueue(HttpRequest request) {
        if (learningBridge == null) {
            return Promise.of(http.errorResponse(503, "Learning bridge not available in this deployment"));
        }
        Map<String, Map<String, Object>> items = learningBridge.getReviewQueue();
        return Promise.of(http.jsonResponse(Map.of(
            "items",     items.values(),
            "count",     items.size(),
            "timestamp", Instant.now().toString()
        )));
    }

    public Promise<HttpResponse> handleLearningReviewApprove(HttpRequest request) {
        if (learningBridge == null) {
            return Promise.of(http.errorResponse(503, "Learning bridge not available in this deployment"));
        }
        String reviewId = request.getPathParameter("reviewId");
        boolean applied = learningBridge.approveReview(reviewId);
        if (!applied) {
            return Promise.of(http.errorResponse(404, "Review item not found: " + reviewId));
        }
        return Promise.of(http.jsonResponse(Map.of(
            "reviewId",  reviewId,
            "decision",  "APPROVED",
            "timestamp", Instant.now().toString()
        )));
    }

    public Promise<HttpResponse> handleLearningReviewReject(HttpRequest request) {
        if (learningBridge == null) {
            return Promise.of(http.errorResponse(503, "Learning bridge not available in this deployment"));
        }
        String reviewId = request.getPathParameter("reviewId");
        boolean applied = learningBridge.rejectReview(reviewId);
        if (!applied) {
            return Promise.of(http.errorResponse(404, "Review item not found: " + reviewId));
        }
        return Promise.of(http.jsonResponse(Map.of(
            "reviewId",  reviewId,
            "decision",  "REJECTED",
            "timestamp", Instant.now().toString()
        )));
    }

    /**
     * {@code DELETE /api/v1/learning/review/completed}
     *
     * <p>Removes all APPROVED and REJECTED items from the in-memory review queue.
     * Safe to call repeatedly; idempotent when the queue is already clean.
     */
    public Promise<HttpResponse> handlePurgeCompletedReviews(HttpRequest request) {
        if (learningBridge == null) {
            return Promise.of(http.errorResponse(503, "Learning bridge not available in this deployment"));
        }
        int purged = learningBridge.purgeCompletedReviews();
        return Promise.of(http.jsonResponse(Map.of(
            "purged",    purged,
            "timestamp", Instant.now().toString()
        )));
    }
}
