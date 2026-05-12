/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning.delta;

import com.ghatana.agent.learning.LearningTarget;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a proposed change to learning artifacts.
 *
 * <p>Learning deltas are staged changes that require evaluation and promotion
 * before becoming active. This prevents direct mutation of active knowledge.
 *
 * @doc.type record
 * @doc.purpose Staged learning change proposal
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record LearningDelta(
        @NotNull String deltaId,
        @NotNull String agentId,
        @NotNull String agentReleaseId,
        @NotNull LearningTarget target,
        @NotNull LearningChangeType changeType,
        @NotNull LearningDeltaState state,
        @NotNull String targetId,
        @NotNull String proposedArtifactRef,
        @NotNull List<String> sourceEpisodeIds,
        @NotNull List<String> evidenceRefs,
        @NotNull List<String> evaluationRefs,
        @NotNull String rollbackRef,
        double confidenceBefore,
        double confidenceAfter,
        boolean requiresHumanReview,
        @NotNull Instant createdAt,
        @NotNull Map<String, String> labels
) {
    public LearningDelta {
        Objects.requireNonNull(deltaId, "deltaId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(agentReleaseId, "agentReleaseId must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(changeType, "changeType must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        Objects.requireNonNull(proposedArtifactRef, "proposedArtifactRef must not be null");
        Objects.requireNonNull(sourceEpisodeIds, "sourceEpisodeIds must not be null");
        Objects.requireNonNull(evidenceRefs, "evidenceRefs must not be null");
        Objects.requireNonNull(evaluationRefs, "evaluationRefs must not be null");
        Objects.requireNonNull(rollbackRef, "rollbackRef must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(labels, "labels must not be null");
        sourceEpisodeIds = List.copyOf(sourceEpisodeIds);
        evidenceRefs = List.copyOf(evidenceRefs);
        evaluationRefs = List.copyOf(evaluationRefs);
        labels = Map.copyOf(labels);
    }

    /**
     * Creates a new learning delta proposal.
     *
     * @param agentId agent identifier
     * @param agentReleaseId agent release identifier
     * @param target learning target
     * @param changeType type of change
     * @param targetId target artifact identifier
     * @param proposedArtifactRef reference to proposed artifact
     * @param sourceEpisodeIds source episode identifiers
     * @return learning delta
     */
    @NotNull
    public static LearningDelta propose(
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull LearningTarget target,
            @NotNull LearningChangeType changeType,
            @NotNull String targetId,
            @NotNull String proposedArtifactRef,
            @NotNull List<String> sourceEpisodeIds
    ) {
        return new LearningDelta(
                java.util.UUID.randomUUID().toString(),
                agentId,
                agentReleaseId,
                target,
                changeType,
                LearningDeltaState.PROPOSED,
                targetId,
                proposedArtifactRef,
                sourceEpisodeIds,
                List.of(),
                List.of(),
                "",
                0.0,
                0.0,
                false,
                Instant.now(),
                Map.of()
        );
    }

    /**
     * Returns true if this delta can be promoted.
     *
     * @return true if promotion is allowed
     */
    public boolean canPromote() {
        return state == LearningDeltaState.APPROVED || state == LearningDeltaState.EVALUATED;
    }
}
