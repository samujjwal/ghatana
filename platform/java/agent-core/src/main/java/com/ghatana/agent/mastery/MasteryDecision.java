/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.runtime.mode.ExecutionMode;

import java.util.List;
import java.util.Objects;

/**
 * Decision result from a mastery query.
 *
 * @doc.type record
 * @doc.purpose Decision result from mastery query
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record MasteryDecision(
        @NotNull String masteryItemId,
        @NotNull String skillId,
        @NotNull ExecutionMode recommendedMode,
        boolean executable,
        boolean requiresHumanApproval,
        boolean requiresVerification,
        @NotNull String reason,
        @NotNull List<String> evidenceRefs,
        @Nullable MasteryState state,
        @Nullable VersionScope versionScope,
        double confidence
) {
    public MasteryDecision {
        Objects.requireNonNull(masteryItemId, "masteryItemId must not be null");
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(recommendedMode, "recommendedMode must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(evidenceRefs, "evidenceRefs must not be null");
        evidenceRefs = List.copyOf(evidenceRefs);
    }

    /**
     * Creates a decision that allows execution.
     *
     * @param masteryItemId mastery item identifier
     * @param skillId skill identifier
     * @param recommendedMode recommended execution mode
     * @param reason decision reason
     * @return decision allowing execution
     */
    @NotNull
    public static MasteryDecision allow(
            @NotNull String masteryItemId,
            @NotNull String skillId,
            @NotNull ExecutionMode recommendedMode,
            @NotNull String reason
    ) {
        return new MasteryDecision(
                masteryItemId,
                skillId,
                recommendedMode,
                true,
                false,
                false,
                reason,
                List.of(),
                null,
                null,
                0.0
        );
    }

    /**
     * Creates a decision that blocks execution.
     *
     * @param masteryItemId mastery item identifier
     * @param skillId skill identifier
     * @param recommendedMode recommended execution mode
     * @param reason decision reason
     * @return decision blocking execution
     */
    @NotNull
    public static MasteryDecision block(
            @NotNull String masteryItemId,
            @NotNull String skillId,
            @NotNull ExecutionMode recommendedMode,
            @NotNull String reason
    ) {
        return new MasteryDecision(
                masteryItemId,
                skillId,
                recommendedMode,
                false,
                false,
                false,
                reason,
                List.of(),
                null,
                null,
                0.0
        );
    }

    /**
     * Creates a decision that requires human approval.
     *
     * @param masteryItemId mastery item identifier
     * @param skillId skill identifier
     * @param recommendedMode recommended execution mode
     * @param reason decision reason
     * @return decision requiring human approval
     */
    @NotNull
    public static MasteryDecision requireApproval(
            @NotNull String masteryItemId,
            @NotNull String skillId,
            @NotNull ExecutionMode recommendedMode,
            @NotNull String reason
    ) {
        return new MasteryDecision(
                masteryItemId,
                skillId,
                recommendedMode,
                false,
                true,
                false,
                reason,
                List.of(),
                null,
                null,
                0.0
        );
    }

    /**
     * Creates a decision that requires verification.
     *
     * @param masteryItemId mastery item identifier
     * @param skillId skill identifier
     * @param recommendedMode recommended execution mode
     * @param reason decision reason
     * @param evidenceRefs evidence references
     * @return decision requiring verification
     */
    @NotNull
    public static MasteryDecision requireVerification(
            @NotNull String masteryItemId,
            @NotNull String skillId,
            @NotNull ExecutionMode recommendedMode,
            @NotNull String reason,
            @NotNull List<String> evidenceRefs
    ) {
        return new MasteryDecision(
                masteryItemId,
                skillId,
                recommendedMode,
                true,
                false,
                true,
                reason,
                evidenceRefs,
                null,
                null,
                0.0
        );
    }
}
