package com.ghatana.phr.performance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.phr.api.PhrApiResponse;
import com.ghatana.phr.fhir.server.PhrFhirR4Server;
import com.ghatana.phr.kernel.service.PhrTestInfrastructure;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Performance smoke coverage for PHR workflows declared in product metric mapping
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PHR workflow performance smoke tests")
class PhrWorkflowPerformanceSmokeTest extends EventloopTestBase {

    private static final Duration FHIR_VALIDATION_BUDGET = Duration.ofMillis(950);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private PhrFhirR4Server fhirServer;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud = new PhrTestInfrastructure.StubDataCloudAdapter();
        fhirServer = new PhrFhirR4Server(PhrTestInfrastructure.createTestContext(dataCloud));
        runPromise(fhirServer::start);
    }

    @Test
    @DisplayName("fhir-validation completes within the smoke latency budget")
    void fhirValidationCompletesWithinSmokeLatencyBudget() throws Exception {
        Instant startedAt = Instant.now();

        PhrApiResponse response = runPromise(() -> fhirServer.createResource("Patient", patientJson()));
        Duration duration = Duration.between(startedAt, Instant.now());

        JsonNode created = JSON_MAPPER.readTree(response.body());
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(created.path("resourceType").asText()).isEqualTo("Patient");
        assertThat(duration).isLessThan(FHIR_VALIDATION_BUDGET);
    }

    private String patientJson() {
        return """
            {
              "resourceType": "Patient",
              "id": "performance-smoke-patient",
              "identifier": [{"value": "PERF-12345"}],
              "name": [{"family": "Smoke", "given": ["Performance"]}],
              "gender": "unknown",
              "active": true
            }
            """;
    }
}
