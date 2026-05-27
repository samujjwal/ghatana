package com.ghatana.phr.kernel.service;

import com.ghatana.phr.kernel.service.EmergencyAccessLogService.EmergencyAccessEvent;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService.ReviewStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests emergency access review workflow notifications and escalation handling
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EmergencyAccessReviewWorkflow")
class EmergencyAccessReviewWorkflowTest extends EventloopTestBase {

    private RecordingNotificationSender notificationSender;
    private RecordingAuditLogger auditLogger;
    private EmergencyAccessReviewWorkflow workflow;

    @BeforeEach
    void setUp() {
        notificationSender = new RecordingNotificationSender();
        auditLogger = new RecordingAuditLogger();
        workflow = new EmergencyAccessReviewWorkflow(
                notificationSender,
                auditLogger,
                "reviewer-1"::equals);
    }

    @Test
    @DisplayName("queues compliance review when emergency access is initiated")
    void queuesReview() {
        EmergencyAccessEvent event = event(ReviewStatus.PENDING_REVIEW, null, null);

        EmergencyAccessReviewCase reviewCase = runPromise(() -> workflow.initiate(event));

        assertThat(reviewCase.caseId()).isEqualTo(event.reviewCaseId());
        assertThat(reviewCase.status()).isEqualTo(EmergencyAccessReviewCase.ReviewCaseStatus.QUEUED);
        assertThat(notificationSender.complianceNotifications).hasSize(1);
        assertThat(notificationSender.reviewSchedules).hasSize(1);
        assertThat(auditLogger.queuedCases).hasSize(1);
    }

    @Test
    @DisplayName("sends escalation notification when review escalates")
    void sendsEscalationNotification() {
        EmergencyAccessEvent escalatedEvent = event(
                ReviewStatus.ESCALATED,
                Instant.now(),
                "Needs disciplinary review");

        EmergencyAccessReviewCase reviewCase = runPromise(() -> workflow.complete(escalatedEvent));

        assertThat(reviewCase.status()).isEqualTo(EmergencyAccessReviewCase.ReviewCaseStatus.ESCALATED);
        assertThat(notificationSender.escalations).hasSize(1);
        assertThat(auditLogger.completedCases).hasSize(1);
    }

    @Test
    @DisplayName("completes without escalation notification for normal review")
    void completesReviewedCase() {
        EmergencyAccessEvent reviewedEvent = event(ReviewStatus.REVIEWED, Instant.now(), "Clinically justified");

        EmergencyAccessReviewCase reviewCase = runPromise(() -> workflow.complete(reviewedEvent));

        assertThat(reviewCase.status()).isEqualTo(EmergencyAccessReviewCase.ReviewCaseStatus.REVIEWED);
        assertThat(notificationSender.escalations).isEmpty();
        assertThat(auditLogger.completedCases).hasSize(1);
    }

    @Test
    @DisplayName("retries transient notification delivery during initiation")
    void retriesTransientNotificationDeliveryDuringInitiation() {
        AtomicInteger attempts = new AtomicInteger(0);
        EmergencyAccessNotificationSender flakySender = new EmergencyAccessNotificationSender() {
            @Override
            public Promise<Void> notifyComplianceLead(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
                if (attempts.incrementAndGet() < 3) {
                    return Promise.ofException(new IllegalStateException("temporary notification failure"));
                }
                return Promise.complete();
            }

            @Override
            public Promise<Void> scheduleMandatoryReview(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
                return Promise.complete();
            }

            @Override
            public Promise<Void> notifyEscalation(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
                return Promise.complete();
            }

            @Override
            public Promise<Void> notifyPatient(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
                return Promise.complete();
            }
        };

        workflow = new EmergencyAccessReviewWorkflow(
            new ResilientEmergencyAccessNotificationSender(
                eventloop(),
                flakySender,
                com.ghatana.platform.resilience.RetryPolicy.builder()
                    .maxRetries(2)
                    .initialDelay(Duration.ofMillis(1))
                    .build(),
                com.ghatana.platform.resilience.CircuitBreaker.builder("compliance").failureThreshold(5).resetTimeout(Duration.ofSeconds(1)).build(),
                com.ghatana.platform.resilience.CircuitBreaker.builder("schedule").failureThreshold(5).resetTimeout(Duration.ofSeconds(1)).build(),
                com.ghatana.platform.resilience.CircuitBreaker.builder("escalation").failureThreshold(5).resetTimeout(Duration.ofSeconds(1)).build(),
                com.ghatana.platform.resilience.CircuitBreaker.builder("patient").failureThreshold(5).resetTimeout(Duration.ofSeconds(1)).build()
            ),
            auditLogger
        );

        EmergencyAccessReviewCase reviewCase = runPromise(() -> workflow.initiate(event(ReviewStatus.PENDING_REVIEW, null, null)));

        assertThat(reviewCase.caseId()).isEqualTo("EMR-12345678");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("denies review when reviewer is not authorized")
    void deniesUnauthorizedReview() {
        EmergencyAccessEvent unauthorizedEvent = event(
                ReviewStatus.REVIEWED,
                Instant.now(),
                "Unauthorized reviewer attempt",
                "unauthorized-user"
        );

        EmergencyAccessReviewCase reviewCase = runPromise(() -> workflow.complete(unauthorizedEvent));

        assertThat(reviewCase.status()).isEqualTo(EmergencyAccessReviewCase.ReviewCaseStatus.QUEUED);
        assertThat(auditLogger.completedCases).isEmpty();
    }

    @Test
    @DisplayName("expires emergency access after deadline")
    void expiresEmergencyAccessAfterDeadline() {
        Instant accessedAt = Instant.now().minusSeconds(15000);
        EmergencyAccessEvent expiredEvent = new EmergencyAccessEvent(
                "event-expired",
                "patient-1",
                "doctor-1",
                "ER_PHYSICIAN",
                "Patient unconscious",
                Set.of("medications", "labs"),
                accessedAt,
                accessedAt.plusSeconds(14400),
                ReviewStatus.PENDING_REVIEW,
                accessedAt.plusSeconds(86400),
                null,
                null,
                null,
                "EMR-EXPIRED"
        );

        EmergencyAccessReviewCase reviewCase = runPromise(() -> workflow.initiate(expiredEvent));

        assertThat(reviewCase.accessExpiresAt()).isBefore(Instant.now());
        assertThat(reviewCase.status()).isEqualTo(EmergencyAccessReviewCase.ReviewCaseStatus.QUEUED);
    }

    @Test
    @DisplayName("requires mandatory review before access expires")
    void requiresMandatoryReviewBeforeExpiration() {
        Instant accessedAt = Instant.now().minusSeconds(1800);
        EmergencyAccessEvent baseEvent = event(
                ReviewStatus.PENDING_REVIEW,
                null,
                null
        );
        EmergencyAccessEvent pendingEvent = new EmergencyAccessEvent(
                baseEvent.id(),
                baseEvent.patientId(),
                baseEvent.accessorId(),
                baseEvent.accessorRole(),
                baseEvent.justification(),
                baseEvent.resourcesAccessed(),
                accessedAt,
                accessedAt.plusSeconds(14400),
                ReviewStatus.PENDING_REVIEW,
                accessedAt.plusSeconds(86400),
                null,
                null,
                null,
                "EMR-PENDING"
        );

        EmergencyAccessReviewCase reviewCase = runPromise(() -> workflow.initiate(pendingEvent));

        assertThat(reviewCase.reviewDueAt()).isAfter(Instant.now());
        assertThat(reviewCase.complianceDeadline()).isAfter(Instant.now());
        assertThat(notificationSender.reviewSchedules).hasSize(1);
    }

    @Test
    @DisplayName("audit trail persists all emergency access events")
    void auditTrailPersistsAllEvents() {
        EmergencyAccessEvent event1 = event(ReviewStatus.PENDING_REVIEW, null, null);
        EmergencyAccessEvent event2 = event(ReviewStatus.REVIEWED, Instant.now(), "Clinically justified");

        runPromise(() -> workflow.initiate(event1));
        runPromise(() -> workflow.complete(event2));

        assertThat(auditLogger.queuedCases).hasSize(1);
        assertThat(auditLogger.completedCases).hasSize(1);
        assertThat(auditLogger.queuedCases.get(0).caseId()).isEqualTo("EMR-12345678");
        assertThat(auditLogger.completedCases.get(0).caseId()).isEqualTo("EMR-12345678");
    }

    private static EmergencyAccessEvent event(ReviewStatus status, Instant reviewedAt, String notes) {
        return event(status, reviewedAt, notes, "reviewer-1");
    }

    private static EmergencyAccessEvent event(ReviewStatus status, Instant reviewedAt, String notes, String reviewerId) {
        Instant accessedAt = Instant.now().minusSeconds(300);
        return new EmergencyAccessEvent(
                "event-1",
                "patient-1",
                "doctor-1",
                "ER_PHYSICIAN",
                "Patient unconscious",
                Set.of("medications", "labs"),
                accessedAt,
                accessedAt.plusSeconds(14400),
                status,
                accessedAt.plusSeconds(86400),
                reviewedAt,
                reviewerId,
                notes,
                "EMR-12345678"
        );
    }

    private static final class RecordingNotificationSender implements EmergencyAccessNotificationSender {
        private final List<EmergencyAccessReviewCase> complianceNotifications = new ArrayList<>();
        private final List<EmergencyAccessReviewCase> reviewSchedules = new ArrayList<>();
        private final List<EmergencyAccessReviewCase> escalations = new ArrayList<>();

        @Override
        public Promise<Void> notifyComplianceLead(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
            complianceNotifications.add(reviewCase);
            return Promise.complete();
        }

        @Override
        public Promise<Void> scheduleMandatoryReview(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
            reviewSchedules.add(reviewCase);
            return Promise.complete();
        }

        @Override
        public Promise<Void> notifyEscalation(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
            escalations.add(reviewCase);
            return Promise.complete();
        }

        @Override
        public Promise<Void> notifyPatient(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
            return Promise.complete();
        }
    }

    private static final class RecordingAuditLogger implements EmergencyAccessReviewAuditLogger {
        private final List<EmergencyAccessReviewCase> queuedCases = new ArrayList<>();
        private final List<EmergencyAccessReviewCase> completedCases = new ArrayList<>();

        @Override
        public Promise<Void> logReviewQueued(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
            queuedCases.add(reviewCase);
            return Promise.complete();
        }

        @Override
        public Promise<Void> logReviewCompleted(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
            completedCases.add(reviewCase);
            return Promise.complete();
        }
    }
}
