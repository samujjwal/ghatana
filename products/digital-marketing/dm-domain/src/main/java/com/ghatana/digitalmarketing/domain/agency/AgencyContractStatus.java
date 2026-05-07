package com.ghatana.digitalmarketing.domain.agency;

/**
 * Lifecycle status for agency contracts.
 *
 * @doc.type enum
 * @doc.purpose Agency contract lifecycle states (P3-002)
 * @doc.layer product
 * @doc.pattern StateMachine
 */
public enum AgencyContractStatus {
    /**
     * Contract is in draft state, not yet sent to client.
     */
    DRAFT,

    /**
     * Contract is pending client approval.
     */
    PENDING,

    /**
     * Contract is active and in effect.
     */
    ACTIVE,

    /**
     * Contract has been terminated.
     */
    TERMINATED,

    /**
     * Contract has expired naturally.
     */
    EXPIRED
}
