package com.ghatana.plugin.approval;

/**
 * The decision rendered by a human reviewer for an approval request.
 *
 * @doc.type enum
 * @doc.purpose Approval decision values
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum ApprovalDecision {
    /** The request was approved by the reviewer. */
    APPROVED,
    /** The request was rejected by the reviewer. */
    REJECTED
}
