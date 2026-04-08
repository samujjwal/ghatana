/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import com.ghatana.platform.workflow.WorkflowRun;
import com.ghatana.platform.workflow.WorkflowRunStatus;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Manages Human-in-the-Loop (HITL) pause and resume lifecycle for workflow runs.
 *
 * <p>A HITL pause checkpoint stops workflow execution at a
 * {@link WorkflowStepKind#HUMAN_IN_THE_LOOP} step and transitions the run to
 * {@link WorkflowRunStatus#WAITING_FOR_HITL}. Execution resumes only after an
 * explicit call to {@link #approve(String, Map)} or {@link #reject(String, String)}.
 *
 * <p>This operator delegates to {@link DurableWorkflowRuntime#resume(String, String, Map)}
 * for the actual resumption logic, acting as a typed facade that makes the caller's
 * intent explicit.
 *
 * <p>Usage:
 * <pre>{@code
 * HitlPauseOperator hitl = new HitlPauseOperator(runtime);
 * // Approve with additional context:
 * hitl.approve(runId, Map.of("reviewer", "alice"));
 * // Reject with a reason:
 * hitl.reject(runId, "Policy violation — action blocked");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Manages HITL pause/resume lifecycle for workflow runs
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class HitlPauseOperator {

    private static final Logger log = LoggerFactory.getLogger(HitlPauseOperator.class);

    static final String DECISION_APPROVED = "APPROVED";
    static final String DECISION_REJECTED = "REJECTED";

    private final DurableWorkflowRuntime runtime;

    /**
     * Creates a {@code HitlPauseOperator} backed by the given workflow runtime.
     *
     * @param runtime the runtime to delegate resume calls to; must not be null
     */
    public HitlPauseOperator(@NotNull DurableWorkflowRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    /**
     * Approves the HITL checkpoint for the given run, resuming workflow execution.
     *
     * <p>The run must be in {@link WorkflowRunStatus#WAITING_FOR_HITL} status.
     *
     * @param runId   the workflow run ID to approve; must not be blank
     * @param context additional context merged into run variables before resumption
     * @return a promise of the resumed {@link WorkflowRun}
     * @throws IllegalArgumentException if {@code runId} is blank
     */
    @NotNull
    public Promise<WorkflowRun> approve(@NotNull String runId, @NotNull Map<String, Object> context) {
        validateRunId(runId);
        log.info("HITL approved for run {}", runId);
        return runtime.resume(runId, DECISION_APPROVED, context);
    }

    /**
     * Approves the HITL checkpoint with no additional context.
     *
     * @param runId the workflow run ID to approve
     * @return a promise of the resumed {@link WorkflowRun}
     */
    @NotNull
    public Promise<WorkflowRun> approve(@NotNull String runId) {
        return approve(runId, Map.of());
    }

    /**
     * Rejects the HITL checkpoint for the given run, cancelling workflow execution.
     *
     * <p>The run must be in {@link WorkflowRunStatus#WAITING_FOR_HITL} status.
     * Upon rejection, the run transitions to {@link WorkflowRunStatus#CANCELLED}.
     *
     * @param runId  the workflow run ID to reject; must not be blank
     * @param reason human-readable reason for rejection; included in run error
     * @return a promise of the cancelled {@link WorkflowRun}
     * @throws IllegalArgumentException if {@code runId} is blank
     */
    @NotNull
    public Promise<WorkflowRun> reject(@NotNull String runId, @NotNull String reason) {
        validateRunId(runId);
        Objects.requireNonNull(reason, "reason");
        log.info("HITL rejected for run {} — reason: {}", runId, reason);
        return runtime.resume(runId, DECISION_REJECTED, Map.of("__hitlRejectionReason", reason));
    }

    private static void validateRunId(String runId) {
        if (Objects.requireNonNull(runId, "runId").isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
    }
}
