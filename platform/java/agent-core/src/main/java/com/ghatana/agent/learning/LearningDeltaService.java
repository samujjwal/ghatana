/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Service for managing learning deltas through the full proposal→evaluation→approval pipeline.
 *
 * <p>A learning delta represents a proposed change to agent behaviour (semantic fact, procedural
 * skill, retrieval policy, etc.). The service enforces {@link LearningContract} governance on
 * proposal, drives evaluation via {@link LearningDeltaEvaluator}, persists state transitions, and
 * routes the delta to the appropriate next stage (auto-approve, human-review queue, or rejection).
 *
 * @doc.type interface
 * @doc.purpose Learning delta lifecycle management — proposal, evaluation, approval, rejection
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public interface LearningDeltaService {

    /**
     * Proposes a new learning delta and drives it through the evaluation pipeline.
     *
     * <p>Contract checks are enforced before the delta is persisted:
     * <ul>
     *   <li>{@link LearningContract#permits(LearningTarget)} — target must be allowed.</li>
     *   <li>Provenance requirement: if {@code contract.provenanceRequired()}, at least one evidence
     *       ref must be present.</li>
     *   <li>Promotion gate: if {@code contract.promotionRequired()}, the delta is not auto-promoted
     *       without evaluation passing.</li>
     * </ul>
     *
     * <p>On success the delta is persisted as {@link LearningDeltaState#PENDING_EVALUATION} and
     * evaluation is triggered immediately. The returned delta reflects the state after evaluation
     * ({@code EVALUATED} or {@code PENDING_HUMAN_REVIEW}).
     *
     * @param delta    the proposed learning delta (must be in {@link LearningDeltaState#PROPOSED})
     * @param contract the learning contract governing what this agent may learn
     * @return promise of the evaluated delta
     * @throws IllegalArgumentException if the contract forbids the delta's target or provenance is missing
     */
    @NotNull
    Promise<LearningDelta> propose(@NotNull LearningDelta delta, @NotNull LearningContract contract);

    /**
     * Runs the evaluation pipeline for a delta already persisted in the repository.
     *
     * @param deltaId delta identifier
     * @return promise of the structured evaluation result
     */
    @NotNull
    Promise<LearningDeltaEvaluator.EvaluationResult> evaluate(@NotNull String deltaId);

    /**
     * Approves a delta that is in {@link LearningDeltaState#PENDING_HUMAN_REVIEW}.
     *
     * <p>Transitions the delta to {@link LearningDeltaState#APPROVED} and records the approver.
     *
     * @param deltaId   delta identifier
     * @param approvedBy identity of the approver (user, system, workflow ID)
     * @return promise of the approved delta
     */
    @NotNull
    Promise<LearningDelta> approve(@NotNull String deltaId, @NotNull String approvedBy);

    /**
     * Rejects a delta with an explicit reason.
     *
     * <p>Transitions the delta to {@link LearningDeltaState#REJECTED} and records the reason.
     *
     * @param deltaId       delta identifier
     * @param reason        human-readable rejection reason
     * @param rejectedBy    identity of the rejector
     * @return promise of the rejected delta
     */
    @NotNull
    Promise<LearningDelta> reject(
            @NotNull String deltaId,
            @NotNull String reason,
            @NotNull String rejectedBy);

    /**
     * Lists deltas that are pending human review for a specific tenant.
     *
     * @param tenantId tenant scope (required)
     * @param filters  optional filter map; supported keys: {@code agentId}, {@code skillId},
     *                 {@code target} (LearningTarget name), {@code limit}, {@code offset}
     * @return promise of matching deltas in descending proposal-time order
     */
    @NotNull
    Promise<List<LearningDelta>> listPending(
            @NotNull String tenantId,
            @Nullable Map<String, String> filters);
}
