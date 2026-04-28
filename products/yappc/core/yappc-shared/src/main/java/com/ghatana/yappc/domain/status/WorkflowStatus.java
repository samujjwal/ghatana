package com.ghatana.yappc.domain.status;

/**
 * Canonical top-level workflow lifecycle status.
 *
 * <p>Supersedes the nested {@code AiWorkflowInstance.WorkflowStatus} enum.
 * Existing code should migrate to this type; the nested enum is kept temporarily
 * for backward compatibility and will be removed in a follow-on PR.</p>
 *
 * <p>Lifecycle transitions:</p>
 * <pre>
 * DRAFT → PENDING → IN_PROGRESS → AWAITING_REVIEW → COMPLETED
 *                  └─→ PAUSED ──────────────────────────────────┘
 *                  └─→ FAILED
 *                  └─→ CANCELLED
 * </pre>
 *
 * @doc.type enum
 * @doc.purpose Canonical workflow lifecycle status
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public enum WorkflowStatus implements Lifecycle {

    /** Created but not yet submitted for processing. */
    DRAFT,

    /** Queued for processing; no worker has picked it up yet. */
    PENDING,

    /** Actively being executed. */
    IN_PROGRESS,

    /** Temporarily halted; can be resumed. */
    PAUSED,

    /** Execution complete; awaiting human or automated review. */
    AWAITING_REVIEW,

    /** Successfully finished. */
    COMPLETED,

    /** Terminated due to an error. */
    FAILED,

    /** Manually cancelled before completion. */
    CANCELLED;

    /** Returns {@code true} if this status represents a terminal state. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    /** Returns {@code true} if the workflow is actively making progress. */
    public boolean isActive() {
        return this == IN_PROGRESS || this == AWAITING_REVIEW;
    }
}
