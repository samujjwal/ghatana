/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation.pack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a single execution of an {@link EvaluationPack}.
 *
 * <p>An {@code EvaluationRun} is created when a pack begins execution and tracks
 * lifecycle state through to completion. It is persisted so that audits and
 * promotion pipelines can trace which runs contributed evidence.
 *
 * @doc.type record
 * @doc.purpose Lifecycle record for a single evaluation pack execution
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record EvaluationRun(
        @NotNull String runId,
        @NotNull String evaluationPackId,
        @NotNull String tenantId,
        @NotNull String agentId,
        @NotNull String agentReleaseId,
        @NotNull Instant startedAt,
        @Nullable Instant completedAt,
        @NotNull EvaluationRunStatus status,
        @NotNull String initiatedBy
) {
    public EvaluationRun {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(evaluationPackId, "evaluationPackId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(agentReleaseId, "agentReleaseId must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(initiatedBy, "initiatedBy must not be null");
    }

    /**
     * Returns {@code true} if this run has reached a terminal state.
     *
     * @return true if the run is in a terminal state
     */
    public boolean isTerminal() {
        return status == EvaluationRunStatus.PASSED
                || status == EvaluationRunStatus.FAILED
                || status == EvaluationRunStatus.ABORTED;
    }

    /**
     * Creates an in-progress run.
     *
     * @param runId            unique run ID
     * @param evaluationPackId ID of the pack being run
     * @param tenantId         tenant owning this run
     * @param agentId          agent under evaluation
     * @param agentReleaseId   specific release under evaluation
     * @param initiatedBy      user or system component that triggered the run
     * @return an in-progress evaluation run
     */
    @NotNull
    public static EvaluationRun started(
            @NotNull String runId,
            @NotNull String evaluationPackId,
            @NotNull String tenantId,
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull String initiatedBy) {
        return new EvaluationRun(
                runId, evaluationPackId, tenantId, agentId, agentReleaseId,
                Instant.now(), null, EvaluationRunStatus.IN_PROGRESS, initiatedBy);
    }
}
