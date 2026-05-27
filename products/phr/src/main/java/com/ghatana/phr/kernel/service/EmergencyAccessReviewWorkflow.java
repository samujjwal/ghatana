package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService.EmergencyAccessEvent;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService.ReviewStatus;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * @doc.type class
 * @doc.purpose Orchestrates mandatory compliance review for break-glass emergency access
 * @doc.layer product
 * @doc.pattern Workflow
 */
public class EmergencyAccessReviewWorkflow {

    private static final Duration COMPLIANCE_NOTIFICATION_DEADLINE = Duration.ofHours(1);

    private final EmergencyAccessNotificationSender notificationSender;
    private final EmergencyAccessReviewAuditLogger auditLogger;
    private final Predicate<String> reviewerAuthorizer;

    public EmergencyAccessReviewWorkflow(
            EmergencyAccessNotificationSender notificationSender,
            EmergencyAccessReviewAuditLogger auditLogger) {
        this(notificationSender, auditLogger, reviewerId -> true);
    }

    public EmergencyAccessReviewWorkflow(
            EmergencyAccessNotificationSender notificationSender,
            EmergencyAccessReviewAuditLogger auditLogger,
            Predicate<String> reviewerAuthorizer) {
        this.notificationSender = Objects.requireNonNull(notificationSender, "notificationSender must not be null");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger must not be null");
        this.reviewerAuthorizer = Objects.requireNonNull(reviewerAuthorizer, "reviewerAuthorizer must not be null");
    }

    public static EmergencyAccessReviewWorkflow fromContext(KernelContext context) {
        Eventloop eventloop = Objects.requireNonNull(context.getEventloop(), "eventloop must not be null");
        EmergencyAccessNotificationSender notificationSender = context
            .getOptionalDependency(EmergencyAccessNotificationSender.class)
            .orElseThrow(() -> new IllegalStateException(
                "EmergencyAccessNotificationSender dependency is required for emergency review workflow"
            ));
        EmergencyAccessReviewAuditLogger auditLogger = context
            .getOptionalDependency(EmergencyAccessReviewAuditLogger.class)
            .orElseThrow(() -> new IllegalStateException(
                "EmergencyAccessReviewAuditLogger dependency is required for emergency review workflow"
            ));
        return new EmergencyAccessReviewWorkflow(
            new ResilientEmergencyAccessNotificationSender(eventloop, notificationSender),
            new ResilientEmergencyAccessReviewAuditLogger(eventloop, auditLogger)
        );
    }

    public static String createCaseId() {
        return "EMR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public Promise<EmergencyAccessReviewCase> initiate(EmergencyAccessEvent event) {
        EmergencyAccessReviewCase reviewCase = toReviewCase(event);
        return auditLogger.logReviewQueued(reviewCase, event)
            .then(() -> notificationSender.notifyComplianceLead(reviewCase, event))
            .then(() -> notificationSender.scheduleMandatoryReview(reviewCase, event))
            .map($ -> reviewCase);
    }

    public Promise<EmergencyAccessReviewCase> complete(EmergencyAccessEvent event) {
        EmergencyAccessReviewCase reviewCase = toReviewCase(event);
        if (!isAuthorizedReviewer(event)) {
            return Promise.of(reviewCase.withStatus(EmergencyAccessReviewCase.ReviewCaseStatus.QUEUED));
        }
        EmergencyAccessReviewCase completedCase = reviewCase.withStatus(mapStatus(event.reviewStatus()));
        return auditLogger.logReviewCompleted(completedCase, event)
            .then(() -> event.reviewStatus() == ReviewStatus.ESCALATED
                ? notificationSender.notifyEscalation(completedCase, event)
                : Promise.complete())
            .map($ -> completedCase);
    }

    /**
     * Notifies the patient of emergency access to their records.
     *
     * <p>This is a policy gate requirement: patients must be notified when their
     * records are accessed via emergency break-glass.</p>
     *
     * @param event the emergency access event
     * @return Promise completing when notification is sent
     */
    public Promise<Void> notifyPatient(EmergencyAccessEvent event) {
        EmergencyAccessReviewCase reviewCase = toReviewCase(event);
        return notificationSender.notifyPatient(reviewCase, event);
    }

    private EmergencyAccessReviewCase toReviewCase(EmergencyAccessEvent event) {
        Instant initiatedAt = event.accessedAt();
        Instant complianceDeadline = initiatedAt.plus(COMPLIANCE_NOTIFICATION_DEADLINE);
        return new EmergencyAccessReviewCase(
            event.reviewCaseId(),
            event.id(),
            event.patientId(),
            event.accessorId(),
            initiatedAt,
            event.accessExpiresAt(),
            event.reviewDueAt(),
            complianceDeadline,
            mapStatus(event.reviewStatus())
        );
    }

    private static EmergencyAccessReviewCase.ReviewCaseStatus mapStatus(ReviewStatus reviewStatus) {
        return switch (reviewStatus) {
            case PENDING_REVIEW -> EmergencyAccessReviewCase.ReviewCaseStatus.QUEUED;
            case REVIEWED -> EmergencyAccessReviewCase.ReviewCaseStatus.REVIEWED;
            case ESCALATED -> EmergencyAccessReviewCase.ReviewCaseStatus.ESCALATED;
        };
    }

    private boolean isAuthorizedReviewer(EmergencyAccessEvent event) {
        if (event.reviewStatus() == ReviewStatus.PENDING_REVIEW) {
            return true;
        }
        return event.reviewedBy() != null && reviewerAuthorizer.test(event.reviewedBy());
    }
}
