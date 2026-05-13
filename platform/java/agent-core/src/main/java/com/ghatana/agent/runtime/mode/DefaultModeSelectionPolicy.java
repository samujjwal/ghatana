/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.mastery.MasteryDecision;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionApplicability;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Default implementation of {@link ModeSelectionPolicy}.
 *
 * <p>Maps mastery state and version applicability to execution strategy and supervision mode:
 * <ul>
 *   <li>MASTERED + ACTIVE → DETERMINISTIC_EXECUTION + AUTONOMOUS</li>
 *   <li>COMPETENT + ACTIVE → BOUNDED_PROBABILISTIC_REASONING + SUPERVISED (requiresVerification)</li>
 *   <li>PRACTICED + ACTIVE → EXPLORATORY_FAST_LEARNING + HUMAN_GATED (requiresApproval)</li>
 *   <li>OBSERVED (any non-obsolete) → VERIFICATION_FIRST + HUMAN_GATED</li>
 *   <li>MAINTENANCE_ONLY → MAINTENANCE_ONLY + HUMAN_GATED</li>
 *   <li>OBSOLETE version or OBSOLETE/RETIRED/QUARANTINED state → BLOCKED</li>
 *   <li>UNKNOWN → EXPLORATORY_FAST_LEARNING + HUMAN_GATED</li>
 * </ul>
 *
 * <p>High-risk and critical-risk tasks escalate the supervision level by one tier.
 *
 * @doc.type class
 * @doc.purpose Default policy mapping mastery state and version to execution strategy and supervision
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public final class DefaultModeSelectionPolicy implements ModeSelectionPolicy {

    /**
     * Creates a new {@code DefaultModeSelectionPolicy}.
     */
    public DefaultModeSelectionPolicy() {
    }

    @Override
    @NotNull
    public Promise<ModeSelectionResult> selectMode(
            @NotNull MasteryDecision masteryDecision,
            @NotNull TaskClassification taskClassification,
            @NotNull VersionContext versionContext
    ) {
        ModeSelectionResult result = computeResult(masteryDecision, taskClassification, versionContext);
        return Promise.of(result);
    }

    @NotNull
    private ModeSelectionResult computeResult(
            @NotNull MasteryDecision masteryDecision,
            @NotNull TaskClassification taskClassification,
            @NotNull VersionContext versionContext
    ) {
        // Use the version applicability from the decision (already computed), but
        // override with a live classification if the versionScope can resolve it.
        VersionApplicability applicability = masteryDecision.versionApplicability();
        if (!masteryDecision.versionScope().active().isEmpty()
                || !masteryDecision.versionScope().maintenance().isEmpty()
                || !masteryDecision.versionScope().obsolete().isEmpty()) {
            VersionApplicability live = masteryDecision.versionScope().classify(versionContext);
            if (live != VersionApplicability.UNKNOWN) {
                applicability = live;
            }
        }

        // Obsolete version overrides mastery state — agent must not act
        if (applicability == VersionApplicability.OBSOLETE) {
            return ModeSelectionResult.blocked(
                    "VERSION_OBSOLETE: Version is obsolete for mastery " + masteryDecision.masteryItemId());
        }

        // Maintenance version requires legacy context
        if (applicability == VersionApplicability.MAINTENANCE) {
            if (!masteryDecision.state().requiresLegacyContext()) {
                return ModeSelectionResult.blocked(
                        "VERSION_MISMATCH: Maintenance version requires legacy context for state " + masteryDecision.state());
            }
        }

        MasteryState state = masteryDecision.state();

        // Terminal mastery states — agent must not act regardless of version
        if (state.isTerminal()) {
            return ModeSelectionResult.blocked(
                    "TERMINAL_STATE: Mastery state is " + state + " for skill " + masteryDecision.skillId());
        }

        // Stale mastery items require refresh
        if (masteryDecision.stale()) {
            return ModeSelectionResult.blocked(
                    "STALE_MASTERY: Mastery item is stale and requires refresh for skill " + masteryDecision.skillId());
        }

        // Blocked by registry decision
        if (!masteryDecision.executable()) {
            return ModeSelectionResult.blocked(
                    "BLOCKED_BY_REGISTRY: " + masteryDecision.reason());
        }

        // Honor explicit requiresHumanApproval flag from decision
        if (masteryDecision.requiresHumanApproval()) {
            return ModeSelectionResult.humanGated(
                    ExecutionStrategy.EXPLORATORY_FAST_LEARNING,
                    "HUMAN_APPROVAL_REQUIRED: " + masteryDecision.reason() + " (state=" + state + ", applicability=" + applicability + ")");
        }

        // Honor explicit requiresVerification flag from decision
        if (masteryDecision.requiresVerification()) {
            return ModeSelectionResult.supervised(
                    ExecutionStrategy.BOUNDED_PROBABILISTIC_REASONING,
                    "VERIFICATION_REQUIRED: " + masteryDecision.reason() + " (state=" + state + ", applicability=" + applicability + ")");
        }

        boolean isHighRisk = taskClassification.riskLevel() == TaskRiskLevel.HIGH
                || taskClassification.riskLevel() == TaskRiskLevel.CRITICAL;

        return switch (state) {
            case MASTERED -> {
                if (isHighRisk || applicability != VersionApplicability.ACTIVE) {
                    yield ModeSelectionResult.supervised(
                            ExecutionStrategy.DETERMINISTIC_EXECUTION,
                            "MASTERED_HIGH_RISK_OR_NON_ACTIVE: " + masteryDecision.reason() + " (risk=" + taskClassification.riskLevel() + ", applicability=" + applicability + ")");
                }
                yield ModeSelectionResult.autonomous(
                        ExecutionStrategy.DETERMINISTIC_EXECUTION,
                        "MASTERED_ACTIVE: " + masteryDecision.reason() + " (applicability=" + applicability + ", score=" + masteryDecision.executionScore() + ")");
            }
            case COMPETENT -> {
                if (isHighRisk) {
                    yield ModeSelectionResult.humanGated(
                            ExecutionStrategy.BOUNDED_PROBABILISTIC_REASONING,
                            "COMPETENT_HIGH_RISK: " + masteryDecision.reason() + " (risk=" + taskClassification.riskLevel() + ")");
                }
                yield ModeSelectionResult.supervised(
                        ExecutionStrategy.BOUNDED_PROBABILISTIC_REASONING,
                        "COMPETENT_SUPERVISED: " + masteryDecision.reason() + " (score=" + masteryDecision.executionScore() + ")");
            }
            case PRACTICED -> ModeSelectionResult.humanGated(
                    ExecutionStrategy.EXPLORATORY_FAST_LEARNING,
                    "PRACTICED_HUMAN_GATED: " + masteryDecision.reason() + " (score=" + masteryDecision.executionScore() + ")");
            case OBSERVED -> ModeSelectionResult.humanGated(
                    ExecutionStrategy.VERIFICATION_FIRST,
                    "OBSERVED_VERIFICATION_FIRST: " + masteryDecision.reason());
            case MAINTENANCE_ONLY -> ModeSelectionResult.humanGated(
                    ExecutionStrategy.MAINTENANCE_ONLY,
                    "MAINTENANCE_ONLY_HUMAN_GATED: " + masteryDecision.reason() + " (applicability=" + applicability + ")");
            case UNKNOWN -> ModeSelectionResult.humanGated(
                    ExecutionStrategy.EXPLORATORY_FAST_LEARNING,
                    "UNKNOWN_CAUTIOUS: " + masteryDecision.reason() + " (applicability=" + applicability + ")");
            default -> ModeSelectionResult.blocked(
                    "UNHANDLED_STATE: Unhandled mastery state " + state + " — blocking as safety default");
        };
    }
}
