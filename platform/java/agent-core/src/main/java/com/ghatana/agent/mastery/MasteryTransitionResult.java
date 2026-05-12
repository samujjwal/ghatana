/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Result of a mastery state transition operation.
 *
 * @doc.type record
 * @doc.purpose Result of mastery state transition
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record MasteryTransitionResult(
        @NotNull String masteryId,
        @NotNull MasteryState previousState,
        @NotNull MasteryState newState,
        boolean success,
        @NotNull String transitionId,
        @NotNull Optional<String> errorMessage
) {
    public MasteryTransitionResult {
        if (success && errorMessage.isPresent()) {
            throw new IllegalArgumentException("errorMessage must be empty when success is true");
        }
    }

    /**
     * Creates a successful transition result.
     *
     * @param masteryId mastery item identifier
     * @param previousState previous state
     * @param newState new state
     * @param transitionId transition identifier
     * @return successful transition result
     */
    @NotNull
    public static MasteryTransitionResult success(
            @NotNull String masteryId,
            @NotNull MasteryState previousState,
            @NotNull MasteryState newState,
            @NotNull String transitionId
    ) {
        return new MasteryTransitionResult(masteryId, previousState, newState, true, transitionId, Optional.empty());
    }

    /**
     * Creates a failed transition result.
     *
     * @param masteryId mastery item identifier
     * @param previousState previous state (unchanged)
     * @param errorMessage error message explaining the failure
     * @return failed transition result
     */
    @NotNull
    public static MasteryTransitionResult failure(
            @NotNull String masteryId,
            @NotNull MasteryState previousState,
            @NotNull String errorMessage
    ) {
        return new MasteryTransitionResult(masteryId, previousState, previousState, false, "", Optional.of(errorMessage));
    }
}
