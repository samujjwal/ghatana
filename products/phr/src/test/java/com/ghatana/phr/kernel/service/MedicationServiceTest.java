package com.ghatana.phr.kernel.service;

import com.ghatana.phr.kernel.service.MedicationService.*;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MedicationService}.
 *
 * @doc.type class
 * @doc.purpose Tests for PHR medication service — prescriptions, refills, discontinuation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("MedicationService")
class MedicationServiceTest extends EventloopTestBase {

    private PhrTestInfrastructure.StubDataCloudAdapter dataCloud;
    private MedicationService service;

    @BeforeEach
    void setUp() {
        dataCloud = new PhrTestInfrastructure.StubDataCloudAdapter();
        service = new MedicationService(PhrTestInfrastructure.createTestContext(dataCloud));
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
        @DisplayName("isHealthy returns false after stop")
        void unhealthyAfterStop() {
            runPromise(service::stop);
            assertFalse(service.isHealthy());
        }

        @Test
        @DisplayName("reports service name")
        void serviceName() {
            assertEquals("medication", service.getName());
        }
    }

    // ─────────────────────── prescribe ────────────────────────────────────────

    @Nested
    @DisplayName("prescribe")
    class PrescribeTests {

        @Test
        @DisplayName("stores prescription and returns it with generated id")
        void prescribeStoresPrescription() {
            Prescription rx = buildPrescription("patient-1", "metformin", null);

            Prescription stored = runPromise(() -> service.prescribe(rx));

            assertNotNull(stored.id());
            assertThat(stored.patientId()).isEqualTo("patient-1");
            assertThat(stored.medicationCode()).isEqualTo("metformin");
            assertThat(stored.status()).isEqualTo(PrescriptionStatus.ACTIVE);
        }

        @Test
        @DisplayName("preserves caller-supplied id")
        void preservesSuppliedId() {
            Prescription rx = buildPrescription("patient-1", "metformin", "rx-custom-id");

            Prescription stored = runPromise(() -> service.prescribe(rx));

            assertThat(stored.id()).isEqualTo("rx-custom-id");
        }

        @Test
        @DisplayName("rejects null patientId")
        void rejectsNullPatient() {
            Prescription rx = buildPrescription(null, "metformin", null);
            assertThrows(Exception.class, () -> runPromise(() -> service.prescribe(rx)));
            clearFatalError();
        }

        @Test
        @DisplayName("throws when service not running")
        void throwsWhenStopped() {
            runPromise(service::stop);
            Prescription rx = buildPrescription("p1", "metformin", null);
            assertThrows(Exception.class, () -> runPromise(() -> service.prescribe(rx)));
            clearFatalError();
        }
    }

    // ─────────────────────── getPrescription ──────────────────────────────────

    @Nested
    @DisplayName("getPrescription")
    class GetPrescriptionTests {

        @Test
        @DisplayName("returns stored prescription by id")
        void returnsStoredPrescription() {
            Prescription rx = runPromise(() -> service.prescribe(buildPrescription("p-10", "atorva", null)));

            Optional<Prescription> found = runPromise(() -> service.getPrescription(rx.id()));

            assertTrue(found.isPresent());
            assertThat(found.get().id()).isEqualTo(rx.id());
        }

        @Test
        @DisplayName("returns empty for unknown id")
        void returnsEmptyForUnknown() {
            Optional<Prescription> found = runPromise(() -> service.getPrescription("no-such-id"));
            assertTrue(found.isEmpty());
        }
    }

    // ─────────────────────── getActivePrescriptions ───────────────────────────

    @Nested
    @DisplayName("getActivePrescriptions")
    class GetActiveTests {

        @Test
        @DisplayName("returns prescriptions for patient")
        void returnsForPatient() {
            runPromise(() -> service.prescribe(buildPrescription("patient-A", "amox", null)));
            runPromise(() -> service.prescribe(buildPrescription("patient-A", "ibup", null)));
            runPromise(() -> service.prescribe(buildPrescription("patient-B", "aspirin", null)));

            List<Prescription> active = runPromise(() -> service.getActivePrescriptions("patient-A"));

            assertThat(active).hasSize(2);
            assertThat(active).allMatch(rx -> "patient-A".equals(rx.patientId()));
        }
    }

    // ─────────────────────── discontinue ──────────────────────────────────────

    @Nested
    @DisplayName("discontinue")
    class DiscontinueTests {

        @Test
        @DisplayName("sets status to DISCONTINUED")
        void setsDiscontinued() {
            Prescription rx = runPromise(() -> service.prescribe(buildPrescription("p1", "metformin", null)));

            Prescription disc = runPromise(() -> service.discontinue(rx.id(), "Adverse reaction"));

            assertThat(disc.status()).isEqualTo(PrescriptionStatus.DISCONTINUED);
        }

        @Test
        @DisplayName("throws for unknown prescription")
        void throwsForUnknown() {
            assertThrows(Exception.class, () -> runPromise(() -> service.discontinue("ghost-id", "reason")));
            clearFatalError();
        }
    }

    // ─────────────────────── refill ───────────────────────────────────────────

    @Nested
    @DisplayName("refill")
    class RefillTests {

        @Test
        @DisplayName("decrements refills remaining")
        void decrementsRefills() {
            Prescription rx = buildPrescriptionWithRefills("patient-1", "amox", 3);
            Prescription stored = runPromise(() -> service.prescribe(rx));

            Prescription refilled = runPromise(() -> service.refill(stored.id()));

            assertThat(refilled.refillsRemaining()).isEqualTo(2);
        }

        @Test
        @DisplayName("throws when no refills remaining")
        void throwsWhenNoRefills() {
            Prescription rx = buildPrescriptionWithRefills("patient-1", "amox", 0);
            Prescription stored = runPromise(() -> service.prescribe(rx));

            assertThrows(Exception.class, () -> runPromise(() -> service.refill(stored.id())));
            clearFatalError();
        }

        @Test
        @DisplayName("throws when prescription is DISCONTINUED")
        void throwsWhenDiscontinued() {
            Prescription rx = buildPrescriptionWithRefills("patient-1", "amox", 2);
            Prescription stored = runPromise(() -> service.prescribe(rx));
            runPromise(() -> service.discontinue(stored.id(), "reason"));

            assertThrows(Exception.class, () -> runPromise(() -> service.refill(stored.id())));
            clearFatalError();
        }
    }

    // ─────────────────────── Helpers ──────────────────────────────────────────

    private static Prescription buildPrescription(String patientId, String medCode, String id) {
        return new Prescription(id, patientId, "doctor-1", "enc-1",
                medCode, medCode + " generic",
                "Take once daily", "Type 2 Diabetes",
                Instant.now(), Instant.now().plus(Duration.ofDays(30)),
                3, Duration.ofDays(30),
                PrescriptionStatus.ACTIVE);
    }

    private static Prescription buildPrescriptionWithRefills(String patientId, String medCode,
                                                              int refills) {
        return new Prescription(null, patientId, "doctor-1", "enc-1",
                medCode, medCode + " generic",
                "Take once daily", "indication",
                Instant.now(), Instant.now().plus(Duration.ofDays(30)),
                refills, Duration.ofDays(30),
                PrescriptionStatus.ACTIVE);
    }
}
