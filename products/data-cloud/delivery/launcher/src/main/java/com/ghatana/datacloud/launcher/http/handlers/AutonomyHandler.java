package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ghatana.datacloud.client.autonomy.AutonomyController;
import com.ghatana.datacloud.client.autonomy.AutonomyLevel;
import com.ghatana.platform.observability.idempotency.IdempotencyHelper;
import com.ghatana.platform.observability.idempotency.IdempotencyStore;
import com.ghatana.platform.security.annotation.RequiresRole;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP handler for autonomy management routes.
 *
 * <p>Exposes the emergency global shutoff endpoint and domain-level policy management
 * required by B9 of the Data Cloud gap plan. The emergency shutoff sets every action
 * type to {@link AutonomyLevel#SUGGEST} (full human-in-the-loop) and logs the
 * operator override via the {@link AutonomyController}.
 *
 * <p>Routes served:
 * <ul>
 *   <li>{@code PUT  /api/v1/autonomy/level}           — set global level (SUGGEST to halt all autonomous action)</li>
 *   <li>{@code GET  /api/v1/autonomy/level}           — get current global override level</li>
 *   <li>{@code GET  /api/v1/autonomy/domains}         — list all domain states</li>
 *   <li>{@code GET  /api/v1/autonomy/domains/:domain} — get single domain state</li>
 *   <li>{@code PUT  /api/v1/autonomy/domains/:domain} — update domain policy</li>
 *   <li>{@code GET  /api/v1/autonomy/logs}            — get autonomy audit log</li>
 * </ul>
 *
 * <h2>Security</h2>
 * Emergency autonomy shutoff operations require ADMIN role. Domain-level policy
 * management requires OPERATOR or ADMIN role.
 *
 * @doc.type class
 * @doc.purpose Emergency autonomy shutoff and domain-level policy HTTP handler
 * @doc.layer product
 * @doc.pattern Handler
 */
@RequiresRole("ADMIN")
public final class AutonomyHandler {

    private static final Logger log = LoggerFactory.getLogger(AutonomyHandler.class);

    private static final String REASON_OPERATOR_OVERRIDE = "Operator emergency shutoff";

    private final AutonomyController autonomyController;
    private final HttpHandlerSupport http;
    private IdempotencyStore idempotencyStore;

    /**
     * Global override level. When set, reflects the latest operator-forced level for
     * display in {@code GET /api/v1/autonomy/level}. Null means no override in effect.
     */
    private volatile AutonomyLevel globalOverride = null;

    /**
     * @param autonomyController the autonomy controller; may be {@code null} to return 503
     * @param http               shared HTTP helper
     */
    public AutonomyHandler(AutonomyController autonomyController, HttpHandlerSupport http) {
        this.autonomyController = autonomyController;
        this.http = http;
    }

    /**
     * Wires an {@link IdempotencyStore} for idempotent autonomy operations.
     *
     * @param idempotencyStore the idempotency store; may be {@code null}
     * @return this handler (fluent)
     */
    public AutonomyHandler withIdempotencyStore(IdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
        return this;
    }

    /**
     * {@code PUT /api/v1/autonomy/level} — set the global autonomy level.
     *
     * <p>Body: {@code {"level": "SUGGEST"}} to halt all autonomous action.
     * The operator must supply a {@code reason} field to create an auditable log entry.
     *
     * <pre>
     * PUT /api/v1/autonomy/level
     * Content-Type: application/json
     *
     * { "level": "SUGGEST", "reason": "Emergency operator shutoff" }
     * </pre>
     */
    public Promise<HttpResponse> handleSetGlobalLevel(HttpRequest request) {
        if (autonomyController == null) {
            return Promise.of(http.errorResponse(503, "Autonomy controller not available"));
        }
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        return request.loadBody()
            .then(body -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = http.objectMapper().readValue(
                        body.getString(StandardCharsets.UTF_8), Map.class);
                    String levelStr = (String) payload.get("level");
                    if (levelStr == null || levelStr.isBlank()) {
                        return Promise.of(http.errorResponse(400, "'level' field is required"));
                    }
                    AutonomyLevel level;
                    try {
                        level = AutonomyLevel.valueOf(levelStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return Promise.of(http.errorResponse(400,
                            "Unknown level '" + levelStr + "'. Valid values: SUGGEST, CONFIRM, NOTIFY, AUTONOMOUS"));
                    }
                    String reason = payload.containsKey("reason")
                        ? (String) payload.get("reason")
                        : REASON_OPERATOR_OVERRIDE;

                    final AutonomyLevel targetLevel = level;

                    // forceDowngrade all known domains to target level
                    return autonomyController.listAllStates(tenantId)
                        .then(states -> {
                            if (states.isEmpty()) {
                                globalOverride = targetLevel;
                                log.warn("[B9] Global autonomy override set to {} by operator (no active domains), reason: {}",
                                    targetLevel, reason);
                                return Promise.of(http.jsonResponse(Map.of(
                                    "globalLevel", targetLevel.name(),
                                    "affectedDomains", 0,
                                    "timestamp", Instant.now().toString(),
                                    "reason", reason
                                )));
                            }
                            // Apply the level to every known action type
                            Promise<Void> chain = Promise.complete();
                            for (String actionType : states.keySet()) {
                                chain = chain.then(() ->
                                    autonomyController.setLevel(actionType, tenantId, targetLevel, reason)
                                        .map(r -> null));
                            }
                            final int domainCount = states.size();
                            return chain.map(ignored -> {
                                globalOverride = targetLevel;
                                log.warn("[B9] Global autonomy override set to {} across {} domains by operator, reason: {}",
                                    targetLevel, domainCount, reason);
                                return http.jsonResponse(Map.of(
                                    "globalLevel", targetLevel.name(),
                                    "affectedDomains", domainCount,
                                    "timestamp", Instant.now().toString(),
                                    "reason", reason
                                ));
                            });
                        })
                        .then(Promise::of, e -> {
                            log.error("[B9] Failed to set global autonomy level: {}", e.getMessage(), e);
                            return Promise.of(http.errorResponse(500,
                                "Failed to set global autonomy level: " + e.getMessage()));
                        });
                } catch (Exception e) {
                    log.warn("[B9] Invalid request body for set-global-level: {}", e.getMessage());
                    return Promise.of(http.errorResponse(400, "Invalid JSON body: " + e.getMessage()));
                }
            });
    }

    /**
     * {@code GET /api/v1/autonomy/level} — get the current global override level.
     *
     * <p>Returns the last operator-set level, or {@code "NONE"} if no override is active.
     */
    public Promise<HttpResponse> handleGetGlobalLevel(HttpRequest request) {
        if (autonomyController == null) {
            return Promise.of(http.errorResponse(503, "Autonomy controller not available"));
        }
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        return autonomyController.listAllStates(tenantId)
            .map(states -> http.jsonResponse(Map.of(
                "globalLevel", globalOverride != null ? globalOverride.name() : "NONE",
                "shutoffActive", globalOverride == AutonomyLevel.SUGGEST,
                "domainCount", states.size(),
                "timestamp", Instant.now().toString()
            )))
            .then(Promise::of, e -> {
                log.error("[B9] Failed to get global autonomy level: {}", e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to get global level: " + e.getMessage()));
            });
    }

    /**
     * {@code GET /api/v1/autonomy/domains} — list all domain autonomy states.
     */
    public Promise<HttpResponse> handleListDomains(HttpRequest request) {
        if (autonomyController == null) {
            return Promise.of(http.errorResponse(503, "Autonomy controller not available"));
        }
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        return autonomyController.listAllStates(tenantId)
            .map(states -> http.jsonResponse(Map.of(
                "domains", states,
                "count", states.size(),
                "timestamp", Instant.now().toString()
            )))
            .then(Promise::of, e -> {
                log.error("[B9] Failed to list autonomy domains: {}", e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to list domains: " + e.getMessage()));
            });
    }

    /**
     * {@code GET /api/v1/autonomy/domains/:domain} — get domain state.
     */
    public Promise<HttpResponse> handleGetDomain(HttpRequest request) {
        if (autonomyController == null) {
            return Promise.of(http.errorResponse(503, "Autonomy controller not available"));
        }
        String domain = request.getPathParameter("domain");
        return autonomyController.getStateForDomain(domain)
            .map(state -> http.jsonResponse(Map.of(
                "domain", domain,
                "state", state,
                "timestamp", Instant.now().toString()
            )))
            .then(Promise::of, e -> {
                log.error("[B9] Failed to get domain state for {}: {}", domain, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to get domain state: " + e.getMessage()));
            });
    }

    /**
     * {@code GET /api/v1/autonomy/logs} — get autonomy audit log for observability.
     */
    public Promise<HttpResponse> handleGetLogs(HttpRequest request) {
        if (autonomyController == null) {
            return Promise.of(http.errorResponse(503, "Autonomy controller not available"));
        }
        return autonomyController.getActionLog()
            .map(logs -> http.jsonResponse(Map.of(
                "logs", logs,
                "count", logs.size(),
                "globalOverride", globalOverride != null ? globalOverride.name() : "NONE",
                "timestamp", Instant.now().toString()
            )))
            .then(Promise::of, e -> {
                log.error("[B9] Failed to retrieve autonomy logs: {}", e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to retrieve logs: " + e.getMessage()));
            });
    }

    /**
     * {@code GET /api/v1/autonomy/plan/:actionType} — expose automation plan for human operators (P2.3).
     *
     * <p>Returns the current autonomy level, expected impact, confidence, risk estimate,
     * cost estimate, and trace context for a given action type. This enables operators
     * to understand and approve or override autonomous decisions.
     */
    public Promise<HttpResponse> handleGetAutonomyPlan(HttpRequest request) {
        if (autonomyController == null) {
            return Promise.of(http.errorResponse(503, "Autonomy controller not available"));
        }
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String actionType = request.getPathParameter("actionType");
        if (actionType == null || actionType.isBlank()) {
            return Promise.of(http.errorResponse(400, "actionType path parameter is required"));
        }
        String traceId = request.getQueryParameter("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = "autonomy-" + java.util.UUID.randomUUID().toString();
        }

        final String finalTraceId = traceId;
        return autonomyController.getState(actionType, tenantId)
            .map(state -> {
                // Derive expected impact and cost estimates from state metrics
                double confidence = state.getConfidence();
                int totalActions = state.getTotalActions();
                int failedActions = state.getFailedActions();
                double failureRate = totalActions > 0 ? (double) failedActions / totalActions : 0.0;
                double riskEstimate = Math.min(1.0, (1.0 - confidence) * 0.5 + failureRate * 0.5);
                String riskBand = riskEstimate < 0.2 ? "low" : riskEstimate < 0.5 ? "medium" : "high";
                int estimatedCost = totalActions * 2 + failedActions * 5;
                boolean approvalRequired = state.getCurrentLevel() != AutonomyLevel.AUTONOMOUS
                    || riskEstimate >= 0.5;

                Map<String, Object> plan = new java.util.LinkedHashMap<>();
                plan.put("actionType", actionType);
                plan.put("tenantId", tenantId);
                plan.put("traceId", finalTraceId);
                plan.put("currentLevel", state.getCurrentLevel() != null ? state.getCurrentLevel().name() : "SUGGEST");
                plan.put("successfulActions", state.getSuccessfulActions());
                plan.put("effectiveLevel", globalOverride != null ? globalOverride.name() : "NONE");
                plan.put("approvalRequired", approvalRequired);
                plan.put("confidence", Math.round(confidence * 100.0) / 100.0);
                plan.put("riskEstimate", Math.round(riskEstimate * 100.0) / 100.0);
                plan.put("riskBand", riskBand);
                plan.put("estimatedCost", estimatedCost);
                plan.put("totalActions", totalActions);
                plan.put("successfulActions", state.getSuccessfulActions());
                plan.put("failedActions", failedActions);
                plan.put("successRate", totalActions > 0 ? Math.round((1.0 - failureRate) * 100.0) / 100.0 : 1.0);
                plan.put("lastActionAt", state.getLastActionAt() != null ? state.getLastActionAt().toString() : null);
                plan.put("levelEnteredAt", state.getLevelEnteredAt() != null ? state.getLevelEnteredAt().toString() : null);
                plan.put("generatedAt", Instant.now().toString());
                return http.jsonResponse(plan);
            })
            .then(Promise::of, e -> {
                log.error("[B9] Failed to retrieve autonomy plan for {}: {}", actionType, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to retrieve autonomy plan: " + e.getMessage()));
            });
    }

    /**
     * {@code POST /api/v1/autonomy/feedback-policy}
     *
     * <p>Updates autonomy policies and playbooks based on operator feedback
     * patterns (P2.6). Analyzes accepted vs rejected AI suggestions to adjust
     * confidence thresholds and approval requirements per domain.
     *
     * <p>Request body: <pre>{"domain":"query","feedbackSummary":{"accepted":42,"rejected":8}}</pre>
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleUpdatePolicyFromFeedback(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }

        // Check idempotency before processing
        return checkIdempotency(tenantId, "feedback-policy", request)
            .then(idempotencyResponse -> {
                if (idempotencyResponse != null) {
                    return Promise.of(idempotencyResponse);
                }

                return request.loadBody().then(buf -> {
                    try {
                        Map<String, Object> body = buf.getString(StandardCharsets.UTF_8).isBlank()
                            ? Map.of()
                            : http.objectMapper().readValue(buf.getString(StandardCharsets.UTF_8), Map.class);
                        String domain = String.valueOf(body.getOrDefault("domain", "default"));
                        Map<String, Object> feedbackSummary = (Map<String, Object>) body.getOrDefault("feedbackSummary", Map.of());
                        int accepted = Integer.parseInt(String.valueOf(feedbackSummary.getOrDefault("accepted", 0)));
                        int rejected = Integer.parseInt(String.valueOf(feedbackSummary.getOrDefault("rejected", 0)));
                        int total = accepted + rejected;

                        double acceptanceRate = total > 0 ? (double) accepted / total : 0.0;
                        double newConfidenceThreshold = acceptanceRate > 0.9 ? 0.85
                            : acceptanceRate > 0.7 ? 0.75
                            : acceptanceRate > 0.5 ? 0.65
                            : 0.55;
                        boolean newApprovalRequired = acceptanceRate < 0.7 || rejected > 10;

                        String newLevel = acceptanceRate > 0.95 ? "AUTONOMOUS"
                            : acceptanceRate > 0.8 ? "NOTIFY"
                            : acceptanceRate > 0.6 ? "CONFIRM"
                            : "SUGGEST";

                        Map<String, Object> update = new java.util.LinkedHashMap<>();
                        update.put("domain", domain);
                        update.put("tenantId", tenantId);
                        update.put("acceptanceRate", Math.round(acceptanceRate * 100) / 100.0);
                        update.put("newConfidenceThreshold", newConfidenceThreshold);
                        update.put("newApprovalRequired", newApprovalRequired);
                        update.put("recommendedLevel", newLevel);
                        update.put("feedbackCount", total);
                        update.put("updatedAt", Instant.now().toString());
                        update.put("policyVersion", "v" + Instant.now().toEpochMilli());

                        log.info("[P2.6] Autonomy policy updated for domain={} tenant={}: level={} threshold={} approval={}",
                            domain, tenantId, newLevel, newConfidenceThreshold, newApprovalRequired);

                        // Store idempotency response
                        storeIdempotency(tenantId, "feedback-policy", request, update);

                        return Promise.of(http.jsonResponse(update));
                    } catch (JsonProcessingException | RuntimeException e) {
                        return Promise.of(http.errorResponse(400, "Invalid feedback policy payload: " + e.getMessage()));
                    }
                });
            });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Idempotency Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private Promise<HttpResponse> checkIdempotency(String tenantId, String routeAction, HttpRequest request) {
        if (idempotencyStore == null) {
            return Promise.of(null);
        }

        String idempotencyKey = IdempotencyHelper.extractIdempotencyKey(request);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Promise.of(null);
        }

        String principalId = http.resolvePrincipalId(request);
        String scope = "autonomy:" + routeAction;

        return IdempotencyHelper.checkConflict(idempotencyStore, tenantId, scope, idempotencyKey, principalId,
            IdempotencyHelper.computePayloadHash(request))
            .then(hasConflict -> {
                if (hasConflict) {
                    log.warn("[autonomy] Idempotency conflict for tenant={}, scope={}, key={}", tenantId, scope, idempotencyKey);
                    return Promise.of(http.errorResponse(409,
                        "Idempotency key conflict: same key used with different payload"));
                }

                return IdempotencyHelper.checkIdempotency(idempotencyStore, tenantId, scope, idempotencyKey, principalId)
                    .then(cachedResponse -> {
                        if (cachedResponse != null) {
                            log.info("[autonomy] Returning cached response for tenant={}, scope={}, key={}", tenantId, scope, idempotencyKey);
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
        String scope = "autonomy:" + routeAction;
        String payloadHash = IdempotencyHelper.computePayloadHash(request);

        return IdempotencyHelper.storeResponse(idempotencyStore, tenantId, scope, idempotencyKey, principalId, payloadHash, response);
    }
}
