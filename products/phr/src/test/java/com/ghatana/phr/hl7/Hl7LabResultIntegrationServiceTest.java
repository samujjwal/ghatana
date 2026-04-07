package com.ghatana.phr.hl7;

import com.ghatana.phr.kernel.service.LabResultService;
import com.ghatana.phr.kernel.service.LabResultService.LabObservation;
import com.ghatana.phr.kernel.service.PhrTestInfrastructure;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Integration coverage for importing HL7 lab results into the canonical PHR lab service
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Hl7LabResultIntegrationService")
class Hl7LabResultIntegrationServiceTest extends EventloopTestBase {

    private LabResultService labResultService;
    private Hl7LabResultIntegrationService integrationService;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud = new PhrTestInfrastructure.StubDataCloudAdapter();
        labResultService = new LabResultService(PhrTestInfrastructure.createTestContext(dataCloud));
        runPromise(labResultService::start);

        integrationService = new Hl7LabResultIntegrationService(labResultService);
        runPromise(integrationService::start);
    }

    @Test
    @DisplayName("imports HL7 ORU observations into LabResultService")
    void importsObservationIntoLabService() {
        LabObservation stored = runPromise(() -> integrationService.importObservationMessage(Hl7OruMessageParserTest.sampleMessage()));
        Optional<LabObservation> loaded = runPromise(() -> labResultService.getObservation(stored.id()));

        assertThat(stored.patientId()).isEqualTo("patient-1");
        assertThat(stored.loincCode()).isEqualTo("2160-0");
        assertThat(stored.performingLabId()).isEqualTo("GHATANA-LAB");
        assertThat(loaded).isPresent();
    }
}