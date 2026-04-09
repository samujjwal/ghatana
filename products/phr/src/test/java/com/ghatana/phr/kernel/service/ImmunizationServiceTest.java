package com.ghatana.phr.kernel.service;

import com.ghatana.phr.kernel.service.ImmunizationService.*;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ImmunizationService}.
 *
 * @doc.type class
 * @doc.purpose Tests for PHR immunization service — CVX-coded vaccines, schedules
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ImmunizationService")
class ImmunizationServiceTest extends EventloopTestBase {

    private ImmunizationService service;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud =
                new PhrTestInfrastructure.StubDataCloudAdapter();
        service = new ImmunizationService(PhrTestInfrastructure.createTestContext(dataCloud));
        runPromise(service::start);
    }

    @Nested
    @DisplayName("service lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("isHealthy after start")
        void healthyAfterStart() {
            assertTrue(service.isHealthy());
        }

        @Test
        @DisplayName("reports correct service name")
        void serviceName() {
            assertEquals("immunization", service.getName());
        }
    }

    @Nested
    @DisplayName("recordImmunization")
    class RecordTests {

        @Test
        @DisplayName("stores immunization record with generated id")
        void storesRecord() {
            ImmunizationRecord record = buildRecord("patient-1", "21", "Varicella", null);

            ImmunizationRecord stored = runPromise(() -> service.recordImmunization(record));

            assertNotNull(stored.id());
            assertThat(stored.cvxCode()).isEqualTo("21");
            assertThat(stored.status()).isEqualTo(ImmunizationStatus.ADMINISTERED);
        }

        @Test
        @DisplayName("rejects null patientId")
        void rejectsNullPatient() {
            assertThrows(Exception.class,
                    () -> runPromise(() -> service.recordImmunization(buildRecord(null, "21", "V", null))));
            clearFatalError();
        }

        @Test
        @DisplayName("rejects null cvxCode")
        void rejectsNullCvx() {
            assertThrows(Exception.class,
                    () -> runPromise(() -> service.recordImmunization(buildRecord("p1", null, "V", null))));
            clearFatalError();
        }
    }

    @Nested
    @DisplayName("getImmunizationHistory")
    class HistoryTests {

        @Test
        @DisplayName("returns all records for a patient")
        void returnsPatientHistory() {
            runPromise(() -> service.recordImmunization(buildRecord("patient-A", "21", "Varicella", null)));
            runPromise(() -> service.recordImmunization(buildRecord("patient-A", "08", "Hep B", null)));
            runPromise(() -> service.recordImmunization(buildRecord("patient-B", "08", "Hep B", null)));

            List<ImmunizationRecord> history = runPromise(() ->
                    service.getImmunizationHistory("patient-A"));

            assertThat(history).hasSize(2);
            assertThat(history).allMatch(r -> "patient-A".equals(r.patientId()));
        }
    }

    @Nested
    @DisplayName("vaccination schedules")
    class ScheduleTests {

        @Test
        @DisplayName("creates and retrieves due schedules")
        void createAndRetrieveDue() {
            VaccinationSchedule schedule = buildSchedule("patient-1", "21", Instant.now().minusSeconds(60));
            runPromise(() -> service.createSchedule(schedule));

            List<VaccinationSchedule> due = runPromise(() -> service.getDueSchedules("patient-1"));

            assertThat(due).hasSize(1);
            assertThat(due.get(0).cvxCode()).isEqualTo("21");
        }

        @Test
        @DisplayName("getDueSchedules sorts by dueDate ascending")
        void sortedByDueDate() {
            Instant now = Instant.now();
            runPromise(() -> service.createSchedule(buildSchedule("p1", "21", now.minusSeconds(7200))));
            runPromise(() -> service.createSchedule(buildSchedule("p1", "08", now.minusSeconds(3600))));

            List<VaccinationSchedule> due = runPromise(() -> service.getDueSchedules("p1"));

            assertThat(due).hasSize(2);
            assertThat(due.get(0).dueDate()).isBefore(due.get(1).dueDate());
        }
    }

    // ─────────────────────── Helpers ──────────────────────────────────────────

    private static ImmunizationRecord buildRecord(String patientId, String cvxCode,
                                                   String vaccineName, String id) {
        return new ImmunizationRecord(id, patientId, "enc-1",
                cvxCode, vaccineName, "nurse-1",
                Instant.now(), Instant.now(),
                "LOT123", LocalDate.now().plusYears(2),
                "IM", "Primary Series", 1, false,
                null, ImmunizationStatus.ADMINISTERED);
    }

    private static VaccinationSchedule buildSchedule(String patientId, String cvxCode,
                                                      Instant dueDate) {
        return new VaccinationSchedule(null, patientId, cvxCode, "Varicella",
                "Primary Series", 1, dueDate, ScheduleStatus.PENDING, null);
    }
}
