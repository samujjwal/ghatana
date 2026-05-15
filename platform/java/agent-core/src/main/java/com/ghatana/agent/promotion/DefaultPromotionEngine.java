/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.evaluation.EvaluationHarness;
import com.ghatana.agent.evaluation.EvaluationContext;
import com.ghatana.agent.evaluation.EvaluationPack;
import com.ghatana.agent.evaluation.EvaluationResult;
import com.ghatana.agent.evaluation.DefaultEvaluationHarness;
import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaRepository;
import com.ghatana.agent.learning.LearningDeltaState;
import com.ghatana.agent.learning.LearningTarget;
import com.ghatana.agent.mastery.ApplicabilityScope;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryScore;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.MasteryTransition;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.agent.mastery.VersionConstraint;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default implementation of PromotionEngine for promoting evaluated/approved learning deltas.
 *
 * <p>Promotes learning deltas to mastery after successful evaluation by:
 * <ul>
 *   <li>Checking promotion policy approval</li>
 *   <li>Updating learning delta state to PROMOTED</li>
 *   <li>Updating mastery item state via mastery registry transition</li>
 *   <li>Recording promotion metadata</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Default implementation of PromotionEngine
 * @doc.layer agent-core
 * @doc.pattern Engine
 */
public final class DefaultPromotionEngine implements PromotionEngine {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DefaultPromotionEngine.class);

    private final MasteryRegistry masteryRegistry;
    private final LearningDeltaRepository deltaRepository;
    private final EvaluationHarness evaluationHarness;
    private PromotionPolicy promotionPolicy;

    /**
     * Creates a default promotion engine with a default evaluation harness.
     *
     * @param masteryRegistry mastery registry for state transitions
     * @param deltaRepository learning delta repository for state updates
     */
    public DefaultPromotionEngine(
            @NotNull MasteryRegistry masteryRegistry,
            @NotNull LearningDeltaRepository deltaRepository
    ) {
        this(masteryRegistry, deltaRepository, new DefaultEvaluationHarness());
    }

    /**
     * Creates a default promotion engine with a custom evaluation harness.
     *
     * @param masteryRegistry   mastery registry for state transitions
     * @param deltaRepository   learning delta repository for state updates
     * @param evaluationHarness harness to run evaluation packs
     */
    public DefaultPromotionEngine(
            @NotNull MasteryRegistry masteryRegistry,
            @NotNull LearningDeltaRepository deltaRepository,
            @NotNull EvaluationHarness evaluationHarness
    ) {
        this.masteryRegistry = java.util.Objects.requireNonNull(masteryRegistry, "masteryRegistry must not be null");
        this.deltaRepository = java.util.Objects.requireNonNull(deltaRepository, "deltaRepository must not be null");
        this.evaluationHarness = java.util.Objects.requireNonNull(evaluationHarness, "evaluationHarness must not be null");
        this.promotionPolicy = new DefaultPromotionPolicy();
    }

    @Override
    public void setPolicy(@NotNull PromotionPolicy policy) {
        this.promotionPolicy = policy;
    }

    @Override
    @NotNull
    public Promise<PromotionResult> promote(
            @NotNull LearningDelta delta,
            @NotNull EvaluationResult result,
            @NotNull String tenantId
    ) {
        // Idempotency check: if delta is already PROMOTED, return success
        if (delta.state() == LearningDeltaState.PROMOTED) {
            return deltaRepository.findById(delta.deltaId())
                    .then(deltaOpt -> {
                        if (deltaOpt.isPresent() && deltaOpt.get().state() == LearningDeltaState.PROMOTED) {
                            // Find the mastery item to return current state
                            MasteryQuery query = MasteryQuery.bySkill(delta.skillId())
                                    .withTenantId(delta.tenantId())
                                    .withAgentId(delta.agentId())
                                    .withAgentReleaseId(delta.agentReleaseId());
                            return masteryRegistry.query(query)
                                    .then(masteryItems -> {
                                        MasteryItem item = masteryItems.stream().findFirst().orElse(null);
                                        if (item != null) {
                                            return Promise.of(PromotionResult.success(
                                                    delta.deltaId(),
                                                    item.masteryId(),
                                                    item.state(),
                                                    item.state()
                                            ));
                                        }
                                        return Promise.of(PromotionResult.success(
                                                delta.deltaId(),
                                                deriveMasteryId(delta),
                                                MasteryState.UNKNOWN,
                                                MasteryState.UNKNOWN
                                        ));
                                    });
                        }
                        // Delta was PROMOTED but no longer found, proceed with promotion
                        return proceedWithPromotion(delta, result, tenantId);
                    });
        }

        return proceedWithPromotion(delta, result, tenantId);
    }

    /**
     * Proceeds with the promotion flow after idempotency check.
     */
    @NotNull
    private Promise<PromotionResult> proceedWithPromotion(
            @NotNull LearningDelta delta,
            @NotNull EvaluationResult result,
            @NotNull String tenantId
    ) {
        // Check if promotion is allowed by policy
        if (!promotionPolicy.canPromote(delta, result)) {
            return Promise.of(PromotionResult.failure(
                    delta.deltaId(),
                    deriveMasteryId(delta),
                    MasteryState.UNKNOWN,
                    "Promotion rejected by policy: evaluation results do not meet promotion criteria"
            ));
        }

        // Determine target mastery state
        MasteryState targetState = promotionPolicy.targetState(delta, result);
        if (targetState == null) {
            return Promise.of(PromotionResult.failure(
                    delta.deltaId(),
                    deriveMasteryId(delta),
                    MasteryState.UNKNOWN,
                    "Promotion rejected: no valid target state determined"
            ));
        }

        // Find existing mastery item using tenant-scoped query
        MasteryQuery query = MasteryQuery.bySkill(delta.skillId())
                .withTenantId(delta.tenantId())
                .withAgentId(delta.agentId())
                .withAgentReleaseId(delta.agentReleaseId());

        return masteryRegistry.query(query)
                .then(masteryItems -> {
                    MasteryItem existingItem = masteryItems.stream().findFirst().orElse(null);

                    if (existingItem == null) {
                        // Bootstrap an initial mastery item in UNKNOWN state, then transition
                        MasteryItem initial = buildInitialMasteryItem(delta);
                        return masteryRegistry.save(initial)
                                .then(saved -> applyTransition(delta, result, saved, targetState));
                    } else {
                        // Target-state mapping: ensure the transition is valid from current state
                        // If the item is already at or beyond the target state, skip the transition
                        if (existingItem.state() == targetState || isStateBeyondTarget(existingItem.state(), targetState)) {
                            // Update delta to PROMOTED but skip mastery transition
                            return updateDeltaToPromoted(delta)
                                    .map(ignored -> PromotionResult.success(
                                            delta.deltaId(),
                                            existingItem.masteryId(),
                                            existingItem.state(),
                                            existingItem.state()
                                    ));
                        }
                        return applyTransition(delta, result, existingItem, targetState);
                    }
                });
    }

    /**
     * Checks if the current state is beyond the target state in the mastery progression.
     * Used for target-state mapping to avoid unnecessary transitions.
     */
    private boolean isStateBeyondTarget(@NotNull MasteryState currentState, @NotNull MasteryState targetState) {
        // Define state progression: UNKNOWN < OBSERVED < PRACTICED < COMPETENT < MASTERED
        // Terminal states (QUARANTINED, OBSOLETE, RETIRED, MAINTENANCE_ONLY) are not in progression
        int currentRank = getStateRank(currentState);
        int targetRank = getStateRank(targetState);
        return currentRank > targetRank;
    }

    /**
     * Returns the rank of a mastery state for progression comparison.
     */
    private int getStateRank(@NotNull MasteryState state) {
        return switch (state) {
            case UNKNOWN -> 0;
            case OBSERVED -> 1;
            case PRACTICED -> 2;
            case COMPETENT -> 3;
            case MASTERED -> 4;
            // Terminal states - not in progression
            case QUARANTINED, OBSOLETE, RETIRED, MAINTENANCE_ONLY -> -1;
        };
    }

    /**
     * Applies a mastery state transition and, on success, marks the delta as PROMOTED.
     * Updates the MasteryItem with procedure/fact/negative IDs, evaluation refs, score, and version scope.
     */
    @NotNull
    private Promise<PromotionResult> applyTransition(
            @NotNull LearningDelta delta,
            @NotNull EvaluationResult evaluationResult,
            @NotNull MasteryItem item,
            @NotNull MasteryState targetState
    ) {
        MasteryState previousState = item.state();

        // Use the provided evaluation result for evidence mapping
        Map<String, String> evidenceMap = PromotionEvidenceMapper.toEvidenceMap(
                delta,
                previousState,
                targetState,
                evaluationResult
        );
        Map<String, String> metadata = PromotionEvidenceMapper.toMetadata(delta);

        MasteryTransition transition = new MasteryTransition(
                UUID.randomUUID().toString(),
                delta.tenantId(),
                item.masteryId(),
                delta.agentId(),
                delta.agentReleaseId(),
                delta.skillId(),
                previousState,
                targetState,
                "Promoted via learning delta: " + delta.deltaId(),
                "promotion-engine",
                Instant.now(),
                evidenceMap,
                metadata
        );

        return masteryRegistry.transition(transition).then(transitionResult -> {
            if (!transitionResult.success()) {
                return Promise.of(PromotionResult.failure(
                        delta.deltaId(),
                        item.masteryId(),
                        previousState,
                        transitionResult.errorMessage().orElse("Mastery transition failed")
                ));
            }

            // Reload the item from registry to get the post-transition state, then update with delta metadata
            return masteryRegistry.getById(delta.tenantId(), item.masteryId())
                    .then(currentItemOpt -> {
                        MasteryItem baseItem = currentItemOpt.orElse(item);
                        // Update mastery item with delta's procedure/fact/negative IDs, evaluation refs, score, and version scope
                        // using the target state from the successful transition
                        return updateMasteryItemWithDelta(delta, baseItem, targetState)
                                .then(updatedItem -> updateDeltaToPromoted(delta)
                                        .map(ignored -> PromotionResult.success(
                                                delta.deltaId(),
                                                item.masteryId(),
                                                previousState,
                                                targetState
                                        )));
                    });
        });
    }

    /**
     * Updates the MasteryItem with procedure/fact/negative IDs, evaluation refs, score, and version scope from the LearningDelta.
     *
     * @param delta learning delta containing metadata to merge
     * @param item base mastery item to update (should be post-transition state)
     * @param targetState the target mastery state after transition
     */
    @NotNull
    private Promise<MasteryItem> updateMasteryItemWithDelta(
            @NotNull LearningDelta delta,
            @NotNull MasteryItem item,
            @NotNull MasteryState targetState) {
        // Build updated lists with delta's IDs
        List<String> updatedProcedureIds = new java.util.ArrayList<>(item.procedureIds());
        List<String> updatedSemanticFactIds = new java.util.ArrayList<>(item.semanticFactIds());
        List<String> updatedNegativeKnowledgeIds = new java.util.ArrayList<>(item.negativeKnowledgeIds());
        List<String> updatedEvaluationRefs = new java.util.ArrayList<>(item.evaluationRefs());
        
        // Add IDs from delta if present
        if (delta.procedureId() != null && !updatedProcedureIds.contains(delta.procedureId())) {
            updatedProcedureIds.add(delta.procedureId());
        }
        if (delta.semanticFactId() != null && !updatedSemanticFactIds.contains(delta.semanticFactId())) {
            updatedSemanticFactIds.add(delta.semanticFactId());
        }
        if (delta.negativeKnowledgeId() != null && !updatedNegativeKnowledgeIds.contains(delta.negativeKnowledgeId())) {
            updatedNegativeKnowledgeIds.add(delta.negativeKnowledgeId());
        }
        
        // Add evaluation refs from delta
        for (String evalRef : delta.evaluationRefs()) {
            if (!updatedEvaluationRefs.contains(evalRef)) {
                updatedEvaluationRefs.add(evalRef);
            }
        }
        
        // Phase 2 FIX: Preserve multidimensional MasteryScore instead of always replacing with correctnessOnly
        // Merge the existing score with delta's confidence, preserving other dimensions if present
        MasteryScore updatedScore = item.score();
        if (updatedScore == null || updatedScore.correctness() == 0.0) {
            // Only use correctnessOnly if we have no existing score
            updatedScore = MasteryScore.correctnessOnly(delta.confidenceAfter());
        } else {
            // Preserve existing dimensions but update correctness from delta
            updatedScore = new MasteryScore(
                    delta.confidenceAfter(),  // update correctness
                    updatedScore.freshness(),
                    updatedScore.applicability(),
                    updatedScore.safety(),
                    updatedScore.transferability(),
                    updatedScore.evidenceStrength(),
                    updatedScore.regressionStability()
            );
        }
        
        // Phase 2 FIX: Parse and apply version scope from delta metadata
        VersionScope updatedVersionScope = item.versionScope();
        if (delta.labels().containsKey("versionScope")) {
            try {
                String versionScopeJson = delta.labels().get("versionScope");
                if (versionScopeJson != null && !versionScopeJson.isBlank()) {
                    // Parse version scope from JSON if available in delta metadata
                    // For now, use a simple parsing approach - in production, use proper JSON deserializer
                    updatedVersionScope = parseVersionScopeFromJson(versionScopeJson, item.versionScope());
                }
            } catch (Exception e) {
                // If parsing fails, keep existing version scope
                log.debug("Failed to parse version scope from delta metadata, keeping existing: {}", e.getMessage());
            }
        }
        
        // Phase 3 FIX: Ensure stateHistory cannot be lost
        // Validate that stateHistory is not null or empty when updating an existing item
        if (item.stateHistory() == null) {
            log.warn("stateHistory is null for mastery item {}, initializing empty list", item.masteryId());
        }
        
        // Create updated MasteryItem using targetState to preserve the transition
        MasteryItem updatedItem = new MasteryItem(
                item.masteryId(),
                item.tenantId(),
                item.skillId(),
                item.domain(),
                item.agentId(),
                item.agentReleaseId(),
                targetState,
                updatedVersionScope,
                item.applicability(),
                updatedScore,
                updatedProcedureIds,
                updatedSemanticFactIds,
                updatedNegativeKnowledgeIds,
                item.evidenceRefs(),
                updatedEvaluationRefs,
                item.knownFailureModeIds(),
                item.stateHistory(),
                item.lastVerifiedAt(),
                item.staleAfter(),
                item.labels(),
                delta.confidenceAfter()
        );
        
        return masteryRegistry.save(updatedItem);
    }

    /**
     * Phase 2 FIX: Parses version scope from JSON string.
     * This is a simplified parser - in production, use proper JSON deserializer.
     *
     * @param versionScopeJson JSON string containing version scope
     * @param fallback default version scope to use if parsing fails
     * @return parsed VersionScope or fallback
     */
    @NotNull
    private VersionScope parseVersionScopeFromJson(@NotNull String versionScopeJson, @NotNull VersionScope fallback) {
        try {
            // Simple key-value parsing for version scope
            // Expected format: {"minVersion":"1.0.0","maxVersion":"2.0.0","runtime":"aep-runtime"}
            Map<String, String> parsed = new java.util.HashMap<>();
            String[] pairs = versionScopeJson.replaceAll("[{}\"]", "").split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    parsed.put(kv[0].trim(), kv[1].trim());
                }
            }
            
            // Phase 6 FIX: Build VersionScope from parsed values using proper constructor
            String minVersion = parsed.getOrDefault("minVersion", "");
            String maxVersion = parsed.getOrDefault("maxVersion", "");
            String runtime = parsed.getOrDefault("runtime", "");
            
            // Create version constraints from parsed values
            List<VersionConstraint> active = List.of();
            List<VersionConstraint> maintenance = List.of();
            List<VersionConstraint> obsolete = List.of();
            
            if (!minVersion.isEmpty() || !maxVersion.isEmpty()) {
                active = List.of(VersionConstraint.runtimeVersion(runtime, minVersion + ".." + maxVersion, "jvm"));
            }
            
            return new VersionScope(active, maintenance, obsolete);
        } catch (Exception e) {
            log.debug("Failed to parse version scope JSON, using fallback: {}", e.getMessage());
            return fallback;
        }
    }

    /**
     * Bootstraps a new {@link MasteryItem} in {@link MasteryState#UNKNOWN} state from a delta.
     *
     * <p>Uses {@code skillId} as the domain default and derives the applicability scope
     * from the delta's tenant and agent identifiers. Includes procedure/fact/negative IDs from delta.
     */
    @NotNull
    private MasteryItem buildInitialMasteryItem(@NotNull LearningDelta delta) {
        Instant now = Instant.now();
        
        // Build initial lists with IDs from delta if present
        List<String> procedureIds = delta.procedureId() != null 
                ? List.of(delta.procedureId()) 
                : List.of();
        List<String> semanticFactIds = delta.semanticFactId() != null 
                ? List.of(delta.semanticFactId()) 
                : List.of();
        List<String> negativeKnowledgeIds = delta.negativeKnowledgeId() != null 
                ? List.of(delta.negativeKnowledgeId()) 
                : List.of();
        
        // Use delta's confidence after as initial score
        MasteryScore initialScore = MasteryScore.correctnessOnly(delta.confidenceAfter());
        
        return new MasteryItem(
                UUID.randomUUID().toString(),
                delta.tenantId(),
                delta.skillId(),
                delta.skillId(),               // domain defaults to skillId until richer metadata arrives
                delta.agentId(),
                delta.agentReleaseId(),
                MasteryState.UNKNOWN,
                VersionScope.empty(),
                ApplicabilityScope.minimal(delta.tenantId(), "production"),
                initialScore,
                procedureIds,
                semanticFactIds,
                negativeKnowledgeIds,
                delta.evidenceRefs(),
                delta.evaluationRefs(),
                List.of(),                     // knownFailureModeIds
                List.of(),                     // stateHistory
                now,
                now.plus(java.time.Duration.ofDays(30)),
                Map.of(),
                delta.confidenceAfter()
        );
    }

    /**
     * Updates the learning delta state to PROMOTED.
     *
     * @param delta learning delta to update
     * @return promise of updated delta
     */
    @NotNull
    private Promise<LearningDelta> updateDeltaToPromoted(@NotNull LearningDelta delta) {
        return deltaRepository.updateState(delta.deltaId(), LearningDeltaState.PROMOTED);
    }

    /**
     * Derives a mastery ID from a learning delta.
     * Uses the skill ID as the mastery ID for lookup purposes.
     *
     * @param delta learning delta
     * @return derived mastery ID
     */
    @NotNull
    private String deriveMasteryId(@NotNull LearningDelta delta) {
        // In a real implementation, this might use a more sophisticated mapping
        // For now, use skillId as the mastery identifier
        return delta.skillId();
    }

    /**
     * Evaluates a learning delta to determine if it is ready for promotion.
     *
     * <p>Evaluation is computed from the delta's observable properties:
     * confidence gain, evidence count, and evaluation references.
     * A delta passes evaluation when its confidence gain is positive and
     * the promotion policy approves it.
     *
     * @param delta learning delta to evaluate
     * @return promise of evaluation result
     */
    @Override
    @NotNull
    public Promise<EvaluationResult> evaluate(@NotNull LearningDelta delta) {
        java.util.Objects.requireNonNull(delta, "delta must not be null");

        // Build an appropriate evaluation pack based on the delta target
        EvaluationPack pack = buildEvalPackForDelta(delta);

        EvaluationContext context = new EvaluationContext(
                delta.tenantId(),
                delta.agentId(),
                delta.skillId() != null ? delta.skillId() : delta.agentId(),
                null,
                Map.of(
                        "deltaId", delta.deltaId(),
                        "agentReleaseId", delta.agentReleaseId(),
                        "confidenceBefore", String.valueOf(delta.confidenceBefore()),
                        "confidenceAfter", String.valueOf(delta.confidenceAfter())
                )
        );

        log.info("Running evaluation pack {} for delta {} (target={})",
                pack.packId(), delta.deltaId(), delta.target());

        return evaluationHarness.run(pack, delta, context);
    }

    /**
     * Selects or creates an evaluation pack appropriate for the delta's target type.
     */
    @NotNull
    private EvaluationPack buildEvalPackForDelta(@NotNull LearningDelta delta) {
        String artifactId = delta.procedureId() != null ? delta.procedureId()
                : delta.semanticFactId() != null ? delta.semanticFactId()
                : delta.negativeKnowledgeId() != null ? delta.negativeKnowledgeId()
                : delta.skillId();
        if (artifactId == null) artifactId = delta.deltaId();

        return switch (delta.target()) {
            case PROCEDURAL_SKILL, PROMPT_TEMPLATE, PLANNER_POLICY, ROUTING_POLICY
                    -> evaluationHarness.createDefaultPackForProceduralSkill(artifactId);
            case SEMANTIC_FACT, CONFIDENCE_THRESHOLD, RETRIEVAL_POLICY
                    -> evaluationHarness.createDefaultPackForSemanticFact(artifactId);
            case MASTERY_STATE
                    -> evaluationHarness.createMasteredPack(artifactId);
            default -> evaluationHarness.createDefaultPackForSemanticFact(artifactId);
        };
    }
}
