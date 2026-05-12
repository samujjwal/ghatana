/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing mastery state transitions.
 *
 * <p>Transitions are append-only - once created, they cannot be modified or deleted.
 * This ensures a complete audit trail of all state changes.
 *
 * @doc.type interface
 * @doc.purpose Repository for mastery transitions (append-only)
 * @doc.layer agent-core
 * @doc.pattern Repository
 */
public interface MasteryTransitionRepository {

    /**
     * Appends a new transition to the transition log.
     * This is the only write operation - transitions cannot be updated or deleted.
     *
     * @param transition transition to append
     * @return promise of the appended transition
     */
    @NotNull
    Promise<MasteryTransition> append(@NotNull MasteryTransition transition);

    /**
     * Finds a transition by its ID.
     *
     * @param transitionId transition ID
     * @return promise of the transition, or empty if not found
     */
    @NotNull
    Promise<Optional<MasteryTransition>> findById(@NotNull String transitionId);

    /**
     * Finds all transitions for a specific mastery item, ordered by timestamp.
     *
     * @param masteryId mastery item ID
     * @return promise of list of transitions in chronological order
     */
    @NotNull
    Promise<List<MasteryTransition>> findByMasteryId(@NotNull String masteryId);

    /**
     * Finds all transitions initiated by a specific user or system.
     *
     * @param initiatedBy initiator ID
     * @return promise of list of transitions
     */
    @NotNull
    Promise<List<MasteryTransition>> findByInitiatedBy(@NotNull String initiatedBy);

    /**
     * Finds all transitions within a time range.
     *
     * @param from start of time range
     * @param to end of time range
     * @return promise of list of transitions
     */
    @NotNull
    Promise<List<MasteryTransition>> findByTimeRange(@NotNull java.time.Instant from, @NotNull java.time.Instant to);

    /**
     * Finds the most recent transition for a mastery item.
     *
     * @param masteryId mastery item ID
     * @return promise of the most recent transition, or empty if none exist
     */
    @NotNull
    Promise<Optional<MasteryTransition>> findLatestByMasteryId(@NotNull String masteryId);
}
