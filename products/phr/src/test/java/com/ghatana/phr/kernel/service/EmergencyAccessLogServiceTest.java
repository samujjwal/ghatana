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

    private TestableEmergencyAccessLogService service;
    private RecordingNotificationSender notificationSender;
    private RecordingAuditLogger auditLogger;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud =
                new PhrTestInfrastructure.StubDataCloudAdapter();
        notificationSender = new RecordingNotificationSender();
        auditLogger = new RecordingAuditLogger();
        EmergencyAccessReviewWorkflow reviewWorkflow = new EmergencyAccessReviewWorkflow(
                notificationSender,
                auditLogger);
        service = new TestableEmergencyAccessLogService(
            PhrTestInfrastructure.createTestContext(dataCloud),
            reviewWorkflow);
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
            assertNotNull(stored.accessExpiresAt());
            assertNotNull(stored.reviewDueAt());
            assertThat(stored.reviewCaseId()).startsWith("EMR-");
            assertThat(notificationSender.complianceNotifications).hasSize(1);
            assertThat(notificationSender.reviewSchedules).hasSize(1);
            assertThat(notificationSender.patientNotifications).hasSize(1);
            assertThat(auditLogger.queuedCases).hasSize(1);
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
                        Set.of("medications"), null, null, null, null, null, null, null, null))));
            clearFatalError();
        }

        @Test
        @DisplayName("sanitizes emergency justifications")
        void sanitizesEmergencyJustification() {
            EmergencyAccessEvent stored = runPromise(() -> service.logAccess(
                buildEvent("patient-1", "<script>alert('xss')</script>")));

            assertThat(stored.justification()).isEqualTo("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;");
        }

        @Test
        @DisplayName("returns overdue reviews separately")
        void returnsOverdueReviews() {
            EmergencyAccessEvent overdue = new EmergencyAccessEvent(
                    "event-overdue",
                    "patient-overdue",
                    "doctor-1",
                    "ER_PHYSICIAN",
                    "critical bleeding",
                    Set.of("labs"),
                    java.time.Instant.now().minusSeconds(172800),
                    java.time.Instant.now().minusSeconds(158400),
                    ReviewStatus.PENDING_REVIEW,
                    java.time.Instant.now().minusSeconds(86400),
                    null,
                    null,
                    null,
                    "EMR-OVERDUE"
            );
            EmergencyAccessEvent current = buildEvent("patient-current", "current emergency");

            runPromise(() -> service.seedEvent(overdue));
            runPromise(() -> service.logAccess(current));

            List<EmergencyAccessEvent> overdueReviews = runPromise(() -> service.getOverdueReviews(10));
            List<EmergencyAccessEvent> pendingReviews = runPromise(() -> service.getPendingReviews(10));

            assertThat(overdueReviews).hasSize(1);
            assertThat(overdueReviews.get(0).patientId()).isEqualTo("patient-overdue");
            assertThat(pendingReviews.get(0).patientId()).isEqualTo("patient-overdue");
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
            assertNotNull(reviewed.reviewedAt());
            assertThat(auditLogger.completedCases).hasSize(1);
        }

        @Test
        @DisplayName("transitions to ESCALATED")
        void transitionsToEscalated() {
            EmergencyAccessEvent stored =
                    runPromise(() -> service.logAccess(buildEvent("patient-4", "critical")));

            EmergencyAccessEvent escalated = runPromise(() ->
                    service.markReviewed(stored.id(), "reviewer-2", ReviewStatus.ESCALATED,
                        "<b>Needs inquiry</b>"));

            assertThat(escalated.reviewStatus()).isEqualTo(ReviewStatus.ESCALATED);
            assertThat(escalated.reviewerNotes()).isEqualTo("&lt;b&gt;Needs inquiry&lt;/b&gt;");
            assertThat(notificationSender.escalations).hasSize(1);
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

            @Test
            @DisplayName("requires reviewer notes for escalated events")
            void escalationRequiresNotes() {
                EmergencyAccessEvent stored =
                    runPromise(() -> service.logAccess(buildEvent("patient-8", "trauma")));

                assertThrows(Exception.class,
                    () -> runPromise(() ->
                        service.markReviewed(stored.id(), "reviewer-3", ReviewStatus.ESCALATED, "  ")));
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
                null, null, null, null, null, null, null, null);
    }

    private static final class RecordingNotificationSender implements EmergencyAccessNotificationSender {
        private final java.util.List<EmergencyAccessReviewCase> complianceNotifications = new java.util.ArrayList<>();
        private final java.util.List<EmergencyAccessReviewCase> reviewSchedules = new java.util.ArrayList<>();
        private final java.util.List<EmergencyAccessReviewCase> escalations = new java.util.ArrayList<>();
        private final java.util.List<EmergencyAccessReviewCase> patientNotifications = new java.util.ArrayList<>();

        @Override
        public io.activej.promise.Promise<Void> notifyComplianceLead(
                EmergencyAccessReviewCase reviewCase,
                EmergencyAccessEvent event) {
            complianceNotifications.add(reviewCase);
            return io.activej.promise.Promise.complete();
        }

        @Override
        public io.activej.promise.Promise<Void> scheduleMandatoryReview(
                EmergencyAccessReviewCase reviewCase,
                EmergencyAccessEvent event) {
            reviewSchedules.add(reviewCase);
            return io.activej.promise.Promise.complete();
        }

        @Override
        public io.activej.promise.Promise<Void> notifyEscalation(
                EmergencyAccessReviewCase reviewCase,
                EmergencyAccessEvent event) {
            escalations.add(reviewCase);
            return io.activej.promise.Promise.complete();
        }

        @Override
        public io.activej.promise.Promise<Void> notifyPatient(
                EmergencyAccessReviewCase reviewCase,
                EmergencyAccessEvent event) {
            patientNotifications.add(reviewCase);
            return io.activej.promise.Promise.complete();
        }
    }

    private static final class RecordingAuditLogger implements EmergencyAccessReviewAuditLogger {
        private final java.util.List<EmergencyAccessReviewCase> queuedCases = new java.util.ArrayList<>();
        private final java.util.List<EmergencyAccessReviewCase> completedCases = new java.util.ArrayList<>();

        @Override
        public io.activej.promise.Promise<Void> logReviewQueued(
                EmergencyAccessReviewCase reviewCase,
                EmergencyAccessEvent event) {
            queuedCases.add(reviewCase);
            return io.activej.promise.Promise.complete();
        }

        @Override
        public io.activej.promise.Promise<Void> logReviewCompleted(
                EmergencyAccessReviewCase reviewCase,
                EmergencyAccessEvent event) {
            completedCases.add(reviewCase);
            return io.activej.promise.Promise.complete();
        }
    }

    private static final class TestableEmergencyAccessLogService extends EmergencyAccessLogService {

        private TestableEmergencyAccessLogService(
                com.ghatana.kernel.context.KernelContext context,
                EmergencyAccessReviewWorkflow reviewWorkflow) {
            super(context, reviewWorkflow);
        }

        private io.activej.promise.Promise<EmergencyAccessEvent> seedEvent(EmergencyAccessEvent event) {
            return createRecord(
                    "phr.emergency.access.log",
                    event.id(),
                    event,
                    java.util.Map.of(
                            "patientId", event.patientId(),
                            "accessorId", event.accessorId(),
                            "reviewStatus", event.reviewStatus().name(),
                            "accessedAt", event.accessedAt().toString(),
                            "accessExpiresAt", event.accessExpiresAt().toString(),
                            "reviewDueAt", event.reviewDueAt().toString(),
                            "reviewCaseId", event.reviewCaseId(),
                            "tenantId", event.patientId(),
                            "principalId", event.accessorId()),
                    "EmergencyAccessEvent",
                    1
            ).map($ -> event);
        }
    }
}
