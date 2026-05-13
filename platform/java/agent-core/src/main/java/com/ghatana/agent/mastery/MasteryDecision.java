/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Decision result from a mastery query.
 *
 * <p>Carries the mastery state, confidence score, version scope, version applicability,
 * staleness, terminal state, execution score, executable flag, and evidence refs.
 * Execution strategy and supervision mode are determined by {@code ModeSelectionPolicy}, not here.
 *
 * @doc.type record
 * @doc.purpose Decision result from mastery query
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record MasteryDecision(
        @NotNull String masteryItemId,
        @NotNull String skillId,
        @NotNull MasteryState state,
        @NotNull MasteryScore confidence,
        @NotNull VersionScope versionScope,
        @NotNull VersionApplicability versionApplicability,
        boolean stale,
        boolean terminal,
        double executionScore,
        boolean executable,
        boolean requiresHumanApproval,
        boolean requiresVerification,
        @NotNull String reason,
        @NotNull List<String> evidenceRefs
) {
    public MasteryDecision {
        Objects.requireNonNull(masteryItemId, "masteryItemId must not be null");
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(versionScope, "versionScope must not be null");
        Objects.requireNonNull(versionApplicability, "versionApplicability must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(evidenceRefs, "evidenceRefs must not be null");
        evidenceRefs = List.copyOf(evidenceRefs);
        
        // Validate execution score range
        if (executionScore < 0.0 || executionScore > 1.0) {
            throw new IllegalArgumentException("executionScore must be between 0.0 and 1.0");
        }
    }

    /**
     * Convenience alias for {@link #executable()}.
     *
     * @return true if the agent is permitted to execute this skill
     */
    public boolean allowed() {
        return executable;
    }

    /**
     * Creates a decision that allows execution with no human gates.
     *
     * @param masteryItemId mastery item identifier
     * @param skillId       skill identifier
     * @param state         mastery state
     * @param confidence    mastery confidence score
     * @param versionScope  version scope
     * @param reason        decision reason
     * @return decision allowing execution
     */
    @NotNull
    public static MasteryDecision allow(
            @NotNull String masteryItemId,
            @NotNull String skillId,
            @NotNull MasteryState state,
            @NotNull MasteryScore confidence,
            @NotNull VersionScope versionScope,
            @NotNull String reason
    ) {
        return new MasteryDecision(
                masteryItemId,
                skillId,
                state,
                confidence,
                versionScope,
                VersionApplicability.UNKNOWN,
                false,
                false,
                confidence.executionScore(),
                true,
                false,
                false,
                reason,
                List.of()
        );
    }

    /**
     * Creates a decision that allows execution with version applicability and execution score.
     *
     * @param masteryItemId mastery item identifier
     * @param skillId       skill identifier
     * @param state         mastery state
     * @param confidence    mastery confidence score
     * @param versionScope  version scope
     * @param versionApplicability version applicability
     * @param executionScore execution score
     * @param stale         whether the mastery is stale
     * @param reason        decision reason
     * @return decision allowing execution with full metadata
     */
    @NotNull
    public static MasteryDecision allow(
            @NotNull String masteryItemId,
            @NotNull String skillId,
            @NotNull MasteryState state,
            @NotNull MasteryScore confidence,
            @NotNull VersionScope versionScope,
            @NotNull VersionApplicability versionApplicability,
            double executionScore,
            boolean stale,
            @NotNull String reason
    ) {
        return new MasteryDecision(
                masteryItemId,
                skillId,
                state,
                confidence,
                versionScope,
                versionApplicability,
                stale,
                false,
                executionScore,
                true,
                false,
                false,
                reason,
                List.of()
        );
    }

    /**
     * Creates a decision that blocks execution.
     *
     * @param masteryItemId mastery item identifier
     * @param skillId       skill identifier
     * @param state         mastery state
     * @param confidence    mastery confidence score
     * @param versionScope  version scope
     * @param reason        decision reason
     * @return decision blocking execution
     */
    @NotNull
    public static MasteryDecision block(
            @NotNull String masteryItemId,
            @NotNull String skillId,
            @NotNull MasteryState state,
            @NotNull MasteryScore confidence,
            @NotNull VersionScope versionScope,
            @NotNull String reason
    ) {
        return new MasteryDecision(
                masteryItemId,
                skillId,
                state,
                confidence,
                versionScope,
                VersionApplicability.UNKNOWN,
                false,
                false,
                0.0,
                false,
                false,
                false,
                reason,
                List.of()
        );
    }

    /**
     * Creates a decision that blocks execution with terminal state.
     *
     * @param masteryItemId mastery item identifier
     * @param skillId       skill identifier
     * @param state         mastery state
     * @param confidence    mastery confidence score
     * @param versionScope  version scope
     * @param versionApplicability version applicability
     * @param terminal      whether the state is terminal
     * @param reason        decision reason
     * @return decision blocking execution with terminal flag
     */
    @NotNull
    public static MasteryDecision block(
            @NotNull String masteryItemId,
            @NotNull String skillId,
            @NotNull MasteryState state,
            @NotNull MasteryScore confidence,
            @NotNull VersionScope versionScope,
            @NotNull VersionApplicability versionApplicability,
            boolean terminal,
            @NotNull String reason
    ) {
        return new MasteryDecision(
                masteryItemId,
                skillId,
                state,
                confidence,
                versionScope,
                versionApplicability,
                false,
                terminal,
                0.0,
                false,
                false,
                false,
                reason,
                List.of()
        );
    }

    /**
     * Creates a decision that requires human approval before execution.
     *
     * @param masteryItemId mastery item identifier
     * @param skillId       skill identifier
     * @param state         mastery state
     * @param confidence    mastery confidence score
     * @param versionScope  version scope
     * @param reason        decision reason
     * @return decision requiring human approval
     */
    @NotNull
    public static MasteryDecision requireApproval(
            @NotNull String masteryItemId,
            @NotNull String skillId,
            @NotNull MasteryState state,
            @NotNull MasteryScore confidence,
            @NotNull VersionScope versionScope,
            @NotNull String reason
    ) {
        return new MasteryDecision(
                masteryItemId,
                skillId,
                state,
                confidence,
                versionScope,
                VersionApplicability.UNKNOWN,
                false,
                false,
                confidence.executionScore(),
                true,
                true,
                false,
                reason,
                List.of()
        );
    }

    /**
     * Creates a decision that requires human approval with version applicability.
     *
     * @param masteryItemId mastery item identifier
     * @param skillId       skill identifier
     * @param state         mastery state
     * @param confidence    mastery confidence score
     * @param versionScope  version scope
     * @param versionApplicability version applicability
     * @param executionScore execution score
     * @param stale         whether the mastery is stale
     * @param reason        decision reason
     * @return decision requiring human approval with full metadata
     */
    @NotNull
    public static MasteryDecision requireApproval(
            @NotNull String masteryItemId,
            @NotNull String skillId,
            @NotNull MasteryState state,
            @NotNull MasteryScore confidence,
            @NotNull VersionScope versionScope,
            @NotNull VersionApplicability versionApplicability,
            double executionScore,
            boolean stale,
            @NotNull String reason
    ) {
        return new MasteryDecision(
                masteryItemId,
                skillId,
                state,
                confidence,
                versionScope,
                versionApplicability,
                stale,
                false,
                executionScore,
                true,
                true,
                false,
                reason,
                List.of()
        );
    }

    /**
     * Creates a decision that allows execution but requires verification first.
     *
     * @param masteryItemId mastery item identifier
     * @param skillId       skill identifier
     * @param state         mastery state
     * @param confidence    mastery confidence score
     * @param versionScope  version scope
     * @param reason        decision reason
     * @param evidenceRefs  evidence references supporting the decision
     * @return decision requiring verification
     */
    @NotNull
    public static MasteryDecision requireVerification(
            @NotNull String masteryItemId,
            @NotNull String skillId,
            @NotNull MasteryState state,
            @NotNull MasteryScore confidence,
            @NotNull VersionScope versionScope,
            @NotNull String reason,
            @NotNull List<String> evidenceRefs
    ) {
        return new MasteryDecision(
                masteryItemId,
                skillId,
                state,
                confidence,
                versionScope,
                VersionApplicability.UNKNOWN,
                false,
                false,
                confidence.executionScore(),
                true,
                false,
                true,
                reason,
                evidenceRefs
        );
    }

    /**
     * Creates a decision that requires verification with version applicability.
     *
     * @param masteryItemId mastery item identifier
     * @param skillId       skill identifier
     * @param state         mastery state
     * @param confidence    mastery confidence score
     * @param versionScope  version scope
     * @param versionApplicability version applicability
     * @param executionScore execution score
     * @param stale         whether the mastery is stale
     * @param reason        decision reason
     * @param evidenceRefs  evidence references supporting the decision
     * @return decision requiring verification with full metadata
     */
    @NotNull
    public static MasteryDecision requireVerification(
            @NotNull String masteryItemId,
            @NotNull String skillId,
            @NotNull MasteryState state,
            @NotNull MasteryScore confidence,
            @NotNull VersionScope versionScope,
            @NotNull VersionApplicability versionApplicability,
            double executionScore,
            boolean stale,
            @NotNull String reason,
            @NotNull List<String> evidenceRefs
    ) {
        return new MasteryDecision(
                masteryItemId,
                skillId,
                state,
                confidence,
                versionScope,
                versionApplicability,
                stale,
                false,
                executionScore,
                true,
                false,
                true,
                reason,
                evidenceRefs
        );
    }

    /**
     * Creates a decision for maintenance-only mode.
     *
     * @param masteryItemId mastery item identifier
     * @param skillId       skill identifier
     * @param state         mastery state (should be MAINTENANCE_ONLY)
     * @param confidence    mastery confidence score
     * @param versionScope  version scope
     * @param reason        decision reason
     * @return decision for maintenance-only mode
     */
    @NotNull
    public static MasteryDecision maintenanceOnly(
            @NotNull String masteryItemId,
            @NotNull String skillId,
            @NotNull MasteryState state,
            @NotNull MasteryScore confidence,
            @NotNull VersionScope versionScope,
            @NotNull String reason
    ) {
        return new MasteryDecision(
                masteryItemId,
                skillId,
                state,
                confidence,
                versionScope,
                VersionApplicability.MAINTENANCE,
                false,
                false,
                confidence.executionScore(),
                true,
                false,
                false,
                reason,
                List.of()
        );
    }

    /**
     * Creates a decision for obsolete mastery.
     *
     * @param masteryItemId mastery item identifier
     * @param skillId       skill identifier
     * @param state         mastery state (should be OBSOLETE)
     * @param confidence    mastery confidence score
     * @param versionScope  version scope
     * @param reason        decision reason
     * @return decision for obsolete mastery
     */
    @NotNull
    public static MasteryDecision obsolete(
            @NotNull String masteryItemId,
            @NotNull String skillId,
            @NotNull MasteryState state,
            @NotNull MasteryScore confidence,
            @NotNull VersionScope versionScope,
            @NotNull String reason
    ) {
        return new MasteryDecision(
                masteryItemId,
                skillId,
                state,
                confidence,
                versionScope,
                VersionApplicability.OBSOLETE,
                true,
                true,
                0.0,
                false,
                false,
                false,
                reason,
                List.of()
        );
    }
}
