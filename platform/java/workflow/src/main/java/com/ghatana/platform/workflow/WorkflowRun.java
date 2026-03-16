/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents one execution instance of a workflow (one run).
 *
 * <p>This is the primary state object that tracks a workflow's progress from
 * {@link WorkflowRunStatus#PENDING} through to terminal states. For durable
 * workflows, it is persisted by a {@link WorkflowStateStore}.
 *
 * @doc.type record
 * @doc.purpose Workflow execution instance (run) state
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record WorkflowRun(
        @NotNull String runId,
        @NotNull String workflowId,
        @Nullable String tenantId,
        @NotNull WorkflowKind kind,
        @NotNull WorkflowRunStatus status,
        @NotNull WorkflowOptions options,
        @NotNull Instant startedAt,
        @Nullable Instant completedAt,
        @Nullable String currentStepId,
        @NotNull Map<String, Object> variables,
        @Nullable String errorMessage,
        @Nullable String triggeredBy,
        @NotNull List<WorkflowLifecycleEvent> history
) {

    /**
     * Canonical constructor with defensive copies.
     */
    public WorkflowRun {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(workflowId, "workflowId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(startedAt, "startedAt");
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        history = history == null ? List.of() : List.copyOf(history);
    }

    /**
     * Returns a copy with the specified status.
     */
    public WorkflowRun withStatus(@NotNull WorkflowRunStatus newStatus) {
        return new WorkflowRun(
                runId, workflowId, tenantId, kind, newStatus, options,
                startedAt, completedAt, currentStepId, variables, errorMessage,
                triggeredBy, history);
    }

    /**
     * Returns a copy with the specified status and completion time.
     */
    public WorkflowRun withCompleted(@NotNull WorkflowRunStatus terminalStatus, @NotNull Instant completedAt) {
        return new WorkflowRun(
                runId, workflowId, tenantId, kind, terminalStatus, options,
                startedAt, completedAt, currentStepId, variables, errorMessage,
                triggeredBy, history);
    }

    /**
     * Returns a copy with the specified current step.
     */
    public WorkflowRun withCurrentStep(@Nullable String stepId) {
        return new WorkflowRun(
                runId, workflowId, tenantId, kind, status, options,
                startedAt, completedAt, stepId, variables, errorMessage,
                triggeredBy, history);
    }

    /**
     * Returns a copy with the specified error message.
     */
    public WorkflowRun withError(@NotNull String errorMessage) {
        return new WorkflowRun(
                runId, workflowId, tenantId, kind, status, options,
                startedAt, completedAt, currentStepId, variables, errorMessage,
                triggeredBy, history);
    }

    /**
     * Returns a copy with the specified variables.
     */
    public WorkflowRun withVariables(@NotNull Map<String, Object> variables) {
        return new WorkflowRun(
                runId, workflowId, tenantId, kind, status, options,
                startedAt, completedAt, currentStepId, variables, errorMessage,
                triggeredBy, history);
    }
}
