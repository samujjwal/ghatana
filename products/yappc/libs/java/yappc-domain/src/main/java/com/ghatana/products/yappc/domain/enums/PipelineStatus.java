package com.ghatana.products.yappc.domain.enums;

/**
 * Pipeline status enumeration for CI/CD pipeline execution tracking.
 *
 * <p><b>Purpose</b><br>
 * Defines execution states for CI/CD pipeline runs from trigger to completion.
 *
 * <p><b>Status Flow</b><br>
 * PENDING → RUNNING → SUCCESS
 * Alternative: PENDING → RUNNING → FAILED
 * Cancellation: PENDING/RUNNING → CANCELLED
 *
 * @see com.ghatana.products.yappc.domain.model.PipelineRun
 * @doc.type enum
 * @doc.purpose Pipeline execution lifecycle
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum PipelineStatus {
    /**
     * Pending - pipeline queued, not started.
     */
    PENDING,

    /**
     * Running - pipeline actively executing.
     */
    RUNNING,

    /**
     * Success - pipeline completed successfully.
     */
    SUCCESS,

    /**
     * Failed - pipeline failed with errors.
     */
    FAILED,

    /**
     * Cancelled - pipeline cancelled by user.
     */
    CANCELLED,

    /**
     * Partial - pipeline partially completed (some stages failed).
     */
    PARTIAL
}
