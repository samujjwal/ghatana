package com.ghatana.digitalmarketing.domain.workflow;

/**
 * Lifecycle states for an individual step within a durable workflow.
 *
 * @doc.type class
 * @doc.purpose Tracks per-step execution status in a workflow (DMOS-F2-004)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmWorkflowStepStatus {
    /** Step has not yet been attempted. */
    PENDING,

    /** Step is currently executing. */
    EXECUTING,

    /** Step completed successfully. */
    COMPLETED,

    /** Step failed; may be retried depending on workflow policy. */
    FAILED,

    /** Step was intentionally skipped (e.g. conditional branch not taken). */
    SKIPPED
}
