package com.ghatana.plugin.approval;

/**
 * Lifecycle states of a human approval request.
 *
 * <p>State machine:</p>
 * <pre>
 *   PENDING → APPROVED
 *   PENDING → REJECTED
 *   PENDING → CANCELLED
 *   PENDING → EXPIRED   (deadline passed without decision)
 * </pre>
 *
 * @doc.type enum
 * @doc.purpose Approval lifecycle states
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum ApprovalStatus {
    /** Awaiting a reviewer decision. */
    PENDING,
    /** Approved by a reviewer. */
    APPROVED,
    /** Rejected by a reviewer. */
    REJECTED,
    /** Cancelled by the requesting system before a decision was made. */
    CANCELLED,
    /** Expired because the deadline passed without a decision. */
    EXPIRED
}
