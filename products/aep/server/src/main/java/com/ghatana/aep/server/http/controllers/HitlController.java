/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.observability.AepSloMetrics;
import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.agent.learning.review.HumanReviewQueue;
import com.ghatana.agent.learning.review.ReviewDecision;
import com.ghatana.agent.learning.review.ReviewFilter;
import com.ghatana.agent.learning.review.ReviewItem;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;

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
    private static final String HITL_ESCALATIONS_TOTAL = "aep.hitl.escalations.total";
    private static final String HITL_OVERDUE_SCANS_TOTAL = "aep.hitl.overdue.scans.total";
    private static final String HITL_OVERDUE_ITEMS_TOTAL = "aep.hitl.overdue.items.total";
    private static final String HITL_TIMEOUT_ACTIONS_TOTAL = "aep.hitl.timeout.actions.total";

    /** Default auto-escalation timeout: 30 minutes (1800 seconds). */
    public static final long DEFAULT_ESCALATION_TIMEOUT_SECONDS = 1800L;

    @Nullable
    private final HumanReviewQueue reviewQueue;
    private final BiConsumer<String, Map<String, Object>> ssePublisher;
    @Nullable
    private final AepSloMetrics sloMetrics;
    private final MetricsCollector metricsCollector;
    private final long defaultEscalationTimeoutSeconds;
    private final Map<String, TenantHitlPolicy> tenantPolicies;

    /**
     * @param reviewQueue  human review queue; {@code null} disables HITL (endpoints return 501)
     * @param ssePublisher SSE publisher for live HITL update notifications
     */
    public HitlController(
            @Nullable HumanReviewQueue reviewQueue,
            BiConsumer<String, Map<String, Object>> ssePublisher) {
        this(reviewQueue, ssePublisher, null, MetricsCollectorFactory.createNoop(), DEFAULT_ESCALATION_TIMEOUT_SECONDS, Map.of());
    }

    /**
     * Creates a controller with observability support and a configurable default escalation timeout.
     */
    public HitlController(
            @Nullable HumanReviewQueue reviewQueue,
            BiConsumer<String, Map<String, Object>> ssePublisher,
            @Nullable AepSloMetrics sloMetrics,
            MetricsCollector metricsCollector,
            long defaultEscalationTimeoutSeconds) {
        this(reviewQueue, ssePublisher, sloMetrics, metricsCollector, defaultEscalationTimeoutSeconds, Map.of());
    }

    /**
     * Creates a controller with observability support and tenant-specific timeout policies.
     */
    public HitlController(
            @Nullable HumanReviewQueue reviewQueue,
            BiConsumer<String, Map<String, Object>> ssePublisher,
            @Nullable AepSloMetrics sloMetrics,
            MetricsCollector metricsCollector,
            long defaultEscalationTimeoutSeconds,
            Map<String, TenantHitlPolicy> tenantPolicies) {
        this.reviewQueue = reviewQueue;
        this.ssePublisher = Objects.requireNonNull(ssePublisher, "ssePublisher");
        this.sloMetrics = sloMetrics;
        this.metricsCollector = metricsCollector != null ? metricsCollector : MetricsCollectorFactory.createNoop();
        this.defaultEscalationTimeoutSeconds = defaultEscalationTimeoutSeconds > 0
            ? defaultEscalationTimeoutSeconds
            : DEFAULT_ESCALATION_TIMEOUT_SECONDS;
        this.tenantPolicies = tenantPolicies == null ? Map.of() : Map.copyOf(tenantPolicies);
    }

    /** GET /api/v1/hitl/pending — lists pending review items for a tenant. */
    public Promise<HttpResponse> handleListPending(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        TenantHitlPolicy tenantPolicy = resolvePolicy(tenantId);
        long thresholdSeconds = resolveEscalationTimeoutSeconds(request.getQueryParameter("thresholdSeconds"), tenantPolicy);
        boolean autoEscalate = Boolean.parseBoolean(String.valueOf(request.getQueryParameter("autoEscalate")));
        if (reviewQueue == null) {
            return Promise.of(HttpHelper.jsonResponse(buildPendingQueueResponse(
                java.util.List.of(),
                0,
                0,
                0,
                0,
                thresholdSeconds,
                false,
                tenantId == null ? "default" : tenantId,
                tenantPolicy.overdueAction(),
                tenantPolicy.escalationDestinationType(),
                tenantPolicy.escalationDestination(),
                "HITL queue not configured")));
        }
        ReviewFilter filter = tenantId != null ? ReviewFilter.forTenant(tenantId) : null;
        return scanOverdueItems(filter, thresholdSeconds, autoEscalate)
            .then(scanResult -> reviewQueue.getPending(filter)
                .map(items -> HttpHelper.jsonResponse(buildPendingQueueResponse(
                    items.stream().map(this::toMap).collect(Collectors.toList()),
                    scanResult.overdueCount(),
                    scanResult.autoEscalatedCount(),
                    scanResult.autoApprovedCount(),
                    scanResult.autoRejectedCount(),
                    thresholdSeconds,
                    true,
                    tenantId == null ? "all" : tenantId,
                    scanResult.policyAction(),
                    scanResult.destinationType(),
                    scanResult.destination(),
                    null))))
            .then(response -> Promise.of(response), e -> {
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
                        recordReviewDecisionMetrics(item);
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
                        recordReviewDecisionMetrics(item);
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
        return request.loadBody().then(buf -> {
            EscalationRequest escalationRequest;
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                escalationRequest = parseEscalationRequest(body);
            } catch (Exception exception) {
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + exception.getMessage()));
            }
            return reviewQueue.escalate(reviewId)
                .map(item -> {
                    recordEscalation(item, "manual", OverdueAction.ESCALATE,
                        escalationRequest.destinationType(), escalationRequest.destination());
                    Map<String, Object> event = new java.util.HashMap<>();
                    event.put("event", "hitl.escalated");
                    event.put("reviewId", item.getReviewId());
                    event.put("skillId", item.getSkillId());
                    event.put("status", item.getStatus().name());
                    event.put("reason", escalationRequest.reason());
                    if (escalationRequest.destinationType() != null) {
                        event.put("destinationType", escalationRequest.destinationType());
                    }
                    if (escalationRequest.destination() != null) {
                        event.put("destination", escalationRequest.destination());
                    }
                    ssePublisher.accept(item.getTenantId(), Map.copyOf(event));
                    return HttpHelper.jsonResponse(toEscalateMap(item, escalationRequest));
                })
                .then(Promise::of, e -> {
                    log.warn("[hitl] escalate failed reviewId={}: {}", reviewId, e.getMessage());
                    return Promise.of(HttpHelper.errorResponse(404, "Review item not found or cannot be escalated: " + reviewId));
                });
        }, e -> Promise.of(HttpHelper.errorResponse(400, "Failed to read request body")));
    }

    /** Shuts down any background resources held by this controller. No-op in the default implementation. */
    public void shutdown() {
        // No background resources to release in the default in-memory implementation.
    }

    private Promise<OverdueScanResult> scanOverdueItems(@Nullable ReviewFilter filter,
                                                        long thresholdSeconds,
                                                        boolean autoEscalate) {
        if (reviewQueue == null) {
            return Promise.of(new OverdueScanResult(0, 0, 0, 0, resolvePolicy(filter != null ? filter.tenantId() : null).overdueAction(), null, null));
        }
        String tenantId = filter != null ? filter.tenantId() : null;
        metricsCollector.incrementCounter(HITL_OVERDUE_SCANS_TOTAL,
            "tenant", tenantId == null || tenantId.isBlank() ? "all" : tenantId,
            "auto_escalate", Boolean.toString(autoEscalate));

        return reviewQueue.getPending(filter)
            .then(overdueItems -> {
                List<ReviewItem> matchingOverdueItems = overdueItems.stream()
                    .filter(item -> item.getCreatedAt().isBefore(Instant.now().minusSeconds(resolveThresholdSeconds(item, thresholdSeconds))))
                    .toList();

                if (!autoEscalate || matchingOverdueItems.isEmpty()) {
                    TenantHitlPolicy tenantPolicy = resolvePolicy(tenantId);
                    return Promise.of(new OverdueScanResult(
                        matchingOverdueItems.size(),
                        0,
                        0,
                        0,
                        tenantPolicy.overdueAction(),
                        tenantPolicy.escalationDestinationType(),
                        tenantPolicy.escalationDestination()));
                }

                List<Promise<ResolutionOutcome>> actions = new ArrayList<>(matchingOverdueItems.size());
                for (ReviewItem item : matchingOverdueItems) {
                    TenantHitlPolicy policy = resolvePolicy(item.getTenantId());
                    actions.add(applyOverdueAction(item, policy)
                        .map(outcome -> {
                            publishOverdueEvent(outcome.item(), outcome.action(), policy);
                            return outcome;
                        })
                        .then(Promise::of, error -> {
                            log.warn("[hitl] overdue escalation failed reviewId={}: {}", item.getReviewId(), error.getMessage());
                            return Promise.of(null);
                        }));
                }

                return Promises.toList(actions)
                    .map(resolved -> {
                        int escalatedCount = 0;
                        int approvedCount = 0;
                        int rejectedCount = 0;
                        OverdueAction lastAction = resolvePolicy(tenantId).overdueAction();
                        String destinationType = null;
                        String destination = null;
                        for (ResolutionOutcome outcome : resolved) {
                            if (outcome == null) {
                                continue;
                            }
                            lastAction = outcome.action();
                            if (outcome.destinationType() != null) {
                                destinationType = outcome.destinationType();
                            }
                            if (outcome.destination() != null) {
                                destination = outcome.destination();
                            }
                            switch (outcome.action()) {
                                case ESCALATE -> escalatedCount++;
                                case AUTO_APPROVE -> approvedCount++;
                                case AUTO_REJECT -> rejectedCount++;
                            }
                        }
                        return new OverdueScanResult(
                            matchingOverdueItems.size(),
                            escalatedCount,
                            approvedCount,
                            rejectedCount,
                            lastAction,
                            destinationType,
                            destination);
                    });
            });
    }

    private long resolveEscalationTimeoutSeconds(@Nullable String thresholdParameter,
                                                 TenantHitlPolicy tenantPolicy) {
        if (thresholdParameter == null || thresholdParameter.isBlank()) {
            return tenantPolicy.thresholdSeconds();
        }
        try {
            long thresholdSeconds = Long.parseLong(thresholdParameter.trim());
            return thresholdSeconds > 0 ? thresholdSeconds : tenantPolicy.thresholdSeconds();
        } catch (NumberFormatException exception) {
            return tenantPolicy.thresholdSeconds();
        }
    }

    private long resolveThresholdSeconds(ReviewItem item, long queryThresholdSeconds) {
        return queryThresholdSeconds > 0 ? queryThresholdSeconds : resolvePolicy(item.getTenantId()).thresholdSeconds();
    }

    private TenantHitlPolicy resolvePolicy(@Nullable String tenantId) {
        if (tenantId != null) {
            TenantHitlPolicy explicit = tenantPolicies.get(tenantId);
            if (explicit != null) {
                return explicit;
            }
        }
        TenantHitlPolicy defaultPolicy = tenantPolicies.get("default");
        if (defaultPolicy != null) {
            return defaultPolicy;
        }
        return new TenantHitlPolicy(defaultEscalationTimeoutSeconds, OverdueAction.ESCALATE, null, null);
    }

    private Promise<ResolutionOutcome> applyOverdueAction(ReviewItem item, TenantHitlPolicy policy) {
        return switch (policy.overdueAction()) {
            case ESCALATE -> reviewQueue.escalate(item.getReviewId())
                .map(escalated -> {
                    recordEscalation(escalated, "sla_breach", policy.overdueAction(),
                        policy.escalationDestinationType(), policy.escalationDestination());
                    return new ResolutionOutcome(escalated, OverdueAction.ESCALATE,
                        policy.escalationDestinationType(), policy.escalationDestination());
                });
            case AUTO_APPROVE -> reviewQueue.approve(item.getReviewId(), timeoutDecision(item, policy, "AUTO_APPROVE"))
                .map(approved -> {
                    recordTimeoutAction(approved, policy.overdueAction(),
                        policy.escalationDestinationType(), policy.escalationDestination());
                    recordReviewDecisionMetrics(approved);
                    return new ResolutionOutcome(approved, OverdueAction.AUTO_APPROVE,
                        policy.escalationDestinationType(), policy.escalationDestination());
                });
            case AUTO_REJECT -> reviewQueue.reject(item.getReviewId(), timeoutDecision(item, policy, "AUTO_REJECT"))
                .map(rejected -> {
                    recordTimeoutAction(rejected, policy.overdueAction(),
                        policy.escalationDestinationType(), policy.escalationDestination());
                    recordReviewDecisionMetrics(rejected);
                    return new ResolutionOutcome(rejected, OverdueAction.AUTO_REJECT,
                        policy.escalationDestinationType(), policy.escalationDestination());
                });
        };
    }

    private ReviewDecision timeoutDecision(ReviewItem item, TenantHitlPolicy policy, String actionName) {
        String destination = policy.escalationDestination() != null
            ? " destination=" + policy.escalationDestination()
            : "";
        return new ReviewDecision(
            "system-hitl-timeout",
            "Tenant HITL timeout policy applied: " + actionName + destination,
            Instant.now(),
            "thresholdSeconds=" + policy.thresholdSeconds());
    }

    private void publishOverdueEvent(ReviewItem item, OverdueAction action, TenantHitlPolicy policy) {
        Map<String, Object> event = new java.util.HashMap<>();
        event.put("reviewId", item.getReviewId());
        event.put("skillId", item.getSkillId());
        event.put("status", item.getStatus().name());
        event.put("policyAction", action.apiValue());
        if (policy.escalationDestinationType() != null) {
            event.put("destinationType", policy.escalationDestinationType());
        }
        if (policy.escalationDestination() != null) {
            event.put("destination", policy.escalationDestination());
        }
        switch (action) {
            case ESCALATE -> {
                event.put("event", "hitl.escalated");
                event.put("reason", "sla_breach");
            }
            case AUTO_APPROVE -> {
                event.put("event", "hitl.approved");
                event.put("reason", "timeout_policy");
            }
            case AUTO_REJECT -> {
                event.put("event", "hitl.rejected");
                event.put("reason", "timeout_policy");
            }
        }
        ssePublisher.accept(item.getTenantId(), Map.copyOf(event));
    }

    private void recordReviewDecisionMetrics(ReviewItem item) {
        if (sloMetrics != null) {
            Instant decidedAt = item.getDecidedAt() != null ? item.getDecidedAt() : Instant.now();
            sloMetrics.recordReviewQueueLatency(
                item.getCreatedAt(),
                decidedAt,
                item.getTenantId(),
                item.getItemType().name());
        }
    }

    private void recordEscalation(ReviewItem item,
                                  String reason,
                                  OverdueAction action,
                                  @Nullable String destinationType,
                                  @Nullable String destination) {
        metricsCollector.incrementCounter(HITL_ESCALATIONS_TOTAL,
            "tenant", item.getTenantId(),
            "itemType", item.getItemType().name(),
            "reason", reason,
            "action", action.apiValue(),
            "destination_type", defaultString(destinationType),
            "destination", defaultString(destination));
        if ("sla_breach".equals(reason)) {
            metricsCollector.incrementCounter(HITL_OVERDUE_ITEMS_TOTAL,
                "tenant", item.getTenantId(),
                "itemType", item.getItemType().name());
        }
    }

    private void recordTimeoutAction(ReviewItem item,
                                     OverdueAction action,
                                     @Nullable String destinationType,
                                     @Nullable String destination) {
        metricsCollector.incrementCounter(HITL_TIMEOUT_ACTIONS_TOTAL,
            "tenant", item.getTenantId(),
            "itemType", item.getItemType().name(),
            "action", action.apiValue(),
            "destination_type", defaultString(destinationType),
            "destination", defaultString(destination));
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

    private Map<String, Object> toEscalateMap(ReviewItem item, EscalationRequest escalationRequest) {
        Map<String, Object> map = new java.util.HashMap<>(toMap(item));
        map.put("escalatedAt", Instant.now().toString());
        map.put("reason", escalationRequest.reason());
        map.put("policyAction", OverdueAction.ESCALATE.apiValue());
        if (escalationRequest.destinationType() != null) {
            map.put("destinationType", escalationRequest.destinationType());
        }
        if (escalationRequest.destination() != null) {
            map.put("destination", escalationRequest.destination());
        }
        return Map.copyOf(map);
    }

    private EscalationRequest parseEscalationRequest(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return new EscalationRequest("manual", null, null);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = HttpHelper.mapper().readValue(body, Map.class);
        String reason = valueAsString(data.get("reason"));
        String destinationType = valueAsString(data.get("destinationType"));
        String destination = valueAsString(data.get("destination"));
        return new EscalationRequest(reason == null ? "manual" : reason, destinationType, destination);
    }

    private static String valueAsString(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value).trim();
        return stringValue.isEmpty() ? null : stringValue;
    }

    private Map<String, Object> buildPendingQueueResponse(
            List<?> pendingItems,
            int overdueCount,
            int autoEscalatedCount,
            int autoApprovedCount,
            int autoRejectedCount,
            long thresholdSeconds,
            boolean configured,
            String tenantId,
            OverdueAction policyAction,
            @Nullable String destinationType,
            @Nullable String destination,
            @Nullable String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("pending", pendingItems);
        response.put("count", pendingItems.size());
        response.put("overdueCount", overdueCount);
        response.put("autoEscalatedCount", autoEscalatedCount);
        response.put("autoApprovedCount", autoApprovedCount);
        response.put("autoRejectedCount", autoRejectedCount);
        response.put("escalationTimeoutSeconds", thresholdSeconds);
        response.put("configured", configured);
        response.put("tenantId", tenantId);
        response.put("policyAction", policyAction.apiValue());
        response.put("escalationDestinationType", defaultString(destinationType));
        response.put("escalationDestination", defaultString(destination));
        if (message != null) {
            response.put("message", message);
        }
        response.put("timestamp", Instant.now().toString());
        return Map.copyOf(response);
    }

    private static String defaultString(@Nullable String value) {
        return value == null ? "" : value;
    }

    public enum OverdueAction {
        ESCALATE,
        AUTO_APPROVE,
        AUTO_REJECT;

        public String apiValue() {
            return name().toLowerCase();
        }

        public static OverdueAction from(String value) {
            String normalized = value == null ? "" : value.trim().replace('-', '_').toUpperCase();
            return switch (normalized) {
                case "AUTO_APPROVE" -> AUTO_APPROVE;
                case "AUTO_REJECT" -> AUTO_REJECT;
                default -> ESCALATE;
            };
        }
    }

    public record TenantHitlPolicy(long thresholdSeconds,
                                   OverdueAction overdueAction,
                                   @Nullable String escalationDestinationType,
                                   @Nullable String escalationDestination) {
        public TenantHitlPolicy {
            thresholdSeconds = thresholdSeconds > 0 ? thresholdSeconds : DEFAULT_ESCALATION_TIMEOUT_SECONDS;
            overdueAction = overdueAction == null ? OverdueAction.ESCALATE : overdueAction;
        }
    }

    private record EscalationRequest(String reason,
                                     @Nullable String destinationType,
                                     @Nullable String destination) {
    }

    private record ResolutionOutcome(ReviewItem item,
                                     OverdueAction action,
                                     @Nullable String destinationType,
                                     @Nullable String destination) {
    }

    private record OverdueScanResult(int overdueCount,
                                     int autoEscalatedCount,
                                     int autoApprovedCount,
                                     int autoRejectedCount,
                                     OverdueAction policyAction,
                                     @Nullable String destinationType,
                                     @Nullable String destination) {
    }
}
