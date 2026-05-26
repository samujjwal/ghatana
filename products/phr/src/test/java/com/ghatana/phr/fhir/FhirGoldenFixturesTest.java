package com.ghatana.phr.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden FHIR R4 fixture validation tests.
 *
 * <p>These tests validate that the golden FHIR fixtures in src/test/resources/fhir/golden/
 * conform to the FHIR R4 specification. The fixtures serve as canonical examples for:
 * <ul>
 *   <li>Patient resources</li>
 *   <li>Observation (lab) resources</li>
 *   <li>MedicationRequest resources</li>
 *   <li>Immunization resources</li>
 *   <li>ImagingStudy resources</li>
 *   <li>ClinicalImpression (clinical notes) resources</li>
 *   <li>ServiceRequest (referrals) resources</li>
 * </ul>
 *
 * These fixtures are used for contract validation, interoperability testing, and as
 * reference implementations for FHIR resource generation in PHR.</p>
 *
 * <p>Golden fixtures validated:
 * <ul>
 *   <li>Patient resources</li>
 *   <li>Observation (lab) resources</li>
 *   <li>MedicationRequest resources</li>
 *   <li>Immunization resources</li>
 *   <li>ImagingStudy resources</li>
 *   <li>ClinicalImpression (clinical notes) resources</li>
 *   <li>ServiceRequest (referrals) resources</li>
 *   <li>DiagnosticReport resources</li>
 *   <li>DocumentReference resources</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Validates golden FHIR R4 fixtures against specification
 * @doc.layer product
 * @doc.pattern ContractTest
 */
@DisplayName("FHIR Golden Fixtures Validation")
class FhirGoldenFixturesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GOLDEN_FIXTURES_PATH = "src/test/resources/fhir/golden";

    @Nested
    @DisplayName("Patient golden fixture")
    class PatientGoldenFixture {

        @Test
        @DisplayName("patient-golden.json exists and is valid JSON")
        void fixtureExists() throws Exception {
            JsonNode root = loadFixture("patient-golden.json");
            assertThat(root).isNotNull();
        }

        @Test
        @DisplayName("resourceType is Patient")
        void hasResourceType() throws Exception {
            JsonNode root = loadFixture("patient-golden.json");
            assertThat(root.path("resourceType").asText()).isEqualTo("Patient");
        }

        @Test
        @DisplayName("has required identifier with NHS number system")
        void hasIdentifier() throws Exception {
            JsonNode root = loadFixture("patient-golden.json");
            JsonNode identifiers = root.path("identifier");
            assertThat(identifiers.isArray()).isTrue();
            assertThat(identifiers.size()).isGreaterThan(0);
            
            JsonNode identifier = identifiers.get(0);
            assertThat(identifier.path("system").asText()).isEqualTo("https://fhir.nhs.uk/Id/nhs-number");
            assertThat(identifier.path("value").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("has name with family and given")
        void hasName() throws Exception {
            JsonNode root = loadFixture("patient-golden.json");
            JsonNode names = root.path("name");
            assertThat(names.isArray()).isTrue();
            assertThat(names.size()).isGreaterThan(0);
            
            JsonNode name = names.get(0);
            assertThat(name.path("family").asText()).isNotEmpty();
            assertThat(name.path("given").isArray()).isTrue();
            assertThat(name.path("given").size()).isGreaterThan(0);
        }

        @Test
        @DisplayName("gender is valid value")
        void hasValidGender() throws Exception {
            JsonNode root = loadFixture("patient-golden.json");
            String gender = root.path("gender").asText();
            assertThat(gender).isIn("male", "female", "other", "unknown");
        }

        @Test
        @DisplayName("birthDate matches yyyy-MM-dd format")
        void hasBirthDate() throws Exception {
            JsonNode root = loadFixture("patient-golden.json");
            String birthDate = root.path("birthDate").asText();
            assertThat(birthDate).matches("\\d{4}-\\d{2}-\\d{2}");
        }

        @Test
        @DisplayName("telecom entries have system and value")
        void hasTelecom() throws Exception {
            JsonNode root = loadFixture("patient-golden.json");
            JsonNode telecoms = root.path("telecom");
            assertThat(telecoms.isArray()).isTrue();
            
            for (JsonNode telecom : telecoms) {
                assertThat(telecom.path("system").asText()).isIn("phone", "email", "fax", "pager", "url", "sms", "other");
                assertThat(telecom.path("value").asText()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("address has required fields")
        void hasAddress() throws Exception {
            JsonNode root = loadFixture("patient-golden.json");
            JsonNode addresses = root.path("address");
            assertThat(addresses.isArray()).isTrue();
            assertThat(addresses.size()).isGreaterThan(0);
            
            JsonNode address = addresses.get(0);
            assertThat(address.path("city").asText()).isNotEmpty();
            assertThat(address.path("country").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("active is boolean")
        void hasActive() throws Exception {
            JsonNode root = loadFixture("patient-golden.json");
            assertThat(root.path("active").isBoolean()).isTrue();
        }
    }

    @Nested
    @DisplayName("Observation (lab) golden fixture")
    class ObservationLabGoldenFixture {

        @Test
        @DisplayName("observation-lab-golden.json exists and is valid JSON")
        void fixtureExists() throws Exception {
            JsonNode root = loadFixture("observation-lab-golden.json");
            assertThat(root).isNotNull();
        }

        @Test
        @DisplayName("resourceType is Observation")
        void hasResourceType() throws Exception {
            JsonNode root = loadFixture("observation-lab-golden.json");
            assertThat(root.path("resourceType").asText()).isEqualTo("Observation");
        }

        @Test
        @DisplayName("status is valid Observation status")
        void hasValidStatus() throws Exception {
            JsonNode root = loadFixture("observation-lab-golden.json");
            String status = root.path("status").asText();
            assertThat(status).isIn("registered", "preliminary", "final", "amended", "corrected", "cancelled", "entered-in-error", "unknown");
        }

        @Test
        @DisplayName("category includes laboratory")
        void hasLaboratoryCategory() throws Exception {
            JsonNode root = loadFixture("observation-lab-golden.json");
            JsonNode categories = root.path("category");
            assertThat(categories.isArray()).isTrue();
            
            boolean hasLab = false;
            for (JsonNode category : categories) {
                for (JsonNode coding : category.path("coding")) {
                    if ("laboratory".equals(coding.path("code").asText())) {
                        hasLab = true;
                    }
                }
            }
            assertThat(hasLab).isTrue();
        }

        @Test
        @DisplayName("code uses LOINC system")
        void codeUsesLoinc() throws Exception {
            JsonNode root = loadFixture("observation-lab-golden.json");
            JsonNode coding = root.path("code").path("coding");
            assertThat(coding.isArray()).isTrue();
            assertThat(coding.get(0).path("system").asText()).isEqualTo("http://loinc.org");
            assertThat(coding.get(0).path("code").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("valueQuantity uses UCUM system")
        void valueQuantityUsesUcum() throws Exception {
            JsonNode root = loadFixture("observation-lab-golden.json");
            JsonNode valueQuantity = root.path("valueQuantity");
            assertThat(valueQuantity.path("system").asText()).isEqualTo("http://unitsofmeasure.org");
            assertThat(valueQuantity.path("value").isNumber()).isTrue();
        }

        @Test
        @DisplayName("subject reference follows Patient/{id} format")
        void subjectReferenceFormat() throws Exception {
            JsonNode root = loadFixture("observation-lab-golden.json");
            assertThat(root.path("subject").path("reference").asText()).startsWith("Patient/");
        }

        @Test
        @DisplayName("referenceRange has low and high")
        void hasReferenceRange() throws Exception {
            JsonNode root = loadFixture("observation-lab-golden.json");
            JsonNode referenceRanges = root.path("referenceRange");
            assertThat(referenceRanges.isArray()).isTrue();
            assertThat(referenceRanges.size()).isGreaterThan(0);
            
            JsonNode range = referenceRanges.get(0);
            assertThat(range.path("low").path("value").isNumber()).isTrue();
            assertThat(range.path("high").path("value").isNumber()).isTrue();
        }
    }

    @Nested
    @DisplayName("MedicationRequest golden fixture")
    class MedicationRequestGoldenFixture {

        @Test
        @DisplayName("medication-request-golden.json exists and is valid JSON")
        void fixtureExists() throws Exception {
            JsonNode root = loadFixture("medication-request-golden.json");
            assertThat(root).isNotNull();
        }

        @Test
        @DisplayName("resourceType is MedicationRequest")
        void hasResourceType() throws Exception {
            JsonNode root = loadFixture("medication-request-golden.json");
            assertThat(root.path("resourceType").asText()).isEqualTo("MedicationRequest");
        }

        @Test
        @DisplayName("status is valid MedicationRequest status")
        void hasValidStatus() throws Exception {
            JsonNode root = loadFixture("medication-request-golden.json");
            String status = root.path("status").asText();
            assertThat(status).isIn("active", "on-hold", "cancelled", "completed", "entered-in-error", "stopped", "draft", "unknown");
        }

        @Test
        @DisplayName("intent is present")
        void hasIntent() throws Exception {
            JsonNode root = loadFixture("medication-request-golden.json");
            String intent = root.path("intent").asText();
            assertThat(intent).isIn("proposal", "plan", "order", "original-order", "reflex-order", "filler-order", "instance-order", "option");
        }

        @Test
        @DisplayName("medicationCodeableConcept uses RxNorm system")
        void medicationUsesRxNorm() throws Exception {
            JsonNode root = loadFixture("medication-request-golden.json");
            JsonNode coding = root.path("medicationCodeableConcept").path("coding");
            assertThat(coding.isArray()).isTrue();
            assertThat(coding.get(0).path("system").asText()).isEqualTo("http://www.nlm.nih.gov/research/umls/rxnorm");
            assertThat(coding.get(0).path("code").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("dosageInstruction has required fields")
        void hasDosageInstruction() throws Exception {
            JsonNode root = loadFixture("medication-request-golden.json");
            JsonNode dosageInstructions = root.path("dosageInstruction");
            assertThat(dosageInstructions.isArray()).isTrue();
            assertThat(dosageInstructions.size()).isGreaterThan(0);
            
            JsonNode dosage = dosageInstructions.get(0);
            assertThat(dosage.path("text").asText()).isNotEmpty();
            assertThat(dosage.path("timing")).isNotNull();
        }

        @Test
        @DisplayName("dispenseRequest has quantity and duration")
        void hasDispenseRequest() throws Exception {
            JsonNode root = loadFixture("medication-request-golden.json");
            JsonNode dispenseRequest = root.path("dispenseRequest");
            assertThat(dispenseRequest.path("quantity").path("value").isNumber()).isTrue();
            assertThat(dispenseRequest.path("expectedSupplyDuration").path("value").isNumber()).isTrue();
        }
    }

    @Nested
    @DisplayName("Immunization golden fixture")
    class ImmunizationGoldenFixture {

        @Test
        @DisplayName("immunization-golden.json exists and is valid JSON")
        void fixtureExists() throws Exception {
            JsonNode root = loadFixture("immunization-golden.json");
            assertThat(root).isNotNull();
        }

        @Test
        @DisplayName("resourceType is Immunization")
        void hasResourceType() throws Exception {
            JsonNode root = loadFixture("immunization-golden.json");
            assertThat(root.path("resourceType").asText()).isEqualTo("Immunization");
        }

        @Test
        @DisplayName("status is valid Immunization status")
        void hasValidStatus() throws Exception {
            JsonNode root = loadFixture("immunization-golden.json");
            String status = root.path("status").asText();
            assertThat(status).isIn("completed", "entered-in-error", "not-done");
        }

        @Test
        @DisplayName("vaccineCode uses CVX system")
        void vaccineCodeUsesCvx() throws Exception {
            JsonNode root = loadFixture("immunization-golden.json");
            JsonNode coding = root.path("vaccineCode").path("coding");
            assertThat(coding.isArray()).isTrue();
            assertThat(coding.get(0).path("system").asText()).isEqualTo("http://hl7.org/fhir/sid/cvx");
            assertThat(coding.get(0).path("code").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("patient reference follows Patient/{id} format")
        void patientReferenceFormat() throws Exception {
            JsonNode root = loadFixture("immunization-golden.json");
            assertThat(root.path("patient").path("reference").asText()).startsWith("Patient/");
        }

        @Test
        @DisplayName("occurrenceDateTime is ISO datetime")
        void occurrenceDateTimeFormat() throws Exception {
            JsonNode root = loadFixture("immunization-golden.json");
            String occurrence = root.path("occurrenceDateTime").asText();
            assertThat(occurrence).matches("\\d{4}-\\d{2}-\\d{2}T.*");
        }

        @Test
        @DisplayName("primarySource is boolean")
        void hasPrimarySource() throws Exception {
            JsonNode root = loadFixture("immunization-golden.json");
            assertThat(root.path("primarySource").isBoolean()).isTrue();
        }

        @Test
        @DisplayName("protocolApplied has doseNumberPositiveInt")
        void hasProtocolApplied() throws Exception {
            JsonNode root = loadFixture("immunization-golden.json");
            JsonNode protocols = root.path("protocolApplied");
            assertThat(protocols.isArray()).isTrue();
            assertThat(protocols.size()).isGreaterThan(0);
            
            JsonNode protocol = protocols.get(0);
            assertThat(protocol.path("doseNumberPositiveInt").isInt()).isTrue();
        }

        @Test
        @DisplayName("has lotNumber and expirationDate")
        void hasLotAndExpiration() throws Exception {
            JsonNode root = loadFixture("immunization-golden.json");
            assertThat(root.path("lotNumber").asText()).isNotEmpty();
            assertThat(root.path("expirationDate").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
        }
    }

    @Nested
    @DisplayName("ImagingStudy golden fixture")
    class ImagingStudyGoldenFixture {

        @Test
        @DisplayName("imaging-study-golden.json exists and is valid JSON")
        void fixtureExists() throws Exception {
            JsonNode root = loadFixture("imaging-study-golden.json");
            assertThat(root).isNotNull();
        }

        @Test
        @DisplayName("resourceType is ImagingStudy")
        void hasResourceType() throws Exception {
            JsonNode root = loadFixture("imaging-study-golden.json");
            assertThat(root.path("resourceType").asText()).isEqualTo("ImagingStudy");
        }

        @Test
        @DisplayName("status is valid ImagingStudy status")
        void hasValidStatus() throws Exception {
            JsonNode root = loadFixture("imaging-study-golden.json");
            String status = root.path("status").asText();
            assertThat(status).isIn("registered", "available", "cancelled", "entered-in-error", "unknown");
        }

        @Test
        @DisplayName("modality uses DICOM system")
        void modalityUsesDicom() throws Exception {
            JsonNode root = loadFixture("imaging-study-golden.json");
            JsonNode modalities = root.path("modality");
            assertThat(modalities.isArray()).isTrue();
            
            JsonNode coding = modalities.get(0).path("coding").get(0);
            assertThat(coding.path("system").asText()).isEqualTo("http://dicom.nema.org/resources/ontology/DCM");
            assertThat(coding.path("code").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("subject reference follows Patient/{id} format")
        void subjectReferenceFormat() throws Exception {
            JsonNode root = loadFixture("imaging-study-golden.json");
            assertThat(root.path("subject").path("reference").asText()).startsWith("Patient/");
        }

        @Test
        @DisplayName("has series with instances")
        void hasSeries() throws Exception {
            JsonNode root = loadFixture("imaging-study-golden.json");
            JsonNode series = root.path("series");
            assertThat(series.isArray()).isTrue();
            assertThat(series.size()).isGreaterThan(0);
            
            JsonNode firstSeries = series.get(0);
            assertThat(firstSeries.path("uid").asText()).isNotEmpty();
            assertThat(firstSeries.path("instance").isArray()).isTrue();
        }
    }

    @Nested
    @DisplayName("ClinicalImpression (clinical note) golden fixture")
    class ClinicalNoteGoldenFixture {

        @Test
        @DisplayName("clinical-note-golden.json exists and is valid JSON")
        void fixtureExists() throws Exception {
            JsonNode root = loadFixture("clinical-note-golden.json");
            assertThat(root).isNotNull();
        }

        @Test
        @DisplayName("resourceType is ClinicalImpression")
        void hasResourceType() throws Exception {
            JsonNode root = loadFixture("clinical-note-golden.json");
            assertThat(root.path("resourceType").asText()).isEqualTo("ClinicalImpression");
        }

        @Test
        @DisplayName("status is valid ClinicalImpression status")
        void hasValidStatus() throws Exception {
            JsonNode root = loadFixture("clinical-note-golden.json");
            String status = root.path("status").asText();
            assertThat(status).isIn("in-progress", "completed", "entered-in-error");
        }

        @Test
        @DisplayName("description is present")
        void hasDescription() throws Exception {
            JsonNode root = loadFixture("clinical-note-golden.json");
            assertThat(root.path("description").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("subject reference follows Patient/{id} format")
        void subjectReferenceFormat() throws Exception {
            JsonNode root = loadFixture("clinical-note-golden.json");
            assertThat(root.path("subject").path("reference").asText()).startsWith("Patient/");
        }

        @Test
        @DisplayName("has findings")
        void hasFindings() throws Exception {
            JsonNode root = loadFixture("clinical-note-golden.json");
            JsonNode findings = root.path("finding");
            assertThat(findings.isArray()).isTrue();
            assertThat(findings.size()).isGreaterThan(0);
        }

        @Test
        @DisplayName("has note with author and time")
        void hasNote() throws Exception {
            JsonNode root = loadFixture("clinical-note-golden.json");
            JsonNode notes = root.path("note");
            assertThat(notes.isArray()).isTrue();
            assertThat(notes.size()).isGreaterThan(0);
            
            JsonNode note = notes.get(0);
            assertThat(note.path("authorReference").path("reference").asText()).startsWith("Practitioner/");
            assertThat(note.path("time").asText()).matches("\\d{4}-\\d{2}-\\d{2}T.*");
        }
    }

    @Nested
    @DisplayName("ServiceRequest (referral) golden fixture")
    class ServiceRequestReferralGoldenFixture {

        @Test
        @DisplayName("service-request-referral-golden.json exists and is valid JSON")
        void fixtureExists() throws Exception {
            JsonNode root = loadFixture("service-request-referral-golden.json");
            assertThat(root).isNotNull();
        }

        @Test
        @DisplayName("resourceType is ServiceRequest")
        void hasResourceType() throws Exception {
            JsonNode root = loadFixture("service-request-referral-golden.json");
            assertThat(root.path("resourceType").asText()).isEqualTo("ServiceRequest");
        }

        @Test
        @DisplayName("status is valid ServiceRequest status")
        void hasValidStatus() throws Exception {
            JsonNode root = loadFixture("service-request-referral-golden.json");
            String status = root.path("status").asText();
            assertThat(status).isIn("draft", "active", "on-hold", "revoked", "completed", "entered-in-error", "unknown");
        }

        @Test
        @DisplayName("intent is present")
        void hasIntent() throws Exception {
            JsonNode root = loadFixture("service-request-referral-golden.json");
            String intent = root.path("intent").asText();
            assertThat(intent).isIn("proposal", "plan", "order", "original-order", "reflex-order", "filler-order", "instance-order", "option");
        }

        @Test
        @DisplayName("category includes referral")
        void hasReferralCategory() throws Exception {
            JsonNode root = loadFixture("service-request-referral-golden.json");
            JsonNode categories = root.path("category");
            assertThat(categories.isArray()).isTrue();
            
            boolean hasReferral = false;
            for (JsonNode category : categories) {
                for (JsonNode coding : category.path("coding")) {
                    if ("103696004".equals(coding.path("code").asText())) {
                        hasReferral = true;
                    }
                }
            }
            assertThat(hasReferral).isTrue();
        }

        @Test
        @DisplayName("subject reference follows Patient/{id} format")
        void subjectReferenceFormat() throws Exception {
            JsonNode root = loadFixture("service-request-referral-golden.json");
            assertThat(root.path("subject").path("reference").asText()).startsWith("Patient/");
        }

        @Test
        @DisplayName("has performer reference")
        void hasPerformer() throws Exception {
            JsonNode root = loadFixture("service-request-referral-golden.json");
            JsonNode performers = root.path("performer");
            assertThat(performers.isArray()).isTrue();
            assertThat(performers.size()).isGreaterThan(0);
            
            assertThat(performers.get(0).path("reference").asText()).startsWith("Practitioner/");
        }

        @Test
        @DisplayName("has reasonCode")
        void hasReasonCode() throws Exception {
            JsonNode root = loadFixture("service-request-referral-golden.json");
            JsonNode reasonCodes = root.path("reasonCode");
            assertThat(reasonCodes.isArray()).isTrue();
            assertThat(reasonCodes.size()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("DiagnosticReport golden fixture")
    class DiagnosticReportGoldenFixture {

        @Test
        @DisplayName("diagnostic-report-golden.json exists and is valid JSON")
        void fixtureExists() throws Exception {
            JsonNode root = loadFixture("diagnostic-report-golden.json");
            assertThat(root).isNotNull();
        }

        @Test
        @DisplayName("resourceType is DiagnosticReport")
        void hasResourceType() throws Exception {
            JsonNode root = loadFixture("diagnostic-report-golden.json");
            assertThat(root.path("resourceType").asText()).isEqualTo("DiagnosticReport");
        }

        @Test
        @DisplayName("status is valid DiagnosticReport status")
        void hasValidStatus() throws Exception {
            JsonNode root = loadFixture("diagnostic-report-golden.json");
            String status = root.path("status").asText();
            assertThat(status).isIn("registered", "partial", "preliminary", "final", "amended", "corrected", "appended", "cancelled", "entered-in-error", "unknown");
        }

        @Test
        @DisplayName("code uses LOINC system")
        void codeUsesLoinc() throws Exception {
            JsonNode root = loadFixture("diagnostic-report-golden.json");
            JsonNode coding = root.path("code").path("coding");
            assertThat(coding.isArray()).isTrue();
            assertThat(coding.get(0).path("system").asText()).isEqualTo("http://loinc.org");
            assertThat(coding.get(0).path("code").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("subject reference follows Patient/{id} format")
        void subjectReferenceFormat() throws Exception {
            JsonNode root = loadFixture("diagnostic-report-golden.json");
            assertThat(root.path("subject").path("reference").asText()).startsWith("Patient/");
        }

        @Test
        @DisplayName("has result Observation reference")
        void hasResultObservationReference() throws Exception {
            JsonNode root = loadFixture("diagnostic-report-golden.json");
            JsonNode results = root.path("result");
            assertThat(results.isArray()).isTrue();
            assertThat(results.size()).isGreaterThan(0);
            assertThat(results.get(0).path("reference").asText()).startsWith("Observation/");
        }
    }

    @Nested
    @DisplayName("DocumentReference golden fixture")
    class DocumentReferenceGoldenFixture {

        @Test
        @DisplayName("document-reference-golden.json exists and is valid JSON")
        void fixtureExists() throws Exception {
            JsonNode root = loadFixture("document-reference-golden.json");
            assertThat(root).isNotNull();
        }

        @Test
        @DisplayName("resourceType is DocumentReference")
        void hasResourceType() throws Exception {
            JsonNode root = loadFixture("document-reference-golden.json");
            assertThat(root.path("resourceType").asText()).isEqualTo("DocumentReference");
        }

        @Test
        @DisplayName("status is valid DocumentReference status")
        void hasValidStatus() throws Exception {
            JsonNode root = loadFixture("document-reference-golden.json");
            String status = root.path("status").asText();
            assertThat(status).isIn("current", "superseded", "entered-in-error", "unknown");
        }

        @Test
        @DisplayName("docStatus is present")
        void hasDocStatus() throws Exception {
            JsonNode root = loadFixture("document-reference-golden.json");
            String docStatus = root.path("docStatus").asText();
            assertThat(docStatus).isIn("final", "amended", "entered-in-error", "preliminary", "cancelled");
        }

        @Test
        @DisplayName("type uses LOINC or SNOMED CT system")
        void typeUsesStandardSystem() throws Exception {
            JsonNode root = loadFixture("document-reference-golden.json");
            JsonNode coding = root.path("type").path("coding");
            assertThat(coding.isArray()).isTrue();
            assertThat(coding.get(0).path("system").asText()).isIn(
                "http://loinc.org",
                "http://snomed.info/sct"
            );
            assertThat(coding.get(0).path("code").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("subject reference follows Patient/{id} format")
        void subjectReferenceFormat() throws Exception {
            JsonNode root = loadFixture("document-reference-golden.json");
            assertThat(root.path("subject").path("reference").asText()).startsWith("Patient/");
        }

        @Test
        @DisplayName("has content with attachment")
        void hasContent() throws Exception {
            JsonNode root = loadFixture("document-reference-golden.json");
            JsonNode content = root.path("content");
            assertThat(content.isArray()).isTrue();
            assertThat(content.size()).isGreaterThan(0);

            JsonNode attachment = content.get(0).path("attachment");
            assertThat(attachment.path("contentType").asText()).isNotEmpty();
            assertThat(attachment.path("data").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("has context with period")
        void hasContext() throws Exception {
            JsonNode root = loadFixture("document-reference-golden.json");
            JsonNode context = root.path("context");
            assertThat(context).isNotNull();
            assertThat(context.path("period")).isNotNull();
        }

        @Test
        @DisplayName("has author reference")
        void hasAuthor() throws Exception {
            JsonNode root = loadFixture("document-reference-golden.json");
            JsonNode authors = root.path("author");
            assertThat(authors.isArray()).isTrue();
            assertThat(authors.size()).isGreaterThan(0);

            assertThat(authors.get(0).path("reference").asText()).startsWith("Practitioner/");
        }
    }

    // ==================== Helper Methods ====================

    private JsonNode loadFixture(String filename) throws Exception {
        Path path = Paths.get(GOLDEN_FIXTURES_PATH, filename);
        String content = Files.readString(path);
        return MAPPER.readTree(content);
    }
}
