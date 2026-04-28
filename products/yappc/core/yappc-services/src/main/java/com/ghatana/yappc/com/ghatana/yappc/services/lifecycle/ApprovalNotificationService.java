/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — Approval Notification Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.yappc.agent.AepEventPublisher;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Publishes structured AEP notification events when approval requests are created
 * or decided so that downstream subscribers (e-mail, WebSocket, Slack adapters) can
 * react without coupling to the approval-service internals.
 *
 * <p>Three event types are emitted:
 * <ul>
 *   <li>{@code approval.notification.requested} — fired after a new request is created</li>
 *   <li>{@code approval.notification.approved} — fired after an approver approves</li>
 *   <li>{@code approval.notification.rejected} — fired after an approver rejects</li>
 * </ul>
 *
 * <p>All events carry a canonical payload that can be routed by topic name alone;
 * callers must not depend on the internal field ordering of the JSON object.
 *
 * @doc.type class
 * @doc.purpose Publishes AEP notification events for approval lifecycle state changes
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle act
 */
public final class ApprovalNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalNotificationService.class);

    static final String TOPIC_REQUESTED = "approval.notification.requested";
    static final String TOPIC_APPROVED  = "approval.notification.approved";
    static final String TOPIC_REJECTED  = "approval.notification.rejected";

    private final AepEventPublisher publisher;

    /**
     * @param publisher AEP event publisher; must not be null
     */
    public ApprovalNotificationService(AepEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    /**
     * Publishes an {@code approval.notification.requested} event.
     * Failures are logged as warnings; the returned promise always completes successfully
     * so that notification errors do not abort the approval creation flow.
     *
     * @param request newly created approval request
     * @return always-completing promise
     */
    public Promise<Void> notifyRequested(ApprovalRequest request) {
        return publish(TOPIC_REQUESTED, buildPayload(request, null, null));
    }

    /**
     * Publishes an {@code approval.notification.approved} event.
     *
     * @param request  the approved request
     * @param decidedBy identity of the approver
     * @return always-completing promise
     */
    public Promise<Void> notifyApproved(ApprovalRequest request, String decidedBy) {
        return publish(TOPIC_APPROVED, buildPayload(request, decidedBy, null));
    }

    /**
     * Publishes an {@code approval.notification.rejected} event.
     *
     * @param request  the rejected request
     * @param decidedBy identity of the rejector
     * @return always-completing promise
     */
    public Promise<Void> notifyRejected(ApprovalRequest request, String decidedBy) {
        return publish(TOPIC_REJECTED, buildPayload(request, decidedBy, null));
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private Promise<Void> publish(String topic, Map<String, Object> payload) {
        String tenantId = (String) payload.getOrDefault("tenantId", "unknown");
        return publisher.publish(topic, tenantId, payload)
                .mapException(ex -> {
                    log.warn("[topic={}][tenant={}] Notification publish failed: {}",
                            topic, tenantId, ex.getMessage());
                    return ex;
                })
                .then($ -> Promise.complete(),
                      ex -> {
                          // Swallow errors so notification failures do not abort approval flows
                          return Promise.complete();
                      });
    }

    private Map<String, Object> buildPayload(ApprovalRequest request, String decidedBy, @SuppressWarnings("unused") String extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId",          request.id());
        payload.put("tenantId",           request.tenantId());
        payload.put("projectId",          request.projectId());
        payload.put("requestingAgentId",  request.requestingAgentId());
        payload.put("approvalType",       request.approvalType().name());
        payload.put("status",             request.status().name());
        payload.put("createdAt",          request.createdAt().toString());

        if (request.expiresAt() != null) {
            payload.put("expiresAt", request.expiresAt().toString());
        }
        if (decidedBy != null) {
            payload.put("decidedBy", decidedBy);
        }
        if (request.decidedAt() != null) {
            payload.put("decidedAt", request.decidedAt().toString());
        }

        ApprovalRequest.ApprovalContext ctx = request.context();
        if (ctx != null) {
            payload.put("fromPhase",   ctx.fromPhase());
            payload.put("toPhase",     ctx.toPhase());
            payload.put("blockReason", ctx.blockReason());
        }

        payload.put("notificationTimestamp", Instant.now().toString());
        return payload;
    }
}
