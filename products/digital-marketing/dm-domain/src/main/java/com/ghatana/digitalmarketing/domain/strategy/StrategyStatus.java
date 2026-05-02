package com.ghatana.digitalmarketing.domain.strategy;

/**
 * Lifecycle status of a marketing strategy document.
 *
 * @doc.type class
 * @doc.purpose Represents the lifecycle state of a 30-day marketing strategy
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum StrategyStatus {
    /**
     * Strategy has been generated but not yet submitted for approval.
     */
    DRAFT,

    /**
     * Strategy has been submitted and is waiting for human approval.
     */
    PENDING_APPROVAL,

    /**
     * Strategy has been approved and is ready for execution planning.
     */
    APPROVED,

    /**
     * Strategy was rejected and requires revision or regeneration.
     */
    REJECTED
}
