package com.ghatana.kernel.workflow;

/**
 * Status of a workflow execution.
 *
 * @doc.type enum
 * @doc.purpose Workflow lifecycle states (KERNEL-P0)
 * @doc.layer core
 */
public enum WorkflowStatus {
    /**
     * Workflow is pending execution.
     */
    PENDING,

    /**
     * Workflow is currently running.
     */
    RUNNING,

    /**
     * Workflow completed successfully.
     */
    COMPLETED,

    /**
     * Workflow failed execution.
     */
    FAILED,

    /**
     * Workflow was cancelled.
     */
    CANCELLED,

    /**
     * Workflow is paused.
     */
    PAUSED
}
