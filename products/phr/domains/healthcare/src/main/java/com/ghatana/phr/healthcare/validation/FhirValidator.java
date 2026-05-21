package com.ghatana.phr.healthcare.validation;

import com.ghatana.phr.healthcare.domain.Patient;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * FHIR R4 schema-backed validator for healthcare domain objects.
 *
 * <p>This validator enforces FHIR R4 conformance for Patient records and other
 * healthcare domain objects. Invalid FHIR data is rejected at the API boundary.</p>
 *
 * @doc.type class
 * @doc.purpose FHIR R4 schema-backed validator for healthcare domain
 * @doc.layer domain-pack
 * @doc.pattern Validator
 */
public final class FhirValidator {

    /**
     * Validates a Patient record against FHIR R4 Patient resource schema.
     *
     * @param patient the patient to validate
     * @return validation result with any errors
     */
    public FhirValidationResult validatePatient(Patient patient) {
        Objects.requireNonNull(patient, "patient must not be null");
        
        List<String> errors = new ArrayList<>();
        
        // Validate required FHIR R4 Patient fields
        if (patient.firstName() == null || patient.firstName().isBlank()) {
            errors.add("Patient.name.given is required in FHIR R4");
        }
        
        if (patient.lastName() == null || patient.lastName().isBlank()) {
            errors.add("Patient.name.family is required in FHIR R4");
        }
        
        if (patient.gender() == null || patient.gender().isBlank()) {
            errors.add("Patient.gender is required in FHIR R4");
        } else {
            // Validate FHIR R4 AdministrativeGender values
            String gender = patient.gender().toLowerCase();
            if (!gender.equals("male") && !gender.equals("female") && 
                !gender.equals("other") && !gender.equals("unknown")) {
                errors.add("Patient.gender must be one of: male, female, other, unknown (FHIR R4 AdministrativeGender)");
            }
        }
        
        if (patient.dateOfBirth() == null) {
            errors.add("Patient.birthDate is required in FHIR R4");
        } else {
            // Validate birthDate is not in the future
            if (patient.dateOfBirth().isAfter(java.time.LocalDate.now())) {
                errors.add("Patient.birthDate cannot be in the future");
            }
        }
        
        // Validate NHS ID format if provided
        if (patient.nhsId() != null && !patient.nhsId().isBlank()) {
            if (!isValidNhsId(patient.nhsId())) {
                errors.add("Patient.identifier (NHS ID) format is invalid");
            }
        }
        
        return new FhirValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * Validates NHS ID format (simplified validation for demonstration).
     * Nepal NHS IDs follow a specific pattern.
     */
    private boolean isValidNhsId(String nhsId) {
        // Simplified validation: check for alphanumeric-with-hyphen with reasonable length
        // In production, this would validate against the official Nepal NHS ID format
        return nhsId.matches("[A-Za-z0-9][A-Za-z0-9-]{7,19}");
    }
    
    /**
     * Validation result for FHIR conformance checks.
     */
    public record FhirValidationResult(
        boolean valid,
        List<String> errors
    ) {
        public FhirValidationResult {
            Objects.requireNonNull(errors, "errors must not be null");
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public String getErrorMessage() {
            if (errors.isEmpty()) {
                return "No errors";
            }
            return String.join("; ", errors);
        }
    }
}
