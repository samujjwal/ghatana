package com.ghatana.phr.kernel.service;

import com.ghatana.phr.kernel.service.LabResultService.*;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LabResultService}.
 *
 * @doc.type class
 * @doc.purpose Tests for PHR lab result service — observations, panels, trending
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("LabResultService")
class LabResultServiceTest extends EventloopTestBase {

    private LabResultService service;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud =
                new PhrTestInfrastructure.StubDataCloudAdapter();
        service = new LabResultService(PhrTestInfrastructure.createTestContext(dataCloud));
        runPromise(service::start);
    }

    // ─────────────────────── Lifecycle ────────────────────────────────────────

    @Nested
    @DisplayName("service lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("isHealthy returns true after start")
        void healthyAfterStart() {
            assertTrue(service.isHealthy());
        }

        @Test
        @DisplayName("returns correct service name")
        void serviceName() {
            assertEquals("lab-results", service.getName());
        }
    }

    // ─────────────────────── recordObservation ────────────────────────────────

    @Nested
    @DisplayName("recordObservation")
    class RecordObservationTests {

        @Test
        @DisplayName("stores observation and returns it with LOINC metadata")
        void storesObservation() {
            LabObservation obs = buildObservation("patient-1", "2160-0", "Creatinine", null);

            LabObservation stored = runPromise(() -> service.recordObservation(obs));

            assertNotNull(stored.id());
            assertThat(stored.patientId()).isEqualTo("patient-1");
            assertThat(stored.loincCode()).isEqualTo("2160-0");
            assertThat(stored.testName()).isEqualTo("Creatinine");
        }

        @Test
        @DisplayName("rejects null patientId")
        void rejectsNullPatient() {
            LabObservation obs = buildObservation(null, "2160-0", "Creatinine", null);
            assertThrows(Exception.class, () -> runPromise(() -> service.recordObservation(obs)));
            clearFatalError();
        }

        @Test
        @DisplayName("rejects null loincCode")
        void rejectsNullLoinc() {
            LabObservation obs = buildObservation("patient-1", null, "Test", null);
            assertThrows(Exception.class, () -> runPromise(() -> service.recordObservation(obs)));
            clearFatalError();
        }
    }

    // ─────────────────────── getObservation ───────────────────────────────────

    @Nested
    @DisplayName("getObservation")
    class GetObservationTests {

        @Test
        @DisplayName("returns stored observation by id")
        void returnsById() {
            LabObservation stored = runPromise(() ->
                    service.recordObservation(buildObservation("p1", "2160-0", "Creatinine", null)));

            Optional<LabObservation> found = runPromise(() -> service.getObservation(stored.id()));

            assertTrue(found.isPresent());
            assertThat(found.get().loincCode()).isEqualTo("2160-0");
        }

        @Test
        @DisplayName("returns empty for unknown id")
        void returnsEmptyForUnknown() {
            Optional<LabObservation> found = runPromise(() -> service.getObservation("ghost"));
            assertTrue(found.isEmpty());
        }
    }

    // ─────────────────────── getTrend ─────────────────────────────────────────

    @Nested
    @DisplayName("getTrend")
    class GetTrendTests {

        @Test
        @DisplayName("returns sorted observations for same LOINC code")
        void returnsSortedTrend() {
            Instant t1 = Instant.now().minusSeconds(3600);
            Instant t2 = Instant.now().minusSeconds(1800);
            Instant t3 = Instant.now();

            runPromise(() -> service.recordObservation(buildObservationAt("p1", "2160-0", t3)));
            runPromise(() -> service.recordObservation(buildObservationAt("p1", "2160-0", t1)));
            runPromise(() -> service.recordObservation(buildObservationAt("p1", "2160-0", t2)));

            List<LabObservation> trend = runPromise(() -> service.getTrend("p1", "2160-0"));

            assertThat(trend).hasSize(3);
            // sorted ascending by resultedAt
            assertThat(trend.get(0).resultedAt()).isEqualTo(t1);
            assertThat(trend.get(2).resultedAt()).isEqualTo(t3);
        }

        @Test
        @DisplayName("returns empty trend for unknown patient")
        void emptyForUnknown() {
            List<LabObservation> trend = runPromise(() -> service.getTrend("nobody", "2160-0"));
            assertThat(trend).isEmpty();
        }
    }

    // ─────────────────────── Helpers ──────────────────────────────────────────

    private static LabObservation buildObservation(String patientId, String loincCode,
                                                    String testName, String id) {
        return new LabObservation(id, patientId, "enc-1", "ord-1",
                loincCode, testName + " display", testName,
                new BigDecimal("1.0"), null, "mg/dL",
                "0.5–1.3", "lab-central",
                Instant.now().minusSeconds(600), Instant.now(),
                ObservationStatus.FINAL, null);
    }

    private static LabObservation buildObservationAt(String patientId, String loincCode,
                                                      Instant resultedAt) {
        return new LabObservation(null, patientId, "enc-1", "ord-1",
                loincCode, "display", "Test",
                new BigDecimal("1.0"), null, "mg/dL",
                "0.5–1.3", "lab-central",
                resultedAt.minusSeconds(600), resultedAt,
                ObservationStatus.FINAL, null);
    }
}
