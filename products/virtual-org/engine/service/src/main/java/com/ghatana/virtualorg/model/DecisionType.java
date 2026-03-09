package com.ghatana.virtualorg.model;

/**
 * Types of decisions an agent can make.
 *
 * <p><b>Purpose</b><br>
 * Defines the outcome types for agent task processing:
 * - APPROVE: Task/request approved
 * - REJECT: Task/request rejected
 * - ESCALATE: Decision escalated to higher authority
 * - REQUEST_CHANGES: Requires modifications before approval
 * - DEFER: Decision deferred pending more information
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Decision decision = Decision.builder()
 *     .type(DecisionType.APPROVE)
 *     .rationale("All criteria met")
 *     .build();
 *
 * switch (decision.getType()) {
 *     case APPROVE -> proceed();
 *     case REJECT -> halt();
 *     case ESCALATE -> escalate(decision.getEscalationTarget());
 *     case REQUEST_CHANGES -> requestRevision();
 *     case DEFER -> waitForInfo();
 * }
 * }</pre>
 *
 * @doc.type enum
 * @doc.purpose Agent decision type enumeration
 * @doc.layer product
 * @doc.pattern Value Type
 */
public enum DecisionType {
    /**
     * Decision to approve the request/task.
     */
    APPROVE,

    /**
     * Decision to reject the request/task.
     */
    REJECT,

    /**
     * Decision to escalate to higher authority.
     * Must include escalationTarget in Decision.
     */
    ESCALATE,

    /**
     * Request changes/modifications before approval.
     */
    REQUEST_CHANGES,

    /**
     * Defer decision pending additional information.
     */
    DEFER
}
