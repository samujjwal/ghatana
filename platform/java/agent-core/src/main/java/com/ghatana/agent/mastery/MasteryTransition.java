/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a transition of a mastery item from one state to another.
 *
 * <p>Transitions are append-only and include the reason, evidence references,
 * and metadata for auditability.
 *
 * @doc.type record
 * @doc.purpose Mastery state transition record
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record MasteryTransition(
        @NotNull String transitionId,
        @NotNull String masteryId,
        @NotNull MasteryState fromState,
        @NotNull MasteryState toState,
        @NotNull String reason,
        @NotNull String initiatedBy,
        @NotNull Instant transitionedAt,
        @NotNull Map<String, String> evidenceRefs,
        @NotNull Map<String, String> metadata
) {
    public MasteryTransition {
        Objects.requireNonNull(transitionId, "transitionId must not be null");
        Objects.requireNonNull(masteryId, "masteryId must not be null");
        Objects.requireNonNull(fromState, "fromState must not be null");
        Objects.requireNonNull(toState, "toState must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(initiatedBy, "initiatedBy must not be null");
        Objects.requireNonNull(transitionedAt, "transitionedAt must not be null");
        Objects.requireNonNull(evidenceRefs, "evidenceRefs must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        evidenceRefs = Map.copyOf(evidenceRefs);
        metadata = Map.copyOf(metadata);
    }

    /**
     * Creates a manual transition initiated by a user.
     *
     * @param masteryId mastery item identifier
     * @param fromState current state
     * @param toState target state
     * @param reason transition reason
     * @param initiatedBy user who initiated the transition
     * @return mastery transition
     */
    @NotNull
    public static MasteryTransition manual(
            @NotNull String masteryId,
            @NotNull MasteryState fromState,
            @NotNull MasteryState toState,
            @NotNull String reason,
            @NotNull String initiatedBy
    ) {
        return new MasteryTransition(
                java.util.UUID.randomUUID().toString(),
                masteryId,
                fromState,
                toState,
                reason,
                initiatedBy,
                Instant.now(),
                Map.of(),
                Map.of()
        );
    }

    /**
     * Creates an automatic transition initiated by the system.
     *
     * @param masteryId mastery item identifier
     * @param fromState current state
     * @param toState target state
     * @param reason transition reason
     * @return mastery transition
     */
    @NotNull
    public static MasteryTransition automatic(
            @NotNull String masteryId,
            @NotNull MasteryState fromState,
            @NotNull MasteryState toState,
            @NotNull String reason
    ) {
        return new MasteryTransition(
                java.util.UUID.randomUUID().toString(),
                masteryId,
                fromState,
                toState,
                reason,
                "system",
                Instant.now(),
                Map.of(),
                Map.of()
        );
    }
}
