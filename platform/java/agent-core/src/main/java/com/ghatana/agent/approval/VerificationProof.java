/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.approval;

import java.time.Instant;
import java.util.Map;

/**
 * Proof reference for a verification decision.
 *
 * <p>Encapsulates the reference to a verification record that can be validated
 * for existence, tenant scope, applicability, expiration, and outcome.
 *
 * @param proofId unique identifier for the verification proof
 * @param tenantId tenant that owns the verification
 * @param agentId agent the verification applies to
 * @param releaseId release the verification applies to (optional)
 * @param skillId skill the verification applies to (optional)
 * @param taskId task the verification applies to (optional)
 * @param verifiedAt timestamp when verification was completed
 * @param expiresAt timestamp when verification expires (null for non-expiring)
 * @param verifierId principal who performed verification
 * @param outcome verification outcome (VERIFIED, FAILED, etc.)
 * @param verificationType type of verification performed
 * @param metadata additional evidence metadata
 *
 * @doc.type record
 * @doc.purpose Proof reference for verification decisions
 * @doc.layer agent-core
 * @doc.pattern ValueObject
 */
public record VerificationProof(
        String proofId,
        String tenantId,
        String agentId,
        String releaseId,
        String skillId,
        String taskId,
        Instant verifiedAt,
        Instant expiresAt,
        String verifierId,
        VerificationOutcome outcome,
        VerificationType verificationType,
        Map<String, String> metadata
) {

    /**
     * Verification outcome enumeration.
     */
    public enum VerificationOutcome {
        VERIFIED,
        FAILED,
        PENDING,
        EXPIRED
    }

    /**
     * Verification type enumeration.
     */
    public enum VerificationType {
        MANUAL,
        AUTOMATED,
        HYBRID,
        PEER_REVIEW,
        AUDIT
    }

    public VerificationProof {
        if (proofId == null || proofId.isBlank()) {
            throw new IllegalArgumentException("proofId must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        if (verifiedAt == null) {
            throw new IllegalArgumentException("verifiedAt must not be null");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (verificationType == null) {
            throw new IllegalArgumentException("verificationType must not be null");
        }
        metadata = Map.copyOf(metadata);
    }

    /**
     * Checks if this verification is valid for the given context.
     *
     * @param tenantId tenant to validate against
     * @param agentId agent to validate against
     * @param releaseId release to validate against (optional)
     * @param skillId skill to validate against (optional)
     * @param taskId task to validate against (optional)
     * @return true if verification is valid, false otherwise
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
        return outcome == VerificationOutcome.VERIFIED;
    }

    /**
     * Returns true if this verification has expired.
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
