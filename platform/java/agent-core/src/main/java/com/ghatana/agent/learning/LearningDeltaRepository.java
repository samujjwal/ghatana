/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing learning deltas.
 *
 * @doc.type interface
 * @doc.purpose Repository for managing learning deltas
 * @doc.layer agent-core
 * @doc.pattern Repository
 */
public interface LearningDeltaRepository {

    /**
     * Saves a learning delta.
     *
     * @param delta learning delta to save
     * @return promise of saved learning delta
     */
    @NotNull
    Promise<LearningDelta> save(@NotNull LearningDelta delta);

    /**
     * Finds a learning delta by ID.
     *
     * @param deltaId delta identifier
     * @return promise of optional learning delta
     */
    @NotNull
    Promise<Optional<LearningDelta>> findById(@NotNull String deltaId);

    /**
     * Finds learning deltas by agent ID.
     *
     * @param agentId agent identifier
     * @return promise of list of learning deltas
     */
    @NotNull
    Promise<List<LearningDelta>> findByAgentId(@NotNull String agentId);

    /**
     * Finds learning deltas by skill ID.
     *
     * @param skillId skill identifier
     * @return promise of list of learning deltas
     */
    @NotNull
    Promise<List<LearningDelta>> findBySkillId(@NotNull String skillId);

    /**
     * Finds learning deltas by state.
     *
     * @param state learning delta state
     * @return promise of list of learning deltas
     */
    @NotNull
    Promise<List<LearningDelta>> findByState(@NotNull LearningDeltaState state);

    /**
     * Finds learning deltas that are pending evaluation.
     *
     * @return promise of list of learning deltas
     */
    @NotNull
    Promise<List<LearningDelta>> findPendingEvaluation();

    /**
     * Finds learning deltas that are promotable.
     *
     * @return promise of list of learning deltas
     */
    @NotNull
    Promise<List<LearningDelta>> findPromotable();

    /**
     * Finds learning deltas that are obsolete.
     *
     * @param before timestamp before which deltas are considered obsolete
     * @return promise of list of learning deltas
     */
    @NotNull
    Promise<List<LearningDelta>> findObsolete(@NotNull Instant before);

    /**
     * Updates the state of a learning delta.
     *
     * @param deltaId delta identifier
     * @param newState new state
     * @return promise of updated learning delta
     */
    @NotNull
    Promise<LearningDelta> updateState(@NotNull String deltaId, @NotNull LearningDeltaState newState);

    /**
     * Updates the state of a learning delta with a rejection reason.
     *
     * @param deltaId delta identifier
     * @param newState new state
     * @param rejectionReason rejection reason
     * @return promise of updated learning delta
     */
    @NotNull
    Promise<LearningDelta> updateState(@NotNull String deltaId, @NotNull LearningDeltaState newState, @NotNull String rejectionReason);

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
