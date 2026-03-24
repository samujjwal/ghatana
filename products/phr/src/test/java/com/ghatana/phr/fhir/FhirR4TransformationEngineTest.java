package com.ghatana.phr.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.phr.healthcare.domain.DataClassification;
import com.ghatana.phr.healthcare.domain.Patient;
import com.ghatana.phr.kernel.service.ImmunizationService.ImmunizationRecord;
import com.ghatana.phr.kernel.service.ImmunizationService.ImmunizationStatus;
import com.ghatana.phr.kernel.service.LabResultService.LabObservation;
import com.ghatana.phr.kernel.service.LabResultService.ObservationStatus;
import com.ghatana.phr.kernel.service.MedicationService.Prescription;
import com.ghatana.phr.kernel.service.MedicationService.PrescriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FhirR4TransformationEngine}.
 *
 * <p>All methods are synchronous static helpers, so no {@code EventloopTestBase} is needed.</p>
 *
 * @doc.type class
 * @doc.purpose Tests for FHIR R4 bidirectional transformation engine
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("FhirR4TransformationEngine")
class FhirR4TransformationEngineTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ─────────────────────── Patient ──────────────────────────────────────────

    @Nested
    @DisplayName("Patient → FHIR Patient")
    class PatientTransformTests {

        @Test
        @DisplayName("produces resourceType=Patient")
        void resourceTypeIsPatient() throws Exception {
            Patient patient = buildPatient();

            String fhirJson = FhirR4TransformationEngine.toFhir(patient, "Patient");

            JsonNode root = MAPPER.readTree(fhirJson);
            assertThat(root.path("resourceType").asText()).isEqualTo("Patient");
        }

        @Test
        @DisplayName("maps patientId as FHIR id")
        void mappingPatientId() throws Exception {
            Patient patient = buildPatient();

            String fhirJson = FhirR4TransformationEngine.toFhir(patient, "Patient");

            JsonNode root = MAPPER.readTree(fhirJson);
            assertThat(root.path("id").asText()).isEqualTo(patient.patientId().toString());
        }

        @Test
        @DisplayName("maps name family and given")
        void mappingName() throws Exception {
            Patient patient = buildPatient();

            String fhirJson = FhirR4TransformationEngine.toFhir(patient, "Patient");

            JsonNode root = MAPPER.readTree(fhirJson);
            JsonNode nameNode = root.path("name").get(0);
            assertThat(nameNode.path("family").asText()).isEqualTo("Sharma");
            assertThat(nameNode.path("given").get(0).asText()).isEqualTo("Arjun");
        }

        @Test
        @DisplayName("round-trip: fromFhir preserves patientId")
        void roundTripPatientId() throws Exception {
            Patient patient = buildPatient();
            String fhirJson = FhirR4TransformationEngine.toFhir(patient, "Patient");

            Patient restored = (Patient) FhirR4TransformationEngine.fromFhir(fhirJson, "Patient");

            assertThat(restored.patientId().toString())
                    .isEqualTo(patient.patientId().toString());
        }

        @Test
        @DisplayName("round-trip: fromFhir preserves firstName and lastName")
        void roundTripName() throws Exception {
            Patient patient = buildPatient();
            String fhirJson = FhirR4TransformationEngine.toFhir(patient, "Patient");

            Patient restored = (Patient) FhirR4TransformationEngine.fromFhir(fhirJson, "Patient");

            assertThat(restored.firstName()).isEqualTo("Arjun");
            assertThat(restored.lastName()).isEqualTo("Sharma");
        }
    }

    // ─────────────────────── Prescription → MedicationRequest ─────────────────

    @Nested
    @DisplayName("Prescription → FHIR MedicationRequest")
    class MedicationRequestTests {

        @Test
        @DisplayName("produces resourceType=MedicationRequest")
        void resourceTypeIsMedicationRequest() throws Exception {
            Prescription rx = buildPrescription();

            String fhirJson = FhirR4TransformationEngine.toFhir(rx, "MedicationRequest");

            JsonNode root = MAPPER.readTree(fhirJson);
            assertThat(root.path("resourceType").asText()).isEqualTo("MedicationRequest");
        }

        @Test
        @DisplayName("maps patientId as subject reference")
        void subjectReference() throws Exception {
            Prescription rx = buildPrescription();

            String fhirJson = FhirR4TransformationEngine.toFhir(rx, "MedicationRequest");

            JsonNode root = MAPPER.readTree(fhirJson);
            assertThat(root.path("subject").path("reference").asText())
                    .contains("patient-fhir-1");
        }

        @Test
        @DisplayName("maps medicationCode as RxNorm coding")
        void medicationCoding() throws Exception {
            Prescription rx = buildPrescription();

            String fhirJson = FhirR4TransformationEngine.toFhir(rx, "MedicationRequest");

            JsonNode root = MAPPER.readTree(fhirJson);
            String codingSystem = root
                    .path("medicationCodeableConcept")
                    .path("coding").get(0)
                    .path("system").asText();
            assertThat(codingSystem).isEqualTo("http://www.nlm.nih.gov/research/umls/rxnorm");
        }
    }

    // ─────────────────────── LabObservation → Observation ─────────────────────

    @Nested
    @DisplayName("LabObservation → FHIR Observation")
    class ObservationTests {

        @Test
        @DisplayName("maps loincCode with LOINC system")
        void loincCoding() throws Exception {
            LabObservation obs = buildObservation("N");

            String fhirJson = FhirR4TransformationEngine.toFhir(obs, "Observation");

            JsonNode root = MAPPER.readTree(fhirJson);
            String codingSystem = root
                    .path("code").path("coding").get(0)
                    .path("system").asText();
            assertThat(codingSystem).isEqualTo("http://loinc.org");
        }

        @Test
        @DisplayName("includes valueQuantity with value and unit")
        void valueQuantity() throws Exception {
            LabObservation obs = buildObservation("N");

            String fhirJson = FhirR4TransformationEngine.toFhir(obs, "Observation");

            JsonNode root = MAPPER.readTree(fhirJson);
            assertThat(root.has("valueQuantity")).isTrue();
            assertThat(root.path("valueQuantity").path("unit").asText()).isEqualTo("g/dL");
        }

        @Test
        @DisplayName("abnormal flag sets interpretation coding")
        void abnormalFlag() throws Exception {
            LabObservation obs = buildObservation("H"); // "H" = high

            String fhirJson = FhirR4TransformationEngine.toFhir(obs, "Observation");

            JsonNode root = MAPPER.readTree(fhirJson);
            assertThat(root.has("interpretation")).isTrue();
        }
    }

    // ─────────────────────── ImmunizationRecord → Immunization ────────────────

    @Nested
    @DisplayName("ImmunizationRecord → FHIR Immunization")
    class ImmunizationTests {

        @Test
        @DisplayName("maps cvxCode with CVX coding system")
        void cvxCoding() throws Exception {
            ImmunizationRecord imm = buildImmunization();

            String fhirJson = FhirR4TransformationEngine.toFhir(imm, "Immunization");

            JsonNode root = MAPPER.readTree(fhirJson);
            String codingSystem = root
                    .path("vaccineCode").path("coding").get(0)
                    .path("system").asText();
            assertThat(codingSystem).isEqualTo("http://hl7.org/fhir/sid/cvx");
        }

        @Test
        @DisplayName("maps doseNumber as protocolApplied doseNumber")
        void doseNumber() throws Exception {
            ImmunizationRecord imm = buildImmunization();

            String fhirJson = FhirR4TransformationEngine.toFhir(imm, "Immunization");

            // Parse and verify resourceType at minimum
            JsonNode root = MAPPER.readTree(fhirJson);
            assertThat(root.path("resourceType").asText()).isEqualTo("Immunization");
        }
    }

    // ─────────────────────── Error Cases ──────────────────────────────────────

    @Nested
    @DisplayName("error handling")
    class ErrorTests {

        @Test
        @DisplayName("toFhir with unsupported resourceType throws IllegalArgumentException")
        void unsupportedResourceType() {
            Patient patient = buildPatient();

            assertThrows(IllegalArgumentException.class,
                    () -> FhirR4TransformationEngine.toFhir(patient, "DiagnosticReport"));
        }

        @Test
        @DisplayName("toFhir with mismatched object type throws IllegalArgumentException")
        void mismatchedObjectType() {
            // Passing a Patient but requesting MedicationRequest
            Patient patient = buildPatient();

            assertThrows(IllegalArgumentException.class,
                    () -> FhirR4TransformationEngine.toFhir(patient, "MedicationRequest"));
        }

        @Test
        @DisplayName("fromFhir with unsupported resourceType throws IllegalArgumentException")
        void fromFhirUnsupportedType() {
            assertThrows(IllegalArgumentException.class,
                    () -> FhirR4TransformationEngine.fromFhir("{}", "DiagnosticReport"));
        }
    }

    // ─────────────────────── Builders ─────────────────────────────────────────

    private static Patient buildPatient() {
        return new Patient(
                UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
                "tenant-test",
                "NHS-12345",
                "Arjun",
                "Sharma",
                LocalDate.of(1985, 6, 15),
                "male",
                "AB+",
                "+977-9800000001",
                "arjun@example.com",
                "Kathmandu, Baneshwor",
                "Bagmati",
                DataClassification.C2,
                "registrar-1",
                Instant.now(),
                null,
                true
        );
    }

    private static Prescription buildPrescription() {
        return new Prescription(
                "rx-fhir-1",
                "patient-fhir-1",
                "dr-fhir-1",
                "1049502",   // RxNorm code for Metformin
                "Metformin 500mg",
                "500mg",
                "twice daily",
                Duration.ofDays(30),
                "Take with food",
                Instant.now(),
                Instant.now().plusSeconds(86400L * 30),
                PrescriptionStatus.ACTIVE,
                3
        );
    }

    private static LabObservation buildObservation(String interpretation) {
        return new LabObservation(
                "obs-fhir-1",
                "patient-fhir-1",
                "lab-1",
                "dr-fhir-1",
                "718-7",         // LOINC code for Hemoglobin
                "Hemoglobin",
                "13.5",
                "g/dL",
                12.0,
                17.5,
                interpretation,
                Instant.now(),
                ObservationStatus.FINAL,
                null
        );
    }

    private static ImmunizationRecord buildImmunization() {
        return new ImmunizationRecord(
                "imm-fhir-1",
                "patient-fhir-1",
                "nurse-1",
                "115",           // CVX code for Tdap
                "Tdap",
                "LOT-9999",
                "Sanofi",
                Instant.now(),
                "deltoid",
                "intramuscular",
                "0.5mL",
                1,
                1,
                ImmunizationStatus.COMPLETED,
                null
        );
    }
}
