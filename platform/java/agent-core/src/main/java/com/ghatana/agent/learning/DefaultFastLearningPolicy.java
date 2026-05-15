/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryState;
import org.jetbrains.annotations.NotNull;

/**
 * Default implementation of FastLearningPolicy.
 * Phase 8 FIX: Default fast-learning policy with strict rules for unknown/new versions.
 *
 * <p>Fast-learning rules:
 * <ul>
 *   <li>Used for unknown/new versions</li>
 *   <li>Always human-gated or supervised</li>
 *   <li>Creates tentative deltas only</li>
 *   <li>Requires sandbox experiments</li>
 *   <li>Can reach OBSERVED or PRACTICED, not MASTERED, without evals</li>
 *   <li>Must store failures as negative knowledge</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Default implementation of FastLearningPolicy
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public final class DefaultFastLearningPolicy implements FastLearningPolicy {

    private boolean humanGated = true;
    private boolean sandboxRequired = true;

    @Override
    public boolean canEnterFastLearning(@NotNull MasteryItem item) {
        // Phase 8 FIX: Fast-learning is for unknown/new versions
        return item.state() == MasteryState.UNKNOWN || item.state() == MasteryState.OBSERVED;
    }

    @Override
    public boolean canCreateDelta(@NotNull MasteryItem item, @NotNull LearningDelta delta) {
        // Phase 8 FIX: Always human-gated or supervised
        if (humanGated && !delta.requiresHumanReview()) {
            return false;
        }

        // Phase 8 FIX: Creates tentative deltas only
        if (delta.state() != LearningDeltaState.PROPOSED && delta.state() != LearningDeltaState.PENDING_HUMAN_REVIEW) {
            return false;
        }

        // Phase 8 FIX: Requires sandbox experiments for unknown versions
        if (sandboxRequired && !delta.labels().containsKey("sandboxExperimentId")) {
            return false;
        }

        return true;
    }

    @Override
    @NotNull
    public MasteryState maxStateWithoutEvaluation(@NotNull MasteryItem item) {
        // Phase 8 FIX: Can reach OBSERVED or PRACTICED, not MASTERED, without evals
        if (item.state() == MasteryState.UNKNOWN) {
            return MasteryState.OBSERVED;
        }
        if (item.state() == MasteryState.OBSERVED) {
            return MasteryState.PRACTICED;
        }
        return MasteryState.PRACTICED; // Max is PRACTICED without full evaluation
    }

    @Override
    @NotNull
    public ValidationResult validateFailureStorage(@NotNull MasteryItem item, @NotNull String failureDescription) {
        // Phase 8 FIX: Must store failures as negative knowledge
        if (failureDescription == null || failureDescription.isBlank()) {
            return ValidationResult.denied("Failure description must not be blank");
        }

        // Check if the item is in fast-learning mode
        if (!canEnterFastLearning(item)) {
            return ValidationResult.denied("Item is not in fast-learning mode");
        }

        return ValidationResult.allowed("Failure storage validated for negative knowledge");
    }

    /**
     * Sets whether human gating is enabled.
     * Phase 8 FIX: Always human-gated or supervised by default.
     *
     * @param humanGated true if human gating is enabled
     */
    public void setHumanGated(boolean humanGated) {
        this.humanGated = humanGated;
    }

    /**
     * Sets whether sandbox experiments are required.
     * Phase 8 FIX: Requires sandbox experiments by default.
     *
     * @param sandboxRequired true if sandbox experiments are required
     */
    public void setSandboxRequired(boolean sandboxRequired) {
        this.sandboxRequired = sandboxRequired;
    }
}
