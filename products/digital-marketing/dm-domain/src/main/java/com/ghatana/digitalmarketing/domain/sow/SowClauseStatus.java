package com.ghatana.digitalmarketing.domain.sow;

/**
 * Approval status for a DMOS SOW clause library entry.
 *
 * <p>Only {@code APPROVED} clauses may be used when generating a Statement of Work
 * draft. A clause in {@code DRAFT} status triggers a
 * {@link SowRiskType#MISSING_APPROVAL} risk flag.</p>
 *
 * @doc.type class
 * @doc.purpose Lifecycle states for SOW clause library entries
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum SowClauseStatus {
    /** Clause is being authored and has not yet been reviewed. */
    DRAFT,
    /** Clause has been reviewed and may be included in SOW drafts. */
    APPROVED
}
