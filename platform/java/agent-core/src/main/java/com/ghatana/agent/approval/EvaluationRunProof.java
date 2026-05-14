/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.approval;

import java.time.Instant;
import java.util.Map;

/**
 * Proof reference for an evaluation run result.
 *
 * <p>Encapsulates the reference to an evaluation run that can be validated
 * for existence, tenant scope, applicability, expiration, and outcome.
 *
 * @param proofId unique identifier for the evaluation run proof
 * @param tenantId tenant that owns the evaluation run
 * @param agentId agent the evaluation applies to
 * @param releaseId release the evaluation applies to
 * @param skillId skill the evaluation applies to (optional)
 * @param evaluationPackId evaluation pack that was executed
 * @param executedAt timestamp when evaluation was executed
 * @param expiresAt timestamp when evaluation result expires (null for non-expiring)
 * @param outcome evaluation outcome (PASSED, FAILED, etc.)
 * @param score evaluation score
 * @param metadata additional evidence metadata
 *
 * @doc.type record
 * @doc.purpose Proof reference for evaluation run results
 * @doc.layer agent-core
 * @doc.pattern ValueObject
 */
public record EvaluationRunProof(
        String proofId,
        String tenantId,
        String agentId,
        String releaseId,
        String skillId,
        String evaluationPackId,
        Instant executedAt,
        Instant expiresAt,
        EvaluationOutcome outcome,
        double score,
        Map<String, String> metadata
) {

    /**
     * Evaluation outcome enumeration.
     */
    public enum EvaluationOutcome {
        PASSED,
        FAILED,
        INCOMPLETE,
        EXPIRED
    }

    public EvaluationRunProof {
        if (proofId == null || proofId.isBlank()) {
            throw new IllegalArgumentException("proofId must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        if (releaseId == null || releaseId.isBlank()) {
            throw new IllegalArgumentException("releaseId must not be blank");
        }
        if (evaluationPackId == null || evaluationPackId.isBlank()) {
            throw new IllegalArgumentException("evaluationPackId must not be blank");
        }
        if (executedAt == null) {
            throw new IllegalArgumentException("executedAt must not be null");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score must be between 0.0 and 1.0");
        }
        metadata = Map.copyOf(metadata);
    }

    /**
     * Checks if this evaluation run is valid for the given context.
     *
     * @param tenantId tenant to validate against
     * @param agentId agent to validate against
     * @param releaseId release to validate against
     * @param skillId skill to validate against (optional)
     * @return true if evaluation run is valid, false otherwise
     */
    public boolean isValidFor(
            String tenantId,
            String agentId,
            String releaseId,
            String skillId) {
        // Check tenant scope
        if (!this.tenantId.equals(tenantId)) {
            return false;
        }

        // Check agent scope
        if (!this.agentId.equals(agentId)) {
            return false;
        }

        // Check release scope
        if (!this.releaseId.equals(releaseId)) {
            return false;
        }

        // Check skill scope if specified
        if (this.skillId != null && !this.skillId.isBlank() && skillId != null && !skillId.isBlank()) {
            if (!this.skillId.equals(skillId)) {
                return false;
            }
        }

        // Check expiration
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }

        // Check outcome
        return outcome == EvaluationOutcome.PASSED;
    }

    /**
     * Returns true if this evaluation run has expired.
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
