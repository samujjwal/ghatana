package com.ghatana.digitalmarketing.domain.contact;

/**
 * Consent status for a {@link Contact} with respect to a specific marketing purpose.
 *
 * @doc.type enum
 * @doc.purpose Contact consent status enumeration
 * @doc.layer product
 * @doc.pattern StatePattern
 */
public enum ConsentStatus {

    /** Explicit consent granted by the contact. */
    GRANTED,

    /** Consent explicitly withdrawn by the contact. */
    WITHDRAWN,

    /** No consent recorded — contact must not be targeted until consent is obtained. */
    UNKNOWN
}
