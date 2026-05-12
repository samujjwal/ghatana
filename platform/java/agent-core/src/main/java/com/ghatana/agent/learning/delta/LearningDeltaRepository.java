/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning.delta;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Repository for learning deltas.
 *
 * @doc.type interface
 * @doc.purpose Repository for learning delta persistence
 * @doc.layer agent-core
 * @doc.pattern Repository
 */
public interface LearningDeltaRepository {

    /**
     * Saves a learning delta.
     *
     * @param delta learning delta to save
     * @return promise of saved delta
     */
    @NotNull
    Promise<LearningDelta> save(@NotNull LearningDelta delta);

    /**
     * Finds a learning delta by ID.
     *
     * @param deltaId delta identifier
     * @return promise of optional delta
     */
    @NotNull
    Promise<Optional<LearningDelta>> findById(@NotNull String deltaId);

    /**
     * Finds pending learning deltas for an agent.
     *
     * @param agentId agent identifier
     * @return promise of list of pending deltas
     */
    @NotNull
    Promise<List<LearningDelta>> findPending(@NotNull String agentId);

    /**
     * Transitions a learning delta to a new state.
     *
     * @param deltaId delta identifier
     * @param state new state
     * @return promise of updated delta
     */
    @NotNull
    Promise<LearningDelta> transition(@NotNull String deltaId, @NotNull LearningDeltaState state);
}
