/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — Approval Audit Logger
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.audit.AuditLogger;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Compliance and security audit logging for approval lifecycle actions.
 *
 * <p>Wraps the platform {@link AuditLogger} and provides named, type-safe
 * methods for each approval event so that audit entries remain consistent
 * and do not rely on string keys spread across callers.
 *
 * <p><b>Audit event types emitted:</b>
 * <ul>
 *   <li>{@code approval.created}  — new approval request created</li>
 *   <li>{@code approval.review.started}  — reviewer picked up the request</li>
 *   <li>{@code approval.approved} — human approver approved the request</li>
 *   <li>{@code approval.rejected} — human approver rejected the request</li>
 * </ul>
 *
 * <p>All methods return always-completing promises — audit failures are logged
 * as warnings and never propagate to callers.
 *
 * @doc.type class
 * @doc.purpose Compliance audit log for all human approval lifecycle actions
 * @doc.layer product
 * @doc.pattern Adapter
 * @doc.gaa.lifecycle capture
 */
public final class ApprovalAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(ApprovalAuditLogger.class);

    static final String EVENT_CREATED       = "approval.created";
    static final String EVENT_REVIEW        = "approval.review.started";
    static final String EVENT_APPROVED      = "approval.approved";
    static final String EVENT_REJECTED      = "approval.rejected";

    private final AuditLogger delegate;

    /**
     * @param delegate platform audit logger; must not be null
     */
    public ApprovalAuditLogger(AuditLogger delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /**
     * Records an {@code approval.created} entry when a new request is created.
     *
     * @param request the freshly created approval request
     * @return always-completing promise
     */
    public Promise<Void> logCreated(ApprovalRequest request) {
        return emit(EVENT_CREATED, request, null);
    }

    /**
     * Records an {@code approval.review.started} entry when a reviewer picks up a request.
     *
     * @param request the request now in REVIEWING state
     * @return always-completing promise
     */
    public Promise<Void> logReviewStarted(ApprovalRequest request) {
        return emit(EVENT_REVIEW, request, null);
    }

    /**
     * Records an {@code approval.approved} entry when an approver approves.
     *
     * @param request     the approved request (post-transition)
     * @param decidedBy   user ID of the approver
     * @param priorStatus the request status immediately before this decision
     * @return always-completing promise
     */
    public Promise<Void> logApproved(
            ApprovalRequest request,
            String decidedBy,
            ApprovalRequest.ApprovalStatus priorStatus) {
        return emit(EVENT_APPROVED, request, decidedBy, priorStatus);
    }

    /**
     * Records an {@code approval.rejected} entry when an approver rejects.
     *
     * @param request     the rejected request (post-transition)
     * @param decidedBy   user ID of the rejector
     * @param priorStatus the request status immediately before this decision
     * @return always-completing promise
     */
    public Promise<Void> logRejected(
            ApprovalRequest request,
            String decidedBy,
            ApprovalRequest.ApprovalStatus priorStatus) {
        return emit(EVENT_REJECTED, request, decidedBy, priorStatus);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private Promise<Void> emit(
            String eventType,
            ApprovalRequest request,
            String decidedBy,
            ApprovalRequest.ApprovalStatus priorStatus) {
        Map<String, Object> event = buildEvent(eventType, request, decidedBy, priorStatus);
        return delegate.log(event)
                .then(
                        $ -> Promise.complete(),
                        ex -> {
                            log.warn("[audit][type={}][tenant={}][requestId={}] Audit write failed: {}",
                                    eventType, request.tenantId(), request.id(), ex.getMessage());
                            return Promise.complete();
                        }
                );
    }

    private Map<String, Object> buildEvent(
            String eventType,
            ApprovalRequest request,
            String decidedBy,
            ApprovalRequest.ApprovalStatus priorStatus) {

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type",               eventType);
        event.put("tenantId",           request.tenantId());
        event.put("requestId",          request.id());
        event.put("projectId",          request.projectId());
        event.put("requestingAgentId",  request.requestingAgentId());
        event.put("approvalType",       request.approvalType().name());
        event.put("status",             request.status().name());
        event.put("occurredAt",         Instant.now().toString());

        if (priorStatus != null) {
            event.put("priorStatus",   priorStatus.name());
            event.put("statusDiff",    priorStatus.name() + " → " + request.status().name());
        }
        if (decidedBy != null) {
            event.put("decidedBy", decidedBy);
        }
        if (request.decidedAt() != null) {
            event.put("decidedAt", request.decidedAt().toString());
        }

        ApprovalRequest.ApprovalContext ctx = request.context();
        if (ctx != null) {
            event.put("fromPhase",     ctx.fromPhase());
            event.put("toPhase",       ctx.toPhase());
            event.put("blockReason",   ctx.blockReason());
            if (ctx.workflowId() != null) {
                event.put("workflowId",  ctx.workflowId());
            }
            if (ctx.planId() != null) {
                event.put("planId",      ctx.planId());
            }
            if (ctx.priorPlanId() != null) {
                event.put("priorPlanId", ctx.priorPlanId());
            }
        }
        return event;
    }

    // --- Legacy two-arg overloads kept for logCreated/logReviewStarted ----------

    private Promise<Void> emit(String eventType, ApprovalRequest request, String decidedBy) {
        return emit(eventType, request, decidedBy, null);
    }
}
