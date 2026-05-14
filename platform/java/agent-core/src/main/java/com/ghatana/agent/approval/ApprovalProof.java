/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.approval;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Proof reference for a human approval decision.
 *
 * <p>Encapsulates the reference to an approval record that can be validated
 * for existence, tenant scope, applicability, expiration, and outcome.
 *
 * @param proofId unique identifier for the approval proof
 * @param tenantId tenant that owns the approval
 * @param agentId agent the approval applies to
 * @param releaseId release the approval applies to (optional)
 * @param skillId skill the approval applies to (optional)
 * @param taskId task the approval applies to (optional)
 * @param approvedAt timestamp when approval was granted
 * @param expiresAt timestamp when approval expires (null for non-expiring)
 * @param approverId principal who granted approval
 * @param outcome approval outcome (APPROVED, DENIED, etc.)
 * @param metadata additional evidence metadata
 *
 * @doc.type record
 * @doc.purpose Proof reference for human approval decisions
 * @doc.layer agent-core
 * @doc.pattern ValueObject
 */
public record ApprovalProof(
        String proofId,
        String tenantId,
        String agentId,
        String releaseId,
        String skillId,
        String taskId,
        Instant approvedAt,
        Instant expiresAt,
        String approverId,
        ApprovalOutcome outcome,
        Map<String, String> metadata
) {

    /**
     * Approval outcome enumeration.
     */
    public enum ApprovalOutcome {
        APPROVED,
        DENIED,
        DEFERRED,
        EXPIRED
    }

    public ApprovalProof {
        if (proofId == null || proofId.isBlank()) {
            throw new IllegalArgumentException("proofId must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        if (approvedAt == null) {
            throw new IllegalArgumentException("approvedAt must not be null");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        metadata = Map.copyOf(metadata);
    }

    /**
     * Checks if this approval is valid for the given context.
     *
     * @param tenantId tenant to validate against
     * @param agentId agent to validate against
     * @param releaseId release to validate against (optional)
     * @param skillId skill to validate against (optional)
     * @param taskId task to validate against (optional)
     * @return true if approval is valid, false otherwise
     */
    public boolean isValidFor(
            String tenantId,
            String agentId,
            String releaseId,
            String skillId,
            String taskId) {
        // Check tenant scope
        if (!this.tenantId.equals(tenantId)) {
            return false;
        }

        // Check agent scope
        if (!this.agentId.equals(agentId)) {
            return false;
        }

        // Check release scope if specified
        if (this.releaseId != null && !this.releaseId.isBlank() && releaseId != null && !releaseId.isBlank()) {
            if (!this.releaseId.equals(releaseId)) {
                return false;
            }
        }

        // Check skill scope if specified
        if (this.skillId != null && !this.skillId.isBlank() && skillId != null && !skillId.isBlank()) {
            if (!this.skillId.equals(skillId)) {
                return false;
            }
        }

        // Check task scope if specified
        if (this.taskId != null && !this.taskId.isBlank() && taskId != null && !taskId.isBlank()) {
            if (!this.taskId.equals(taskId)) {
                return false;
            }
        }

        // Check expiration
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }

        // Check outcome
        return outcome == ApprovalOutcome.APPROVED;
    }

    /**
     * Returns true if this approval has expired.
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
