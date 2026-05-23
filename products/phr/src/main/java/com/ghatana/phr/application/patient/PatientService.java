package com.ghatana.phr.application.patient;

import com.ghatana.phr.domain.patient.Patient;
import com.ghatana.phr.domain.patient.PatientProfile;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Service interface for patient profile management.
 *
 * @doc.type class
 * @doc.purpose Defines operations for managing patient profiles (PHR-F1-001)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface PatientService {

    /**
     * Create a new patient profile.
     *
     * @param ctx     operation context
     * @param request patient creation request
     * @return the newly created patient
     */
    Promise<Patient> createPatient(PatientOperationContext ctx, CreatePatientRequest request);

    /**
     * Fetch a patient by ID.
     *
     * @param ctx       operation context
     * @param patientId patient ID
     * @return optional patient
     */
    Promise<Optional<Patient>> getPatient(PatientOperationContext ctx, String patientId);

    /**
     * Update patient profile.
     *
     * @param ctx       operation context
     * @param patientId patient ID
     * @param profile   new profile
     * @return updated patient
     */
    Promise<Patient> updateProfile(PatientOperationContext ctx, String patientId, PatientProfile profile);

    /**
     * Verify patient profile.
     *
     * @param ctx       operation context
     * @param patientId patient ID
     * @return updated patient
     */
    Promise<Patient> verifyPatient(PatientOperationContext ctx, String patientId);

    /**
     * Get patient demographics.
     *
     * @param ctx       operation context
     * @param patientId patient ID
     * @return patient demographics
     */
    Promise<PatientProfile> getDemographics(PatientOperationContext ctx, String patientId);

    // ── Request types ─────────────────────────────────────────────────────────

    record CreatePatientRequest(
        String tenantId,
        PatientProfile profile
    ) {
        public CreatePatientRequest {
            // Validation logic
        }
    }

    record PatientOperationContext(
        String tenantId,
        String userId,
        String patientId
    ) {}
}
