/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
     * Finds learning deltas by tenant ID.
     *
     * @param tenantId tenant identifier
     * @param agentId optional agent identifier
     * @param limit maximum number of results
     * @param offset number of results to skip
     * @return promise of list of learning deltas
     */
    @NotNull
    Promise<List<LearningDelta>> findByTenant(@NotNull String tenantId, @Nullable String agentId, @Nullable Integer limit, @Nullable Integer offset);

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
     * Updates the state of a learning delta to REJECTED with a reason.
     * Convenience alias for {@link #updateState(String, LearningDeltaState, String)} for
     * explicit rejection semantics.
     *
     * @param deltaId         delta identifier
     * @param newState        target state (typically REJECTED)
     * @param rejectionReason human-readable rejection reason
     * @return promise of updated delta
     */
    @NotNull
    Promise<LearningDelta> updateStateWithRejection(
            @NotNull String deltaId,
            @NotNull LearningDeltaState newState,
            @NotNull String rejectionReason);

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

    /**
     * Appends an evaluation result to a learning delta.
     *
     * @param deltaId delta identifier
     * @param evaluationRunId evaluation run identifier
     * @param outcome evaluation outcome (PASSED/FAILED)
     * @param metrics evaluation metrics
     * @return promise of updated delta
     */
    @NotNull
    Promise<LearningDelta> appendEvaluationResult(
            @NotNull String deltaId,
            @NotNull String evaluationRunId,
            @NotNull String outcome,
            @NotNull java.util.Map<String, Object> metrics);

    /**
     * Appends a promotion result to a learning delta.
     *
     * @param deltaId delta identifier
     * @param promotionId promotion identifier
     * @param outcome promotion outcome (PROMOTED/FAILED)
     * @param reason promotion reason or failure explanation
     * @return promise of updated delta
     */
    @NotNull
    Promise<LearningDelta> appendPromotionResult(
            @NotNull String deltaId,
            @NotNull String promotionId,
            @NotNull String outcome,
            @Nullable String reason);
}
