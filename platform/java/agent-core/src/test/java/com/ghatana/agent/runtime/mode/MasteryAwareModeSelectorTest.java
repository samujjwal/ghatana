/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.mastery.MasteryDecision;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.agent.runtime.mode.DefaultModeSelectionPolicy;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MasteryAwareModeSelector.
 *
 * @doc.type class
 * @doc.purpose Tests for MasteryAwareModeSelector
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("MasteryAwareModeSelector Tests")
class MasteryAwareModeSelectorTest {

    @Test
    @DisplayName("Should select mode based on mastery decision")
    void shouldSelectModeBasedOnMasteryDecision() {
        MasteryRegistry masteryRegistry = new TestMasteryRegistry();
        TaskClassifier taskClassifier = new TestTaskClassifier();
        ModeSelectionPolicy selectionPolicy = new TestModeSelectionPolicy();

        MasteryAwareModeSelector selector = new MasteryAwareModeSelector(
                masteryRegistry,
                taskClassifier,
                selectionPolicy
        );

        VersionContext versionContext = VersionContext.empty();
        Promise<ModeSelectionPolicy.ModeSelectionResult> result = selector.selectMode(
                "skill-123",
                "agent-123",
                "test task",
                "context",
                versionContext
        );

        ModeSelectionPolicy.ModeSelectionResult selection = result.await();
        assertThat(selection.mode()).isEqualTo(ExecutionMode.DETERMINISTIC_EXECUTION);
    }

    @Test
    @DisplayName("Should select mode with custom query")
    void shouldSelectModeWithCustomQuery() {
        MasteryRegistry masteryRegistry = new TestMasteryRegistry();
        TaskClassifier taskClassifier = new TestTaskClassifier();
        ModeSelectionPolicy selectionPolicy = new TestModeSelectionPolicy();

        MasteryAwareModeSelector selector = new MasteryAwareModeSelector(
                masteryRegistry,
                taskClassifier,
                selectionPolicy
        );

        MasteryQuery query = MasteryQuery.bySkill("skill-123");
        TaskClassification classification = TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR);
        VersionContext versionContext = VersionContext.empty();

        Promise<ModeSelectionPolicy.ModeSelectionResult> result = selector.selectMode(
                query,
                classification,
                versionContext
        );

        ModeSelectionPolicy.ModeSelectionResult selection = result.await();
        assertThat(selection.mode()).isEqualTo(ExecutionMode.DETERMINISTIC_EXECUTION);
    }

    @Test
    @DisplayName("Unknown skill should block execution")
    void unknownSkillShouldBlockExecution() {
        MasteryRegistry masteryRegistry = new TestMasteryRegistry(MasteryState.UNKNOWN);
        TaskClassifier taskClassifier = new TestTaskClassifier();
        ModeSelectionPolicy selectionPolicy = new DefaultModeSelectionPolicy();

        MasteryAwareModeSelector selector = new MasteryAwareModeSelector(
                masteryRegistry,
                taskClassifier,
                selectionPolicy
        );

        MasteryQuery query = MasteryQuery.bySkill("unknown-skill").withTenantId("tenant-1");
        TaskClassification classification = TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR);
        VersionContext versionContext = VersionContext.empty();

        Promise<ModeSelectionPolicy.ModeSelectionResult> result = selector.selectMode(
                query,
                classification,
                versionContext
        );

        ModeSelectionPolicy.ModeSelectionResult selection = result.await();
        assertThat(selection.mode()).isEqualTo(ExecutionMode.BLOCKED);
        assertThat(selection.reasoning()).contains("unknown");
    }

    @Test
    @DisplayName("OBSERVED state should require verification")
    void observedStateShouldRequireVerification() {
        MasteryRegistry masteryRegistry = new TestMasteryRegistry(MasteryState.OBSERVED);
        TaskClassifier taskClassifier = new TestTaskClassifier();
        ModeSelectionPolicy selectionPolicy = new DefaultModeSelectionPolicy();

        MasteryAwareModeSelector selector = new MasteryAwareModeSelector(
                masteryRegistry,
                taskClassifier,
                selectionPolicy
        );

        MasteryQuery query = MasteryQuery.bySkill("skill-123").withTenantId("tenant-1");
        TaskClassification classification = TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR);
        VersionContext versionContext = VersionContext.empty();

        Promise<ModeSelectionPolicy.ModeSelectionResult> result = selector.selectMode(
                query,
                classification,
                versionContext
        );

        ModeSelectionPolicy.ModeSelectionResult selection = result.await();
        assertThat(selection.requiresVerification()).isTrue();
        assertThat(selection.mode()).isEqualTo(ExecutionMode.SUPERVISED);
    }

    @Test
    @DisplayName("PRACTICED state should require approval")
    void practicedStateShouldRequireApproval() {
        MasteryRegistry masteryRegistry = new TestMasteryRegistry(MasteryState.PRACTICED);
        TaskClassifier taskClassifier = new TestTaskClassifier();
        ModeSelectionPolicy selectionPolicy = new DefaultModeSelectionPolicy();

        MasteryAwareModeSelector selector = new MasteryAwareModeSelector(
                masteryRegistry,
                taskClassifier,
                selectionPolicy
        );

        MasteryQuery query = MasteryQuery.bySkill("skill-123").withTenantId("tenant-1");
        TaskClassification classification = TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR);
        VersionContext versionContext = VersionContext.empty();

        Promise<ModeSelectionPolicy.ModeSelectionResult> result = selector.selectMode(
                query,
                classification,
                versionContext
        );

        ModeSelectionPolicy.ModeSelectionResult selection = result.await();
        assertThat(selection.requiresApproval()).isTrue();
        assertThat(selection.mode()).isEqualTo(ExecutionMode.SUPERVISED);
    }

    @Test
    @DisplayName("COMPETENT state should execute with verification")
    void competentStateShouldExecuteWithVerification() {
        MasteryRegistry masteryRegistry = new TestMasteryRegistry(MasteryState.COMPETENT);
        TaskClassifier taskClassifier = new TestTaskClassifier();
        ModeSelectionPolicy selectionPolicy = new DefaultModeSelectionPolicy();

        MasteryAwareModeSelector selector = new MasteryAwareModeSelector(
                masteryRegistry,
                taskClassifier,
                selectionPolicy
        );

        MasteryQuery query = MasteryQuery.bySkill("skill-123").withTenantId("tenant-1");
        TaskClassification classification = TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR);
        VersionContext versionContext = VersionContext.empty();

        Promise<ModeSelectionPolicy.ModeSelectionResult> result = selector.selectMode(
                query,
                classification,
                versionContext
        );

        ModeSelectionPolicy.ModeSelectionResult selection = result.await();
        assertThat(selection.mode()).isEqualTo(ExecutionMode.AUTONOMOUS);
        assertThat(selection.requiresVerification()).isTrue();
    }

    @Test
    @DisplayName("MASTERED state should execute deterministically")
    void masteredStateShouldExecuteDeterministically() {
        MasteryRegistry masteryRegistry = new TestMasteryRegistry(MasteryState.MASTERED);
        TaskClassifier taskClassifier = new TestTaskClassifier();
        ModeSelectionPolicy selectionPolicy = new DefaultModeSelectionPolicy();

        MasteryAwareModeSelector selector = new MasteryAwareModeSelector(
                masteryRegistry,
                taskClassifier,
                selectionPolicy
        );

        MasteryQuery query = MasteryQuery.bySkill("skill-123").withTenantId("tenant-1");
        TaskClassification classification = TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR);
        VersionContext versionContext = VersionContext.empty();

        Promise<ModeSelectionPolicy.ModeSelectionResult> result = selector.selectMode(
                query,
                classification,
                versionContext
        );

        ModeSelectionPolicy.ModeSelectionResult selection = result.await();
        assertThat(selection.mode()).isEqualTo(ExecutionMode.AUTONOMOUS);
        assertThat(selection.requiresApproval()).isFalse();
        assertThat(selection.requiresVerification()).isFalse();
    }

    @Test
    @DisplayName("MAINTENANCE_ONLY should be for legacy only")
    void maintenanceOnlyShouldBeForLegacyOnly() {
        MasteryRegistry masteryRegistry = new TestMasteryRegistry(MasteryState.MAINTENANCE_ONLY);
        TaskClassifier taskClassifier = new TestTaskClassifier();
        ModeSelectionPolicy selectionPolicy = new DefaultModeSelectionPolicy();

        MasteryAwareModeSelector selector = new MasteryAwareModeSelector(
                masteryRegistry,
                taskClassifier,
                selectionPolicy
        );

        MasteryQuery query = MasteryQuery.bySkill("skill-123").withTenantId("tenant-1");
        TaskClassification classification = TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR);
        VersionContext versionContext = VersionContext.empty();

        Promise<ModeSelectionPolicy.ModeSelectionResult> result = selector.selectMode(
                query,
                classification,
                versionContext
        );

        ModeSelectionPolicy.ModeSelectionResult selection = result.await();
        // Maintenance-only should require approval
        assertThat(selection.requiresApproval()).isTrue();
        assertThat(selection.mode()).isEqualTo(ExecutionMode.SUPERVISED);
    }

    @Test
    @DisplayName("OBSOLETE state should block execution")
    void obsoleteStateShouldBlockExecution() {
        MasteryRegistry masteryRegistry = new TestMasteryRegistry(MasteryState.OBSOLETE);
        TaskClassifier taskClassifier = new TestTaskClassifier();
        ModeSelectionPolicy selectionPolicy = new DefaultModeSelectionPolicy();

        MasteryAwareModeSelector selector = new MasteryAwareModeSelector(
                masteryRegistry,
                taskClassifier,
                selectionPolicy
        );

        MasteryQuery query = MasteryQuery.bySkill("skill-123").withTenantId("tenant-1");
        TaskClassification classification = TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR);
        VersionContext versionContext = VersionContext.empty();

        Promise<ModeSelectionPolicy.ModeSelectionResult> result = selector.selectMode(
                query,
                classification,
                versionContext
        );

        ModeSelectionPolicy.ModeSelectionResult selection = result.await();
        assertThat(selection.mode()).isEqualTo(ExecutionMode.BLOCKED);
        assertThat(selection.reasoning()).contains("obsolete");
    }

    @Test
    @DisplayName("QUARANTINED state should block execution")
    void quarantinedStateShouldBlockExecution() {
        MasteryRegistry masteryRegistry = new TestMasteryRegistry(MasteryState.QUARANTINED);
        TaskClassifier taskClassifier = new TestTaskClassifier();
        ModeSelectionPolicy selectionPolicy = new DefaultModeSelectionPolicy();

        MasteryAwareModeSelector selector = new MasteryAwareModeSelector(
                masteryRegistry,
                taskClassifier,
                selectionPolicy
        );

        MasteryQuery query = MasteryQuery.bySkill("skill-123").withTenantId("tenant-1");
        TaskClassification classification = TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR);
        VersionContext versionContext = VersionContext.empty();

        Promise<ModeSelectionPolicy.ModeSelectionResult> result = selector.selectMode(
                query,
                classification,
                versionContext
        );

        ModeSelectionPolicy.ModeSelectionResult selection = result.await();
        assertThat(selection.mode()).isEqualTo(ExecutionMode.BLOCKED);
        assertThat(selection.reasoning()).contains("quarantined");
    }

    // Test implementations

    private static class TestMasteryRegistry implements MasteryRegistry {
        private final MasteryState state;

        TestMasteryRegistry() {
            this(MasteryState.MASTERED);
        }

        TestMasteryRegistry(MasteryState state) {
            this.state = state;
        }

        @Override
        public io.activej.promise.Promise<java.util.Optional<com.ghatana.agent.mastery.MasteryItem>> findBySkill(
                String skillId, com.ghatana.agent.environment.EnvironmentFingerprint env) {
            MasteryDecision decision = MasteryDecision.allow("mastery-123", skillId, ExecutionMode.DETERMINISTIC_EXECUTION, "test");
            return io.activej.promise.Promise.of(java.util.Optional.empty());
        }

        @Override
        public io.activej.promise.Promise<java.util.List<com.ghatana.agent.mastery.MasteryItem>> query(MasteryQuery query) {
            return io.activej.promise.Promise.of(java.util.List.of());
        }

        @Override
        public io.activej.promise.Promise<com.ghatana.agent.mastery.MasteryItem> save(com.ghatana.agent.mastery.MasteryItem item) {
            return io.activej.promise.Promise.of(item);
        }

        @Override
        public io.activej.promise.Promise<com.ghatana.agent.mastery.MasteryTransitionResult> transition(
                com.ghatana.agent.mastery.MasteryTransition transition) {
            return io.activej.promise.Promise.of(com.ghatana.agent.mastery.MasteryTransitionResult.success(
                    transition.masteryId(), com.ghatana.agent.mastery.MasteryState.UNKNOWN,
                    com.ghatana.agent.mastery.MasteryState.COMPETENT, transition.transitionId()));
        }

        @Override
        public io.activej.promise.Promise<java.util.List<com.ghatana.agent.mastery.MasteryItem>> findStale(java.time.Instant now) {
            return io.activej.promise.Promise.of(java.util.List.of());
        }

        @Override
        public io.activej.promise.Promise<MasteryDecision> decide(MasteryQuery query) {
            return io.activej.promise.Promise.of(MasteryDecision.of(
                    "mastery-123",
                    query.skillId() != null ? query.skillId() : "unknown",
                    state,
                    VersionScope.empty(),
                    0.8,
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of(),
                    java.time.Instant.now(),
                    null,
                    java.util.Map.of()
            ));
        }
    }

    private static class TestTaskClassifier implements TaskClassifier {
        @Override
        public io.activej.promise.Promise<TaskClassification> classify(String taskDescription, String context) {
            return io.activej.promise.Promise.of(TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR));
        }

        @Override
        public io.activej.promise.Promise<TaskClassification> classify(
                String taskDescription, String context, java.util.Map<String, String> metadata) {
            return io.activej.promise.Promise.of(TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR));
        }
    }
}
