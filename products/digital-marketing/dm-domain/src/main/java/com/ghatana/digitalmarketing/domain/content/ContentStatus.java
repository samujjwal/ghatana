package com.ghatana.digitalmarketing.domain.content;

/**
 * Approval status of a content asset.
 *
 * @doc.type enum
 * @doc.purpose Content asset approval status enumeration
 * @doc.layer product
 * @doc.pattern StatePattern
 */
public enum ContentStatus {

    /** Draft — not yet reviewed for approval. */
    DRAFT,

    /** Content is approved and ready for use in campaigns. */
    APPROVED,

    /** Content has been rejected; must be revised before resubmission. */
    REJECTED,

    /** Content has been archived and is no longer in active use. */
    ARCHIVED
}
