/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Represents a lifecycle approval request awaiting a human decision.
 *
 * <p>Approval requests are created when a phase transition is blocked by a gate
 * that requires an explicit human sign-off (PHASE_ADVANCE), a deployment gate,
 * or a risk-acceptance checkpoint.
 *
 * @param id                 unique approval request ID
 * @param projectId          YAPPC project this request belongs to
 * @param requestingAgentId  agent that created the request (may be {@code null} if created by a user)
 * @param approvalType       one of {@code PHASE_ADVANCE | DEPLOYMENT | RISK_ACCEPTANCE}
 * @param context            structured context providing all information a reviewer needs
 * @param status             current status of the request
 * @param tenantId           tenant owning this request
 * @param createdAt          creation timestamp
 * @param decidedAt          decision timestamp ({@code null} while PENDING)
 * @param decidedBy          user who made the decision ({@code null} while PENDING)
 * @param expiresAt          optional SLA deadline ({@code null} means no expiry)
 *
 * @doc.type record
 * @doc.purpose Value object representing a lifecycle human-approval gate request
 * @doc.layer product
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle act
 */
public record ApprovalRequest(
        String id,
        String projectId,
        String requestingAgentId,
        ApprovalType approvalType,
        ApprovalContext context,
        ApprovalStatus status,
        String tenantId,
        Instant createdAt,
        Instant decidedAt,
        String decidedBy,
        Instant expiresAt
) {

    /** Supported approval request types. */
    public enum ApprovalType {
        PHASE_ADVANCE,
        DEPLOYMENT,
        RISK_ACCEPTANCE
    }

    /** Lifecycle of an approval request. */
    public enum ApprovalStatus {
        PENDING,
        APPROVED,
        REJECTED,
        EXPIRED
    }

    /**
     * Structured context block carried by a {@link ApprovalRequest}.
     *
     * @param fromPhase        source phase when type is {@code PHASE_ADVANCE}
     * @param toPhase          target phase when type is {@code PHASE_ADVANCE}
     * @param blockReason      human-readable description of the blocking concern
     * @param unmetCriteria    list of unmet gate criteria (may be empty)
     * @param missingArtifacts artifact IDs that are absent (may be empty)
     */
    public record ApprovalContext(
            String fromPhase,
            String toPhase,
            String blockReason,
            List<String> unmetCriteria,
            List<String> missingArtifacts
    ) {
        public ApprovalContext {
            unmetCriteria    = unmetCriteria    != null ? List.copyOf(unmetCriteria)    : List.of();
            missingArtifacts = missingArtifacts != null ? List.copyOf(missingArtifacts) : List.of();
        }
    }

    /** Returns {@code true} when no decision has been made yet. */
    public boolean isPending() {
        return status == ApprovalStatus.PENDING;
    }

    /** Returns {@code true} when the request has been approved. */
    public boolean isApproved() {
        return status == ApprovalStatus.APPROVED;
    }

    /** Returns an expired copy of this request. */
    ApprovalRequest asExpired() {
        return new ApprovalRequest(id, projectId, requestingAgentId, approvalType,
                context, ApprovalStatus.EXPIRED, tenantId, createdAt, Instant.now(), "system", expiresAt);
    }

    /** Returns an approved copy of this request. */
    ApprovalRequest asApproved(String decidedBy) {
        Objects.requireNonNull(decidedBy, "decidedBy must not be null");
        return new ApprovalRequest(id, projectId, requestingAgentId, approvalType,
                context, ApprovalStatus.APPROVED, tenantId, createdAt, Instant.now(), decidedBy, expiresAt);
    }

    /** Returns a rejected copy of this request. */
    ApprovalRequest asRejected(String decidedBy) {
        Objects.requireNonNull(decidedBy, "decidedBy must not be null");
        return new ApprovalRequest(id, projectId, requestingAgentId, approvalType,
                context, ApprovalStatus.REJECTED, tenantId, createdAt, Instant.now(), decidedBy, expiresAt);
    }
}
