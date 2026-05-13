/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation.pack;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Executes an {@link EvaluationPack} against a running agent release.
 *
 * <p>Implementations are responsible for:
 * <ol>
 *   <li>Creating an {@link EvaluationRun} record and persisting it.</li>
 *   <li>Running each {@link EvaluationCase} in the pack against the target agent.</li>
 *   <li>Aggregating case outcomes into an {@link EvaluationRunResult}.</li>
 *   <li>Persisting the result and updating the run status.</li>
 * </ol>
 *
 * <p>All execution must be non-blocking. Implementations that call into real agent
 * inference or sandboxes must bridge through an async adapter rather than blocking
 * the event loop.
 *
 * @doc.type interface
 * @doc.purpose Service contract for executing mastery-scoped evaluation packs
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public interface EvaluationRunner {

    /**
     * Runs a complete evaluation pack and returns the aggregated result.
     *
     * <p>If any {@link EvaluationCase#required() required} case fails, the result
     * will be {@link EvaluationRunResult#passed() non-passing} regardless of the
     * overall pass rate.
     *
     * @param pack           the evaluation pack to execute
     * @param agentId        the agent under evaluation
     * @param agentReleaseId the specific release being evaluated
     * @param initiatedBy    user or system component triggering the run
     * @return promise of the completed run result
     */
    @NotNull
    Promise<EvaluationRunResult> run(
            @NotNull EvaluationPack pack,
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull String initiatedBy);

    /**
     * Creates and persists an {@link EvaluationRun} record without starting execution.
     *
     * <p>This is useful when callers need a run ID before execution starts,
     * e.g. for correlation IDs in distributed traces.
     *
     * @param pack           the pack that will be executed
     * @param agentId        agent under evaluation
     * @param agentReleaseId specific release under evaluation
     * @param initiatedBy    user or system component triggering the run
     * @return promise of the persisted run record in {@code IN_PROGRESS} state
     */
    @NotNull
    Promise<EvaluationRun> createRun(
            @NotNull EvaluationPack pack,
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull String initiatedBy);
}
