/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.promotion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable record representing audit evidence for a memory promotion step.
 *
 * <p>Each step in the 7-step episodic→procedural promotion pipeline produces
 * one {@code PromotionEvidence} record. The full audit trail for a promotion
 * consists of up to 7 evidence records linked by {@link #sourceMemoryId}.
 *
 * @param evidenceId      globally unique evidence record ID
 * @param tenantId        tenant scope
 * @param agentId         the agent whose memory is being promoted
 * @param namespaceId     the target memory namespace
 * @param sourceMemoryId  the episodic memory entry being promoted
 * @param targetMemoryId  the resulting procedural memory entry (set on step 7)
 * @param promotionStep   step name in the promotion pipeline
 * @param stepOrdinal     1-based step ordinal (1–7)
 * @param score           quality score at this step (null if step does not score)
 * @param passed          whether this step's gate was passed
 * @param approverId      optional ID of the human/agent that approved
 * @param approvedAt      timestamp of approval (null if not applicable)
 * @param rejectedReason  reason for rejection (null if passed)
 * @param promotedAt      when this evidence was recorded
 * @param data            additional structured metadata
 *
 * @doc.type class
 * @doc.purpose Immutable promotion step evidence record for memory promotion audit trail
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record PromotionEvidence(
        @NotNull String evidenceId,
        @NotNull String tenantId,
        @NotNull String agentId,
        @NotNull String namespaceId,
        @NotNull String sourceMemoryId,
        @Nullable String targetMemoryId,
        @NotNull String promotionStep,
        int stepOrdinal,
        @Nullable Double score,
        boolean passed,
        @Nullable String approverId,
        @Nullable Instant approvedAt,
        @Nullable String rejectedReason,
        @NotNull Instant promotedAt,
        @NotNull Map<String, Object> data
) {

    /** Validates required fields and normalises the data map to an immutable copy. */
    public PromotionEvidence {
        Objects.requireNonNull(evidenceId,     "evidenceId");
        Objects.requireNonNull(tenantId,       "tenantId");
        Objects.requireNonNull(agentId,        "agentId");
        Objects.requireNonNull(namespaceId,    "namespaceId");
        Objects.requireNonNull(sourceMemoryId, "sourceMemoryId");
        Objects.requireNonNull(promotionStep,  "promotionStep");
        Objects.requireNonNull(promotedAt,     "promotedAt");
        Objects.requireNonNull(data,           "data");

        if (evidenceId.isBlank())     throw new IllegalArgumentException("evidenceId must not be blank");
        if (tenantId.isBlank())       throw new IllegalArgumentException("tenantId must not be blank");
        if (agentId.isBlank())        throw new IllegalArgumentException("agentId must not be blank");
        if (namespaceId.isBlank())    throw new IllegalArgumentException("namespaceId must not be blank");
        if (sourceMemoryId.isBlank()) throw new IllegalArgumentException("sourceMemoryId must not be blank");
        if (promotionStep.isBlank())  throw new IllegalArgumentException("promotionStep must not be blank");
        if (stepOrdinal < 1)          throw new IllegalArgumentException("stepOrdinal must be >= 1");
        if (score != null && (score < 0.0 || score > 1.0)) {
            throw new IllegalArgumentException("score must be in [0.0, 1.0]");
        }

        data = Map.copyOf(data);
    }

    /**
     * Creates a passing step evidence record with no score (for non-scored steps).
     *
     * @param evidenceId     unique ID
     * @param tenantId       tenant scope
     * @param agentId        owning agent
     * @param namespaceId    target namespace
     * @param sourceMemoryId source episodic memory
     * @param promotionStep  step name
     * @param stepOrdinal    1-based ordinal
     * @param promotedAt     when this evidence was produced
     * @return a passing evidence record
     */
    public static PromotionEvidence passing(
            String evidenceId,
            String tenantId,
            String agentId,
            String namespaceId,
            String sourceMemoryId,
            String promotionStep,
            int stepOrdinal,
            Instant promotedAt) {
        return new PromotionEvidence(evidenceId, tenantId, agentId, namespaceId,
                sourceMemoryId, null, promotionStep, stepOrdinal,
                null, true, null, null, null, promotedAt, Map.of());
    }

    /**
     * Creates a rejected step evidence record.
     *
     * @param evidenceId     unique ID
     * @param tenantId       tenant scope
     * @param agentId        owning agent
     * @param namespaceId    target namespace
     * @param sourceMemoryId source episodic memory
     * @param promotionStep  step name
     * @param stepOrdinal    1-based ordinal
     * @param rejectedReason reason for rejection
     * @param promotedAt     when this evidence was produced
     * @return a rejected evidence record
     */
    public static PromotionEvidence rejected(
            String evidenceId,
            String tenantId,
            String agentId,
            String namespaceId,
            String sourceMemoryId,
            String promotionStep,
            int stepOrdinal,
            String rejectedReason,
            Instant promotedAt) {
        return new PromotionEvidence(evidenceId, tenantId, agentId, namespaceId,
                sourceMemoryId, null, promotionStep, stepOrdinal,
                null, false, null, null, rejectedReason, promotedAt, Map.of());
    }
}
