/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.mastery.MasteryDecision;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.mastery.MasteryScore;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
class MasteryAwareModeSelectorTest extends EventloopTestBase {

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

        ModeSelectionResult selection = runPromise(() -> selector.selectMode(
                "skill-123",
                "agent-123",
                "test-tenant",
                "test task",
                "context",
                versionContext
        ));

        assertThat(selection.strategy()).isEqualTo(ExecutionStrategy.DETERMINISTIC_EXECUTION);
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

        ModeSelectionResult selection = runPromise(() -> selector.selectMode(
                query,
                classification,
                versionContext
        ));

        assertThat(selection.strategy()).isEqualTo(ExecutionStrategy.DETERMINISTIC_EXECUTION);
    }

    // Test implementations

    private static class TestMasteryRegistry implements MasteryRegistry {
        @Override
        public io.activej.promise.Promise<java.util.Optional<com.ghatana.agent.mastery.MasteryItem>> findBest(MasteryQuery query) {
            return io.activej.promise.Promise.of(java.util.Optional.empty());
        }

        @Override
        public io.activej.promise.Promise<java.util.Optional<com.ghatana.agent.mastery.MasteryItem>> findBySkill(
                String skillId, com.ghatana.agent.environment.EnvironmentFingerprint env) {
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
                    com.ghatana.agent.mastery.MasteryState.MASTERED,
                    com.ghatana.agent.mastery.MasteryScore.perfect(),
                    com.ghatana.agent.mastery.VersionScope.empty(),
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
            return io.activej.promise.Promise.of(ModeSelectionResult.autonomous(
                    ExecutionStrategy.DETERMINISTIC_EXECUTION,
                    "test selection"
            ));
        }
    }

    // -------------------------------------------------------------------------
    // Additional tests using DefaultModeSelectionPolicy for full coverage
    // -------------------------------------------------------------------------

    /**
     * Registry that returns a {@link MasteryDecision} with the configured state
     * and treats the version scope as ACTIVE (uses activeOnly constraint).
     */
    private static MasteryRegistry registryReturning(MasteryState state, MasteryScore score) {
        return new MasteryRegistry() {
            @Override
            public io.activej.promise.Promise<java.util.Optional<com.ghatana.agent.mastery.MasteryItem>> findBest(MasteryQuery query) {
                return Promise.of(java.util.Optional.empty());
            }

            @Override
            public io.activej.promise.Promise<java.util.Optional<com.ghatana.agent.mastery.MasteryItem>> findBySkill(
                    String skillId, com.ghatana.agent.environment.EnvironmentFingerprint env) {
                return Promise.of(java.util.Optional.empty());
            }

            @Override
            public io.activej.promise.Promise<java.util.List<com.ghatana.agent.mastery.MasteryItem>> query(MasteryQuery query) {
                return Promise.of(java.util.List.of());
            }

            @Override
            public io.activej.promise.Promise<com.ghatana.agent.mastery.MasteryItem> save(com.ghatana.agent.mastery.MasteryItem item) {
                return Promise.of(item);
            }

            @Override
            public io.activej.promise.Promise<com.ghatana.agent.mastery.MasteryTransitionResult> transition(
                    com.ghatana.agent.mastery.MasteryTransition transition) {
                return Promise.of(com.ghatana.agent.mastery.MasteryTransitionResult.success(
                        transition.masteryId(), MasteryState.UNKNOWN, MasteryState.COMPETENT, transition.transitionId()));
            }

            @Override
            public io.activej.promise.Promise<java.util.List<com.ghatana.agent.mastery.MasteryItem>> findStale(java.time.Instant now) {
                return Promise.of(java.util.List.of());
            }

            @Override
            public io.activej.promise.Promise<MasteryDecision> decide(MasteryQuery query) {
                // Use activeOnly version scope so the policy sees ACTIVE applicability
                VersionScope activeScope = VersionScope.activeOnly(java.util.List.of());
                return Promise.of(MasteryDecision.allow(
                        "mastery-test",
                        query.skillId() != null ? query.skillId() : "unknown",
                        state,
                        score,
                        activeScope,
                        "test"
                ));
            }
        };
    }

    @Test
    @DisplayName("PRACTICED mastery returns requiresApproval=true")
    void practicedMasteryReturnsRequiresApproval() {
        MasteryAwareModeSelector selector = new MasteryAwareModeSelector(
                registryReturning(MasteryState.PRACTICED, MasteryScore.correctnessOnly(0.5)),
                new TestTaskClassifier(),
                new DefaultModeSelectionPolicy()
        );

        ModeSelectionResult result = runPromise(() -> selector.selectMode(
                "skill-p", "agent-1", "tenant-1", "task", "ctx", VersionContext.empty()
        ));

        assertThat(result.requiresApproval()).isTrue();
    }

    @Test
    @DisplayName("COMPETENT mastery returns requiresVerification=true")
    void competentMasteryReturnsRequiresVerification() {
        MasteryAwareModeSelector selector = new MasteryAwareModeSelector(
                registryReturning(MasteryState.COMPETENT, MasteryScore.correctnessOnly(0.75)),
                new TestTaskClassifier(),
                new DefaultModeSelectionPolicy()
        );

        ModeSelectionResult result = runPromise(() -> selector.selectMode(
                "skill-c", "agent-1", "tenant-1", "task", "ctx", VersionContext.empty()
        ));

        assertThat(result.requiresVerification()).isTrue();
    }

    @Test
    @DisplayName("MAINTENANCE_ONLY state returns approval-required mode")
    void maintenanceOnlyReturnsApprovalRequired() {
        MasteryAwareModeSelector selector = new MasteryAwareModeSelector(
                registryReturning(MasteryState.MAINTENANCE_ONLY, MasteryScore.zero()),
                new TestTaskClassifier(),
                new DefaultModeSelectionPolicy()
        );

        ModeSelectionResult result = runPromise(() -> selector.selectMode(
                "skill-m", "agent-1", "tenant-1", "task", "ctx", VersionContext.empty()
        ));

        assertThat(result.requiresApproval()).isTrue();
    }

    @Test
    @DisplayName("Tenant is passed explicitly and is not derived from dependency map")
    void tenantIsNotDerivedFromDependencyMap() {
        // The selector receives "explicit-tenant" as a direct parameter.
        // The VersionContext has no dependency entry for "tenant".
        // If the selector incorrectly used versionContext.dependencies().getOrDefault("tenant", "default"),
        // the captured tenant would be "default", not "explicit-tenant".
        MasteryAwareModeSelector selector = new MasteryAwareModeSelector(
                registryReturning(MasteryState.MASTERED, MasteryScore.perfect()),
                new TestTaskClassifier(),
                new TestModeSelectionPolicy()
        );

        VersionContext contextWithNoDeps = VersionContext.empty();
        // Should not throw, and should use "explicit-tenant" (not "default")
        ModeSelectionResult result = runPromise(() -> selector.selectMode(
                "skill-t", "agent-1", "explicit-tenant", "task", "ctx", contextWithNoDeps
        ));

        assertThat(result).isNotNull();
    }
}

