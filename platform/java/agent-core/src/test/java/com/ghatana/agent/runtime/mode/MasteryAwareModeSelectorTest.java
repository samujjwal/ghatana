/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.mastery.MasteryDecision;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryQuery;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

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

    // Test implementations

    private static class TestMasteryRegistry implements MasteryRegistry {
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
            return io.activej.promise.Promise.of(MasteryDecision.allow(
                    "mastery-123",
                    query.skillId() != null ? query.skillId() : "unknown",
                    ExecutionMode.DETERMINISTIC_EXECUTION,
                    "test"
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

    private static class TestModeSelectionPolicy implements ModeSelectionPolicy {
        @Override
        public io.activej.promise.Promise<ModeSelectionResult> selectMode(
                MasteryDecision masteryDecision,
                TaskClassification taskClassification,
                VersionContext versionContext) {
            return io.activej.promise.Promise.of(ModeSelectionResult.of(
                    masteryDecision.executionMode(),
                    "test selection"
            ));
        }
    }
}
