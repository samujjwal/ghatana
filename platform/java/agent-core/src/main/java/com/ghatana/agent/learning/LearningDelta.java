/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Proposed learning change to agent behavior or knowledge.
 *
 * <p>A LearningDelta represents a proposed change to an agent's learned knowledge,
 * including the type of change, the content, evidence, and promotion state.
 *
 * @doc.type record
 * @doc.purpose Proposed learning change to agent behavior or knowledge
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record LearningDelta(
        @NotNull String deltaId,
        @NotNull LearningDeltaType type,
        @NotNull LearningTarget target,
        @NotNull LearningDeltaState state,
        @NotNull String agentId,
        @NotNull String agentReleaseId,
        @NotNull String skillId,
        @NotNull String tenantId,
        @Nullable String procedureId,
        @Nullable String semanticFactId,
        @Nullable String negativeKnowledgeId,
        @NotNull String contentDigest,
        @NotNull Map<String, Object> proposedContent,
        @NotNull List<String> evidenceRefs,
        @NotNull List<String> evaluationRefs,
        @NotNull List<String> sourceEpisodeIds,
        @Nullable String rollbackRef,
        double confidenceBefore,
        double confidenceAfter,
        boolean requiresHumanReview,
        @NotNull String proposedBy,
        @NotNull Instant proposedAt,
        @Nullable Instant evaluatedAt,
        @Nullable Instant promotedAt,
        @Nullable Instant rejectedAt,
        @NotNull Map<String, String> labels,
        @Nullable String rejectionReason,
        @Nullable String approvalProofRef,
        // Phase 6 FIX: Add environment/version fields for learning loop provenance
        @Nullable String versionContextDigest,
        @Nullable String environmentFingerprintRef,
        @Nullable String repositoryConventionRef,
        @Nullable String runtimeFingerprintRef
) {
    public LearningDelta {
        Objects.requireNonNull(deltaId, "deltaId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(agentReleaseId, "agentReleaseId must not be null");
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(contentDigest, "contentDigest must not be null");
        Objects.requireNonNull(proposedContent, "proposedContent must not be null");
        Objects.requireNonNull(evidenceRefs, "evidenceRefs must not be null");
        Objects.requireNonNull(evaluationRefs, "evaluationRefs must not be null");
        Objects.requireNonNull(sourceEpisodeIds, "sourceEpisodeIds must not be null");
        Objects.requireNonNull(proposedBy, "proposedBy must not be null");
        Objects.requireNonNull(proposedAt, "proposedAt must not be null");
        Objects.requireNonNull(labels, "labels must not be null");
        proposedContent = Map.copyOf(proposedContent);
        evidenceRefs = List.copyOf(evidenceRefs);
        evaluationRefs = List.copyOf(evaluationRefs);
        sourceEpisodeIds = List.copyOf(sourceEpisodeIds);
        labels = Map.copyOf(labels);
    }

    /**
     * Returns true if this delta is currently promotable.
     * A delta is promotable if it is in EVALUATED or APPROVED state.
     *
     * @return true if promotable
     */
    public boolean isPromotable() {
        return state == LearningDeltaState.EVALUATED || state == LearningDeltaState.APPROVED;
    }

    /**
     * Returns true if this delta is currently rejected.
     *
     * @return true if rejected
     */
    public boolean isRejected() {
        return state == LearningDeltaState.REJECTED;
    }

    /**
     * Returns true if this delta is currently promoted.
     *
     * @return true if promoted
     */
    public boolean isPromoted() {
        return state == LearningDeltaState.PROMOTED;
    }

    /**
     * Returns true if this delta is currently pending evaluation.
     *
     * @return true if pending evaluation
     */
    public boolean isPendingEvaluation() {
        return state == LearningDeltaState.PENDING_EVALUATION;
    }

    /**
     * Returns true if this delta is currently being promoted.
     *
     * @return true if promoting
     */
    public boolean isPromoting() {
        return state == LearningDeltaState.PROMOTING;
    }
}
