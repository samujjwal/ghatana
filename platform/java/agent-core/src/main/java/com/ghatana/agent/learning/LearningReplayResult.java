/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import java.time.Instant;
import java.util.List;

/**
 * Result of replaying candidate evidence against an evaluation pack.
 */
public record LearningReplayResult(
        String runId,
        String candidateId,
        String evaluationPackId,
        double score,
        boolean passed,
        List<String> evaluationRefs,
        Instant evaluatedAt
) {
    public LearningReplayResult {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId is required");
        if (candidateId == null || candidateId.isBlank()) throw new IllegalArgumentException("candidateId is required");
        if (evaluationPackId == null || evaluationPackId.isBlank()) throw new IllegalArgumentException("evaluationPackId is required");
        if (score < 0.0 || score > 1.0) throw new IllegalArgumentException("score must be in [0.0, 1.0]");
        evaluationRefs = evaluationRefs == null ? List.of() : List.copyOf(evaluationRefs);
        evaluatedAt = evaluatedAt == null ? Instant.now() : evaluatedAt;
    }
}
