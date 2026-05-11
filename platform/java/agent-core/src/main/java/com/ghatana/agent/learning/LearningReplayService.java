/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic replay scorer for learned candidates.
 */
public final class LearningReplayService {

    public @NotNull LearningReplayResult replay(
            @NotNull LearningCandidate candidate,
            @NotNull String evaluationPackId,
            @NotNull List<LearningReplayCase> cases,
            double passThreshold) {
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("at least one replay case is required");
        }
        double totalWeight = 0.0;
        double matchedWeight = 0.0;
        for (LearningReplayCase replayCase : cases) {
            totalWeight += replayCase.weight();
            if (candidate.proposedArtifact().entrySet().containsAll(replayCase.expected().entrySet())) {
                matchedWeight += replayCase.weight();
            }
        }
        double score = totalWeight == 0.0 ? 0.0 : matchedWeight / totalWeight;
        return new LearningReplayResult(
                "lr-" + UUID.randomUUID(),
                candidate.candidateId(),
                evaluationPackId,
                score,
                score >= passThreshold,
                cases.stream().map(LearningReplayCase::caseId).toList(),
                Instant.now());
    }
}
