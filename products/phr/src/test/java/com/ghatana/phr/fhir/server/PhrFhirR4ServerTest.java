package com.ghatana.phr.fhir.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.phr.api.PhrApiResponse;
import com.ghatana.phr.kernel.service.PhrTestInfrastructure;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
}
