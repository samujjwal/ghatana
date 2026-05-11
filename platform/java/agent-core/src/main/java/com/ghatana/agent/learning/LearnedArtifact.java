/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
        Instant createdAt
) {
    public LearnedArtifact {
        if (artifactId == null || artifactId.isBlank()) throw new IllegalArgumentException("artifactId is required");
        if (agentId == null || agentId.isBlank()) throw new IllegalArgumentException("agentId is required");
        if (target == null) throw new IllegalArgumentException("target is required");
        state = state == null ? PromotionState.DRAFT : state;
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        provenanceRefs = provenanceRefs == null ? List.of() : List.copyOf(provenanceRefs);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        if (state == PromotionState.ACTIVE && (promotionEvidenceId == null || promotionEvidenceId.isBlank())) {
            throw new IllegalArgumentException("ACTIVE learned artifacts require promotionEvidenceId");
        }
    }
}
