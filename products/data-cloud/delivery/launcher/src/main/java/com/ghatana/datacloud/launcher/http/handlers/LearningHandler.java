package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
import com.ghatana.platform.observability.idempotency.IdempotencyHelper;
import com.ghatana.platform.observability.idempotency.IdempotencyStore;
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
    private static final HttpResponse NO_IDEMPOTENCY_RESPONSE = null;

    private final DataCloudLearningBridge learningBridge;
    private final HttpHandlerSupport http;
    private IdempotencyStore idempotencyStore;

    public LearningHandler(DataCloudLearningBridge learningBridge, HttpHandlerSupport http) {
        this.learningBridge = learningBridge;
        this.http = http;
    }

    /**
     * P0-07: Wires an {@link IdempotencyStore} for idempotent learning operations.
     *
     * @param idempotencyStore the idempotency store; may be {@code null}
     * @return this handler (fluent)
     */
    public LearningHandler withIdempotencyStore(IdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
        return this;
    }

    // ─── Helper Methods (P0-07) ─────────────────────────────────────────────

    /**
     * P0-07: Check idempotency for learning operations.
     */
    private Promise<HttpResponse> checkIdempotency(String tenantId, String routeAction, HttpRequest request) {
        if (idempotencyStore == null) {
            return Promise.of(NO_IDEMPOTENCY_RESPONSE);
        }

        String idempotencyKey = IdempotencyHelper.extractIdempotencyKey(request);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Promise.of(NO_IDEMPOTENCY_RESPONSE);
        }

        String principalId = http.resolvePrincipalId(request);
        String scope = "learning:" + routeAction;

        return IdempotencyHelper.checkConflict(idempotencyStore, tenantId, scope, idempotencyKey, principalId,
            IdempotencyHelper.computePayloadHash(request))
            .then(hasConflict -> {
                if (hasConflict) {
                    log.warn("[P0-07] Idempotency conflict for tenant={}, scope={}, key={}", tenantId, scope, idempotencyKey);
                    return Promise.of(http.errorResponse(409,
                        "Idempotency key conflict: same key used with different payload"));
                }

                return IdempotencyHelper.checkIdempotency(idempotencyStore, tenantId, scope, idempotencyKey, principalId)
                    .then(cachedResponse -> {
                        if (cachedResponse != null) {
                            log.info("[P0-07] Returning cached response for tenant={}, scope={}, key={}", tenantId, scope, idempotencyKey);
                            if (cachedResponse instanceof HttpResponse) {
                                return Promise.of(IdempotencyHelper.addIdempotencyHeaders((HttpResponse) cachedResponse, "replay"));
                            }
                            if (cachedResponse instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> map = (Map<String, Object>) cachedResponse;
                                return Promise.of(http.jsonResponse(map));
                            }
                            return Promise.of(http.jsonResponse(Map.of("data", cachedResponse)));
                        }
                        return Promise.of((HttpResponse) null);
                    });
            });
    }

    /**
     * P0-07: Store response for idempotent learning operations.
     */
    private Promise<Void> storeIdempotency(String tenantId, String routeAction,
                                          HttpRequest request, Object response) {
        if (idempotencyStore == null) {
            return Promise.of(null);
        }

        String idempotencyKey = IdempotencyHelper.extractIdempotencyKey(request);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Promise.of(null);
        }

        String principalId = http.resolvePrincipalId(request);
        String scope = "learning:" + routeAction;
        String payloadHash = IdempotencyHelper.computePayloadHash(request);

        return IdempotencyHelper.storeResponse(idempotencyStore, tenantId, scope, idempotencyKey, principalId, payloadHash, response);
    }

    public Promise<HttpResponse> handleLearningTrigger(HttpRequest request) {
        if (learningBridge == null) {
            return Promise.of(http.errorResponse(503, "Learning bridge not available in this deployment"));
        }
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }

        // P0-07: Check idempotency before processing
        return checkIdempotency(tenantId, "trigger", request)
            .then(idempotencyResponse -> {
                if (idempotencyResponse != null) {
                    return Promise.of(idempotencyResponse);
                }

                String correlationId = http.resolveCorrelationId(request);
                return Promise.ofBlocking(http.blockingExecutor(), () -> {
                    try {
                        Map<String, Object> result = learningBridge.runLearning(tenantId, true);
                        Map<String, Object> resp = new LinkedHashMap<>(result);
                        resp.put("timestamp", Instant.now().toString());
                        storeIdempotency(tenantId, "trigger", request, resp);
                        return http.jsonResponse(Map.copyOf(resp), correlationId);
                    } catch (Exception e) {
                        log.error("[DC-8] learning trigger failed for tenant={}: {}", tenantId, e.getMessage(), e);
                        return http.errorResponse(500, "Learning trigger failed: " + e.getMessage());
                    }
                });
            })
            .then(Promise::of, e -> {
                log.error("[learningTrigger] tenant={} failed: {}", tenantId, e.getMessage());
                return Promise.of(http.errorResponse(500, "Failed to trigger learning: " + e.getMessage()));
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
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        String userId = http.resolvePrincipalId(request);
        String reviewId = request.getPathParameter("reviewId");
        boolean applied = learningBridge.approveReview(tenantId, userId, reviewId);
        if (!applied) {
            if (learningBridge.getReviewQueue().containsKey(reviewId)) {
                return Promise.of(http.errorResponse(409, "Review item already finalized: " + reviewId));
            }
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
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        String userId = http.resolvePrincipalId(request);
        String reviewId = request.getPathParameter("reviewId");
        String reason = request.getQueryParameter("reason");
        if (reason == null) reason = "No reason provided";
        boolean applied = learningBridge.rejectReview(tenantId, userId, reviewId, reason);
        if (!applied) {
            if (learningBridge.getReviewQueue().containsKey(reviewId)) {
                return Promise.of(http.errorResponse(409, "Review item already finalized: " + reviewId));
            }
            return Promise.of(http.errorResponse(404, "Review item not found: " + reviewId));
        }
        return Promise.of(http.jsonResponse(Map.of(
            "reviewId",  reviewId,
            "decision",  "REJECTED",
            "reason",    reason,
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
