package com.ghatana.yappc.domain.status;

/**
 * Canonical top-level run execution status.
 *
 * <p>Supersedes {@code RunStatus} in {@code yappc-services}. The domain-level
 * enum here is the single source of truth; the product-local copy should
 * delegate to or be replaced by this type in a follow-on PR.</p>
 *
 * <p>Lifecycle transitions:</p>
 * <pre>
 * QUEUED → RUNNING → SUCCESS
 *                 └─→ FAILED
 *                 └─→ CANCELLED
 * NOT_READY → QUEUED (once pre-conditions are satisfied)
 * </pre>
 *
 * @doc.type enum
 * @doc.purpose Canonical run execution lifecycle status
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public enum RunStatus implements Lifecycle {

    /** Pre-conditions not yet satisfied; run cannot be queued yet. */
    NOT_READY,

    /** Accepted and waiting for an executor to pick it up. */
    QUEUED,

    /** Actively executing. */
    RUNNING,

    /** Completed successfully. */
    SUCCESS,

    /** Terminated due to an unrecoverable error. */
    FAILED,

    /** Cancelled before completion. */
    CANCELLED;

    /** Returns {@code true} if this is a terminal state. */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED;
    }

    /** Returns {@code true} if the run is in-flight. */
    public boolean isActive() {
        return this == QUEUED || this == RUNNING;
    }
}
