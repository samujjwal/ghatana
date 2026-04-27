package com.ghatana.datacloud.governance.approval;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a pending or approved destructive action request.
 *
 * <p>Destructive actions (purge, redaction, bulk delete, export of PII) require
 * explicit admin approval.  The requester supplies a justification; an admin with
 * the {@code ADMIN} role must approve before the action is executed.
 *
 * @doc.type class
 * @doc.purpose Destructive action approval record (P0.4)
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record DestructiveActionApproval(
    String approvalId,
    String tenantId,
    String requesterId,
    ActionType actionType,
    String targetCollection,
    String targetEntityId,
    String justification,
    Instant requestedAt,
    Instant approvedAt,
    String approvedBy,
    Status status
) {

    public DestructiveActionApproval {
        Objects.requireNonNull(approvalId, "approvalId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(justification, "justification");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }

    public enum ActionType {
        PURGE,
        REDACT,
        BULK_DELETE,
        EXPORT_PII
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        EXPIRED
    }

    /**
     * Create a new pending approval request.
     */
    public static DestructiveActionApproval create(
        String tenantId,
        String requesterId,
        ActionType actionType,
        String targetCollection,
        String targetEntityId,
        String justification
    ) {
        return new DestructiveActionApproval(
            UUID.randomUUID().toString(),
            tenantId,
            requesterId,
            actionType,
            targetCollection,
            targetEntityId,
            justification,
            Instant.now(),
            null,
            null,
            Status.PENDING
        );
    }

    /**
     * Return a copy marked as approved by the given admin.
     */
    public DestructiveActionApproval approve(String adminId) {
        return new DestructiveActionApproval(
            approvalId,
            tenantId,
            requesterId,
            actionType,
            targetCollection,
            targetEntityId,
            justification,
            requestedAt,
            Instant.now(),
            adminId,
            Status.APPROVED
        );
    }

    /**
     * Return a copy marked as rejected.
     */
    public DestructiveActionApproval reject(String adminId) {
        return new DestructiveActionApproval(
            approvalId,
            tenantId,
            requesterId,
            actionType,
            targetCollection,
            targetEntityId,
            justification,
            requestedAt,
            Instant.now(),
            adminId,
            Status.REJECTED
        );
    }

    public boolean isApproved() {
        return status == Status.APPROVED;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }
}
