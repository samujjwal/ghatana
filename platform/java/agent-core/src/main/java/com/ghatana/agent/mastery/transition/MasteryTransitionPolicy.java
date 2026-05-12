/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery.transition;

import com.ghatana.agent.mastery.MasteryState;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

/**
 * Policy for validating and governing mastery state transitions.
 *
 * @doc.type interface
 * @doc.purpose Policy for mastery state transitions
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public interface MasteryTransitionPolicy {

    /**
     * Validates whether a transition is allowed.
     *
     * @param fromState current state
     * @param toState target state
     * @param evidence evidence for the transition
     * @return validation result with optional error message
     */
    @NotNull
    TransitionValidation canTransition(
            @NotNull MasteryState fromState,
            @NotNull MasteryState toState,
            @NotNull Map<String, String> evidence
    );

    /**
     * Result of transition validation.
     */
    record TransitionValidation(boolean allowed, @NotNull Optional<String> errorMessage) {
        public static TransitionValidation success() {
            return new TransitionValidation(true, Optional.empty());
        }

        public static TransitionValidation denied(@NotNull String reason) {
            return new TransitionValidation(false, Optional.of(reason));
        }
    }
}
