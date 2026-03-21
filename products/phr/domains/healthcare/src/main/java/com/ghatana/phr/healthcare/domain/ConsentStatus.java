package com.ghatana.phr.healthcare.domain;

/**
 * Possible states in the lifecycle of a {@link ConsentRecord}.
 *
 * @doc.type enum
 * @doc.purpose Consent lifecycle state machine
 * @doc.layer domain-pack
 * @doc.pattern StateMachine
 * @since 1.0.0
 */
public enum ConsentStatus {
    /** Created but not yet submitted for patient/provider approval. */
    DRAFT,
    /** Submitted, awaiting patient confirmation. */
    PROPOSED,
    /** Approved and actively in force. */
    ACTIVE,
    /** Patient or system revoked the consent before expiry. */
    REVOKED,
    /** Natural expiry — ttl elapsed or treatment episode ended. */
    EXPIRED,
    /** Marked superseded by a newer consent record for the same scope. */
    SUPERSEDED
}
