package com.ghatana.phr.healthcare.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Patient identity and demographic record.
 *
 * <p>This is the healthcare domain's core aggregate root. A patient represents an individual
 * receiving or requesting healthcare services within a tenant's facility network.
 * All patient data is tenant-scoped; no patient object may cross tenant boundaries
 * without explicit consent and audit.</p>
 *
 * <p>Data classification: C2 (Sensitive — demographics) to C4 (Restricted — health history).
 * Retention: 25 years per Nepal Health Records Act 2081.</p>
 *
 * @doc.type record
 * @doc.purpose Patient identity aggregate root — healthcare domain pack
 * @doc.layer domain-pack
 * @doc.pattern ValueObject, AggregateRoot
 * @since 1.0.0
 */
public record Patient(
    UUID patientId,
    String tenantId,
    String nhsId,          // National Health Service identifier (Nepal)
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    String gender,         // FHIR R4 AdministrativeGender: male | female | other | unknown
    String bloodGroup,
    String primaryPhone,
    String primaryEmail,
    String address,
    String province,       // Nepal province 1–7
    DataClassification classification,
    String registeredBy,
    java.time.Instant registeredAt,
    boolean active
) {

    /**
     * Constructs a Patient, validating required fields and default classification.
     */
    public Patient {
        Objects.requireNonNull(patientId, "patientId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null — all patients are tenant-scoped");
        Objects.requireNonNull(firstName, "firstName must not be null");
        Objects.requireNonNull(lastName, "lastName must not be null");
        Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");
        Objects.requireNonNull(registeredAt, "registeredAt must not be null");
        if (classification == null) {
            classification = DataClassification.C2;
        }
    }

    /**
     * Convenience factory: new patient with assigned ID.
     */
    public static Patient newPatient(
            String tenantId, String nhsId, String firstName, String lastName,
            LocalDate dateOfBirth, String gender, String registeredBy) {
        return new Patient(
            UUID.randomUUID(), tenantId, nhsId,
            firstName, lastName, dateOfBirth, gender,
            null, null, null, null, null,
            DataClassification.C2, registeredBy, java.time.Instant.now(), true
        );
    }

    /**
     * Returns a copy with a higher classification for patients with sensitive diagnoses.
     */
    public Patient withClassification(DataClassification newClassification) {
        return new Patient(
            patientId, tenantId, nhsId, firstName, lastName, dateOfBirth,
            gender, bloodGroup, primaryPhone, primaryEmail, address, province,
            newClassification, registeredBy, registeredAt, active
        );
    }

    /**
     * Returns whether this patient record qualifies for right-to-erasure.
     * Nepal Directive 2081 §22: deletion request can be filed if no active treatments
     * and a 25-year minimum retention period has elapsed.
     */
    public boolean isDeletionEligible(java.time.Instant now, boolean hasActiveTreatment) {
        if (hasActiveTreatment) return false;
        long yearsRetained = java.time.temporal.ChronoUnit.YEARS.between(registeredAt, now);
        return yearsRetained >= 25;
    }
}
