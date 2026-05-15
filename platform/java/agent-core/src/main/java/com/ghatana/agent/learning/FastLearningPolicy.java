/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryState;
import org.jetbrains.annotations.NotNull;

/**
 * Policy for enforcing fast-learning behavior for unknown/new versions.
 * Phase 8 FIX: Policy for fast-learning scenarios with strict rules.
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
 * @doc.type interface
 * @doc.purpose Policy for enforcing fast-learning behavior
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public interface FastLearningPolicy {

    /**
     * Checks if a mastery item can enter fast-learning mode.
     *
     * @param item mastery item to check
     * @return true if fast-learning is allowed, false otherwise
     */
    boolean canEnterFastLearning(@NotNull MasteryItem item);

    /**
     * Checks if a learning delta can be created in fast-learning mode.
     *
     * @param item mastery item
     * @param delta learning delta to validate
     * @return true if delta creation is allowed, false otherwise
     */
    boolean canCreateDelta(@NotNull MasteryItem item, @NotNull LearningDelta delta);

    /**
     * Determines the maximum mastery state reachable without full evaluation in fast-learning mode.
     * Phase 8 FIX: Can reach OBSERVED or PRACTICED, not MASTERED, without evals.
     *
     * @param item mastery item
     * @return maximum mastery state without full evaluation
     */
    @NotNull
    MasteryState maxStateWithoutEvaluation(@NotNull MasteryItem item);

    /**
     * Validates that a failure is stored as negative knowledge.
     * Phase 8 FIX: Must store failures as negative knowledge in fast-learning mode.
     *
     * @param item mastery item
     * @param failureDescription description of the failure
     * @return validation result
     */
    @NotNull
    ValidationResult validateFailureStorage(@NotNull MasteryItem item, @NotNull String failureDescription);

    /**
     * Result of validating a failure storage operation.
     *
     * @doc.type record
     * @doc.purpose Result of validating failure storage
     * @doc.layer agent-core
     * @doc.pattern Record
     */
    record ValidationResult(
            boolean allowed,
            @NotNull String reason
    ) {
        /**
         * Creates an allowed validation result.
         *
         * @param reason validation reason
         * @return allowed validation result
         */
        @NotNull
        public static ValidationResult allowed(@NotNull String reason) {
            return new ValidationResult(true, reason);
        }

        /**
         * Creates a denied validation result.
         *
         * @param reason denial reason
         * @return denied validation result
         */
        @NotNull
        public static ValidationResult denied(@NotNull String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
