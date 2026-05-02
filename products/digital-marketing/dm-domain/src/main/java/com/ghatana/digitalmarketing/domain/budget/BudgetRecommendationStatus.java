package com.ghatana.digitalmarketing.domain.budget;

/**
 * Approval lifecycle state for a {@link BudgetRecommendation}.
 *
 * <p>Distinct from {@link BudgetStatus} which tracks runtime spend lifecycle.
 * This enum tracks the human-approval workflow before a recommendation can
 * back a campaign launch.</p>
 *
 * @doc.type enum
 * @doc.purpose Approval state of a workspace-level budget recommendation
 * @doc.layer product
 * @doc.pattern StatePattern
 */
public enum BudgetRecommendationStatus {
    /** Generated and awaiting owner submission. */
    DRAFT,
    /** Submitted for owner or manager approval. */
    PENDING_APPROVAL,
    /** Approved — eligible to authorize campaign spend. */
    APPROVED,
    /** Rejected — must be revised and resubmitted. */
    REJECTED
}
