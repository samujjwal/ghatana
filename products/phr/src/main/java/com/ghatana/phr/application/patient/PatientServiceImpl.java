package com.ghatana.phr.application.patient;

import com.ghatana.phr.domain.patient.Patient;
import com.ghatana.phr.domain.patient.PatientProfile;
import io.activej.promise.Promise;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of PatientService with in-memory storage.
 *
 * @doc.type class
 * @doc.purpose Provides patient profile management operations
 * @doc.layer product
 * @doc.pattern Service Implementation
 */
public class PatientServiceImpl implements PatientService {

    private final ConcurrentMap<String, Patient> patients = new ConcurrentHashMap<>();

    @Override
    public Promise<Patient> createPatient(PatientOperationContext ctx, CreatePatientRequest request) {
        String patientId = Patient.generatePatientId();
        Patient patient = Patient.builder()
            .patientId(patientId)
            .tenantId(request.tenantId())
            .profile(request.profile())
            .status(Patient.PatientStatus.PENDING)
            .createdBy(ctx.userId())
            .build();

        patients.put(patientId, patient);
        return Promise.of(patient);
    }

    @Override
    public Promise<Optional<Patient>> getPatient(PatientOperationContext ctx, String patientId) {
        return Promise.of(Optional.ofNullable(patients.get(patientId)));
    }

    @Override
    public Promise<Patient> updateProfile(PatientOperationContext ctx, String patientId, PatientProfile profile) {
        Patient patient = patients.get(patientId);
        if (patient == null) {
            return Promise.ofException(new IllegalArgumentException("Patient not found: " + patientId));
        }
        patient.updateProfile(profile);
        return Promise.of(patient);
    }

    @Override
    public Promise<Patient> verifyPatient(PatientOperationContext ctx, String patientId) {
        Patient patient = patients.get(patientId);
        if (patient == null) {
            return Promise.ofException(new IllegalArgumentException("Patient not found: " + patientId));
        }
        patient.verify();
        return Promise.of(patient);
    }

    @Override
    public Promise<PatientProfile> getDemographics(PatientOperationContext ctx, String patientId) {
        Patient patient = patients.get(patientId);
        if (patient == null) {
            return Promise.ofException(new IllegalArgumentException("Patient not found: " + patientId));
        }
        return Promise.of(patient.profile());
    }
}
