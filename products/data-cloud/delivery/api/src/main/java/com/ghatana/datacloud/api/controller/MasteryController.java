/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.controller;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaRepository;
import com.ghatana.agent.promotion.PromotionEngine;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.mastery.MasteryTransition;
import com.ghatana.agent.obsolescence.ObsolescenceDetector;
import com.ghatana.agent.obsolescence.ObsolescenceEvent;
import com.ghatana.agent.obsolescence.ObsolescenceTransitionService;
import com.ghatana.agent.obsolescence.DefaultObsolescenceTransitionService;
import com.ghatana.datacloud.governance.approval.ApprovalService;
import com.ghatana.platform.http.server.JsonServlet;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class MasteryController extends JsonServlet implements AsyncServlet {

    private static final Pattern MASTERY_ITEM_PATH = Pattern.compile(
        "^/api/v1/mastery/([^/]+)(?:/(?:evidence|transitions|version-compatibility|promotion-history))?$");
    private static final Pattern SKILL_EVAL_PATH = Pattern.compile(
        "^/api/v1/mastery/skills/([^/]+)/evaluation-status$");
    private static final Pattern LEARNING_DELTA_PATH = Pattern.compile(
        "^/api/v1/mastery/learning-deltas/([^/]+)/(?:evaluate|promote|dry-run-promotion)$");

    private final com.ghatana.agent.mastery.MasteryRegistry masteryRegistry;
    private final ApprovalService approvalService;
    private final ObsolescenceDetector obsolescenceDetector;
    private final ObsolescenceTransitionService obsolescenceTransitionService;
    private final LearningDeltaRepository learningDeltaRepository;
    private final PromotionEngine promotionEngine;

    public MasteryController(
            @NotNull com.ghatana.agent.mastery.MasteryRegistry masteryRegistry,
            @NotNull ApprovalService approvalService,
            @NotNull ObsolescenceDetector obsolescenceDetector,
            @NotNull LearningDeltaRepository learningDeltaRepository,
            @NotNull PromotionEngine promotionEngine
    ) {
        this.masteryRegistry = masteryRegistry;
        this.approvalService = approvalService != null ? approvalService : ApprovalService.getInstance();
        this.obsolescenceDetector = obsolescenceDetector;
        this.obsolescenceTransitionService = new DefaultObsolescenceTransitionService(masteryRegistry);
        this.learningDeltaRepository = learningDeltaRepository;
        this.promotionEngine = promotionEngine;
    }

    @Override
    public Promise<HttpResponse> serve(@NotNull HttpRequest request) {
        HttpMethod method = request.getMethod();
        String path = request.getPath();

        if (method == HttpMethod.GET && "/api/v1/mastery".equals(path)) {
            return queryMastery(request);
        }
        if (method == HttpMethod.GET && "/api/v1/mastery/stale".equals(path)) {
            return findStale(request);
        }
        if (method == HttpMethod.GET && "/api/v1/mastery/obsolete".equals(path)) {
            return findObsoleteOrQuarantined(request);
        }
        if (method == HttpMethod.GET && "/api/v1/mastery/distribution".equals(path)) {
            return getStateDistribution(request);
        }
        if (method == HttpMethod.GET && "/api/v1/mastery/mode-explanation".equals(path)) {
            return getModeExplanation(request);
        }
        if (method == HttpMethod.GET && "/api/v1/mastery/preview/decision".equals(path)) {
            return previewDecision(request);
        }
        if (method == HttpMethod.GET && "/api/v1/mastery/preview/retrieval".equals(path)) {
            return previewRetrieval(request);
        }
        if (method == HttpMethod.GET && "/api/v1/mastery/learning-deltas".equals(path)) {
            return listLearningDeltas(request);
        }
        if (method == HttpMethod.POST && "/api/v1/mastery".equals(path)) {
            return saveMastery(request);
        }
        if (method == HttpMethod.POST && "/api/v1/mastery/obsolescence/scan".equals(path)) {
            return scanObsolescence(request);
        }
        if (method == HttpMethod.POST && "/api/v1/mastery/obsolescence-events/process".equals(path)) {
            return processObsolescenceEvent(request);
        }
        if (method == HttpMethod.GET && path.matches("^/api/v1/mastery/skills/[^/]+/evaluation-status$")) {
            return getSkillEvalStatus(request);
        }
        if (method == HttpMethod.POST && path.matches("^/api/v1/mastery/learning-deltas/[^/]+/evaluate$")) {
            return evaluateLearningDelta(request);
        }
        if (method == HttpMethod.POST && path.matches("^/api/v1/mastery/learning-deltas/[^/]+/promote$")) {
            return promoteLearningDelta(request);
        }
        if (method == HttpMethod.POST && path.matches("^/api/v1/mastery/learning-deltas/[^/]+/dry-run-promotion$")) {
            return dryRunPromotion(request);
        }
        if (path.matches("^/api/v1/mastery/[^/]+/transition$") && method == HttpMethod.POST) {
            return transition(request);
        }
        if (method == HttpMethod.GET && path.matches("^/api/v1/mastery/[^/]+/evidence$")) {
            return listEvidence(request);
        }
        if (method == HttpMethod.GET && path.matches("^/api/v1/mastery/[^/]+/transitions$")) {
            return listTransitions(request);
        }
        if (method == HttpMethod.GET && path.matches("^/api/v1/mastery/[^/]+/version-compatibility$")) {
            return getVersionCompatibility(request);
        }
        if (method == HttpMethod.GET && path.matches("^/api/v1/mastery/[^/]+/promotion-history$")) {
            return getPromotionHistory(request);
        }
        // Phase 9 FIX: Add approve/reject delta endpoints
        if (method == HttpMethod.POST && path.matches("^/api/v1/mastery/learning-deltas/[^/]+/approve$")) {
            return approveLearningDelta(request);
        }
        if (method == HttpMethod.POST && path.matches("^/api/v1/mastery/learning-deltas/[^/]+/reject$")) {
            return rejectLearningDelta(request);
        }
        // Phase 9 FIX: Add state transition endpoints
        if (method == HttpMethod.POST && path.matches("^/api/v1/mastery/[^/]+/mark-maintenance-only$")) {
            return markMaintenanceOnly(request);
        }
        if (method == HttpMethod.POST && path.matches("^/api/v1/mastery/[^/]+/mark-obsolete$")) {
            return markObsolete(request);
        }
        if (method == HttpMethod.POST && path.matches("^/api/v1/mastery/[^/]+/quarantine$")) {
            return quarantine(request);
        }
        if (method == HttpMethod.POST && path.matches("^/api/v1/mastery/[^/]+/retire$")) {
            return retire(request);
        }
        if (method == HttpMethod.GET && path.matches("^/api/v1/mastery/[^/]+$")) {
            return getMastery(request);
        }

        return Promise.of(notFound("Endpoint not found"));
    }

    /**
     * Creates a mastery controller with explicit obsolescence transition service wiring.
     */
    public MasteryController(
            @NotNull com.ghatana.agent.mastery.MasteryRegistry masteryRegistry,
            @NotNull ApprovalService approvalService,
            @NotNull ObsolescenceDetector obsolescenceDetector,
            @NotNull ObsolescenceTransitionService obsolescenceTransitionService,
            @NotNull LearningDeltaRepository learningDeltaRepository,
            @NotNull PromotionEngine promotionEngine
    ) {
        this.masteryRegistry = masteryRegistry;
        this.approvalService = approvalService != null ? approvalService : ApprovalService.getInstance();
        this.obsolescenceDetector = obsolescenceDetector;
        this.obsolescenceTransitionService = obsolescenceTransitionService;
        this.learningDeltaRepository = learningDeltaRepository;
        this.promotionEngine = promotionEngine;
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

        String masteryId = masteryId(request);
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
     * P1 FIX: Deprecated - Save a mastery item with governance check.
     * This endpoint bypasses proper governance through LearningDelta + PromotionEngine.
     * Use POST /learning-deltas to create learning deltas and POST /learning-deltas/{deltaId}/promote
     * for proper mastery state transitions with evidence and policy validation.
     *
     * @deprecated Use LearningDelta + PromotionEngine workflow for mastery state changes
     */
    @Deprecated
    public Promise<HttpResponse> saveMastery(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        if (!isBreakGlassRequest(request)) {
            return Promise.of(forbidden(
                    "Direct mastery mutation is disabled. Use LearningDelta + Promotion workflow."));
        }

        // Governance check: tenant must have approval to write mastery data
        return approvalService.checkAccess(tenantId, "mastery:breakglass")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Break-glass access denied for direct mastery mutation"));
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
     * P1 FIX: Deprecated - Transition a mastery item to a new state with governance check.
     * This endpoint bypasses proper governance through LearningDelta + PromotionEngine or ObsolescenceTransitionService.
     * Use POST /learning-deltas to create learning deltas and POST /learning-deltas/{deltaId}/promote
     * for promotion transitions, or use ObsolescenceTransitionService for obsolescence transitions.
     *
     * @deprecated Use LearningDelta + PromotionEngine for promotion, or ObsolescenceTransitionService for obsolescence
     */
    @Deprecated
    public Promise<HttpResponse> transition(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        if (!isBreakGlassRequest(request)) {
            return Promise.of(forbidden(
                    "Direct mastery transition is disabled. Use governed promotion/obsolescence workflows."));
        }

        // Governance check: tenant must have approval to transition mastery states
        return approvalService.checkAccess(tenantId, "mastery:breakglass")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Break-glass access denied for direct mastery transition"));
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

        String masteryId = masteryId(request);
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

        String masteryId = masteryId(request);
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

        String masteryId = masteryId(request);
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

        String masteryId = masteryId(request);
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

        String skillId = skillId(request);
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
     * Scan for obsolescence events with governance check.
     */
    public Promise<HttpResponse> scanObsolescence(@NotNull HttpRequest request) {
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

                    // Create environment fingerprint from request parameters
                    EnvironmentFingerprint env = buildEnvironmentFingerprint(request);

                    // Scan for obsolescence events (tenant-scoped)
                    return obsolescenceDetector.scanAll(tenantId, env)
                            .map(this::ok)
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Builds an environment fingerprint from request parameters.
     */
    @NotNull
    private EnvironmentFingerprint buildEnvironmentFingerprint(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) tenantId = "unknown";

        String repoId = request.getQueryParameter("repoId");
        if (repoId == null || repoId.isBlank()) repoId = "unknown";

        String projectType = request.getQueryParameter("projectType");
        if (projectType == null || projectType.isBlank()) projectType = "unknown";

        Map<String, String> dependencies = new HashMap<>();
        String deps = request.getQueryParameter("dependencies");
        if (deps != null && !deps.isBlank()) {
            // Parse comma-separated dependencies (format: name:version,name:version)
            for (String dep : deps.split(",")) {
                String[] parts = dep.split(":");
                if (parts.length == 2) {
                    dependencies.put(parts[0], parts[1]);
                }
            }
        }

        Map<String, String> runtimes = new HashMap<>();
        String runtimesParam = request.getQueryParameter("runtimes");
        if (runtimesParam != null && !runtimesParam.isBlank()) {
            // Parse comma-separated runtimes (format: name:version,name:version)
            for (String runtime : runtimesParam.split(",")) {
                String[] parts = runtime.split(":");
                if (parts.length == 2) {
                    runtimes.put(parts[0], parts[1]);
                }
            }
        }

        return new EnvironmentFingerprint(
                tenantId,
                repoId,
                projectType,
                dependencies,
                Map.of(),
                runtimes,
                Map.of(),
                Map.of(),
                Map.of(),
                java.time.Instant.now(),
                List.of()
        );
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

    /**
     * Preview mastery decision for a given tenant/agent/skill/version context.
     */
    public Promise<HttpResponse> previewDecision(@NotNull HttpRequest request) {
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

        String versionContext = request.getQueryParameter("versionContext");

        return approvalService.checkAccess(tenantId, "mastery:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to mastery decision preview"));
                    }

                    MasteryQuery query = MasteryQuery.bySkill(skillId)
                            .withAgentId(agentId)
                            .withTenantId(tenantId);
                    if (versionContext != null && !versionContext.isBlank()) {
                        query = query.withVersionContext(versionContext);
                    }

                    return masteryRegistry.decide(query)
                            .map(decision -> ok(Map.of(
                                    "tenantId", tenantId,
                                    "agentId", agentId,
                                    "skillId", skillId,
                                    "versionContext", versionContext == null ? "" : versionContext,
                                    "state", decision.state(),
                                    "confidence", decision.confidence(),
                                    "executable", decision.executable(),
                                    "requiresApproval", decision.requiresHumanApproval(),
                                    "requiresVerification", decision.requiresVerification(),
                                    "reason", decision.reason()
                            )))
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Preview mastery retrieval candidates for a tenant/agent/skill tuple.
     */
    public Promise<HttpResponse> previewRetrieval(@NotNull HttpRequest request) {
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

        String limitStr = request.getQueryParameter("limit");
        int limit = limitStr != null ? Integer.parseInt(limitStr) : 20;

        return approvalService.checkAccess(tenantId, "mastery:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to mastery retrieval preview"));
                    }

                    MasteryQuery query = MasteryQuery.bySkill(skillId)
                            .withTenantId(tenantId)
                            .withAgentId(agentId)
                            .withLimit(limit);

                    return masteryRegistry.query(query)
                            .map(items -> ok(Map.of(
                                    "tenantId", tenantId,
                                    "agentId", agentId,
                                    "skillId", skillId,
                                    "count", items.size(),
                                    "items", items.stream().map(item -> Map.of(
                                            "masteryId", item.masteryId(),
                                            "state", item.state(),
                                            "confidence", item.confidence(),
                                            "versionScope", item.versionScope(),
                                            "staleAfter", item.staleAfter()
                                    )).toList()
                            )))
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Dry-run promotion by running evaluation only and returning promotability diagnostics.
     */
    public Promise<HttpResponse> dryRunPromotion(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String deltaId = deltaId(request);
        if (deltaId == null || deltaId.isBlank()) {
            return Promise.of(badRequest("deltaId is required"));
        }

        return approvalService.checkAccess(tenantId, "learning:evaluate")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to dry-run promotion"));
                    }

                    return learningDeltaRepository.findById(tenantId, deltaId)
                            .then(deltaOpt -> {
                                if (deltaOpt.isEmpty()) {
                                    return Promise.of(notFound("Learning delta not found"));
                                }
                                LearningDelta delta = deltaOpt.get();
                                if (!delta.tenantId().equals(tenantId)) {
                                    return Promise.of(forbidden("Cannot evaluate learning delta from another tenant"));
                                }

                                return promotionEngine.evaluate(delta)
                                        .map(result -> ok(Map.of(
                                                "deltaId", deltaId,
                                                "dryRun", true,
                                                "wouldPromote", result.allPassed(),
                                                "passRate", result.passRate(),
                                                "failedTests", result.failedTests(),
                                                "evaluationResult", result
                                        )));
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Process a single obsolescence event through the governed transition service.
     */
    public Promise<HttpResponse> processObsolescenceEvent(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        return approvalService.checkAccess(tenantId, "mastery:transition")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to process obsolescence events"));
                    }

                    return parseBodyAsync(request, ObsolescenceEvent.class)
                            .then(event -> {
                                if (!tenantId.equals(event.tenantId())) {
                                    return Promise.of(forbidden("Cannot process obsolescence event for another tenant"));
                                }

                                return obsolescenceTransitionService.processObsolescenceEvent(event)
                                    .map(result -> ok(Map.of(
                                        "masteryId", result.masteryId(),
                                        "previousState", result.previousState(),
                                        "newState", result.newState(),
                                        "success", result.success(),
                                        "transitionId", result.transitionId(),
                                        "errorMessage", result.errorMessage().orElse("")
                                    )));
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * List learning deltas with governance check.
     * Phase 7.2: GET /tenants/{tenantId}/learning-deltas
     */
    public Promise<HttpResponse> listLearningDeltas(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        // Governance check: tenant must have access to learning data
        return approvalService.checkAccess(tenantId, "learning:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to learning data"));
                    }

                    String agentId = request.getQueryParameter("agentId");
                    String limitStr = request.getQueryParameter("limit");
                    String offsetStr = request.getQueryParameter("offset");

                    int limit = limitStr != null ? Integer.parseInt(limitStr) : 100;
                    int offset = offsetStr != null ? Integer.parseInt(offsetStr) : 0;

                    // Query learning deltas from repository
                    return learningDeltaRepository.findByTenant(tenantId, agentId, limit, offset)
                            .map(deltas -> ok(deltas))
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Evaluate a learning delta with governance check.
     * Phase 7.2: POST /tenants/{tenantId}/learning-deltas/{deltaId}/evaluate
     */
    public Promise<HttpResponse> evaluateLearningDelta(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String deltaId = deltaId(request);
        if (deltaId == null || deltaId.isBlank()) {
            return Promise.of(badRequest("deltaId is required"));
        }

        // Governance check: tenant must have approval to evaluate learning
        return approvalService.checkAccess(tenantId, "learning:evaluate")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to evaluate learning"));
                    }

                    return learningDeltaRepository.findById(tenantId, deltaId)
                            .then(deltaOpt -> {
                                if (deltaOpt.isEmpty()) {
                                    return Promise.of(notFound("Learning delta not found"));
                                }
                                LearningDelta delta = deltaOpt.get();

                                // Validate tenant ownership
                                if (!delta.tenantId().equals(tenantId)) {
                                    return Promise.of(forbidden("Cannot evaluate learning delta from another tenant"));
                                }

                                // Use promotion engine to evaluate
                                return promotionEngine.evaluate(delta)
                                        .map(result -> ok(java.util.Map.of(
                                                "deltaId", deltaId,
                                                "evaluationResult", result
                                        )))
                                        .whenException(e -> Promise.of(internalError(e)));
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Promote a learning delta with governance check.
     * Phase 7.2: POST /tenants/{tenantId}/learning-deltas/{deltaId}/promote
     */
    public Promise<HttpResponse> promoteLearningDelta(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String deltaId = deltaId(request);
        if (deltaId == null || deltaId.isBlank()) {
            return Promise.of(badRequest("deltaId is required"));
        }

        // Governance check: tenant must have approval to promote learning (dangerous operation)
        return approvalService.checkAccess(tenantId, "learning:promote")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to promote learning - requires governance role"));
                    }

                    return learningDeltaRepository.findById(tenantId, deltaId)
                            .then(deltaOpt -> {
                                if (deltaOpt.isEmpty()) {
                                    return Promise.of(notFound("Learning delta not found"));
                                }
                                LearningDelta delta = deltaOpt.get();

                                // Validate tenant ownership
                                if (!delta.tenantId().equals(tenantId)) {
                                    return Promise.of(forbidden("Cannot promote learning delta from another tenant"));
                                }

                                // Evaluate first, then promote with the result
                                return promotionEngine.evaluate(delta)
                                        .then(evaluationResult -> promotionEngine.promote(delta, evaluationResult, tenantId))
                                        .map(result -> {
                                            if (result.success()) {
                                                return ok(java.util.Map.of(
                                                        "deltaId", deltaId,
                                                        "promotionResult", result,
                                                        "message", "Learning delta promoted successfully"
                                                ));
                                            } else {
                                                return ok(java.util.Map.of(
                                                        "deltaId", deltaId,
                                                        "promotionResult", result,
                                                        "message", "Learning delta promotion failed: " + result.errorMessage().orElse("Unknown error")
                                                ));
                                            }
                                        })
                                        .whenException(e -> Promise.of(internalError(e)));
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Phase 9 FIX: Approve a learning delta without full evaluation (human-approved path).
     * POST /api/v1/mastery/learning-deltas/{deltaId}/approve
     */
    public Promise<HttpResponse> approveLearningDelta(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String deltaId = deltaId(request);
        if (deltaId == null || deltaId.isBlank()) {
            return Promise.of(badRequest("deltaId is required"));
        }

        // Governance check: tenant must have approval to approve learning
        return approvalService.checkAccess(tenantId, "learning:approve")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to approve learning"));
                    }

                    return learningDeltaRepository.findById(tenantId, deltaId)
                            .then(deltaOpt -> {
                                if (deltaOpt.isEmpty()) {
                                    return Promise.of(notFound("Learning delta not found"));
                                }
                                LearningDelta delta = deltaOpt.get();

                                // Validate tenant ownership
                                if (!delta.tenantId().equals(tenantId)) {
                                    return Promise.of(forbidden("Cannot approve learning delta from another tenant"));
                                }

                                // Update delta state to APPROVED
                                return learningDeltaRepository.updateState(deltaId, com.ghatana.agent.learning.LearningDeltaState.APPROVED)
                                        .map(updated -> ok(java.util.Map.of(
                                                "deltaId", deltaId,
                                                "state", "APPROVED"
                                        )))
                                        .whenException(e -> Promise.of(internalError(e)));
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Phase 9 FIX: Reject a learning delta.
     * POST /api/v1/mastery/learning-deltas/{deltaId}/reject
     */
    public Promise<HttpResponse> rejectLearningDelta(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String deltaId = deltaId(request);
        if (deltaId == null || deltaId.isBlank()) {
            return Promise.of(badRequest("deltaId is required"));
        }

        // Governance check: tenant must have approval to reject learning
        return approvalService.checkAccess(tenantId, "learning:reject")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to reject learning"));
                    }

                    return learningDeltaRepository.findById(tenantId, deltaId)
                            .then(deltaOpt -> {
                                if (deltaOpt.isEmpty()) {
                                    return Promise.of(notFound("Learning delta not found"));
                                }
                                LearningDelta delta = deltaOpt.get();

                                // Validate tenant ownership
                                if (!delta.tenantId().equals(tenantId)) {
                                    return Promise.of(forbidden("Cannot reject learning delta from another tenant"));
                                }

                                // Update delta state to REJECTED
                                return learningDeltaRepository.updateState(deltaId, com.ghatana.agent.learning.LearningDeltaState.REJECTED)
                                        .map(updated -> ok(java.util.Map.of(
                                                "deltaId", deltaId,
                                                "state", "REJECTED"
                                        )))
                                        .whenException(e -> Promise.of(internalError(e)));
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Phase 9 FIX: Mark a mastery item as MAINTENANCE_ONLY.
     * POST /api/v1/mastery/{masteryId}/mark-maintenance-only
     */
    public Promise<HttpResponse> markMaintenanceOnly(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String masteryId = masteryId(request);
        if (masteryId == null || masteryId.isBlank()) {
            return Promise.of(badRequest("masteryId is required"));
        }

        // Governance check: tenant must have approval to transition mastery states
        return approvalService.checkAccess(tenantId, "mastery:transition")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to transition mastery state"));
                    }

                    return masteryRegistry.getById(tenantId, masteryId)
                            .then(itemOpt -> {
                                if (itemOpt.isEmpty()) {
                                    return Promise.of(notFound("Mastery item not found"));
                                }
                                MasteryItem item = itemOpt.get();

                                // Validate tenant ownership
                                if (!item.tenantId().equals(tenantId)) {
                                    return Promise.of(forbidden("Cannot transition mastery item from another tenant"));
                                }

                                // Create transition to MAINTENANCE_ONLY
                                MasteryTransition transition = new MasteryTransition(
                                        java.util.UUID.randomUUID().toString(),
                                        tenantId,
                                        masteryId,
                                        item.agentId(),
                                        item.agentReleaseId(),
                                        item.skillId(),
                                        item.state(),
                                        com.ghatana.agent.mastery.MasteryState.MAINTENANCE_ONLY,
                                        "Marked as maintenance-only via API",
                                        "api",
                                        java.time.Instant.now(),
                                        Map.of(),
                                        Map.of("source", "api")
                                );

                                return masteryRegistry.transition(transition)
                                        .map(result -> ok(java.util.Map.of(
                                                "masteryId", masteryId,
                                                "previousState", result.previousState(),
                                                "newState", result.newState(),
                                                "success", result.success(),
                                                "transitionId", result.transitionId()
                                        )))
                                        .whenException(e -> Promise.of(internalError(e)));
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Phase 9 FIX: Mark a mastery item as OBSOLETE.
     * POST /api/v1/mastery/{masteryId}/mark-obsolete
     */
    public Promise<HttpResponse> markObsolete(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String masteryId = masteryId(request);
        if (masteryId == null || masteryId.isBlank()) {
            return Promise.of(badRequest("masteryId is required"));
        }

        // Governance check: tenant must have approval to transition mastery states
        return approvalService.checkAccess(tenantId, "mastery:transition")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to transition mastery state"));
                    }

                    return masteryRegistry.getById(tenantId, masteryId)
                            .then(itemOpt -> {
                                if (itemOpt.isEmpty()) {
                                    return Promise.of(notFound("Mastery item not found"));
                                }
                                MasteryItem item = itemOpt.get();

                                // Validate tenant ownership
                                if (!item.tenantId().equals(tenantId)) {
                                    return Promise.of(forbidden("Cannot transition mastery item from another tenant"));
                                }

                                // Create transition to OBSOLETE
                                MasteryTransition transition = new MasteryTransition(
                                        java.util.UUID.randomUUID().toString(),
                                        tenantId,
                                        masteryId,
                                        item.agentId(),
                                        item.agentReleaseId(),
                                        item.skillId(),
                                        item.state(),
                                        com.ghatana.agent.mastery.MasteryState.OBSOLETE,
                                        "Marked as obsolete via API",
                                        "api",
                                        java.time.Instant.now(),
                                        Map.of(),
                                        Map.of("source", "api")
                                );

                                return masteryRegistry.transition(transition)
                                        .map(result -> ok(java.util.Map.of(
                                                "masteryId", masteryId,
                                                "previousState", result.previousState(),
                                                "newState", result.newState(),
                                                "success", result.success(),
                                                "transitionId", result.transitionId()
                                        )))
                                        .whenException(e -> Promise.of(internalError(e)));
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Phase 9 FIX: Quarantine a mastery item.
     * POST /api/v1/mastery/{masteryId}/quarantine
     */
    public Promise<HttpResponse> quarantine(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String masteryId = masteryId(request);
        if (masteryId == null || masteryId.isBlank()) {
            return Promise.of(badRequest("masteryId is required"));
        }

        // Governance check: tenant must have approval to transition mastery states
        return approvalService.checkAccess(tenantId, "mastery:transition")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to transition mastery state"));
                    }

                    return masteryRegistry.getById(tenantId, masteryId)
                            .then(itemOpt -> {
                                if (itemOpt.isEmpty()) {
                                    return Promise.of(notFound("Mastery item not found"));
                                }
                                MasteryItem item = itemOpt.get();

                                // Validate tenant ownership
                                if (!item.tenantId().equals(tenantId)) {
                                    return Promise.of(forbidden("Cannot transition mastery item from another tenant"));
                                }

                                // Create transition to QUARANTINED
                                MasteryTransition transition = new MasteryTransition(
                                        java.util.UUID.randomUUID().toString(),
                                        tenantId,
                                        masteryId,
                                        item.agentId(),
                                        item.agentReleaseId(),
                                        item.skillId(),
                                        item.state(),
                                        com.ghatana.agent.mastery.MasteryState.QUARANTINED,
                                        "Quarantined via API",
                                        "api",
                                        java.time.Instant.now(),
                                        Map.of(),
                                        Map.of("source", "api")
                                );

                                return masteryRegistry.transition(transition)
                                        .map(result -> ok(java.util.Map.of(
                                                "masteryId", masteryId,
                                                "previousState", result.previousState(),
                                                "newState", result.newState(),
                                                "success", result.success(),
                                                "transitionId", result.transitionId()
                                        )))
                                        .whenException(e -> Promise.of(internalError(e)));
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Phase 9 FIX: Retire a mastery item.
     * POST /api/v1/mastery/{masteryId}/retire
     */
    public Promise<HttpResponse> retire(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String masteryId = masteryId(request);
        if (masteryId == null || masteryId.isBlank()) {
            return Promise.of(badRequest("masteryId is required"));
        }

        // Governance check: tenant must have approval to transition mastery states
        return approvalService.checkAccess(tenantId, "mastery:transition")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to transition mastery state"));
                    }

                    return masteryRegistry.getById(tenantId, masteryId)
                            .then(itemOpt -> {
                                if (itemOpt.isEmpty()) {
                                    return Promise.of(notFound("Mastery item not found"));
                                }
                                MasteryItem item = itemOpt.get();

                                // Validate tenant ownership
                                if (!item.tenantId().equals(tenantId)) {
                                    return Promise.of(forbidden("Cannot transition mastery item from another tenant"));
                                }

                                // Create transition to RETIRED
                                MasteryTransition transition = new MasteryTransition(
                                        java.util.UUID.randomUUID().toString(),
                                        tenantId,
                                        masteryId,
                                        item.agentId(),
                                        item.agentReleaseId(),
                                        item.skillId(),
                                        item.state(),
                                        com.ghatana.agent.mastery.MasteryState.RETIRED,
                                        "Retired via API",
                                        "api",
                                        java.time.Instant.now(),
                                        Map.of(),
                                        Map.of("source", "api")
                                );

                                return masteryRegistry.transition(transition)
                                        .map(result -> ok(java.util.Map.of(
                                                "masteryId", masteryId,
                                                "previousState", result.previousState(),
                                                "newState", result.newState(),
                                                "success", result.success(),
                                                "transitionId", result.transitionId()
                                        )))
                                        .whenException(e -> Promise.of(internalError(e)));
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    private static boolean isBreakGlassRequest(@NotNull HttpRequest request) {
        String flag = request.getQueryParameter("breakGlass");
        return flag != null && Boolean.parseBoolean(flag);
    }

    private static String masteryId(@NotNull HttpRequest request) {
        return firstNonBlank(pathParameter(request, "masteryId"), extractGroup(request.getPath(), MASTERY_ITEM_PATH));
    }

    private static String skillId(@NotNull HttpRequest request) {
        return firstNonBlank(pathParameter(request, "skillId"), extractGroup(request.getPath(), SKILL_EVAL_PATH));
    }

    private static String deltaId(@NotNull HttpRequest request) {
        return firstNonBlank(pathParameter(request, "deltaId"), extractGroup(request.getPath(), LEARNING_DELTA_PATH));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static String extractGroup(String path, Pattern pattern) {
        Matcher matcher = pattern.matcher(path);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static String pathParameter(@NotNull HttpRequest request, @NotNull String name) {
        try {
            return request.getPathParameter(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
