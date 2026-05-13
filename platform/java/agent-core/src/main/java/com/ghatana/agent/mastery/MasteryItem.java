/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mastery item tracking an agent's skill maturity, version applicability, and lifecycle state.
 *
 * <p>A mastery item represents what an agent knows, how well it knows it, which versions it applies to,
 * and its current lifecycle state. It links to procedures, semantic facts, negative knowledge,
 * evidence, evaluations, and known failure modes.
 *
 * @doc.type record
 * @doc.purpose Mastery item tracking skill maturity and lifecycle
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record MasteryItem(
        @NotNull String masteryId,
        @NotNull String tenantId,
        @NotNull String skillId,
        @NotNull String domain,
        @NotNull String agentId,
        @NotNull String agentReleaseId,
        @NotNull MasteryState state,
        @NotNull VersionScope versionScope,
        @NotNull ApplicabilityScope applicability,
        @NotNull MasteryScore score,
        @NotNull List<String> procedureIds,
        @NotNull List<String> semanticFactIds,
        @NotNull List<String> negativeKnowledgeIds,
        @NotNull List<String> evidenceRefs,
        @NotNull List<String> evaluationRefs,
        @NotNull List<String> knownFailureModeIds,
        @NotNull Instant lastVerifiedAt,
        @NotNull Instant staleAfter,
        @NotNull Map<String, String> labels,
        @NotNull List<MasteryTransition> stateHistory,
        double confidence
) {
    public MasteryItem {
        Objects.requireNonNull(masteryId, "masteryId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(domain, "domain must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(agentReleaseId, "agentReleaseId must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(versionScope, "versionScope must not be null");
        Objects.requireNonNull(applicability, "applicability must not be null");
        Objects.requireNonNull(score, "score must not be null");
        Objects.requireNonNull(procedureIds, "procedureIds must not be null");
        Objects.requireNonNull(semanticFactIds, "semanticFactIds must not be null");
        Objects.requireNonNull(negativeKnowledgeIds, "negativeKnowledgeIds must not be null");
        Objects.requireNonNull(evidenceRefs, "evidenceRefs must not be null");
        Objects.requireNonNull(evaluationRefs, "evaluationRefs must not be null");
        Objects.requireNonNull(knownFailureModeIds, "knownFailureModeIds must not be null");
        Objects.requireNonNull(lastVerifiedAt, "lastVerifiedAt must not be null");
        Objects.requireNonNull(staleAfter, "staleAfter must not be null");
        Objects.requireNonNull(labels, "labels must not be null");
        procedureIds = List.copyOf(procedureIds);
        semanticFactIds = List.copyOf(semanticFactIds);
        negativeKnowledgeIds = List.copyOf(negativeKnowledgeIds);
        evidenceRefs = List.copyOf(evidenceRefs);
        evaluationRefs = List.copyOf(evaluationRefs);
        knownFailureModeIds = List.copyOf(knownFailureModeIds);
        labels = Map.copyOf(labels);
    }

    /**
     * Returns true if this mastery item is currently fresh (not stale).
     *
     * @param currentTime current time to check against
     * @return true if fresh
     */
    public boolean isFresh(@NotNull Instant currentTime) {
        return currentTime.isBefore(staleAfter);
    }

    /**
     * Returns true if this mastery item is active for retrieval.
     *
     * @return true if active for retrieval
     */
    public boolean isActiveForRetrieval() {
        return state.isActiveForRetrieval();
    }

    /**
     * Returns true if this mastery item can be used for teaching other agents.
     *
     * @return true if eligible for teaching
     */
    public boolean isTeachingEligible() {
        return state == MasteryState.COMPETENT || state == MasteryState.MASTERED;
    }

    /**
     * Returns true if this mastery item can be used for composition with other skills.
     *
     * @return true if eligible for composition
     */
    public boolean isCompositionEligible() {
        return state == MasteryState.MASTERED;
    }
}
