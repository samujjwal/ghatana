/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.release.rollout;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record representing a single rollout request for an {@link com.ghatana.agent.release.AgentRelease}.
 *
 * <p>A rollout captures the intent to promote a specific agent release into a target environment
 * for a given tenant. It tracks the approval decision, the principals involved, the traffic split,
 * fallback configuration, and the kill-switch flag.
 *
 * <p>All fields are set at creation and updated only via the builder or {@link #withApprovalState}
 * factory method, which produces a new immutable record.
 *
 * @param rolloutId             unique identifier for this rollout request
 * @param agentReleaseId        the release being rolled out
 * @param tenantId              the tenant this rollout is scoped to
 * @param targetEnvironment     the environment to roll out into (e.g., {@code "production"})
 * @param trafficSplitPercent   the percentage of traffic to route to the new release (0–100)
 * @param fallbackReleaseId     optional ID of the release to fall back to if the rollout fails
 * @param approvalState         current approval state of this rollout
 * @param requestedBy           identity of the principal who submitted the rollout
 * @param approvedBy            identity of the principal who approved (null until approved)
 * @param rejectedBy            identity of the principal who rejected (null unless rejected)
 * @param rejectedReason        human-readable rejection reason (null unless rejected)
 * @param killSwitch            when {@code true}, the rollout is administratively stopped regardless of approval
 * @param requestedAt           when the rollout was submitted
 * @param decidedAt             when the approval decision was made (null if still pending)
 * @param expiresAt             deadline for a decision before the rollout auto-expires
 *
 * @doc.type class
 * @doc.purpose Immutable value object for a tenant-scoped agent rollout request
 * @doc.layer platform
 * @doc.pattern Record
 */
public record AgentRolloutRecord(
        String rolloutId,
        String agentReleaseId,
        String tenantId,
        String targetEnvironment,
        int trafficSplitPercent,
        String fallbackReleaseId,
        AgentRolloutApprovalState approvalState,
        String requestedBy,
        String approvedBy,
        String rejectedBy,
        String rejectedReason,
        boolean killSwitch,
        Instant requestedAt,
        Instant decidedAt,
        Instant expiresAt) {

    public AgentRolloutRecord {
        Objects.requireNonNull(rolloutId, "rolloutId must not be null");
        Objects.requireNonNull(agentReleaseId, "agentReleaseId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(targetEnvironment, "targetEnvironment must not be null");
        Objects.requireNonNull(approvalState, "approvalState must not be null");
        Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");

        if (rolloutId.isBlank()) {
            throw new IllegalArgumentException("rolloutId must not be blank");
        }
        if (agentReleaseId.isBlank()) {
            throw new IllegalArgumentException("agentReleaseId must not be blank");
        }
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (targetEnvironment.isBlank()) {
            throw new IllegalArgumentException("targetEnvironment must not be blank");
        }
        if (requestedBy.isBlank()) {
            throw new IllegalArgumentException("requestedBy must not be blank");
        }
        if (trafficSplitPercent < 0 || trafficSplitPercent > 100) {
            throw new IllegalArgumentException(
                    "trafficSplitPercent must be between 0 and 100, got: " + trafficSplitPercent);
        }
    }

    /**
     * Returns a new record with the approval state updated to {@code APPROVED}.
     *
     * @param approvedBy  the principal granting the approval
     * @param decidedAt   the moment the decision was made
     * @return a new {@code AgentRolloutRecord} in the APPROVED state
     */
    public AgentRolloutRecord withApproved(String approvedBy, Instant decidedAt) {
        Objects.requireNonNull(approvedBy, "approvedBy must not be null");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");

        return new AgentRolloutRecord(
                rolloutId, agentReleaseId, tenantId, targetEnvironment,
                trafficSplitPercent, fallbackReleaseId,
                AgentRolloutApprovalState.APPROVED,
                requestedBy, approvedBy, null, null,
                killSwitch, requestedAt, decidedAt, expiresAt);
    }

    /**
     * Returns a new record with the approval state updated to {@code REJECTED}.
     *
     * @param rejectedBy  the principal rejecting the rollout
     * @param reason      the human-readable reason for rejection
     * @param decidedAt   the moment the decision was made
     * @return a new {@code AgentRolloutRecord} in the REJECTED state
     */
    public AgentRolloutRecord withRejected(String rejectedBy, String reason, Instant decidedAt) {
        Objects.requireNonNull(rejectedBy, "rejectedBy must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");

        return new AgentRolloutRecord(
                rolloutId, agentReleaseId, tenantId, targetEnvironment,
                trafficSplitPercent, fallbackReleaseId,
                AgentRolloutApprovalState.REJECTED,
                requestedBy, null, rejectedBy, reason,
                killSwitch, requestedAt, decidedAt, expiresAt);
    }

    /**
     * Returns a new record with the approval state updated to {@code ROLLED_BACK}.
     *
     * @param rolledBackBy  the principal performing the rollback
     * @param rolledBackAt  when the rollback occurred
     * @return a new {@code AgentRolloutRecord} in the ROLLED_BACK state
     */
    public AgentRolloutRecord withRolledBack(String rolledBackBy, Instant rolledBackAt) {
        Objects.requireNonNull(rolledBackBy, "rolledBackBy must not be null");
        Objects.requireNonNull(rolledBackAt, "rolledBackAt must not be null");

        return new AgentRolloutRecord(
                rolloutId, agentReleaseId, tenantId, targetEnvironment,
                trafficSplitPercent, fallbackReleaseId,
                AgentRolloutApprovalState.ROLLED_BACK,
                requestedBy, approvedBy, rolledBackBy,
                "Rolled back by " + rolledBackBy,
                killSwitch, requestedAt, rolledBackAt, expiresAt);
    }

    /**
     * Returns {@code true} if this rollout is actionable — i.e., {@link #approvalState}
     * is {@code APPROVED} and the kill-switch is not active.
     *
     * @return whether the rollout should route traffic
     */
    public boolean isActive() {
        return approvalState == AgentRolloutApprovalState.APPROVED && !killSwitch;
    }
}
