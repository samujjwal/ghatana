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
        /** Initial state: awaiting a reviewer to pick up the request. */
        PENDING,
        /** A reviewer has started examining the request but not yet decided. */
        REVIEWING,
        /** Terminal: approved by a human reviewer. */
        APPROVED,
        /** Terminal: rejected by a human reviewer. */
        REJECTED,
        /** Terminal: SLA deadline elapsed without a decision. */
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
     * @param workflowId       workflow ID associated with this gate (may be {@code null})
     * @param planId           current AI plan ID at the time of the gate (may be {@code null})
     * @param priorPlanId      plan ID superseded by this approval decision (may be {@code null})
     */
    public record ApprovalContext(
            String fromPhase,
            String toPhase,
            String blockReason,
            List<String> unmetCriteria,
            List<String> missingArtifacts,
            String workflowId,
            String planId,
            String priorPlanId
    ) {
        public ApprovalContext {
            unmetCriteria    = unmetCriteria    != null ? List.copyOf(unmetCriteria)    : List.of();
            missingArtifacts = missingArtifacts != null ? List.copyOf(missingArtifacts) : List.of();
        }

        /**
         * Convenience constructor for callers that do not yet have workflow / plan context.
         * Equivalent to calling the canonical constructor with {@code null} for the last
         * three fields.
         */
        public ApprovalContext(
                String fromPhase,
                String toPhase,
                String blockReason,
                List<String> unmetCriteria,
                List<String> missingArtifacts) {
            this(fromPhase, toPhase, blockReason, unmetCriteria, missingArtifacts, null, null, null);
        }
    }

    /** Returns {@code true} when no decision has been made yet. */
    public boolean isPending() {
        return status == ApprovalStatus.PENDING;
    }

    /** Returns {@code true} when the request is currently under review. */
    public boolean isReviewing() {
        return status == ApprovalStatus.REVIEWING;
    }

    /** Returns {@code true} when the request has been approved. */
    public boolean isApproved() {
        return status == ApprovalStatus.APPROVED;
    }

    /** Returns a copy of this request transitioned to the REVIEWING state. */
    ApprovalRequest asReviewing() {
        return new ApprovalRequest(id, projectId, requestingAgentId, approvalType,
                context, ApprovalStatus.REVIEWING, tenantId, createdAt, null, null, expiresAt);
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

    /** Builder for creating ApprovalRequest instances. */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String projectId;
        private String requestingAgentId;
        private ApprovalType approvalType;
        private ApprovalContext context;
        private ApprovalStatus status = ApprovalStatus.PENDING;
        private String tenantId;
        private Instant createdAt = Instant.now();
        private Instant decidedAt;
        private String decidedBy;
        private Instant expiresAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder requestingAgentId(String requestingAgentId) {
            this.requestingAgentId = requestingAgentId;
            return this;
        }

        public Builder approvalType(ApprovalType approvalType) {
            this.approvalType = approvalType;
            return this;
        }

        public Builder context(ApprovalContext context) {
            this.context = context;
            return this;
        }

        public Builder status(ApprovalStatus status) {
            this.status = status;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder decidedAt(Instant decidedAt) {
            this.decidedAt = decidedAt;
            return this;
        }

        public Builder decidedBy(String decidedBy) {
            this.decidedBy = decidedBy;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public ApprovalRequest build() {
            return new ApprovalRequest(id, projectId, requestingAgentId, approvalType,
                    context, status, tenantId, createdAt, decidedAt, decidedBy, expiresAt);
        }
    }
}
