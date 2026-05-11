/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Evidence supporting promotion of a learning candidate to active status
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record PromotionEvidence(
        String evidenceId,
        String candidateId,
        String evaluationPackId,
        List<String> evaluationRefs,
        Map<String, Object> metrics,
        String approvedBy,
        Instant createdAt
) {
    public PromotionEvidence {
        if (evidenceId == null || evidenceId.isBlank()) throw new IllegalArgumentException("evidenceId is required");
        if (candidateId == null || candidateId.isBlank()) throw new IllegalArgumentException("candidateId is required");
        evaluationRefs = evaluationRefs == null ? List.of() : List.copyOf(evaluationRefs);
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        if (evaluationRefs.isEmpty()) throw new IllegalArgumentException("evaluationRefs are required");
    }
}
