package com.ghatana.phr.kernel.retention;

/**
 * Outcome of a PHR data deletion or retention evaluation.
 *
 * <p>Every resource evaluated by the deletion pipeline must be assigned one of
 * these outcomes before any mutation is applied. The outcome drives the actual
 * deletion action and determines the audit evidence produced.</p>
 *
 * <h3>Outcome decision tree</h3>
 * <ol>
 *   <li>If a legal hold is active → {@link #RETAIN_UNDER_HOLD}</li>
 *   <li>If the retention floor has not elapsed → {@link #RETAIN_UNDER_HOLD}
 *       (policy floor reason)</li>
 *   <li>If the resource is C3/C4 and patient identity must be preserved
 *       for research → {@link #ANONYMIZE}</li>
 *   <li>If Patient requests full deletion, jurisdiction allows, and no hold
 *       is active → {@link #PURGE}</li>
 *   <li>If the resource is superseded/archived but must remain discoverable
 *       in audit history → {@link #TOMBSTONE}</li>
 * </ol>
 *
 * @doc.type enum
 * @doc.purpose Models the four deletion outcomes required by Nepal Privacy Act 2075
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DeletionOutcome {

    /**
     * All data is permanently and irreversibly deleted — disk, backups, and object storage.
     *
     * <p>Allowed only when: no legal hold is active, the retention floor has fully
     * elapsed, and the patient or authorised representative has submitted a valid
     * deletion request under Nepal Privacy Act 2075 Article 14.</p>
     *
     * <p>An audit record MUST be created before the purge executes so that the fact
     * of deletion is preserved even though the payload is gone.</p>
     */
    PURGE,

    /**
     * Direct identifiers are replaced with synthetic tokens; the clinical payload
     * is retained in de-identified form for research and statistical purposes.
     *
     * <p>Applicable when: the patient requests deletion but the jurisdiction or a
     * data sharing agreement requires retention of de-identified records, or when
     * the resource is C3/C4 and linked to a population-level dataset.</p>
     */
    ANONYMIZE,

    /**
     * The record is logically deleted — marked as deleted with a timestamp, hidden
     * from all patient-facing views, and excluded from clinical queries — but the
     * raw payload remains on disk until the retention floor elapses.
     *
     * <p>Applicable when: the resource is superseded by a newer version, the
     * patient has revoked sharing consent, or administrative deletion is performed
     * but the audit chain must remain intact.</p>
     */
    TOMBSTONE,

    /**
     * No deletion action is taken; the record is explicitly placed on a retention
     * hold that prevents any of the other outcomes from executing.
     *
     * <p>Reasons for a hold include: active legal proceedings, regulatory
     * investigation, retention floor not yet elapsed, or an explicit legal hold
     * raised by a {@link LegalHoldService}.</p>
     *
     * <p>A hold reason must always be recorded alongside the outcome so that it
     * can be cleared when the hold is lifted.</p>
     */
    RETAIN_UNDER_HOLD;

    /** Returns true if this outcome results in any form of data removal or de-identification. */
    public boolean isDestructive() {
        return this == PURGE || this == ANONYMIZE;
    }

    /** Returns true if the data remains legally discoverable after this outcome is applied. */
    public boolean isDiscoverable() {
        return this == TOMBSTONE || this == RETAIN_UNDER_HOLD;
    }
}
