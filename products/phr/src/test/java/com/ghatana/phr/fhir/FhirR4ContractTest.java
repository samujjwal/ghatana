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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FHIR R4 contract tests — validates that serialized output conforms to the
 * FHIR R4 specification (required fields, cardinality, value-set bindings).
 *
 * <p>These are schema-level contract tests, not round-trip unit tests. They
 * guarantee that any FHIR consumer receiving our output can parse it
 * correctly per the HL7 FHIR R4 specification.</p>
 *
 * @doc.type class
 * @doc.purpose FHIR R4 schema contract validation for Patient, MedicationRequest, Observation, Immunization
 * @doc.layer product
 * @doc.pattern ContractTest
 */
@DisplayName("FHIR R4 Contract Tests")
class FhirR4ContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String LOINC_SYSTEM = "http://loinc.org";
    private static final String CVX_SYSTEM = "http://hl7.org/fhir/sid/cvx";
    private static final String RXNORM_SYSTEM = "http://www.nlm.nih.gov/research/umls/rxnorm";
    private static final String UCUM_SYSTEM = "http://unitsofmeasure.org";

    // ─────────────────────── FHIR Patient Contract ───────────────────────

    @Nested
    @DisplayName("Patient resource contract")
    class PatientContract {

        @Test
        @DisplayName("MUST include resourceType=Patient (FHIR base requirement)")
        void hasResourceType() throws Exception {
            JsonNode root = serializePatient();
            assertThat(root.path("resourceType").asText()).isEqualTo("Patient");
        }

        @Test
        @DisplayName("MUST include id field")
        void hasId() throws Exception {
            JsonNode root = serializePatient();
            assertThat(root.path("id").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("MUST include meta.lastUpdated as ISO datetime")
        void hasMetaLastUpdated() throws Exception {
            JsonNode root = serializePatient();
            assertThat(root.path("meta").path("lastUpdated").asText()).matches("\\d{4}-\\d{2}-\\d{2}T.*");
        }

        @Test
        @DisplayName("name array MUST contain at least one entry with family")
        void hasNameWithFamily() throws Exception {
            JsonNode root = serializePatient();
            JsonNode name = root.path("name");
            assertThat(name.isArray()).isTrue();
            assertThat(name.size()).isGreaterThanOrEqualTo(1);
            assertThat(name.get(0).path("family").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("name.given MUST be an array")
        void givenIsArray() throws Exception {
            JsonNode root = serializePatient();
            assertThat(root.path("name").get(0).path("given").isArray()).isTrue();
        }

        @Test
        @DisplayName("gender MUST be one of male|female|other|unknown when present")
        void genderValueSet() throws Exception {
            JsonNode root = serializePatient();
            String gender = root.path("gender").asText("");
            if (!gender.isEmpty()) {
                assertThat(gender).isIn("male", "female", "other", "unknown");
            }
        }

        @Test
        @DisplayName("birthDate MUST match yyyy-MM-dd format when present")
        void birthDateFormat() throws Exception {
            JsonNode root = serializePatient();
            String dob = root.path("birthDate").asText("");
            if (!dob.isEmpty()) {
                assertThat(dob).matches("\\d{4}-\\d{2}-\\d{2}");
            }
        }

        @Test
        @DisplayName("active MUST be a boolean")
        void activeIsBoolean() throws Exception {
            JsonNode root = serializePatient();
            assertThat(root.path("active").isBoolean()).isTrue();
        }

        @Test
        @DisplayName("telecom entries MUST have system and value")
        void telecomStructure() throws Exception {
            JsonNode root = serializePatient();
            JsonNode telecoms = root.path("telecom");
            if (telecoms.isArray()) {
                for (JsonNode t : telecoms) {
                    assertThat(t.path("system").asText()).isIn("phone", "email", "fax", "pager", "url", "sms", "other");
                    assertThat(t.path("value").asText()).isNotEmpty();
                }
            }
        }

        private JsonNode serializePatient() throws Exception {
            Patient patient = new Patient(
                    UUID.randomUUID(), "tenant-1", "NHS-12345",
                    "Arjun", "Sharma",
                    LocalDate.of(1990, 5, 15), "male",
                    "+977-984-1234567", "arjun@example.com",
                    "Kathmandu", "3", null,
                    DataClassification.C3, "system",
                    Instant.now(), null, true
            );
            return MAPPER.readTree(FhirR4TransformationEngine.toFhir(patient, "Patient"));
        }
    }

    // ─────────────────────── FHIR MedicationRequest Contract ─────────────

    @Nested
    @DisplayName("MedicationRequest resource contract")
    class MedicationRequestContract {

        @Test
        @DisplayName("MUST include resourceType=MedicationRequest")
        void hasResourceType() throws Exception {
            JsonNode root = serializePrescription();
            assertThat(root.path("resourceType").asText()).isEqualTo("MedicationRequest");
        }

        @Test
        @DisplayName("status MUST be a valid MedicationRequest status code")
        void statusValueSet() throws Exception {
            JsonNode root = serializePrescription();
            assertThat(root.path("status").asText()).isIn(
                    "active", "on-hold", "cancelled", "completed",
                    "entered-in-error", "stopped", "draft", "unknown"
            );
        }

        @Test
        @DisplayName("intent MUST be present (R4 required)")
        void hasIntent() throws Exception {
            JsonNode root = serializePrescription();
            assertThat(root.path("intent").asText()).isIn(
                    "proposal", "plan", "order", "original-order",
                    "reflex-order", "filler-order", "instance-order", "option"
            );
        }

        @Test
        @DisplayName("medicationCodeableConcept.coding MUST use RxNorm system")
        void medicationUsesRxNorm() throws Exception {
            JsonNode root = serializePrescription();
            JsonNode coding = root.path("medicationCodeableConcept").path("coding");
            assertThat(coding.isArray()).isTrue();
            assertThat(coding.get(0).path("system").asText()).isEqualTo(RXNORM_SYSTEM);
            assertThat(coding.get(0).path("code").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("subject reference MUST follow Patient/{id} format")
        void subjectReferenceFormat() throws Exception {
            JsonNode root = serializePrescription();
            assertThat(root.path("subject").path("reference").asText())
                    .startsWith("Patient/");
        }

        @Test
        @DisplayName("authoredOn MUST be ISO datetime when present")
        void authoredOnFormat() throws Exception {
            JsonNode root = serializePrescription();
            String authored = root.path("authoredOn").asText("");
            if (!authored.isEmpty()) {
                assertThat(authored).matches("\\d{4}-\\d{2}-\\d{2}T.*");
            }
        }

        private JsonNode serializePrescription() throws Exception {
            Prescription rx = new Prescription(
                    "rx-001", "patient-1", "dr-1", "enc-1",
                    "197361", "Amoxicillin 500mg",
                    "500mg every 8 hours",
                    "Upper respiratory infection",
                    Instant.now(), Instant.now().plus(java.time.Duration.ofDays(30)),
                    2, java.time.Duration.ofDays(10),
                    PrescriptionStatus.ACTIVE
            );
            return MAPPER.readTree(FhirR4TransformationEngine.toFhir(rx, "MedicationRequest"));
        }
    }

    // ─────────────────────── FHIR Observation Contract ───────────────────

    @Nested
    @DisplayName("Observation resource contract")
    class ObservationContract {

        @Test
        @DisplayName("MUST include resourceType=Observation")
        void hasResourceType() throws Exception {
            JsonNode root = serializeLabObservation();
            assertThat(root.path("resourceType").asText()).isEqualTo("Observation");
        }

        @Test
        @DisplayName("status MUST be a valid Observation status code")
        void statusValueSet() throws Exception {
            JsonNode root = serializeLabObservation();
            assertThat(root.path("status").asText()).isIn(
                    "registered", "preliminary", "final",
                    "amended", "corrected", "cancelled",
                    "entered-in-error", "unknown"
            );
        }

        @Test
        @DisplayName("category MUST include 'laboratory' for lab observations")
        void categoryIsLaboratory() throws Exception {
            JsonNode root = serializeLabObservation();
            JsonNode categories = root.path("category");
            assertThat(categories.isArray()).isTrue();
            boolean hasLab = false;
            for (JsonNode cat : categories) {
                for (JsonNode coding : cat.path("coding")) {
                    if ("laboratory".equals(coding.path("code").asText())) {
                        hasLab = true;
                    }
                }
            }
            assertThat(hasLab).isTrue();
        }

        @Test
        @DisplayName("code.coding MUST use LOINC system")
        void codeUsesLoinc() throws Exception {
            JsonNode root = serializeLabObservation();
            JsonNode coding = root.path("code").path("coding");
            assertThat(coding.isArray()).isTrue();
            assertThat(coding.get(0).path("system").asText()).isEqualTo(LOINC_SYSTEM);
            assertThat(coding.get(0).path("code").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("valueQuantity.system MUST be UCUM when present")
        void valueQuantityUsesUcum() throws Exception {
            JsonNode root = serializeLabObservation();
            JsonNode vq = root.path("valueQuantity");
            if (!vq.isMissingNode()) {
                assertThat(vq.path("system").asText()).isEqualTo(UCUM_SYSTEM);
                assertThat(vq.path("value").isNumber()).isTrue();
            }
        }

        @Test
        @DisplayName("subject reference MUST follow Patient/{id} format")
        void subjectReferenceFormat() throws Exception {
            JsonNode root = serializeLabObservation();
            assertThat(root.path("subject").path("reference").asText())
                    .startsWith("Patient/");
        }

        @Test
        @DisplayName("interpretation coding uses v3-ObservationInterpretation system for abnormal")
        void interpretationValueSet() throws Exception {
            JsonNode root = serializeLabObservation();
            JsonNode interp = root.path("interpretation");
            if (interp.isArray() && interp.size() > 0) {
                String system = interp.get(0).path("coding").get(0).path("system").asText();
                assertThat(system).contains("ObservationInterpretation");
            }
        }

        private JsonNode serializeLabObservation() throws Exception {
            LabObservation obs = new LabObservation(
                    "obs-001", "patient-1", "enc-1", "order-1",
                    "26515-7", "Platelets", "Platelet count",
                    new BigDecimal("245"), 150.0,
                    "10*3/uL", "150-400 10*3/uL",
                    "lab-central",
                    Instant.now(), Instant.now(),
                    ObservationStatus.FINAL, null,
                    "A"
            );
            return MAPPER.readTree(FhirR4TransformationEngine.toFhir(obs, "Observation"));
        }
    }

    // ─────────────────────── FHIR Immunization Contract ──────────────────

    @Nested
    @DisplayName("Immunization resource contract")
    class ImmunizationContract {

        @Test
        @DisplayName("MUST include resourceType=Immunization")
        void hasResourceType() throws Exception {
            JsonNode root = serializeImmunization();
            assertThat(root.path("resourceType").asText()).isEqualTo("Immunization");
        }

        @Test
        @DisplayName("status MUST be one of completed|entered-in-error|not-done")
        void statusValueSet() throws Exception {
            JsonNode root = serializeImmunization();
            assertThat(root.path("status").asText()).isIn(
                    "completed", "entered-in-error", "not-done"
            );
        }

        @Test
        @DisplayName("vaccineCode.coding MUST use CVX system")
        void vaccineCodeUsesCvx() throws Exception {
            JsonNode root = serializeImmunization();
            JsonNode coding = root.path("vaccineCode").path("coding");
            assertThat(coding.isArray()).isTrue();
            assertThat(coding.get(0).path("system").asText()).isEqualTo(CVX_SYSTEM);
            assertThat(coding.get(0).path("code").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("patient reference MUST follow Patient/{id} format")
        void patientReferenceFormat() throws Exception {
            JsonNode root = serializeImmunization();
            assertThat(root.path("patient").path("reference").asText())
                    .startsWith("Patient/");
        }

        @Test
        @DisplayName("occurrenceDateTime MUST be ISO datetime when present")
        void occurrenceDateTimeFormat() throws Exception {
            JsonNode root = serializeImmunization();
            String occ = root.path("occurrenceDateTime").asText("");
            if (!occ.isEmpty()) {
                assertThat(occ).matches("\\d{4}-\\d{2}-\\d{2}T.*");
            }
        }

        @Test
        @DisplayName("protocolApplied MUST include doseNumberPositiveInt (R4 required)")
        void protocolHasDoseNumber() throws Exception {
            JsonNode root = serializeImmunization();
            JsonNode protocol = root.path("protocolApplied");
            assertThat(protocol.isArray()).isTrue();
            assertThat(protocol.size()).isGreaterThanOrEqualTo(1);
            assertThat(protocol.get(0).path("doseNumberPositiveInt").isInt()).isTrue();
        }

        @Test
        @DisplayName("primarySource MUST be present as boolean")
        void hasPrimarySource() throws Exception {
            JsonNode root = serializeImmunization();
            assertThat(root.path("primarySource").isBoolean()).isTrue();
        }

        private JsonNode serializeImmunization() throws Exception {
            ImmunizationRecord imm = new ImmunizationRecord(
                    "imm-001", "patient-1", "enc-1",
                    "08", "Hepatitis B",
                    "nurse-1",
                    Instant.now(), Instant.now(),
                    "LOT-2026-A", LocalDate.of(2028, 12, 31),
                    "IM", "Hep B 3-dose",
                    2, false, null,
                    ImmunizationStatus.ADMINISTERED
            );
            return MAPPER.readTree(FhirR4TransformationEngine.toFhir(imm, "Immunization"));
        }
    }

    // ─────────────────────── Cross-resource contract rules ───────────────

    @Nested
    @DisplayName("Cross-resource contracts")
    class CrossResourceContracts {

        @Test
        @DisplayName("unsupported resource type throws IllegalArgumentException")
        void unsupportedResourceType() {
            Patient patient = new Patient(
                    UUID.randomUUID(), "t", null,
                    "A", "B", LocalDate.now(), null,
                    null, null, null, null, null,
                    DataClassification.C1, "sys", Instant.now(), null, true
            );
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> FhirR4TransformationEngine.toFhir(patient, "Encounter")
            );
        }

        @Test
        @DisplayName("type mismatch throws IllegalArgumentException")
        void typeMismatch() {
            Patient patient = new Patient(
                    UUID.randomUUID(), "t", null,
                    "A", "B", LocalDate.now(), null,
                    null, null, null, null, null,
                    DataClassification.C1, "sys", Instant.now(), null, true
            );
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> FhirR4TransformationEngine.toFhir(patient, "MedicationRequest")
            );
        }
    }
}
