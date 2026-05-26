package com.ghatana.phr.fhir.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.phr.api.PhrApiResponse;
import com.ghatana.phr.kernel.service.PhrTestInfrastructure;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Integration coverage for the PHR FHIR R4 runtime server and typed providers
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrFhirR4Server")
class PhrFhirR4ServerTest extends EventloopTestBase {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private PhrFhirR4Server server;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud = new PhrTestInfrastructure.StubDataCloudAdapter();
        server = new PhrFhirR4Server(PhrTestInfrastructure.createTestContext(dataCloud));
        runPromise(server::start);
    }

    @Test
    @DisplayName("creates and reads Patient resources through the server surface")
    void createsAndReadsPatientResource() throws Exception {
        PhrApiResponse createResponse = runPromise(() -> server.createResource("Patient", patientJson()));
        JsonNode created = JSON_MAPPER.readTree(createResponse.body());

        PhrApiResponse readResponse = runPromise(() -> server.getResource("Patient", created.path("id").asText()));

        assertThat(createResponse.statusCode()).isEqualTo(201);
        assertThat(readResponse.statusCode()).isEqualTo(200);
        assertThat(JSON_MAPPER.readTree(readResponse.body()).path("resourceType").asText()).isEqualTo("Patient");
    }

    @Test
    @DisplayName("searches Observation resources as a FHIR searchset bundle")
    void searchesObservationResources() throws Exception {
        runPromise(() -> server.createResource("Observation", observationJson("obs-1", "patient-1", "2160-0")));
        runPromise(() -> server.createResource("Observation", observationJson("obs-2", "patient-2", "718-7")));

        PhrApiResponse response = runPromise(() -> server.searchResources(
            "Observation",
            Map.of("subject", "patient-1", "code", "2160-0")
        ));

        JsonNode bundle = JSON_MAPPER.readTree(response.body());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(bundle.path("resourceType").asText()).isEqualTo("Bundle");
        assertThat(bundle.path("total").asInt()).isEqualTo(1);
        assertThat(bundle.path("entry").get(0).path("resource").path("id").asText()).isEqualTo("obs-1");
    }

    @ParameterizedTest(name = "creates and reads {0}")
    @MethodSource("supportedResourcePayloads")
    @DisplayName("creates and reads every registered FHIR resource provider")
    void createsAndReadsEveryRegisteredResourceProvider(String resourceType, String resourceJson) throws Exception {
        PhrApiResponse createResponse = runPromise(() -> server.createResource(resourceType, resourceJson));
        JsonNode created = JSON_MAPPER.readTree(createResponse.body());

        PhrApiResponse readResponse = runPromise(() -> server.getResource(resourceType, created.path("id").asText()));

        assertThat(createResponse.statusCode()).isEqualTo(201);
        assertThat(readResponse.statusCode()).isEqualTo(200);
        assertThat(JSON_MAPPER.readTree(readResponse.body()).path("resourceType").asText()).isEqualTo(resourceType);
    }

    @Test
    @DisplayName("returns OperationOutcome for unsupported resource type")
    void rejectsUnsupportedResourceType() throws Exception {
        PhrApiResponse response = runPromise(() -> server.createResource("Encounter", "{\"resourceType\":\"Encounter\"}"));

        JsonNode outcome = JSON_MAPPER.readTree(response.body());
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(outcome.path("resourceType").asText()).isEqualTo("OperationOutcome");
    }

    private String patientJson() {
        return """
            {
              "resourceType": "Patient",
              "identifier": [{"value": "NHS-12345"}],
              "name": [{"family": "Sharma", "given": ["Arjun"]}],
              "gender": "male",
              "active": true
            }
            """;
    }

    private String observationJson(String id, String patientId, String loincCode) {
        return """
            {
              "resourceType": "Observation",
              "id": "%s",
              "status": "final",
              "subject": {"reference": "Patient/%s"},
              "code": {"coding": [{"system": "http://loinc.org", "code": "%s"}]},
              "valueQuantity": {"value": 1.1, "unit": "mg/dL"}
            }
            """.formatted(id, patientId, loincCode);
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> supportedResourcePayloads() {
        return Stream.of(
            org.junit.jupiter.params.provider.Arguments.of("Patient", """
                {
                  "resourceType": "Patient",
                  "id": "patient-provider-1",
                  "identifier": [{"value": "NHS-67890"}],
                  "name": [{"family": "Karki", "given": ["Maya"]}],
                  "gender": "female",
                  "active": true
                }
                """),
            org.junit.jupiter.params.provider.Arguments.of("Observation", """
                {
                  "resourceType": "Observation",
                  "id": "observation-provider-1",
                  "status": "final",
                  "subject": {"reference": "Patient/patient-provider-1"},
                  "code": {"coding": [{"system": "http://loinc.org", "code": "718-7"}]},
                  "valueQuantity": {"value": 13.2, "unit": "g/dL"}
                }
                """),
            org.junit.jupiter.params.provider.Arguments.of("MedicationRequest", """
                {
                  "resourceType": "MedicationRequest",
                  "id": "medication-request-provider-1",
                  "status": "active",
                  "intent": "order",
                  "subject": {"reference": "Patient/patient-provider-1"},
                  "medicationCodeableConcept": {"text": "Metformin 500 mg"}
                }
                """),
            org.junit.jupiter.params.provider.Arguments.of("Immunization", """
                {
                  "resourceType": "Immunization",
                  "id": "immunization-provider-1",
                  "status": "completed",
                  "vaccineCode": {"text": "Influenza vaccine"},
                  "patient": {"reference": "Patient/patient-provider-1"},
                  "occurrenceDateTime": "2026-05-24T09:00:00Z"
                }
                """),
            org.junit.jupiter.params.provider.Arguments.of("DiagnosticReport", """
                {
                  "resourceType": "DiagnosticReport",
                  "id": "diagnostic-report-provider-1",
                  "status": "final",
                  "code": {"coding": [{"system": "http://loinc.org", "code": "58410-2"}]},
                  "subject": {"reference": "Patient/patient-provider-1"},
                  "result": [{"reference": "Observation/observation-provider-1"}]
                }
                """),
            org.junit.jupiter.params.provider.Arguments.of("DocumentReference", """
                {
                  "resourceType": "DocumentReference",
                  "id": "document-reference-provider-1",
                  "status": "current",
                  "type": {"coding": [{"system": "http://loinc.org", "code": "18842-5"}]},
                  "subject": {"reference": "Patient/patient-provider-1"},
                  "content": [{"attachment": {"contentType": "application/pdf", "data": "JVBERi0xLjQK"}}]
                }
                """)
        );
    }
}
