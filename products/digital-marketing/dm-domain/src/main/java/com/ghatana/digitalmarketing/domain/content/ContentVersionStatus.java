package com.ghatana.digitalmarketing.domain.content;

/**
 * Lifecycle status for a content version.
 *
 * @doc.type enum
 * @doc.purpose DMOS content version lifecycle status enumeration
 * @doc.layer product
 * @doc.pattern StatePattern
 */
public enum ContentVersionStatus {

    /** Draft — created but not yet submitted for approval. */
    DRAFT,

    /** Submitted for review; awaiting approval decision. */
    PENDING_REVIEW,

    /** Approved for use in campaign launches. Immutable once approved. */
    APPROVED,

    /** Archived — superseded by a newer approved version. */
    ARCHIVED
}
