package com.ghatana.phr.hl7;

import com.ghatana.phr.kernel.service.LabResultService;
import com.ghatana.phr.kernel.service.LabResultService.LabObservation;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * End-to-end tests for HL7 lab result import.
 *
 * <p>These tests verify the complete flow from HL7 ORU^R01 message parsing
 * to lab observation recording in the PHR system.</p>
 *
 * @doc.type class
 * @doc.purpose E2E tests for HL7 lab result import workflow
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("HL7 Lab Result Import E2E Tests")
class Hl7LabResultIntegrationE2ETest extends EventloopTestBase {

    private LabResultService mockLabResultService;
    private Hl7LabResultIntegrationService hl7Service;

    @BeforeEach
    void setUp() {
        mockLabResultService = mock(LabResultService.class);
        hl7Service = new Hl7LabResultIntegrationService(mockLabResultService);
        runPromise(hl7Service::start);
    }

    @Test
    @DisplayName("Should successfully import a valid HL7 ORU^R01 message")
    void shouldSuccessfullyImportValidHl7Message() {
        // Given: A valid HL7 ORU^R01 message
        String hl7Message = """
            MSH|^~\\&|LAB|FACILITY|PHR|Ghatana|20240127120000||ORU^R01|MSG12345|P|2.5
            PID|1||PATIENT123^^^FACILITY||Doe^John^||19700101|M
            OBR|1|ORD12345|ORD12345|CBC^Complete Blood Count|||20240127110000
            OBX|1|NM|WBC^White Blood Count||7.5|10*3/uL|4.0-11.0|N||F|||20240127110000
            """;

        LabObservation expectedObservation = observation(
            "PATIENT123",
            "ORD12345",
            "WBC",
            "White Blood Count",
            new BigDecimal("7.5"),
            4.0,
            "10*3/uL",
            "4.0-11.0",
            LabResultService.ObservationStatus.FINAL,
            "N"
        );

        when(mockLabResultService.recordObservation(any(LabObservation.class)))
            .thenReturn(Promise.of(expectedObservation));

        // When: Importing the HL7 message
        LabObservation observation = runPromise(() -> hl7Service.importObservationMessage(hl7Message));
        
        assertNotNull(observation);
        assertEquals("PATIENT123", observation.patientId());
        assertEquals("ORD12345", observation.orderId());
        assertEquals("WBC", observation.loincCode());
        assertEquals(new BigDecimal("7.5"), observation.value());
        assertEquals(LabResultService.ObservationStatus.FINAL, observation.status());
        
        verify(mockLabResultService, times(1)).recordObservation(any(LabObservation.class));
    }

    @Test
    @DisplayName("Should handle HL7 message with preliminary status")
    void shouldHandleHl7MessageWithPreliminaryStatus() {
        // Given: An HL7 message with preliminary status
        String hl7Message = """
            MSH|^~\\&|LAB|FACILITY|PHR|Ghatana|20240127120000||ORU^R01|MSG12346|P|2.5
            PID|1||PATIENT124^^^FACILITY||Smith^Jane^||19850215|F
            OBR|1|ORD12346|ORD12346|LIPID^Lipid Panel|||20240127110000
            OBX|1|NM|CHOL^Cholesterol||210|mg/dL|<200|H||P|||20240127110000
            """;

        LabObservation expectedObservation = observation(
            "PATIENT124",
            "ORD12346",
            "CHOL",
            "Cholesterol",
            new BigDecimal("210"),
            null,
            "mg/dL",
            "<200",
            LabResultService.ObservationStatus.PRELIMINARY,
            "H"
        );

        when(mockLabResultService.recordObservation(any(LabObservation.class)))
            .thenReturn(Promise.of(expectedObservation));

        // When: Importing the HL7 message
        LabObservation observation = runPromise(() -> hl7Service.importObservationMessage(hl7Message));
        
        assertNotNull(observation);
        assertEquals(LabResultService.ObservationStatus.PRELIMINARY, observation.status());
        assertEquals("H", observation.interpretation());
    }

    @Test
    @DisplayName("Should handle HL7 message with cancelled status")
    void shouldHandleHl7MessageWithCancelledStatus() {
        // Given: An HL7 message with cancelled status
        String hl7Message = """
            MSH|^~\\&|LAB|FACILITY|PHR|Ghatana|20240127120000||ORU^R01|MSG12347|P|2.5
            PID|1||PATIENT125^^^FACILITY||Wilson^Robert^||19900320|M
            OBR|1|ORD12347|ORD12347|CBC^Complete Blood Count|||20240127110000
            OBX|1|NM|WBC^White Blood Count||7.5|10*3/uL|4.0-11.0|N||X|||20240127110000
            """;

        LabObservation expectedObservation = observation(
            "PATIENT125",
            "ORD12347",
            "WBC",
            "White Blood Count",
            new BigDecimal("7.5"),
            4.0,
            "10*3/uL",
            "4.0-11.0",
            LabResultService.ObservationStatus.CANCELLED,
            "N"
        );

        when(mockLabResultService.recordObservation(any(LabObservation.class)))
            .thenReturn(Promise.of(expectedObservation));

        // When: Importing the HL7 message
        LabObservation observation = runPromise(() -> hl7Service.importObservationMessage(hl7Message));
        
        assertNotNull(observation);
        assertEquals(LabResultService.ObservationStatus.CANCELLED, observation.status());
    }

    @Test
    @DisplayName("Should fail when service is not started")
    void shouldFailWhenServiceNotStarted() {
        // Given: Service is stopped
        runPromise(hl7Service::stop);
        
        String hl7Message = "MSH|^~\\&|LAB|FACILITY|PHR|Ghatana|20240127120000||ORU^R01|MSG12348|P|2.5";

        // When: Attempting to import
        assertThrows(IllegalStateException.class, () -> runPromise(() -> hl7Service.importObservationMessage(hl7Message)));
    }

    @Test
    @DisplayName("Should parse reference range correctly")
    void shouldParseReferenceRangeCorrectly() {
        // Given: HL7 message with reference range
        String hl7Message = """
            MSH|^~\\&|LAB|FACILITY|PHR|Ghatana|20240127120000||ORU^R01|MSG12349|P|2.5
            PID|1||PATIENT126^^^FACILITY||Brown^Emily^||19881112|F
            OBR|1|ORD12349|ORD12349|CBC^Complete Blood Count|||20240127110000
            OBX|1|NM|HGB^Hemoglobin||13.5|g/dL|12.0-16.0|N||F|||20240127110000
            """;

        LabObservation expectedObservation = observation(
            "PATIENT126",
            "ORD12349",
            "HGB",
            "Hemoglobin",
            new BigDecimal("13.5"),
            12.0,
            "g/dL",
            "12.0-16.0",
            LabResultService.ObservationStatus.FINAL,
            "N"
        );

        when(mockLabResultService.recordObservation(any(LabObservation.class)))
            .thenReturn(Promise.of(expectedObservation));

        // When: Importing the HL7 message
        LabObservation observation = runPromise(() -> hl7Service.importObservationMessage(hl7Message));
        
        assertNotNull(observation);
        assertEquals(12.0, observation.referenceRangeLow());
    }

    private static LabObservation observation(
            String patientId,
            String orderId,
            String loincCode,
            String loincDisplay,
            BigDecimal value,
            Double referenceRangeLow,
            String unit,
            String referenceRange,
            LabResultService.ObservationStatus status,
            String interpretation) {
        Instant now = Instant.now();
        return new LabObservation(
            null,
            patientId,
            null,
            orderId,
            loincCode,
            loincDisplay,
            loincDisplay,
            value,
            referenceRangeLow,
            unit,
            referenceRange,
            "FACILITY",
            now,
            now,
            status,
            "Imported from HL7 ORU^R01",
            interpretation
        );
    }
}
