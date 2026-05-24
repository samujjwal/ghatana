package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.phr.kernel.event.PhrAuditEvent;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Emits emergency review audit events into the PHR audit event stream
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class KernelEventEmergencyAccessReviewAuditLogger implements EmergencyAccessReviewAuditLogger {

    private final KernelContext context;

    public KernelEventEmergencyAccessReviewAuditLogger(KernelContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
    }

    @Override
    public Promise<Void> logReviewQueued(EmergencyAccessReviewCase reviewCase, EmergencyAccessLogService.EmergencyAccessEvent event) {
        context.publishEvent(buildEvent("EMERGENCY_REVIEW", "REVIEW_QUEUED", reviewCase, event));
        return Promise.complete();
    }

    @Override
    public Promise<Void> logReviewCompleted(EmergencyAccessReviewCase reviewCase, EmergencyAccessLogService.EmergencyAccessEvent event) {
        context.publishEvent(buildEvent("EMERGENCY_REVIEW", "REVIEW_COMPLETED", reviewCase, event));
        return Promise.complete();
    }

    private PhrAuditEvent buildEvent(
        String auditType,
        String action,
        EmergencyAccessReviewCase reviewCase,
        EmergencyAccessLogService.EmergencyAccessEvent event
    ) {
        String correlationId = "corr-" + reviewCase.caseId() + "-" + event.id();
        return PhrAuditEvent.builder()
            .auditType(auditType)
            .action(action)
            .resourceType("EmergencyAccessReview")
            .resourceId(reviewCase.caseId())
            .actorId(event.accessorId())
            .actorRole(event.accessorRole())
            .tenantId(resolveTenantId())
            .patientId(event.patientId())
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .metadata(Map.of(
                "eventId", event.id(),
                "reviewStatus", event.reviewStatus().name(),
                "accessExpiresAt", String.valueOf(event.accessExpiresAt())
            ))
            .build();
    }

    private String resolveTenantId() {
        if (context.getTenantContext() == null || context.getTenantContext().getTenantId() == null) {
            return "unknown-tenant";
        }
        return context.getTenantContext().getTenantId();
    }
}
