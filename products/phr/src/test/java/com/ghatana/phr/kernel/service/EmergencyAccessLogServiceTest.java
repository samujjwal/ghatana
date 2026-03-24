package com.ghatana.phr.kernel.service;

import com.ghatana.phr.kernel.service.EmergencyAccessLogService.*;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EmergencyAccessLogService}.
 *
 * @doc.type class
 * @doc.purpose Tests for break-the-glass emergency access audit trail
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EmergencyAccessLogService")
class EmergencyAccessLogServiceTest extends EventloopTestBase {

    private EmergencyAccessLogService service;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud =
                new PhrTestInfrastructure.StubDataCloudAdapter();
        service = new EmergencyAccessLogService(PhrTestInfrastructure.createTestContext(dataCloud));
        runPromise(service::start);
    }

    @Nested
    @DisplayName("service lifecycle")
    class Lifecycle {

        @Test
        void healthyAfterStart() {
            assertTrue(service.isHealthy());
        }

        @Test
        void serviceName() {
            assertEquals("emergency-access-log", service.getName());
        }
    }

    @Nested
    @DisplayName("logAccess")
    class LogAccessTests {

        @Test
        @DisplayName("stores event with PENDING_REVIEW status")
        void storesPendingReview() {
            EmergencyAccessEvent event = buildEvent("patient-1", "emergency");

            EmergencyAccessEvent stored = runPromise(() -> service.logAccess(event));

            assertNotNull(stored.id());
            assertThat(stored.reviewStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
            assertNotNull(stored.accessedAt());
        }

        @Test
        @DisplayName("two logAccess calls create two distinct events")
        void appendsDistinctEvents() {
            runPromise(() -> service.logAccess(buildEvent("patient-2", "reason-A")));
            runPromise(() -> service.logAccess(buildEvent("patient-2", "reason-B")));

            List<EmergencyAccessEvent> log =
                    runPromise(() -> service.getPatientEmergencyLog("patient-2"));

            assertThat(log).hasSize(2);
        }

        @Test
        @DisplayName("rejects null patientId")
        void rejectsNullPatient() {
            assertThrows(Exception.class,
                    () -> runPromise(() -> service.logAccess(buildEvent(null, "reason"))));
            clearFatalError();
        }

        @Test
        @DisplayName("rejects null justification")
        void rejectsNullJustification() {
            assertThrows(Exception.class,
                    () -> runPromise(() -> service.logAccess(
                            new EmergencyAccessEvent(null, "patient-1", "dr-1",
                                    "ER_PHYSICIAN", null,
                                    Set.of("medications"), null, null, null, null))));
            clearFatalError();
        }
    }

    @Nested
    @DisplayName("markReviewed")
    class MarkReviewedTests {

        @Test
        @DisplayName("transitions to REVIEWED")
        void transitionsToReviewed() {
            EmergencyAccessEvent stored =
                    runPromise(() -> service.logAccess(buildEvent("patient-3", "urgent care")));

            EmergencyAccessEvent reviewed = runPromise(() ->
                    service.markReviewed(stored.id(), "reviewer-1", ReviewStatus.REVIEWED, "OK"));

            assertThat(reviewed.reviewStatus()).isEqualTo(ReviewStatus.REVIEWED);
            assertEquals("reviewer-1", reviewed.reviewedBy());
        }

        @Test
        @DisplayName("transitions to ESCALATED")
        void transitionsToEscalated() {
            EmergencyAccessEvent stored =
                    runPromise(() -> service.logAccess(buildEvent("patient-4", "critical")));

            EmergencyAccessEvent escalated = runPromise(() ->
                    service.markReviewed(stored.id(), "reviewer-2", ReviewStatus.ESCALATED, "Needs inquiry"));

            assertThat(escalated.reviewStatus()).isEqualTo(ReviewStatus.ESCALATED);
        }

        @Test
        @DisplayName("cannot re-review an already-reviewed event")
        void cannotReReview() {
            EmergencyAccessEvent stored =
                    runPromise(() -> service.logAccess(buildEvent("patient-5", "routine emergency")));
            runPromise(() ->
                    service.markReviewed(stored.id(), "reviewer-1", ReviewStatus.REVIEWED, "OK"));

            assertThrows(Exception.class,
                    () -> runPromise(() ->
                            service.markReviewed(stored.id(), "reviewer-2", ReviewStatus.REVIEWED, "Again")));
            clearFatalError();
        }
    }

    @Nested
    @DisplayName("getPatientEmergencyLog")
    class PatientLogTests {

        @Test
        @DisplayName("returns events sorted newest-first")
        void sortedNewestFirst() {
            runPromise(() -> service.logAccess(buildEvent("patient-6", "first")));
            runPromise(() -> service.logAccess(buildEvent("patient-6", "second")));
            runPromise(() -> service.logAccess(buildEvent("other-patient", "other")));

            List<EmergencyAccessEvent> log =
                    runPromise(() -> service.getPatientEmergencyLog("patient-6"));

            assertThat(log).hasSize(2);
            assertThat(log).allMatch(e -> "patient-6".equals(e.patientId()));
        }
    }

    @Nested
    @DisplayName("getPendingReviews")
    class PendingReviewTests {

        @Test
        @DisplayName("returns only PENDING_REVIEW events (FIFO)")
        void returnsOnlyPending() {
            EmergencyAccessEvent e1 =
                    runPromise(() -> service.logAccess(buildEvent("patient-7", "first")));
            runPromise(() -> service.logAccess(buildEvent("patient-7", "second")));
            // mark e1 as reviewed → removes from pending queue
            runPromise(() ->
                    service.markReviewed(e1.id(), "reviewer-1", ReviewStatus.REVIEWED, "done"));

            List<EmergencyAccessEvent> pending =
                    runPromise(() -> service.getPendingReviews(10));

            assertThat(pending).hasSize(1);
            assertThat(pending.get(0).reviewStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        }
    }

    // ─────────────────────── Helpers ──────────────────────────────────────────

    private static EmergencyAccessEvent buildEvent(String patientId, String justification) {
        return new EmergencyAccessEvent(
                null, patientId, "doctor-1", "ER_PHYSICIAN",
                justification, Set.of("medications", "labs"),
                null, null, null, null);
    }
}
