package com.ghatana.digitalmarketing.domain.sow;

/**
 * Lifecycle states for a DMOS Statement of Work draft.
 *
 * <p>State transitions:</p>
 * <pre>
 *   DRAFT → PENDING_REVIEW → APPROVED → EXPORTED
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Lifecycle state machine for DMOS SOW drafts
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum SowStatus {
    /** SOW draft has been generated and is awaiting human review. */
    DRAFT,
    /** SOW draft has been submitted for review and approval. */
    PENDING_REVIEW,
    /** SOW draft has been approved by a human reviewer. */
    APPROVED,
    /** Approved SOW draft has been exported for client delivery. */
    EXPORTED
}
