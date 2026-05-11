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
 * @doc.purpose Proposed learned artifact awaiting evaluation and promotion
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record LearningCandidate(
        String candidateId,
        String agentId,
        String agentReleaseId,
        String traceId,
        LearningTarget target,
        LearningCandidateState state,
        List<String> provenanceRefs,
        Map<String, Object> proposedArtifact,
        Instant createdAt
) {
    public LearningCandidate {
        if (candidateId == null || candidateId.isBlank()) throw new IllegalArgumentException("candidateId is required");
        if (agentId == null || agentId.isBlank()) throw new IllegalArgumentException("agentId is required");
        if (target == null) throw new IllegalArgumentException("target is required");
        state = state == null ? LearningCandidateState.PROPOSED : state;
        provenanceRefs = provenanceRefs == null ? List.of() : List.copyOf(provenanceRefs);
        proposedArtifact = proposedArtifact == null ? Map.of() : Map.copyOf(proposedArtifact);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        if (provenanceRefs.isEmpty()) throw new IllegalArgumentException("provenanceRefs are required");
    }
}
