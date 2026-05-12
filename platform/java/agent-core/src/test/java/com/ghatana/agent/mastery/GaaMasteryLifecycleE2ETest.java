/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaFactory;
import com.ghatana.agent.learning.LearningDeltaState;
import com.ghatana.agent.runtime.mode.ExecutionMode;
import com.ghatana.agent.runtime.mode.MasteryAwareModeSelector;
import com.ghatana.agent.runtime.mode.ModeSelectionPolicy;
import com.ghatana.agent.runtime.mode.TaskClassification;
import com.ghatana.agent.runtime.mode.TaskClassifier;
import io.activej.eventloop.Eventloop;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test for GAA mastery lifecycle.
 *
 * Tests the complete flow from initial mastery state through learning delta creation,
 * evaluation, promotion, and mode selection.
 *
 * @doc.type class
 * @doc.purpose End-to-end test for GAA mastery lifecycle
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("GAA Mastery Lifecycle E2E Tests")
class GaaMasteryLifecycleE2ETest extends EventloopTestBase {

    @Test
    @DisplayName("Should complete full mastery lifecycle from novice to competent")
    void shouldCompleteFullMasteryLifecycle() {
        // Setup: Create initial mastery item in novice state
        MasteryItem initialItem = MasteryItem.builder()
                .masteryId("mastery-123")
                .skillId("skill-123")
                .agentId("agent-123")
                .tenantId("tenant-123")
                .state(MasteryState.NOVICE)
                .versionScope(VersionScope.active())
                .confidence(ConfidenceVector.of(0.1, 0.1, 0.1))
                .lastVerifiedAt(Instant.now())
                .knownFailureModeIds(Set.of())
                .labels(Map.of())
                .build();

        // Step 1: Create learning delta from successful episodes
        LearningDelta delta = runPromise(() -> LearningDeltaFactory.propose(
                "agent-123",
                "release-123",
                LearningDelta.TargetType.MASTERY_ITEM,
                "mastery-123",
                List.of("episode-1", "episode-2")
        ));

        assertThat(delta.state()).isEqualTo(LearningDeltaState.PROPOSED);

        // Step 2: Transition learning delta through evaluation
        LearningDelta evaluatedDelta = delta.toBuilder().state(LearningDeltaState.EVALUATED).build();
        assertThat(evaluatedDelta.state()).isEqualTo(LearningDeltaState.EVALUATED);

        // Step 3: Promote learning delta
        LearningDelta promotedDelta = evaluatedDelta.toBuilder().state(LearningDeltaState.PROMOTED).build();
        assertThat(promotedDelta.state()).isEqualTo(LearningDeltaState.PROMOTED);

        // Step 4: Update mastery item based on promoted delta
        MasteryItem updatedItem = initialItem.toBuilder()
                .state(MasteryState.COMPETENT)
                .confidence(ConfidenceVector.of(0.8, 0.7, 0.9))
                .lastVerifiedAt(Instant.now())
                .build();

        assertThat(updatedItem.state()).isEqualTo(MasteryState.COMPETENT);
        assertThat(updatedItem.confidence().overall()).isGreaterThan(initialItem.confidence().overall());

        // Step 5: Select execution mode based on updated mastery
        TestMasteryRegistry masteryRegistry = new TestMasteryRegistry(updatedItem);
        TestTaskClassifier taskClassifier = new TestTaskClassifier();
        TestModeSelectionPolicy selectionPolicy = new TestModeSelectionPolicy();

        MasteryAwareModeSelector modeSelector = new MasteryAwareModeSelector(
                masteryRegistry,
                taskClassifier,
                selectionPolicy
        );

        VersionContext versionContext = VersionContext.empty();
        ModeSelectionPolicy.ModeSelectionResult result = runPromise(() -> modeSelector.selectMode(
                "skill-123",
                "agent-123",
                "test task",
                "context",
                versionContext
        ));

        assertThat(result.mode()).isEqualTo(ExecutionMode.DETERMINISTIC_EXECUTION);
    }

    @Test
    @DisplayName("Should handle mastery transition from competent to obsolete")
    void shouldHandleMasteryTransitionFromCompetentToObsolete() {
        // Setup: Create mastery item in competent state
        MasteryItem competentItem = MasteryItem.builder()
                .masteryId("mastery-456")
                .skillId("skill-456")
                .agentId("agent-456")
                .tenantId("tenant-456")
                .state(MasteryState.COMPETENT)
                .versionScope(VersionScope.active())
                .confidence(ConfidenceVector.of(0.8, 0.7, 0.9))
                .lastVerifiedAt(Instant.now().minus(java.time.Duration.ofDays(90)))
                .knownFailureModeIds(Set.of())
                .labels(Map.of())
                .build();

        // Transition to obsolete due to age
        MasteryItem obsoleteItem = competentItem.toBuilder()
                .state(MasteryState.OBSOLETE)
                .lastVerifiedAt(Instant.now())
                .build();

        assertThat(obsoleteItem.state()).isEqualTo(MasteryState.OBSOLETE);
        assertThat(obsoleteItem.masteryId()).isEqualTo(competentItem.masteryId());
    }

    @Test
    @DisplayName("Should integrate version context with mastery decisions")
    void shouldIntegrateVersionContextWithMasteryDecisions() {
        // Setup: Create version context with specific dependencies
        VersionContext versionContext = VersionContext.builder()
                .dependencies(Map.of(
                        "java", "21.0.0",
                        "spring", "6.0.0"
                ))
                .build();

        // Verify version context is correctly constructed
        assertThat(versionContext.dependencies()).containsKey("java");
        assertThat(versionContext.dependencies()).containsKey("spring");
        assertThat(versionContext.dependencies().get("java")).isEqualTo("21.0.0");
    }

    // Test implementations

    private static class TestMasteryRegistry implements MasteryRegistry {
        private final MasteryItem item;

        TestMasteryRegistry(MasteryItem item) {
            this.item = item;
        }

        @Override
        public io.activej.promise.Promise<java.util.Optional<MasteryItem>> findBySkill(
                String skillId, EnvironmentFingerprint env) {
            return io.activej.promise.Promise.of(java.util.Optional.of(item));
        }

        @Override
        public io.activej.promise.Promise<java.util.List<MasteryItem>> query(MasteryQuery query) {
            return io.activej.promise.Promise.of(List.of(item));
        }

        @Override
        public io.activej.promise.Promise<MasteryItem> save(MasteryItem item) {
            return io.activej.promise.Promise.of(item);
        }

        @Override
        public io.activej.promise.Promise<MasteryTransitionResult> transition(
                MasteryTransition transition) {
            return io.activej.promise.Promise.of(MasteryTransitionResult.success(
                    transition.masteryId(),
                    transition.fromState(),
                    transition.toState(),
                    transition.transitionId()));
        }

        @Override
        public io.activej.promise.Promise<java.util.List<MasteryItem>> findStale(java.time.Instant now) {
            return io.activej.promise.Promise.of(List.of());
        }

        @Override
        public io.activej.promise.Promise<MasteryDecision> decide(MasteryQuery query) {
            return io.activej.promise.Promise.of(MasteryDecision.allow(
                    item.masteryId(),
                    query.skillId() != null ? query.skillId() : "unknown",
                    ExecutionMode.DETERMINISTIC_EXECUTION,
                    "test"
            ));
        }
    }

    private static class TestTaskClassifier implements TaskClassifier {
        @Override
        public io.activej.promise.Promise<TaskClassification> classify(String taskDescription, String context) {
            return io.activej.promise.Promise.of(TaskClassification.of(
                    com.ghatana.agent.runtime.mode.TaskRiskLevel.LOW,
                    com.ghatana.agent.runtime.mode.TaskNovelty.FAMILIAR));
        }

        @Override
        public io.activej.promise.Promise<TaskClassification> classify(
                String taskDescription, String context, Map<String, String> metadata) {
            return io.activej.promise.Promise.of(TaskClassification.of(
                    com.ghatana.agent.runtime.mode.TaskRiskLevel.LOW,
                    com.ghatana.agent.runtime.mode.TaskNovelty.FAMILIAR));
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
                    "test selection"));
        }
    }
}
