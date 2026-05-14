/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.approval;

import java.time.Instant;
import java.util.Map;

/**
 * Proof reference for a policy decision.
 *
 * <p>Encapsulates the reference to a policy decision that can be validated
 * for existence, tenant scope, applicability, expiration, and outcome.
 *
 * @param proofId unique identifier for the policy decision proof
 * @param tenantId tenant that owns the policy decision
 * @param agentId agent the policy decision applies to
 * @param releaseId release the policy decision applies to (optional)
 * @param policyPackId policy pack that was evaluated
 * @param decidedAt timestamp when policy decision was made
 * @param expiresAt timestamp when policy decision expires (null for non-expiring)
 * @param outcome policy decision outcome (ALLOWED, DENIED, etc.)
 * @param decisionReason reason for the policy decision
 * @param metadata additional evidence metadata
 *
 * @doc.type record
 * @doc.purpose Proof reference for policy decisions
 * @doc.layer agent-core
 * @doc.pattern ValueObject
 */
public record PolicyDecisionProof(
        String proofId,
        String tenantId,
        String agentId,
        String releaseId,
        String policyPackId,
        Instant decidedAt,
        Instant expiresAt,
        PolicyOutcome outcome,
        String decisionReason,
        Map<String, String> metadata
) {

    /**
     * Policy outcome enumeration.
     */
    public enum PolicyOutcome {
        ALLOWED,
        DENIED,
        CONDITIONAL,
        EXPIRED
    }

    public PolicyDecisionProof {
        if (proofId == null || proofId.isBlank()) {
            throw new IllegalArgumentException("proofId must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        if (policyPackId == null || policyPackId.isBlank()) {
            throw new IllegalArgumentException("policyPackId must not be blank");
        }
        if (decidedAt == null) {
            throw new IllegalArgumentException("decidedAt must not be null");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (decisionReason == null || decisionReason.isBlank()) {
            throw new IllegalArgumentException("decisionReason must not be blank");
        }
        metadata = Map.copyOf(metadata);
    }

    /**
     * Checks if this policy decision is valid for the given context.
     *
     * @param tenantId tenant to validate against
     * @param agentId agent to validate against
     * @param releaseId release to validate against (optional)
     * @return true if policy decision is valid, false otherwise
     */
    public boolean isValidFor(
            String tenantId,
            String agentId,
            String releaseId) {
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

        // Check expiration
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }

        // Check outcome - ALLOWED and CONDITIONAL are valid
        return outcome == PolicyOutcome.ALLOWED || outcome == PolicyOutcome.CONDITIONAL;
    }

    /**
     * Returns true if this policy decision has expired.
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
