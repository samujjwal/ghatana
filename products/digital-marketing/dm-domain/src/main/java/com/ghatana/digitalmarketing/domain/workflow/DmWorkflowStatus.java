package com.ghatana.digitalmarketing.domain.workflow;

/**
 * Lifecycle states for a durable workflow execution.
 *
 * @doc.type class
 * @doc.purpose Models all states a workflow execution can occupy (DMOS-F2-004)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmWorkflowStatus {
    /** Workflow created, not yet started. */
    PENDING,

    /** Workflow is actively executing steps. */
    RUNNING,

    /** Workflow is paused, waiting for an external signal. */
    PAUSED,

    /** All steps completed successfully. */
    COMPLETED,

    /** Workflow has permanently failed. */
    FAILED,

    /** Workflow was rolled back after a failure. */
    ROLLED_BACK
}
