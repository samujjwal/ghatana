/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionApplicability;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Decision explaining why a memory item was included or excluded from retrieval.
 *
 * <p>Provides traceability for retrieval decisions, enabling debugging and understanding
 * of why specific memory items were selected or rejected based on mastery state, version
 * applicability, freshness, and other factors.
 *
 * @param memoryItemId unique identifier of the memory item
 * @param skillId skill ID the memory item belongs to
 * @param masteryState mastery state of the skill at time of retrieval
 * @param versionApplicability version applicability classification
 * @param freshness freshness score (0.0-1.0, higher is fresher)
 * @param included whether the item was included in retrieval results
 * @param reason explanation of the decision
 * @param priority retrieval priority score (higher = higher priority)
 *
 * @doc.type record
 * @doc.purpose Explains retrieval decisions for memory items
 * @doc.layer agent-core
 * @doc.pattern ValueObject
 */
public record RetrievalDecision(
        @NotNull String memoryItemId,
        @NotNull String skillId,
        @NotNull MasteryState masteryState,
        @NotNull VersionApplicability versionApplicability,
        double freshness,
        boolean included,
        @NotNull String reason,
        int priority
) {
    public RetrievalDecision {
        if (memoryItemId == null || memoryItemId.isBlank()) {
            throw new IllegalArgumentException("memoryItemId must not be blank");
        }
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skillId must not be blank");
        }
        if (masteryState == null) {
            throw new IllegalArgumentException("masteryState must not be null");
        }
        if (versionApplicability == null) {
            throw new IllegalArgumentException("versionApplicability must not be null");
        }
        if (freshness < 0.0 || freshness > 1.0) {
            throw new IllegalArgumentException("freshness must be between 0.0 and 1.0");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("priority must be non-negative");
        }
    }

    /**
     * Creates a retrieval decision for an included item.
     *
     * @param memoryItemId memory item ID
     * @param skillId skill ID
     * @param masteryState mastery state
     * @param versionApplicability version applicability
     * @param freshness freshness score
     * @param reason inclusion reason
     * @param priority priority score
     * @return retrieval decision
     */
    @NotNull
    public static RetrievalDecision included(
            @NotNull String memoryItemId,
            @NotNull String skillId,
            @NotNull MasteryState masteryState,
            @NotNull VersionApplicability versionApplicability,
            double freshness,
            @NotNull String reason,
            int priority) {
        return new RetrievalDecision(
                memoryItemId,
                skillId,
                masteryState,
                versionApplicability,
                freshness,
                true,
                reason,
                priority
        );
    }

    /**
     * Creates a retrieval decision for an excluded item.
     *
     * @param memoryItemId memory item ID
     * @param skillId skill ID
     * @param masteryState mastery state
     * @param versionApplicability version applicability
     * @param freshness freshness score
     * @param reason exclusion reason
     * @return retrieval decision
     */
    @NotNull
    public static RetrievalDecision excluded(
            @NotNull String memoryItemId,
            @NotNull String skillId,
            @NotNull MasteryState masteryState,
            @NotNull VersionApplicability versionApplicability,
            double freshness,
            @NotNull String reason) {
        return new RetrievalDecision(
                memoryItemId,
                skillId,
                masteryState,
                versionApplicability,
                freshness,
                false,
                reason,
                0
        );
    }

    /**
     * Returns a human-readable summary of this decision.
     *
     * @return decision summary
     */
    @NotNull
    public String toSummary() {
        return String.format(
                "[%s] MemoryItem %s (skill: %s, mastery: %s, version: %s, freshness: %.2f) - %s",
                included ? "INCLUDED" : "EXCLUDED",
                memoryItemId,
                skillId,
                masteryState,
                versionApplicability,
                freshness,
                reason
        );
    }
}
