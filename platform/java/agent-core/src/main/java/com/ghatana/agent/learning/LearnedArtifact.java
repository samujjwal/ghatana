/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a learned artifact produced by an agent during the learning phase.
 *
 * <p>Idempotency keys: {@code tenantId + candidateId} and {@code tenantId + contentDigest + target}
 * must be unique per tenant. These constraints prevent duplicate artifacts from being created
 * when the same candidate or content is promoted more than once.
 *
 * @doc.type record
 * @doc.purpose Represents a learned artifact produced by an agent during the learning phase
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record LearnedArtifact(
        String artifactId,
        String agentId,
        String agentReleaseId,
        LearningTarget target,
        PromotionState state,
        Map<String, Object> payload,
        List<String> provenanceRefs,
        String promotionEvidenceId,
        String rollbackRef,
        Instant createdAt,
        /** The learning candidate ID this artifact was promoted from. Used as idempotency key. */
        @Nullable String candidateId,
        /** The skill this artifact applies to. */
        @Nullable String skillId,
        /** The tenant owning this artifact. Required for tenant isolation. */
        @Nullable String tenantId,
        /** Episode IDs used as the training source for this artifact. */
        @Nullable List<String> sourceEpisodeIds,
        /** SHA-256 digest of the payload content. Used as idempotency key together with target and tenantId. */
        @Nullable String contentDigest
) {
    public LearnedArtifact {
        if (artifactId == null || artifactId.isBlank()) throw new IllegalArgumentException("artifactId is required");
        if (agentId == null || agentId.isBlank()) throw new IllegalArgumentException("agentId is required");
        if (target == null) throw new IllegalArgumentException("target is required");
        state = state == null ? PromotionState.DRAFT : state;
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        provenanceRefs = provenanceRefs == null ? List.of() : List.copyOf(provenanceRefs);
        sourceEpisodeIds = sourceEpisodeIds == null ? List.of() : List.copyOf(sourceEpisodeIds);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        if (state == PromotionState.ACTIVE && (promotionEvidenceId == null || promotionEvidenceId.isBlank())) {
            throw new IllegalArgumentException("ACTIVE learned artifacts require promotionEvidenceId");
        }
    }
}
