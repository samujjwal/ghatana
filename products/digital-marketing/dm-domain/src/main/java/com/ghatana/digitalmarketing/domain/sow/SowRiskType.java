package com.ghatana.digitalmarketing.domain.sow;

/**
 * Categories of risk flags that may appear in a DMOS SOW draft.
 *
 * <p>Risk flags are generated automatically when building a SOW draft and must
 * be resolved or accepted by a human reviewer before the draft can be approved
 * and exported.</p>
 *
 * @doc.type class
 * @doc.purpose Taxonomy of SOW risk flag categories
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum SowRiskType {
    /**
     * The SOW contains language that implies a performance guarantee or warranty
     * that cannot be substantiated (e.g. "guaranteed ROI", "warrant results").
     */
    UNSUPPORTED_GUARANTEE,

    /**
     * One or more clauses have not been approved in the clause library and were
     * included without formal sign-off.
     */
    MISSING_APPROVAL,

    /**
     * The SOW references personally identifiable information or personal data
     * without an explicit data-handling clause or consent statement.
     */
    PRIVACY_ISSUE,

    /**
     * Scope, assumptions, or deliverables are insufficiently described and may
     * cause downstream disputes or misaligned expectations.
     */
    AMBIGUOUS_DELIVERABLE
}
