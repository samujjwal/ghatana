/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.controller;

import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.mastery.MasteryTransition;
import com.ghatana.agent.mastery.MasteryTransitionResult;
import com.ghatana.datacloud.governance.approval.ApprovalService;
import com.ghatana.platform.http.server.JsonServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * ActiveJ servlet for mastery operations.
 *
 * <p>Exposes mastery endpoints with governance checks for authorization and approval.
 *
 * @doc.type class
 * @doc.purpose ActiveJ servlet for mastery operations with governance
 * @doc.layer data-cloud
 * @doc.pattern Servlet
 */
public class MasteryController extends JsonServlet {

    private final com.ghatana.agent.mastery.MasteryRegistry masteryRegistry;
    private final ApprovalService approvalService;

    public MasteryController(
            @NotNull com.ghatana.agent.mastery.MasteryRegistry masteryRegistry,
            @NotNull ApprovalService approvalService
    ) {
        this.masteryRegistry = masteryRegistry;
        this.approvalService = approvalService != null ? approvalService : ApprovalService.getInstance();
    }

    /**
     * Query mastery items with governance check.
     */
    public Promise<HttpResponse> queryMastery(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        // Governance check: tenant must have access to mastery data
        return approvalService.checkAccess(tenantId, "mastery:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to mastery data"));
                    }

                    String skillId = request.getQueryParameter("skillId");
                    String agentId = request.getQueryParameter("agentId");
                    String state = request.getQueryParameter("state");
                    String limitStr = request.getQueryParameter("limit");
                    String offsetStr = request.getQueryParameter("offset");

                    Integer limit = limitStr != null ? Integer.parseInt(limitStr) : 100;
                    Integer offset = offsetStr != null ? Integer.parseInt(offsetStr) : 0;

                    MasteryQuery query;
                    if (skillId != null && !skillId.isBlank()) {
                        query = MasteryQuery.bySkill(skillId)
                                .withTenantId(tenantId)
                                .withLimit(limit)
                                .withOffset(offset);
                    } else {
                        // Query all mastery items for tenant when skillId is absent
                        query = MasteryQuery.byTenant(tenantId)
                                .withLimit(limit)
                                .withOffset(offset);
                    }

                    if (agentId != null) {
                        query = query.withAgentId(agentId);
                    }

                    if (state != null && !state.isBlank()) {
                        try {
                            query = query.withStates(java.util.Set.of(com.ghatana.agent.mastery.MasteryState.valueOf(state)));
                        } catch (IllegalArgumentException e) {
                            return Promise.of(badRequest("Invalid state: " + state));
                        }
                    }

                    return masteryRegistry.query(query)
                            .map(this::ok)
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Get a mastery item by ID with governance check.
     */
    public Promise<HttpResponse> getMastery(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String masteryId = request.getPathParameter("masteryId");
        if (masteryId == null || masteryId.isBlank()) {
            return Promise.of(badRequest("masteryId is required"));
        }
        
        // Governance check: tenant must have access to mastery data
        return approvalService.checkAccess(tenantId, "mastery:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to mastery data"));
                    }
                    return masteryRegistry.getById(tenantId, masteryId)
                            .map(optional -> optional
                                    .map(this::ok)
                                    .orElseGet(() -> notFound("Mastery item not found")))
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Save a mastery item with governance check.
     */
    public Promise<HttpResponse> saveMastery(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        // Governance check: tenant must have approval to write mastery data
        return approvalService.checkAccess(tenantId, "mastery:write")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to write mastery data"));
                    }

                    return parseBodyAsync(request, MasteryItem.class)
                            .then((MasteryItem item) -> {
                                // Ensure tenant matches
                                if (!item.applicability().tenantId().equals(tenantId)) {
                                    return Promise.of(badRequest("Tenant ID mismatch"));
                                }
                                return masteryRegistry.save(item).map(savedItem -> created(savedItem));
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Transition a mastery item to a new state with governance check.
     */
    public Promise<HttpResponse> transition(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        // Governance check: tenant must have approval to transition mastery states
        return approvalService.checkAccess(tenantId, "mastery:transition")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to transition mastery"));
                    }

                    return parseBodyAsync(request, MasteryTransition.class)
                            .then((MasteryTransition transition) -> {
                                // Validate tenant: ensure the transition is for the requesting tenant
                                // First, get the mastery item to validate tenant ownership
                                return masteryRegistry.getById(tenantId, transition.masteryId())
                                        .then((Optional<MasteryItem> optional) -> {
                                            if (optional.isEmpty()) {
                                                return Promise.of(notFound("Mastery item not found"));
                                            }
                                            MasteryItem item = optional.get();
                                            if (!item.applicability().tenantId().equals(tenantId)) {
                                                return Promise.of(forbidden("Cannot transition mastery item from another tenant"));
                                            }
                                            return masteryRegistry.transition(transition).map(result -> ok(result));
                                        });
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Find stale mastery items with governance check.
     */
    public Promise<HttpResponse> findStale(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        // Governance check: tenant must have access to mastery data
        return approvalService.checkAccess(tenantId, "mastery:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to mastery data"));
                    }

                    // Use tenant-scoped stale detection
                    return masteryRegistry.findStale(tenantId, java.time.Instant.now())
                            .map(this::ok)
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * List obsolete and quarantined mastery items with governance check.
     */
    public Promise<HttpResponse> findObsoleteOrQuarantined(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        // Governance check: tenant must have access to mastery data
        return approvalService.checkAccess(tenantId, "mastery:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to mastery data"));
                    }

                    MasteryQuery query = MasteryQuery.byTenant(tenantId)
                            .withStates(java.util.Set.of(
                                    com.ghatana.agent.mastery.MasteryState.OBSOLETE,
                                    com.ghatana.agent.mastery.MasteryState.QUARANTINED
                            ));
                    return masteryRegistry.query(query)
                            .map(this::ok)
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * List evidence for a mastery item with governance check.
     */
    public Promise<HttpResponse> listEvidence(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String masteryId = request.getPathParameter("masteryId");
        if (masteryId == null || masteryId.isBlank()) {
            return Promise.of(badRequest("masteryId is required"));
        }

        // Governance check: tenant must have access to mastery data
        return approvalService.checkAccess(tenantId, "mastery:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to mastery data"));
                    }

                    return masteryRegistry.getById(tenantId, masteryId)
                            .map(optional -> optional
                                    .map(item -> ok(java.util.Map.of(
                                            "evidenceRefs", item.evidenceRefs(),
                                            "evaluationRefs", item.evaluationRefs(),
                                            "knownFailureModeIds", item.knownFailureModeIds()
                                    )))
                                    .orElseGet(() -> notFound("Mastery item not found")))
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * List transitions for a mastery item with governance check.
     */
    public Promise<HttpResponse> listTransitions(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String masteryId = request.getPathParameter("masteryId");
        if (masteryId == null || masteryId.isBlank()) {
            return Promise.of(badRequest("masteryId is required"));
        }

        // Governance check: tenant must have access to mastery data
        return approvalService.checkAccess(tenantId, "mastery:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to mastery data"));
                    }

                    return masteryRegistry.getById(tenantId, masteryId)
                            .map(optional -> optional
                                    .map(item -> ok(java.util.Map.of(
                                            "state", item.state(),
                                            "stateHistory", item.stateHistory()
                                    )))
                                    .orElseGet(() -> notFound("Mastery item not found")))
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Get state distribution for mastery items with governance check.
     */
    public Promise<HttpResponse> getStateDistribution(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        // Governance check: tenant must have access to mastery data
        return approvalService.checkAccess(tenantId, "mastery:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to mastery data"));
                    }

                    MasteryQuery query = MasteryQuery.byTenant(tenantId);
                    return masteryRegistry.query(query)
                            .map(items -> {
                                java.util.Map<com.ghatana.agent.mastery.MasteryState, Long> distribution = items.stream()
                                        .collect(java.util.stream.Collectors.groupingBy(
                                                com.ghatana.agent.mastery.MasteryItem::state,
                                                java.util.stream.Collectors.counting()
                                        ));
                                return ok(distribution);
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Get version compatibility view for a mastery item with governance check.
     */
    public Promise<HttpResponse> getVersionCompatibility(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String masteryId = request.getPathParameter("masteryId");
        if (masteryId == null || masteryId.isBlank()) {
            return Promise.of(badRequest("masteryId is required"));
        }

        // Governance check: tenant must have access to mastery data
        return approvalService.checkAccess(tenantId, "mastery:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to mastery data"));
                    }

                    return masteryRegistry.getById(tenantId, masteryId)
                            .map(optional -> optional
                                    .map(item -> ok(java.util.Map.of(
                                            "versionScope", item.versionScope(),
                                            "applicability", item.applicability(),
                                            "staleAfter", item.staleAfter()
                                    )))
                                    .orElseGet(() -> notFound("Mastery item not found")))
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Get promotion history for a mastery item with governance check.
     */
    public Promise<HttpResponse> getPromotionHistory(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String masteryId = request.getPathParameter("masteryId");
        if (masteryId == null || masteryId.isBlank()) {
            return Promise.of(badRequest("masteryId is required"));
        }

        // Governance check: tenant must have access to mastery data
        return approvalService.checkAccess(tenantId, "mastery:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to mastery data"));
                    }

                    return masteryRegistry.getById(tenantId, masteryId)
                            .map(optional -> optional
                                    .map(item -> ok(java.util.Map.of(
                                            "stateHistory", item.stateHistory(),
                                            "evidenceRefs", item.evidenceRefs(),
                                            "evaluationRefs", item.evaluationRefs()
                                    )))
                                    .orElseGet(() -> notFound("Mastery item not found")))
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Get skill-specific evaluation status with governance check.
     */
    public Promise<HttpResponse> getSkillEvalStatus(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String skillId = request.getPathParameter("skillId");
        if (skillId == null || skillId.isBlank()) {
            return Promise.of(badRequest("skillId is required"));
        }

        // Governance check: tenant must have access to mastery data
        return approvalService.checkAccess(tenantId, "mastery:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to mastery data"));
                    }

                    MasteryQuery query = MasteryQuery.bySkill(skillId).withTenantId(tenantId);
                    return masteryRegistry.query(query)
                            .map(items -> {
                                if (items.isEmpty()) {
                                    return ok(java.util.Map.of(
                                            "skillId", skillId,
                                            "hasMastery", false,
                                            "state", com.ghatana.agent.mastery.MasteryState.UNKNOWN
                                    ));
                                }
                                MasteryItem item = items.get(0);
                                return ok(java.util.Map.of(
                                        "skillId", skillId,
                                        "hasMastery", true,
                                        "state", item.state(),
                                        "confidence", item.confidence(),
                                        "evaluationRefs", item.evaluationRefs(),
                                        "knownFailureModeIds", item.knownFailureModeIds()
                                ));
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Get "why this mode?" explanation panel data with governance check.
     * Returns execution mode, version context, mastery state, confidence vector,
     * and approval/verification requirements for a given agent/skill/tenant combination.
     */
    public Promise<HttpResponse> getModeExplanation(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String agentId = request.getQueryParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(badRequest("agentId is required"));
        }

        String skillId = request.getQueryParameter("skillId");
        if (skillId == null || skillId.isBlank()) {
            return Promise.of(badRequest("skillId is required"));
        }

        // Governance check: tenant must have access to mastery data
        return approvalService.checkAccess(tenantId, "mastery:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to mastery data"));
                    }

                    MasteryQuery query = MasteryQuery.bySkill(skillId)
                            .withAgentId(agentId)
                            .withTenantId(tenantId);
                    
                    return masteryRegistry.decide(query)
                            .map((com.ghatana.agent.mastery.MasteryDecision decision) -> {
                                // Build explanation data
                                java.util.Map<String, Object> explanation = new java.util.HashMap<>();
                                explanation.put("agentId", agentId);
                                explanation.put("skillId", skillId);
                                explanation.put("tenantId", tenantId);
                                explanation.put("masteryState", decision.state());
                                explanation.put("confidence", decision.confidence());
                                explanation.put("isExecutable", decision.executable());
                                explanation.put("executionMode", decision.state());
                                explanation.put("requiresApproval", decision.requiresHumanApproval());
                                explanation.put("requiresVerification", decision.requiresVerification());
                                explanation.put("reasoning", decision.reason());
                                explanation.put("versionScope", decision.versionScope());
                                
                                return ok(explanation);
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }
}
