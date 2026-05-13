/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation.pack;

/**
 * Lifecycle status of an {@link EvaluationRun}.
 *
 * @doc.type enum
 * @doc.purpose Lifecycle status values for evaluation runs
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum EvaluationRunStatus {
    /**
     * The run has been accepted and is currently executing.
     */
    IN_PROGRESS,

    /**
     * The run completed and all pass-rate and required-case constraints were satisfied.
     */
    PASSED,

    /**
     * The run completed but one or more constraints were not satisfied.
     */
    FAILED,

    /**
     * The run was cancelled before completion (infrastructure failure, timeout, explicit cancel).
     */
    ABORTED
}
