package com.ghatana.phr.healthcare.domain;

/**
 * Healthcare data classification levels aligned with PHR classification model.
 *
 * <p>All patient data must be classified at ingest and access decisions must respect
 * classification levels. Higher classifications require explicit consent.</p>
 *
 * <ul>
 *   <li><b>C1 — Public</b>: Non-personal reference data (e.g., facility information).</li>
 *   <li><b>C2 — Sensitive</b>: Demographics, appointment records.
 *       Requires authenticated access + tenant verification.</li>
 *   <li><b>C3 — Highly Sensitive</b>: Diagnostic records, lab results, medications.
 *       Requires role-based access + explicit purpose-of-use.</li>
 *   <li><b>C4 — Restricted</b>: Mental health, HIV/AIDS, substance abuse, genetic data,
 *       reproductive health, emergency overrides.
 *       Requires explicit patient consent + mandatory audit.</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Healthcare data classification — governs consent requirement level
 * @doc.layer domain-pack
 * @doc.pattern ValueObject
 * @since 1.0.0
 */
public enum DataClassification {

    /** Non-personal reference data. */
    C1,

    /** Sensitive personal data — demographics, appointments. */
    C2,

    /** Highly sensitive PHI — diagnoses, labs, medications. */
    C3,

    /** Restricted PHI — mental health, HIV, genetics, reproductive health. */
    C4;

    /**
     * Returns whether explicit patient consent is required to access data at this level.
     * C1 and C2 allow role-based access; C3 and C4 require an active consent grant.
     */
    public boolean requiresExplicitConsent() {
        return this == C3 || this == C4;
    }

    /**
     * Returns whether a cryptographic audit signature is mandatory for access at this level.
     */
    public boolean requiresTamperEvidentAudit() {
        return this == C4;
    }
}
