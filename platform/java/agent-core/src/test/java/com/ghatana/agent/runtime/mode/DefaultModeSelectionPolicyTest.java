/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.mastery.MasteryDecision;
import com.ghatana.agent.mastery.MasteryScore;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultModeSelectionPolicy}.
 *
 * @doc.type class
 * @doc.purpose Tests for DefaultModeSelectionPolicy mastery-to-mode mapping
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("DefaultModeSelectionPolicy Tests")
class DefaultModeSelectionPolicyTest extends EventloopTestBase {

    private DefaultModeSelectionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new DefaultModeSelectionPolicy();
    }

    @Test
    @DisplayName("MASTERED skill with UNKNOWN version scope + LOW risk → DETERMINISTIC_EXECUTION + SUPERVISED (non-ACTIVE version)")
    void masteredSkillUnknownScope_shouldSelectDeterministicSupervised() {
        // VersionScope.empty() classifies all VersionContexts as UNKNOWN (no constraints match).
        // Policy: MASTERED with non-ACTIVE applicability → SUPERVISED (not AUTONOMOUS).
        MasteryDecision decision = MasteryDecision.allow(
                "mastery-1", "skill-1",
                MasteryState.MASTERED, MasteryScore.perfect(), VersionScope.empty(),
                "mastered"
        );
        TaskClassification task = TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR);

        ModeSelectionResult result = runPromise(() ->
                policy.selectMode(decision, task, VersionContext.empty()));

        assertThat(result.strategy()).isEqualTo(ExecutionStrategy.DETERMINISTIC_EXECUTION);
        assertThat(result.supervision()).isEqualTo(SupervisionMode.SUPERVISED);
    }

    @Test
    @DisplayName("COMPETENT skill on active version → BOUNDED_PROBABILISTIC_REASONING + SUPERVISED (requiresVerification)")
    void competentActiveSkill_shouldSelectBoundedProbabilisticSupervised() {
        MasteryDecision decision = MasteryDecision.requireVerification(
                "mastery-2", "skill-2",
                MasteryState.COMPETENT, MasteryScore.correctnessOnly(0.8), VersionScope.empty(),
                "competent", List.of("ev-1")
        );
        TaskClassification task = TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR);

        ModeSelectionResult result = runPromise(() ->
                policy.selectMode(decision, task, VersionContext.empty()));

        assertThat(result.strategy()).isEqualTo(ExecutionStrategy.BOUNDED_PROBABILISTIC_REASONING);
        assertThat(result.supervision()).isEqualTo(SupervisionMode.SUPERVISED);
        assertThat(result.requiresVerification()).isTrue();
        assertThat(result.requiresApproval()).isFalse();
    }

    @Test
    @DisplayName("PRACTICED skill → EXPLORATORY_FAST_LEARNING + HUMAN_GATED (requiresApproval)")
    void practicedSkill_shouldSelectExploratoryHumanGated() {
        MasteryDecision decision = MasteryDecision.requireApproval(
                "mastery-3", "skill-3",
                MasteryState.PRACTICED, MasteryScore.correctnessOnly(0.5), VersionScope.empty(),
                "practiced"
        );
        TaskClassification task = TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.NOVEL);

        ModeSelectionResult result = runPromise(() ->
                policy.selectMode(decision, task, VersionContext.empty()));

        assertThat(result.strategy()).isEqualTo(ExecutionStrategy.EXPLORATORY_FAST_LEARNING);
        assertThat(result.supervision()).isEqualTo(SupervisionMode.HUMAN_GATED);
        assertThat(result.requiresApproval()).isTrue();
    }

    @Test
    @DisplayName("MAINTENANCE_ONLY skill → MAINTENANCE_ONLY strategy + HUMAN_GATED")
    void maintenanceOnlySkill_shouldSelectMaintenanceHumanGated() {
        MasteryDecision decision = MasteryDecision.allow(
                "mastery-4", "skill-4",
                MasteryState.MAINTENANCE_ONLY, MasteryScore.correctnessOnly(0.7), VersionScope.empty(),
                "maintenance-only"
        );
        TaskClassification task = TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR);

        ModeSelectionResult result = runPromise(() ->
                policy.selectMode(decision, task, VersionContext.empty()));

        assertThat(result.strategy()).isEqualTo(ExecutionStrategy.MAINTENANCE_ONLY);
        assertThat(result.supervision()).isEqualTo(SupervisionMode.HUMAN_GATED);
    }

    @Test
    @DisplayName("OBSOLETE mastery state → BLOCKED")
    void obsoleteMasteryState_shouldBlock() {
        MasteryDecision decision = MasteryDecision.block(
                "mastery-5", "skill-5",
                MasteryState.OBSOLETE, MasteryScore.zero(), VersionScope.empty(),
                "obsolete"
        );
        TaskClassification task = TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR);

        ModeSelectionResult result = runPromise(() ->
                policy.selectMode(decision, task, VersionContext.empty()));

        assertThat(result.supervision()).isEqualTo(SupervisionMode.BLOCKED);
    }

    @Test
    @DisplayName("RETIRED mastery state → BLOCKED")
    void retiredMasteryState_shouldBlock() {
        MasteryDecision decision = MasteryDecision.block(
                "mastery-6", "skill-6",
                MasteryState.RETIRED, MasteryScore.zero(), VersionScope.empty(),
                "retired"
        );
        TaskClassification task = TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR);

        ModeSelectionResult result = runPromise(() ->
                policy.selectMode(decision, task, VersionContext.empty()));

        assertThat(result.supervision()).isEqualTo(SupervisionMode.BLOCKED);
    }

    @Test
    @DisplayName("Registry-blocked decision → BLOCKED regardless of mastery state")
    void registryBlockedDecision_shouldBlock() {
        MasteryDecision decision = MasteryDecision.block(
                "mastery-7", "skill-7",
                MasteryState.MASTERED, MasteryScore.perfect(), VersionScope.empty(),
                "quarantined by registry"
        );
        TaskClassification task = TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR);

        ModeSelectionResult result = runPromise(() ->
                policy.selectMode(decision, task, VersionContext.empty()));

        assertThat(result.supervision()).isEqualTo(SupervisionMode.BLOCKED);
    }

    @Test
    @DisplayName("MASTERED skill with HIGH-risk task → SUPERVISED (escalated from AUTONOMOUS)")
    void masteredHighRiskTask_shouldEscalateToSupervised() {
        MasteryDecision decision = MasteryDecision.allow(
                "mastery-8", "skill-8",
                MasteryState.MASTERED, MasteryScore.perfect(), VersionScope.empty(),
                "mastered"
        );
        TaskClassification task = TaskClassification.of(TaskRiskLevel.HIGH, TaskNovelty.FAMILIAR);

        ModeSelectionResult result = runPromise(() ->
                policy.selectMode(decision, task, VersionContext.empty()));

        assertThat(result.strategy()).isEqualTo(ExecutionStrategy.DETERMINISTIC_EXECUTION);
        assertThat(result.supervision()).isEqualTo(SupervisionMode.SUPERVISED);
    }

    @Test
    @DisplayName("COMPETENT skill with HIGH-risk task → HUMAN_GATED (escalated from SUPERVISED)")
    void competentHighRiskTask_shouldEscalateToHumanGated() {
        MasteryDecision decision = MasteryDecision.allow(
                "mastery-9", "skill-9",
                MasteryState.COMPETENT, MasteryScore.correctnessOnly(0.8), VersionScope.empty(),
                "competent"
        );
        TaskClassification task = TaskClassification.of(TaskRiskLevel.HIGH, TaskNovelty.FAMILIAR);

        ModeSelectionResult result = runPromise(() ->
                policy.selectMode(decision, task, VersionContext.empty()));

        assertThat(result.strategy()).isEqualTo(ExecutionStrategy.BOUNDED_PROBABILISTIC_REASONING);
        assertThat(result.supervision()).isEqualTo(SupervisionMode.HUMAN_GATED);
    }

    @Test
    @DisplayName("UNKNOWN mastery state → EXPLORATORY_FAST_LEARNING + HUMAN_GATED")
    void unknownMasteryState_shouldSelectExploratoryHumanGated() {
        MasteryDecision decision = MasteryDecision.allow(
                "mastery-10", "skill-10",
                MasteryState.UNKNOWN, MasteryScore.zero(), VersionScope.empty(),
                "unknown"
        );
        TaskClassification task = TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.NOVEL);

        ModeSelectionResult result = runPromise(() ->
                policy.selectMode(decision, task, VersionContext.empty()));

        assertThat(result.strategy()).isEqualTo(ExecutionStrategy.EXPLORATORY_FAST_LEARNING);
        assertThat(result.supervision()).isEqualTo(SupervisionMode.HUMAN_GATED);
    }
}
