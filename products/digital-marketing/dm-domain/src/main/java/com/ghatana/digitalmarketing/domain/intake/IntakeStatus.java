package com.ghatana.digitalmarketing.domain.intake;

/**
 * Status of the business intake questionnaire lifecycle.
 *
 * @doc.type class
 * @doc.purpose DMOS intake questionnaire lifecycle status enum
 * @doc.layer product
 * @doc.pattern Enum
 */
public enum IntakeStatus {
    /** Intake is being edited and can still be changed. */
    DRAFT,
    /** Intake was submitted for downstream planning workflows. */
    SUBMITTED
}
