/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval.mastery;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.mastery.MasteryState;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Query parameters for mastery-aware memory retrieval.
 *
 * <p>Extends basic retrieval with version compatibility, lifecycle state,
 * negative knowledge, and freshness constraints.
 *
 * @doc.type record
 * @doc.purpose Query parameters for mastery-aware memory retrieval
 * @doc.layer agent-runtime
 * @doc.pattern Record
 */
public record MasteryAwareMemoryQuery(
        @NotNull String task,
        @NotNull String skillId,
        @NotNull EnvironmentFingerprint environment,
        @NotNull Set<MasteryState> allowedMasteryStates,
        boolean includeNegativeKnowledge,
        boolean excludeObsolete,
        boolean requireFreshness,
        int maxAgeDays,
        int k,
        double minConfidence
) {
    public MasteryAwareMemoryQuery {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(environment, "environment must not be null");
        Objects.requireNonNull(allowedMasteryStates, "allowedMasteryStates must not be null");
        allowedMasteryStates = Set.copyOf(allowedMasteryStates);
    }

    /**
     * Creates a query for active mastery items only.
     *
     * @param task task description
     * @param skillId skill identifier
     * @param environment environment fingerprint
     * @return mastery-aware memory query
     */
    @NotNull
    public static MasteryAwareMemoryQuery forActiveMastery(
            @NotNull String task,
            @NotNull String skillId,
            @NotNull EnvironmentFingerprint environment
    ) {
        return new MasteryAwareMemoryQuery(
                task,
                skillId,
                environment,
                Set.of(MasteryState.OBSERVED, MasteryState.PRACTICED, MasteryState.COMPETENT, MasteryState.MASTERED),
                true,
                true,
                true,
                30,
                10,
                0.5
        );
    }

    /**
     * Creates a query that includes maintenance-only items.
     *
     * @param task task description
     * @param skillId skill identifier
     * @param environment environment fingerprint
     * @return mastery-aware memory query
     */
    @NotNull
    public static MasteryAwareMemoryQuery includingMaintenance(
            @NotNull String task,
            @NotNull String skillId,
            @NotNull EnvironmentFingerprint environment
    ) {
        return new MasteryAwareMemoryQuery(
                task,
                skillId,
                environment,
                Set.of(MasteryState.OBSERVED, MasteryState.PRACTICED, MasteryState.COMPETENT, MasteryState.MASTERED, MasteryState.MAINTENANCE_ONLY),
                true,
                true,
                true,
                30,
                10,
                0.5
        );
    }
}
