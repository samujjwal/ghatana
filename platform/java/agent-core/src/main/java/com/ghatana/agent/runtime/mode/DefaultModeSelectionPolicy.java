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
        VersionApplicability applicability = masteryDecision.versionScope().classify(versionContext);

        // Obsolete version overrides mastery state — agent must not act
        if (applicability == VersionApplicability.OBSOLETE) {
            return ModeSelectionResult.blocked(
                    "Version is obsolete for mastery " + masteryDecision.masteryItemId());
        }

        MasteryState state = masteryDecision.state();

        // Terminal mastery states — agent must not act regardless of version
        if (state == MasteryState.OBSOLETE
                || state == MasteryState.RETIRED
                || state == MasteryState.QUARANTINED) {
            return ModeSelectionResult.blocked(
                    "Mastery state is " + state + " for skill " + masteryDecision.skillId());
        }

        // Blocked by registry decision
        if (!masteryDecision.executable()) {
            return ModeSelectionResult.blocked(
                    "Mastery registry blocked execution: " + masteryDecision.reason());
        }

        boolean isHighRisk = taskClassification.riskLevel() == TaskRiskLevel.HIGH
                || taskClassification.riskLevel() == TaskRiskLevel.CRITICAL;

        return switch (state) {
            case MASTERED -> {
                if (isHighRisk || applicability != VersionApplicability.ACTIVE) {
                    yield ModeSelectionResult.supervised(
                            ExecutionStrategy.DETERMINISTIC_EXECUTION,
                            "MASTERED skill with high-risk task or non-active version — supervised");
                }
                yield ModeSelectionResult.autonomous(
                        ExecutionStrategy.DETERMINISTIC_EXECUTION,
                        "MASTERED skill on active version — autonomous execution");
            }
            case COMPETENT -> {
                if (isHighRisk) {
                    yield ModeSelectionResult.humanGated(
                            ExecutionStrategy.BOUNDED_PROBABILISTIC_REASONING,
                            "COMPETENT skill with high-risk task — human-gated");
                }
                yield ModeSelectionResult.supervised(
                        ExecutionStrategy.BOUNDED_PROBABILISTIC_REASONING,
                        "COMPETENT skill — supervised with verification");
            }
            case PRACTICED -> ModeSelectionResult.humanGated(
                    ExecutionStrategy.EXPLORATORY_FAST_LEARNING,
                    "PRACTICED skill — human approval required before acting");
            case OBSERVED -> ModeSelectionResult.humanGated(
                    ExecutionStrategy.VERIFICATION_FIRST,
                    "OBSERVED skill — verification-first with human gate");
            case MAINTENANCE_ONLY -> ModeSelectionResult.humanGated(
                    ExecutionStrategy.MAINTENANCE_ONLY,
                    "MAINTENANCE_ONLY skill — human approval required for maintenance operations");
            case UNKNOWN -> ModeSelectionResult.humanGated(
                    ExecutionStrategy.EXPLORATORY_FAST_LEARNING,
                    "Unknown mastery state — cautious fast-learning with human gate");
            default -> ModeSelectionResult.blocked(
                    "Unhandled mastery state " + state + " — blocking as safety default");
        };
    }
}
