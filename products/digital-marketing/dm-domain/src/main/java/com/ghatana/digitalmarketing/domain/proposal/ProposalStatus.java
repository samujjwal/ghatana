package com.ghatana.digitalmarketing.domain.proposal;

/**
 * Lifecycle states for a DMOS proposal.
 *
 * <p>A proposal follows a linear workflow:
 * {@code DRAFT} → {@code PENDING_REVIEW} → {@code APPROVED}.</p>
 *
 * @doc.type class
 * @doc.purpose Lifecycle state machine for DMOS proposals
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum ProposalStatus {
    /** Proposal has been generated and is awaiting human review. */
    DRAFT,
    /** Proposal has been submitted for human review and approval. */
    PENDING_REVIEW,
    /** Proposal has been approved and may be exported or sent to the client. */
    APPROVED
}
