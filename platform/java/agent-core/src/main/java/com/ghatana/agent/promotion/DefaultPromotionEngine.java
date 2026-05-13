/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.evaluation.EvaluationResult;
import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaRepository;
import com.ghatana.agent.learning.LearningDeltaState;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryState;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

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

        // Derive mastery ID from delta
        String masteryId = deriveMasteryId(delta);

        // Find existing mastery item
        return masteryRegistry.findBySkill(delta.skillId(), null)
                .then(masteryOpt -> {
                    MasteryState previousState = masteryOpt.map(MasteryItem::state).orElse(MasteryState.UNKNOWN);

                    // Perform mastery state transition if mastery item exists
                    if (masteryOpt.isPresent()) {
                        MasteryItem item = masteryOpt.get();
                        
                        // Convert evidenceRefs (List<String>) to Map<String, String>
                        Map<String, String> evidenceMap = Map.of();
                        if (delta.evidenceRefs() != null && !delta.evidenceRefs().isEmpty()) {
                            evidenceMap = new java.util.HashMap<>();
                            for (int i = 0; i < delta.evidenceRefs().size(); i++) {
                                evidenceMap.put("evidence_" + i, delta.evidenceRefs().get(i));
                            }
                        }
                        
                        com.ghatana.agent.mastery.MasteryTransition transition =
                                new com.ghatana.agent.mastery.MasteryTransition(
                                        java.util.UUID.randomUUID().toString(),
                                        tenantId,
                                        item.masteryId(),
                                        delta.agentId(),
                                        delta.agentReleaseId(),
                                        delta.skillId(),
                                        item.state(),
                                        targetState,
                                        "Promoted via learning delta: " + delta.deltaId(),
                                        "promotion-engine",
                                        Instant.now(),
                                        evidenceMap,
                                        Map.of("deltaId", delta.deltaId(), "evaluatedBy", "promotion-engine")
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
                            // Update delta state to PROMOTED
                            return updateDeltaToPromoted(delta)
                                    .then(updatedDelta -> Promise.of(PromotionResult.success(
                                            delta.deltaId(),
                                            item.masteryId(),
                                            previousState,
                                            targetState
                                    )));
                        });
                    } else {
                        // No existing mastery item - this is an error condition
                        // Promotion requires an existing mastery item to transition
                        return Promise.of(PromotionResult.failure(
                                delta.deltaId(),
                                masteryId,
                                MasteryState.UNKNOWN,
                                "No mastery item found for skill: " + delta.skillId() + ". Promotion requires existing mastery item."
                        ));
                    }
                });
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
