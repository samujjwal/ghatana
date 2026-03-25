/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.learning;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable provenance record capturing the complete lineage of a promoted policy.
 *
 * <p>Every policy that reaches the review queue or is promoted must carry a
 * {@code PolicyProvenanceRecord} that can answer:
 * <ul>
 *   <li>Which episodes produced this policy?</li>
 *   <li>What evaluation metrics justify the confidence score?</li>
 *   <li>Who approved it, when, and why?</li>
 *   <li>What rollout mode is it in?</li>
 *   <li>What is the rollback target if this policy degrades?</li>
 * </ul>
 *
 * <p>Provenance records are stored alongside the policy in DataCloud so they
 * are available for compliance queries and audit logs.
 *
 * @param policyId          unique identifier of the policy
 * @param tenantId          the tenant that owns this policy
 * @param skillId           the skill this policy governs
 * @param version           monotonically increasing version number
 * @param sourceEpisodeIds  IDs of the episodes that contributed evidence for this policy
 * @param evaluationMetrics key performance metrics from the evaluation gate
 *                          (e.g. {@code successRate}, {@code avgLatencyMs}, {@code errorRate})
 * @param confidenceScore   overall confidence score from the evaluation gate [0.0, 1.0]
 * @param approverId        identity of the human approver; {@code null} when auto-promoted
 * @param approverRationale rationale provided by approver; {@code null} for auto-promotion
 * @param promotedAt        timestamp of promotion; {@code null} when still pending review
 * @param activationMode    current activation mode for this policy
 * @param canaryFraction    fraction of traffic [0.0, 1.0] in canary mode; {@code 0.0} otherwise
 * @param rollbackPointerId ID of the previous active policy version for rollback; {@code null} for first
 *
 * @doc.type record
 * @doc.purpose Immutable policy provenance — source episodes, evaluation metrics, approver, rollout state
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PolicyProvenanceRecord(
        @NotNull  String policyId,
        @NotNull  String tenantId,
        @NotNull  String skillId,
        int version,
        @NotNull  List<String> sourceEpisodeIds,
        @NotNull  Map<String, Double> evaluationMetrics,
        double confidenceScore,
        @Nullable String approverId,
        @Nullable String approverRationale,
        @Nullable Instant promotedAt,
        @NotNull  PolicyActivationMode activationMode,
        double canaryFraction,
        @Nullable String rollbackPointerId
) {

    /**
     * Compact constructor — defensive copies for mutable fields.
     */
    public PolicyProvenanceRecord {
        sourceEpisodeIds = List.copyOf(sourceEpisodeIds);
        evaluationMetrics = Map.copyOf(evaluationMetrics);
        if (canaryFraction < 0.0 || canaryFraction > 1.0) {
            throw new IllegalArgumentException("canaryFraction must be in [0.0, 1.0], got: " + canaryFraction);
        }
    }

    // ─── Factory methods ──────────────────────────────────────────────────────

    /**
     * Creates a new provenance record for a policy that has been submitted for review
     * but not yet promoted. Activation mode begins as {@link PolicyActivationMode#SHADOW}.
     */
    public static PolicyProvenanceRecord pending(
            @NotNull String policyId,
            @NotNull String tenantId,
            @NotNull String skillId,
            int version,
            @NotNull List<String> sourceEpisodeIds,
            @NotNull Map<String, Double> evaluationMetrics,
            double confidenceScore) {
        return new PolicyProvenanceRecord(
                policyId, tenantId, skillId, version,
                sourceEpisodeIds, evaluationMetrics, confidenceScore,
                null, null, null,
                PolicyActivationMode.SHADOW, 0.0, null);
    }

    // ─── Transformation methods ───────────────────────────────────────────────

    /**
     * Returns a copy of this record with the given activation mode.
     * All other fields are preserved.
     */
    public PolicyProvenanceRecord withMode(@NotNull PolicyActivationMode mode) {
        return new PolicyProvenanceRecord(
                policyId, tenantId, skillId, version,
                sourceEpisodeIds, evaluationMetrics, confidenceScore,
                approverId, approverRationale, promotedAt,
                mode, canaryFraction, rollbackPointerId);
    }

    /**
     * Returns a copy promoted to the given activation mode, recording the approver identity and
     * linking the previous policy version for rollback.
     *
     * @param approverIdentity identity string for the approver (human user ID or "auto-promote")
     * @param rationale        human-readable rationale for the promotion decision
     * @param mode             the activation mode to transition to ({@link PolicyActivationMode#CANARY}
     *                         or {@link PolicyActivationMode#ACTIVE})
     * @param rollbackTarget   ID of the prior active policy, or {@code null} for the first version
     */
    public PolicyProvenanceRecord withPromotion(
            @NotNull  String approverIdentity,
            @NotNull  String rationale,
            @NotNull  PolicyActivationMode mode,
            @Nullable String rollbackTarget) {
        return new PolicyProvenanceRecord(
                policyId, tenantId, skillId, version,
                sourceEpisodeIds, evaluationMetrics, confidenceScore,
                approverIdentity, rationale, Instant.now(),
                mode, canaryFraction, rollbackTarget);
    }

    /**
     * Returns a copy with the canary fraction updated.
     * Only meaningful when {@link #activationMode()} is {@link PolicyActivationMode#CANARY}.
     */
    public PolicyProvenanceRecord withCanaryFraction(double fraction) {
        return new PolicyProvenanceRecord(
                policyId, tenantId, skillId, version,
                sourceEpisodeIds, evaluationMetrics, confidenceScore,
                approverId, approverRationale, promotedAt,
                PolicyActivationMode.CANARY, fraction, rollbackPointerId);
    }
}
