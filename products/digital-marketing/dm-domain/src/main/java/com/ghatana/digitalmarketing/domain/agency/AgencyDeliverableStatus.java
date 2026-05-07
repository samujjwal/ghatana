package com.ghatana.digitalmarketing.domain.agency;

/**
 * Lifecycle status for agency deliverables.
 *
 * @doc.type enum
 * @doc.purpose Agency deliverable lifecycle states (P3-002)
 * @doc.layer product
 * @doc.pattern StateMachine
 */
public enum AgencyDeliverableStatus {
    /**
     * Deliverable is pending start.
     */
    PENDING,

    /**
     * Deliverable is in progress.
     */
    IN_PROGRESS,

    /**
     * Deliverable is submitted for client review.
     */
    SUBMITTED,

    /**
     * Deliverable is under review.
     */
    IN_REVIEW,

    /**
     * Deliverable has been completed and approved.
     */
    COMPLETED,

    /**
     * Deliverable was rejected by client.
     */
    REJECTED,

    /**
     * Deliverable was cancelled.
     */
    CANCELLED
}
