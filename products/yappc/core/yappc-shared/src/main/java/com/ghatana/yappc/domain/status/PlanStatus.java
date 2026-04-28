package com.ghatana.yappc.domain.status;

/**
 * Canonical top-level AI plan lifecycle status.
 *
 * <p>Supersedes the nested {@code AiPlan.PlanStatus} enum defined in
 * {@code yappc-domain-impl}. Callers should migrate to this canonical type;
 * the nested enum will be removed in a follow-on PR.</p>
 *
 * <p>Lifecycle transitions:</p>
 * <pre>
 * GENERATING → PENDING_REVIEW → APPROVED → EXECUTED
 *                             └─→ REJECTED → (back to GENERATING or abandoned)
 *                             └─→ MODIFIED → APPROVED
 * GENERATING → FAILED
 * </pre>
 *
 * @doc.type enum
 * @doc.purpose Canonical AI plan lifecycle status
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public enum PlanStatus implements Lifecycle {

    /** AI is currently generating the plan. */
    GENERATING,

    /** Generation complete; waiting for a human reviewer. */
    PENDING_REVIEW,

    /** Approved by the reviewer; ready for execution. */
    APPROVED,

    /** Rejected during review; must be regenerated or revised. */
    REJECTED,

    /** Reviewer has made manual modifications after initial generation. */
    MODIFIED,

    /** Plan has been executed against the workspace. */
    EXECUTED,

    /** Generation or execution failed. */
    FAILED;

    /** Returns {@code true} if this is a terminal state (no further transitions expected). */
    public boolean isTerminal() {
        return this == EXECUTED || this == FAILED;
    }

    /** Returns {@code true} if the plan is waiting for a human decision. */
    public boolean awaitingHuman() {
        return this == PENDING_REVIEW || this == MODIFIED;
    }
}
