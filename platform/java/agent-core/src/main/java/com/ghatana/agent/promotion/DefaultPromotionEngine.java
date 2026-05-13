/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.evaluation.EvaluationResult;
import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaRepository;
import com.ghatana.agent.learning.LearningDeltaState;
import com.ghatana.agent.mastery.ApplicabilityScope;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryScore;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.MasteryTransition;
import com.ghatana.agent.mastery.VersionScope;
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

    private final MasteryRegistry masteryRegistry;
    private final LearningDeltaRepository deltaRepository;
    private PromotionPolicy promotionPolicy;

    /**
     * Creates a default promotion engine.
     *
     * @param masteryRegistry mastery registry for state transitions
     * @param deltaRepository learning delta repository for state updates
     */
    public DefaultPromotionEngine(
            @NotNull MasteryRegistry masteryRegistry,
            @NotNull LearningDeltaRepository deltaRepository
    ) {
        this.masteryRegistry = masteryRegistry;
        this.deltaRepository = deltaRepository;
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
                                .then(saved -> applyTransition(delta, saved, targetState));
                    } else {
                        return applyTransition(delta, existingItem, targetState);
                    }
                });
    }

    /**
     * Applies a mastery state transition and, on success, marks the delta as PROMOTED.
     * Updates the MasteryItem with procedure/fact/negative IDs, evaluation refs, score, and version scope.
     */
    @NotNull
    private Promise<PromotionResult> applyTransition(
            @NotNull LearningDelta delta,
            @NotNull MasteryItem item,
            @NotNull MasteryState targetState
    ) {
        MasteryState previousState = item.state();

        Map<String, String> evidenceMap = new HashMap<>();
        List<String> refs = delta.evidenceRefs();
        for (int i = 0; i < refs.size(); i++) {
            evidenceMap.put("evidence_" + i, refs.get(i));
        }

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
                Map.copyOf(evidenceMap),
                Map.of("deltaId", delta.deltaId())
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

            return updateDeltaToPromoted(delta)
                    .map(ignored -> PromotionResult.success(
                            delta.deltaId(),
                            item.masteryId(),
                            previousState,
                            targetState
                    ));
        });
    }

    /**
     * Updates the MasteryItem with procedure/fact/negative IDs, evaluation refs, score, and version scope from the LearningDelta.
     */
    @NotNull
    private Promise<MasteryItem> updateMasteryItemWithDelta(
            @NotNull LearningDelta delta,
            @NotNull MasteryItem item) {
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
        
        // Update score based on delta's confidence after
        MasteryScore updatedScore = MasteryScore.correctnessOnly(delta.confidenceAfter());
        
        // Update version scope if provided in delta metadata
        VersionScope updatedVersionScope = item.versionScope();
        if (delta.labels().containsKey("versionScope")) {
            // Parse version scope from delta labels if available
            // For now, keep existing version scope
        }
        
        // Create updated MasteryItem
        MasteryItem updatedItem = new MasteryItem(
                item.masteryId(),
                item.tenantId(),
                item.skillId(),
                item.domain(),
                item.agentId(),
                item.agentReleaseId(),
                item.state(),
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
}
