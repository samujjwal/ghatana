package com.ghatana.yappc.domain.status;

/**
 * Canonical top-level requirement lifecycle status.
 *
 * <p>Supersedes the existing {@code RequirementStatus} enum in the {@code core/ai} module.
 * Once callers migrate, the product-local copy will be removed.</p>
 *
 * <p>Lifecycle transitions:</p>
 * <pre>
 * DRAFT → PENDING_REVIEW → IN_REVIEW → APPROVED → IMPLEMENTED → VERIFIED
 *                                   └─→ REJECTED (returns to DRAFT)
 *                                   └─→ DEPRECATED (at any stage)
 * </pre>
 *
 * @doc.type enum
 * @doc.purpose Canonical requirement lifecycle status
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public enum RequirementStatus implements Lifecycle {

    /** Being authored; not yet submitted. */
    DRAFT,

    /** Submitted; waiting for a reviewer to pick it up. */
    PENDING_REVIEW,

    /** Actively under review. */
    IN_REVIEW,

    /** Approved by stakeholders; ready for implementation. */
    APPROVED,

    /** Rejected; author must revise before resubmitting. */
    REJECTED,

    /** Implemented in the product. */
    IMPLEMENTED,

    /** Implementation has been verified and accepted. */
    VERIFIED,

    /** Superseded or no longer relevant. */
    DEPRECATED;

    /** Returns {@code true} if this status is terminal (no further transitions expected). */
    public boolean isTerminal() {
        return this == VERIFIED || this == DEPRECATED;
    }

    /** Returns {@code true} if the requirement is waiting for an action. */
    public boolean isPending() {
        return this == PENDING_REVIEW || this == IN_REVIEW;
    }
}
