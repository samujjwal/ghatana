package com.ghatana.phr.healthcare.domain;

/**
 * Consent action identifiers, aligned with the PHR ConsentService interface specification.
 *
 * <p>These match the {@code ConsentAction} type defined in
 * {@code products/phr/docs/03_architecture/phr_consent_service_interface_spec.md}.</p>
 *
 * @doc.type enum
 * @doc.purpose Healthcare consent action taxonomy — governs what a consent grant covers
 * @doc.layer domain-pack
 * @doc.pattern ValueObject
 * @since 1.0.0
 */
public enum ConsentAction {
    PATIENT_READ,
    PATIENT_WRITE,
    DOCUMENT_READ,
    DOCUMENT_WRITE,
    MEDICATION_READ,
    MEDICATION_WRITE,
    TIMELINE_READ,
    INSURANCE_READ,
    INSURANCE_CHECK,
    AUDIT_READ,
    EMERGENCY_READ
}
