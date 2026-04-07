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

    public EmergencyAccessReviewWorkflow(
            EmergencyAccessNotificationSender notificationSender,
            EmergencyAccessReviewAuditLogger auditLogger) {
        this.notificationSender = Objects.requireNonNull(notificationSender, "notificationSender must not be null");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger must not be null");
    }

    public static EmergencyAccessReviewWorkflow fromContext(KernelContext context) {
        Eventloop eventloop = Objects.requireNonNull(context.getEventloop(), "eventloop must not be null");
        EmergencyAccessNotificationSender notificationSender = context
            .getOptionalDependency(EmergencyAccessNotificationSender.class)
            .orElse(NoOpNotificationSender.INSTANCE);
        EmergencyAccessReviewAuditLogger auditLogger = context
            .getOptionalDependency(EmergencyAccessReviewAuditLogger.class)
            .orElse(NoOpAuditLogger.INSTANCE);
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
        EmergencyAccessReviewCase completedCase = reviewCase.withStatus(mapStatus(event.reviewStatus()));
        return auditLogger.logReviewCompleted(completedCase, event)
            .then(() -> event.reviewStatus() == ReviewStatus.ESCALATED
                ? notificationSender.notifyEscalation(completedCase, event)
                : Promise.complete())
            .map($ -> completedCase);
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

    private enum NoOpNotificationSender implements EmergencyAccessNotificationSender {
        INSTANCE;

        @Override
        public Promise<Void> notifyComplianceLead(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
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
    }

    private enum NoOpAuditLogger implements EmergencyAccessReviewAuditLogger {
        INSTANCE;

        @Override
        public Promise<Void> logReviewQueued(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> logReviewCompleted(EmergencyAccessReviewCase reviewCase, EmergencyAccessEvent event) {
            return Promise.complete();
        }
    }
}