package com.ghatana.digitalmarketing.domain.budget;

/**
 * Allocation status of a {@link Budget} record.
 *
 * @doc.type enum
 * @doc.purpose Budget allocation status enumeration
 * @doc.layer product
 * @doc.pattern StatePattern
 */
public enum BudgetStatus {

    /** Budget draft — not yet approved for spending. */
    DRAFT,

    /** Budget is approved and funds are allocated for campaign use. */
    APPROVED,

    /** Budget is exhausted — all approved funds have been spent. */
    EXHAUSTED,

    /** Budget has been cancelled and funds returned to unallocated. */
    CANCELLED
}
