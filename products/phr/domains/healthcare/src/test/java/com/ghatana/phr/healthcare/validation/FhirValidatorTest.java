package com.ghatana.phr.healthcare.validation;

import com.ghatana.phr.healthcare.domain.DataClassification;
import com.ghatana.phr.healthcare.domain.Patient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FhirValidator}.
 *
 * @doc.type test
 * @doc.purpose Verifies FHIR patient validation for the PHR healthcare domain
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("FHIR Validator")
class FhirValidatorTest {

    private final FhirValidator validator = new FhirValidator();

    @Test
    @DisplayName("Should accept patient with supported Nepal NHS identifier")
    void shouldAcceptPatientWithSupportedNepalNhsIdentifier() {
        FhirValidator.FhirValidationResult result = validator.validatePatient(patient("NHS-12345", LocalDate.of(1991, 5, 10)));

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("Should reject lowercase NHS identifier")
    void shouldRejectLowercaseNhsIdentifier() {
        FhirValidator.FhirValidationResult result = validator.validatePatient(patient("nhs-12345", LocalDate.of(1991, 5, 10)));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Patient.identifier (NHS ID) format is invalid");
    }

    @Test
    @DisplayName("Should reject NHS identifier with empty segment")
    void shouldRejectNhsIdentifierWithEmptySegment() {
        FhirValidator.FhirValidationResult result = validator.validatePatient(patient("NHS--12345", LocalDate.of(1991, 5, 10)));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Patient.identifier (NHS ID) format is invalid");
    }

    @Test
    @DisplayName("Should reject future birth date")
    void shouldRejectFutureBirthDate() {
        FhirValidator.FhirValidationResult result = validator.validatePatient(patient("NHS-12345", LocalDate.now().plusDays(1)));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Patient.birthDate cannot be in the future");
    }

    private static Patient patient(String nhsId, LocalDate dateOfBirth) {
        return new Patient(
            UUID.randomUUID(),
            "tenant-123",
            nhsId,
            "Maya",
            "Shrestha",
            dateOfBirth,
            "female",
            null,
            null,
            null,
            null,
            "3",
            DataClassification.C2,
            "clinician-1",
            Instant.now().minusSeconds(86_400),
            null,
            true
        );
    }
}
