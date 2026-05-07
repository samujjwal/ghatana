package com.ghatana.digitalmarketing.domain.agency;

/**
 * Lifecycle status for agency retainers.
 *
 * @doc.type enum
 * @doc.purpose Agency retainer lifecycle states (P3-002)
 * @doc.layer product
 * @doc.pattern StateMachine
 */
public enum AgencyRetainerStatus {
    /**
     * Retainer is pending activation.
     */
    PENDING,

    /**
     * Retainer is active and billing.
     */
    ACTIVE,

    /**
     * Retainer is temporarily suspended.
     */
    SUSPENDED,

    /**
     * Retainer has been cancelled.
     */
    CANCELLED
}
