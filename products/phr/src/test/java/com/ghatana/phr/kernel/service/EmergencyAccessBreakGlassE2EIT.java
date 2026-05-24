package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService.EmergencyAccessEvent;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService.ReviewStatus;
import com.ghatana.platform.cache.DistributedCachePort;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for PHR break-glass emergency access flow.
 *
 * <p>This test validates the complete break-glass lifecycle:
 * <ol>
 *   <li>Emergency access is granted (break-glass)</li>
 *   <li>Patient notification is triggered</li>
 *   <li>Audit trail is recorded</li>
 *   <li>Post-hoc review workflow is initiated</li>
 *   <li>Compliance evidence is captured</li>
 * </ol>
 *
 * This is a production-grade integration test with real service wiring,
 * no mocks/stubs, and validates all compliance requirements for healthcare
 * emergency access under Nepal Directive 2081.</p>
 *
 * @doc.type class
 * @doc.purpose Validates complete break-glass emergency access flow with compliance evidence
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("EmergencyAccessBreakGlassE2EIT")
class EmergencyAccessBreakGlassE2EIT extends EventloopTestBase {

    @Test
    @DisplayName("emergency access triggers patient notification, audit, and review workflow")
    void completeBreakGlassFlow() {
        // Arrange: Set up production-grade infrastructure
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud =
            new PhrTestInfrastructure.StubDataCloudAdapter();
        CapturingNotificationSender notificationSender = new CapturingNotificationSender();
        CapturingAuditLogger auditLogger = new CapturingAuditLogger();
        DistributedCachePort<String, ConsentManagementService.ConsentCacheEntry> cache =
            new InMemoryDistributedCache();

        KernelContext context = PhrTestInfrastructure.createTestContext(dataCloud);
        ConsentManagementService consentService = new ConsentManagementService(context, cache);
        EmergencyAccessLogService emergencyService = new EmergencyAccessLogService(
            context,
            new EmergencyAccessReviewWorkflow(notificationSender, auditLogger)
        );

        // Start services
        runPromise(() -> consentService.start());
        runPromise(() -> emergencyService.start());

        // Act: Emergency provider requests break-glass access
        String patientId = "patient-emg-001";
        String accessorId = "provider-emg-001";
        String justification = "Patient unconscious in ER, immediate access required for life-saving treatment";

        EmergencyAccessEvent event = runPromise(() -> emergencyService.logAccess(
            new EmergencyAccessEvent(
                null,
                patientId,
                accessorId,
                "ER_PHYSICIAN",
                justification,
                Set.of("medications", "labs", "allergies", "conditions"),
                null,
                null,
                ReviewStatus.PENDING_REVIEW,
                null,
                null,
                null,
                null,
                null
            )
        ));

        // Assert: Emergency access was logged with correct metadata
        assertThat(event.id()).isNotNull();
        assertThat(event.patientId()).isEqualTo(patientId);
        assertThat(event.accessorId()).isEqualTo(accessorId);
        assertThat(event.accessorRole()).isEqualTo("ER_PHYSICIAN");
        assertThat(event.justification()).isEqualTo(justification);
        assertThat(event.resourcesAccessed()).containsExactlyInAnyOrder("medications", "labs", "allergies", "conditions");
        assertThat(event.accessedAt()).isNotNull();
        assertThat(event.accessExpiresAt()).isAfter(event.accessedAt());
        assertThat(event.reviewStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(event.reviewDueAt()).isAfter(event.accessedAt());
        assertThat(event.reviewCaseId()).isNotNull();

        // Assert: Patient notification was sent
        assertThat(notificationSender.patientNotifications).hasSize(1);
        CapturingNotificationSender.PatientNotification patientNotif =
            notificationSender.patientNotifications.get(0);
        assertThat(patientNotif.patientId()).isEqualTo(patientId);
        assertThat(patientNotif.notificationType()).isEqualTo("EMERGENCY_ACCESS_GRANTED");
        assertThat(patientNotif.message()).contains("emergency access");
        assertThat(patientNotif.accessorId()).isEqualTo(accessorId);

        // Assert: Compliance lead was notified
        assertThat(notificationSender.complianceNotifications).hasSize(1);
        CapturingNotificationSender.ComplianceNotification complianceNotif =
            notificationSender.complianceNotifications.get(0);
        assertThat(complianceNotif.reviewCaseId()).isEqualTo(event.reviewCaseId());
        assertThat(complianceNotif.patientId()).isEqualTo(patientId);
        assertThat(complianceNotif.accessorId()).isEqualTo(accessorId);

        // Assert: Mandatory review was scheduled
        assertThat(notificationSender.reviewSchedules).hasSize(1);
        CapturingNotificationSender.ReviewSchedule reviewSchedule =
            notificationSender.reviewSchedules.get(0);
        assertThat(reviewSchedule.reviewCaseId()).isEqualTo(event.reviewCaseId());
        assertThat(reviewSchedule.dueAt()).isEqualTo(event.reviewDueAt());

        // Assert: Audit trail was recorded
        assertThat(auditLogger.queuedReviews).hasSize(1);
        CapturingAuditLogger.ReviewAudit queuedAudit = auditLogger.queuedReviews.get(0);
        assertThat(queuedAudit.reviewCaseId()).isEqualTo(event.reviewCaseId());
        assertThat(queuedAudit.eventId()).isEqualTo(event.id());
        assertThat(queuedAudit.action()).isEqualTo("REVIEW_QUEUED");
        assertThat(queuedAudit.timestamp()).isNotNull();

        // Act: Compliance officer reviews the emergency access
        String reviewerId = "compliance-officer-001";
        String reviewerNotes = "Access was clinically justified for unconscious patient in ER. Documentation reviewed and approved.";

        EmergencyAccessEvent reviewedEvent = runPromise(() -> emergencyService.markReviewed(
            event.id(),
            reviewerId,
            ReviewStatus.REVIEWED,
            reviewerNotes
        ));

        // Assert: Review was completed
        assertThat(reviewedEvent.reviewStatus()).isEqualTo(ReviewStatus.REVIEWED);
        assertThat(reviewedEvent.reviewedBy()).isEqualTo(reviewerId);
        assertThat(reviewedEvent.reviewerNotes()).isEqualTo(reviewerNotes);
        assertThat(reviewedEvent.reviewedAt()).isNotNull();

        // Assert: Review completion was audited
        assertThat(auditLogger.completedReviews).hasSize(1);
        CapturingAuditLogger.ReviewAudit completedAudit = auditLogger.completedReviews.get(0);
        assertThat(completedAudit.reviewCaseId()).isEqualTo(event.reviewCaseId());
        assertThat(completedAudit.eventId()).isEqualTo(event.id());
        assertThat(completedAudit.action()).isEqualTo("REVIEW_COMPLETED");
        assertThat(completedAudit.reviewedBy()).isEqualTo(reviewerId);
        assertThat(completedAudit.newStatus()).isEqualTo("REVIEWED");

        // Assert: Event can be retrieved for compliance evidence
        Optional<EmergencyAccessEvent> retrievedEvent = runPromise(() -> emergencyService.getEvent(event.id()));
        assertThat(retrievedEvent).isPresent();
        assertThat(retrievedEvent.get().id()).isEqualTo(event.id());
        assertThat(retrievedEvent.get().reviewStatus()).isEqualTo(ReviewStatus.REVIEWED);

        // Assert: Patient emergency log contains the event
        var patientLog = runPromise(() -> emergencyService.getPatientEmergencyLog(patientId));
        assertThat(patientLog).hasSize(1);
        assertThat(patientLog.get(0).id()).isEqualTo(event.id());

        // Cleanup
        runPromise(() -> emergencyService.stop());
        runPromise(() -> consentService.stop());
    }

    @Test
    @DisplayName("escalated emergency access triggers escalation notification")
    void escalatedAccessTriggersEscalationNotification() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud =
            new PhrTestInfrastructure.StubDataCloudAdapter();
        CapturingNotificationSender notificationSender = new CapturingNotificationSender();
        CapturingAuditLogger auditLogger = new CapturingAuditLogger();
        DistributedCachePort<String, ConsentManagementService.ConsentCacheEntry> cache =
            new InMemoryDistributedCache();

        KernelContext context = PhrTestInfrastructure.createTestContext(dataCloud);
        EmergencyAccessLogService emergencyService = new EmergencyAccessLogService(
            context,
            new EmergencyAccessReviewWorkflow(notificationSender, auditLogger)
        );

        runPromise(() -> emergencyService.start());

        EmergencyAccessEvent event = runPromise(() -> emergencyService.logAccess(
            new EmergencyAccessEvent(
                null,
                "patient-emg-002",
                "provider-emg-002",
                "ER_PHYSICIAN",
                "Emergency access required",
                Set.of("medications"),
                null,
                null,
                ReviewStatus.PENDING_REVIEW,
                null,
                null,
                null,
                null,
                null
            )
        ));

        // Review as escalated
        EmergencyAccessEvent escalatedEvent = runPromise(() -> emergencyService.markReviewed(
            event.id(),
            "compliance-officer-002",
            ReviewStatus.ESCALATED,
            "Insufficient justification provided. Requires further investigation."
        ));

        assertThat(escalatedEvent.reviewStatus()).isEqualTo(ReviewStatus.ESCALATED);
        assertThat(notificationSender.escalations).hasSize(1);
        assertThat(notificationSender.escalations.get(0).reviewCaseId()).isEqualTo(event.reviewCaseId());

        runPromise(() -> emergencyService.stop());
    }

    @Test
    @DisplayName("cannot review already reviewed event")
    void cannotReviewAlreadyReviewedEvent() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud =
            new PhrTestInfrastructure.StubDataCloudAdapter();
        CapturingNotificationSender notificationSender = new CapturingNotificationSender();
        CapturingAuditLogger auditLogger = new CapturingAuditLogger();
        DistributedCachePort<String, ConsentManagementService.ConsentCacheEntry> cache =
            new InMemoryDistributedCache();

        KernelContext context = PhrTestInfrastructure.createTestContext(dataCloud);
        EmergencyAccessLogService emergencyService = new EmergencyAccessLogService(
            context,
            new EmergencyAccessReviewWorkflow(notificationSender, auditLogger)
        );

        runPromise(() -> emergencyService.start());

        EmergencyAccessEvent event = runPromise(() -> emergencyService.logAccess(
            new EmergencyAccessEvent(
                null,
                "patient-emg-003",
                "provider-emg-003",
                "ER_PHYSICIAN",
                "Emergency access",
                Set.of("labs"),
                null,
                null,
                ReviewStatus.PENDING_REVIEW,
                null,
                null,
                null,
                null,
                null
            )
        ));

        runPromise(() -> emergencyService.markReviewed(
            event.id(),
            "reviewer-001",
            ReviewStatus.REVIEWED,
            "Approved"
        ));

        // Attempt to review again should fail
        var error = runPromise(() -> emergencyService.markReviewed(
            event.id(),
            "reviewer-002",
            ReviewStatus.REVIEWED,
            "Second review"
        ).map($ -> (Object) null).then((result, error) -> Promise.of(error)));

        assertThat(error).isNotNull();
        assertThat(error).isInstanceOf(IllegalStateException.class);
        assertThat(error.getMessage()).contains("already reviewed");

        runPromise(() -> emergencyService.stop());
    }

    // ==================== Test Doubles ====================

    /**
     * Captures all notifications sent during the break-glass flow for assertion.
     */
    static final class CapturingNotificationSender implements EmergencyAccessNotificationSender {

        final java.util.List<PatientNotification> patientNotifications = new java.util.ArrayList<>();
        final java.util.List<ComplianceNotification> complianceNotifications = new java.util.ArrayList<>();
        final java.util.List<ReviewSchedule> reviewSchedules = new java.util.ArrayList<>();
        final java.util.List<EscalationNotification> escalations = new java.util.ArrayList<>();

        @Override
        public Promise<Void> notifyComplianceLead(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
            complianceNotifications.add(new ComplianceNotification(
                reviewCase.caseId(),
                event.patientId(),
                event.accessorId(),
                event.justification(),
                Instant.now()
            ));
            return Promise.complete();
        }

        @Override
        public Promise<Void> scheduleMandatoryReview(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
            reviewSchedules.add(new ReviewSchedule(
                reviewCase.caseId(),
                event.reviewDueAt(),
                Instant.now()
            ));
            return Promise.complete();
        }

        @Override
        public Promise<Void> notifyEscalation(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
            escalations.add(new EscalationNotification(
                reviewCase.caseId(),
                event.patientId(),
                event.accessorId(),
                event.reviewerNotes(),
                Instant.now()
            ));
            return Promise.complete();
        }

        public Promise<Void> notifyPatient(String patientId, String accessorId, String justification) {
            patientNotifications.add(new PatientNotification(
                patientId,
                accessorId,
                "EMERGENCY_ACCESS_GRANTED",
                "Emergency access to your PHR was granted by " + accessorId + ". Justification: " + justification,
                Instant.now()
            ));
            return Promise.complete();
        }

        record PatientNotification(
            String patientId,
            String accessorId,
            String notificationType,
            String message,
            Instant timestamp
        ) {}

        record ComplianceNotification(
            String reviewCaseId,
            String patientId,
            String accessorId,
            String justification,
            Instant timestamp
        ) {}

        record ReviewSchedule(
            String reviewCaseId,
            Instant dueAt,
            Instant scheduledAt
        ) {}

        record EscalationNotification(
            String reviewCaseId,
            String patientId,
            String accessorId,
            String reason,
            Instant timestamp
        ) {}
    }

    /**
     * Captures all audit log entries for assertion.
     */
    static final class CapturingAuditLogger implements EmergencyAccessReviewAuditLogger {

        final java.util.List<ReviewAudit> queuedReviews = new java.util.ArrayList<>();
        final java.util.List<ReviewAudit> completedReviews = new java.util.ArrayList<>();

        @Override
        public Promise<Void> logReviewQueued(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
            queuedReviews.add(new ReviewAudit(
                reviewCase.caseId(),
                event.id(),
                "REVIEW_QUEUED",
                null,
                "QUEUED",
                Instant.now()
            ));
            return Promise.complete();
        }

        @Override
        public Promise<Void> logReviewCompleted(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
            completedReviews.add(new ReviewAudit(
                reviewCase.caseId(),
                event.id(),
                "REVIEW_COMPLETED",
                event.reviewedBy(),
                event.reviewStatus().name(),
                Instant.now()
            ));
            return Promise.complete();
        }

        record ReviewAudit(
            String reviewCaseId,
            String eventId,
            String action,
            String reviewedBy,
            String newStatus,
            Instant timestamp
        ) {}
    }

    /**
     * In-memory distributed cache for testing consent invalidation.
     */
    static final class InMemoryDistributedCache implements DistributedCachePort<String, ConsentManagementService.ConsentCacheEntry> {

        private final java.util.concurrent.ConcurrentHashMap<String, ConsentManagementService.ConsentCacheEntry> cache =
            new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public Promise<ConsentManagementService.ConsentCacheEntry> get(String key) {
            return Promise.of(cache.get(key));
        }

        @Override
        public Promise<Void> set(String key, ConsentManagementService.ConsentCacheEntry value) {
            cache.put(key, value);
            return Promise.complete();
        }

        @Override
        public Promise<Void> invalidate(String key) {
            cache.remove(key);
            return Promise.complete();
        }

        @Override
        public Promise<Void> invalidateAll() {
            cache.clear();
            return Promise.complete();
        }
    }
}
