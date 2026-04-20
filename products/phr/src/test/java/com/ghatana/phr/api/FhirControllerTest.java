package com.ghatana.phr.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.phr.fhir.server.PhrFhirR4Server;
import com.ghatana.phr.kernel.service.PhrTestInfrastructure;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose API-level regression coverage for the PHR FHIR controller facade
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("FhirController")
class FhirControllerTest extends EventloopTestBase {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private FhirController controller;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud = new PhrTestInfrastructure.StubDataCloudAdapter();
        PhrFhirR4Server server = new PhrFhirR4Server(PhrTestInfrastructure.createTestContext(dataCloud));
        runPromise(server::start);
        controller = new FhirController(server);
    }

    @Test
    @DisplayName("creates a MedicationRequest and returns FHIR content headers")
    void createsMedicationRequest() throws Exception {
        PhrApiResponse response = runPromise(() -> controller.createResource("MedicationRequest", medicationRequestJson()));

        JsonNode root = JSON_MAPPER.readTree(response.body());
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers()).containsEntry("Content-Type", "application/fhir+json");
        assertThat(root.path("resourceType").asText()).isEqualTo("MedicationRequest");
    }

    @Test
    @DisplayName("returns a searchset bundle for controller search operations")
    void returnsSearchBundle() throws Exception {
        runPromise(() -> controller.createResource("Immunization", immunizationJson("imm-1", "patient-1")));

        PhrApiResponse response = runPromise(() -> controller.searchResources("Immunization", Map.of("patient", "patient-1")));

        JsonNode root = JSON_MAPPER.readTree(response.body());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(root.path("type").asText()).isEqualTo("searchset");
        assertThat(root.path("entry")).hasSize(1);
    }

    private String medicationRequestJson() {
        return """
            {
              "resourceType": "MedicationRequest",
              "status": "active",
              "intent": "order",
              "subject": {"reference": "Patient/patient-1"},
              "medicationCodeableConcept": {
                "coding": [{"system": "http://www.nlm.nih.gov/research/umls/rxnorm", "code": "197361"}]
              }
            }
            """;
    }

    private String immunizationJson(String id, String patientId) {
        return """
            {
              "resourceType": "Immunization",
              "id": "%s",
              "status": "completed",
              "patient": {"reference": "Patient/%s"},
              "vaccineCode": {"coding": [{"system": "http://hl7.org/fhir/sid/cvx", "code": "207"}]}
            }
            """.formatted(id, patientId);
    }
}
