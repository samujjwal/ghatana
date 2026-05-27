package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Publishes emergency access notification intents as kernel events
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class KernelEventEmergencyAccessNotificationSender implements EmergencyAccessNotificationSender {

    private final KernelContext context;

    public KernelEventEmergencyAccessNotificationSender(KernelContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
    }

    @Override
    public Promise<Void> notifyComplianceLead(EmergencyAccessReviewCase reviewCase, EmergencyAccessLogService.EmergencyAccessEvent event) {
        context.publishEvent(new EmergencyAccessNotificationEvent(
            "compliance_lead_notify",
            reviewCase.caseId(),
            event.id(),
            event.patientId(),
            event.accessorId(),
            Instant.now(),
            Map.of("justification", event.justification())
        ));
        return Promise.complete();
    }

    @Override
    public Promise<Void> scheduleMandatoryReview(EmergencyAccessReviewCase reviewCase, EmergencyAccessLogService.EmergencyAccessEvent event) {
        context.publishEvent(new EmergencyAccessNotificationEvent(
            "mandatory_review_schedule",
            reviewCase.caseId(),
            event.id(),
            event.patientId(),
            event.accessorId(),
            Instant.now(),
            Map.of("reviewDueAt", String.valueOf(event.reviewDueAt()))
        ));
        return Promise.complete();
    }

    @Override
    public Promise<Void> notifyEscalation(EmergencyAccessReviewCase reviewCase, EmergencyAccessLogService.EmergencyAccessEvent event) {
        context.publishEvent(new EmergencyAccessNotificationEvent(
            "review_escalation",
            reviewCase.caseId(),
            event.id(),
            event.patientId(),
            event.accessorId(),
            Instant.now(),
            Map.of("reviewStatus", event.reviewStatus().name())
        ));
        return Promise.complete();
    }

    @Override
    public Promise<Void> notifyPatient(EmergencyAccessReviewCase reviewCase, EmergencyAccessLogService.EmergencyAccessEvent event) {
        context.publishEvent(new EmergencyAccessNotificationEvent(
            "patient_notify",
            reviewCase.caseId(),
            event.id(),
            event.patientId(),
            event.accessorId(),
            Instant.now(),
            Map.of(
                "accessorRole", event.accessorRole(),
                "justification", event.justification(),
                "resourcesAccessed", String.join(",", event.resourcesAccessed())
            )
        ));
        return Promise.complete();
    }

    public record EmergencyAccessNotificationEvent(
        String action,
        String reviewCaseId,
        String emergencyEventId,
        String patientId,
        String accessorId,
        Instant emittedAt,
        Map<String, String> metadata
    ) {}
}
