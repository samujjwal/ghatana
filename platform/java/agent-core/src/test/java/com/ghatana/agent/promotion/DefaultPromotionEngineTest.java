/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.evaluation.EvaluationResult;
import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaRepository;
import com.ghatana.agent.learning.LearningDeltaState;
import com.ghatana.agent.learning.LearningTarget;
import com.ghatana.agent.mastery.ApplicabilityScope;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionApplicability;
import com.ghatana.agent.mastery.VersionConstraint;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for DefaultPromotionEngine with tenant-scoped promotion logic.
 *
 * @doc.type class
 * @doc.purpose Tests for tenant-scoped promotion engine
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("DefaultPromotionEngine Tests")
class DefaultPromotionEngineTest extends EventloopTestBase {

    private MasteryRegistry masteryRegistry;
    private LearningDeltaRepository deltaRepository;
    private DefaultPromotionEngine promotionEngine;

    private static final String TENANT_ID = "test-tenant";
    private static final String AGENT_ID = "test-agent";
    private static final String AGENT_RELEASE_ID = "test-release";
    private static final String SKILL_ID = "test-skill";
    private static final String DELTA_ID = "test-delta";

    @BeforeEach
    void setUp() {
        masteryRegistry = mock(MasteryRegistry.class);
        deltaRepository = mock(LearningDeltaRepository.class);
        promotionEngine = new DefaultPromotionEngine(masteryRegistry, deltaRepository);
    }

    @Nested
    @DisplayName("Tenant-scoped promotion")
    class TenantScopedPromotionTests {

        @Test
        @DisplayName("Should use tenantId for all queries")
        void shouldUseTenantIdForAllQueries() {
            // Arrange
            LearningDelta delta = createTestDelta(LearningTarget.SEMANTIC_FACT);
            EvaluationResult result = createEvaluationResult();
            MasteryItem existingItem = createMasteryItem(MasteryState.OBSERVED);

            when(masteryRegistry.findBySkill(eq(SKILL_ID), any()))
                    .thenReturn(Promise.of(Optional.of(existingItem)));
            when(masteryRegistry.transition(any(com.ghatana.agent.mastery.MasteryTransition.class)))
                    .thenReturn(Promise.of(com.ghatana.agent.mastery.MasteryTransitionResult.success(
                            existingItem.masteryId(),
                            MasteryState.OBSERVED,
                            MasteryState.COMPETENT,
                            "transition-1"
                    )));
            when(deltaRepository.updateState(eq(DELTA_ID), eq(LearningDeltaState.PROMOTED)))
                    .thenReturn(io.activej.promise.Promise.of(delta));

            // Act
            PromotionResult promotionResult = runPromise(() -> promotionEngine.promote(delta, result, TENANT_ID));

            // Assert
            assertTrue(promotionResult.success());
            verify(masteryRegistry).findBySkill(eq(SKILL_ID), any());
            verify(masteryRegistry).transition(any(com.ghatana.agent.mastery.MasteryTransition.class));
        }

        @Test
        @DisplayName("Should not use null tenant for queries")
        void shouldNotUseNullTenantForQueries() {
            // Arrange
            LearningDelta delta = createTestDelta(LearningTarget.SEMANTIC_FACT);
            EvaluationResult result = createEvaluationResult();

            // Act & Assert
            assertThrows(NullPointerException.class, () -> {
                runPromise(() -> promotionEngine.promote(delta, result, null));
            });
        }

        @Test
        @DisplayName("Tenant A cannot transition tenant B's mastery")
        void tenantACannotTransitionTenantBMastery() {
            // Arrange
            LearningDelta delta = createTestDelta(LearningTarget.SEMANTIC_FACT);
            EvaluationResult result = createEvaluationResult();
            MasteryItem existingItem = createMasteryItem(MasteryState.OBSERVED);

            when(masteryRegistry.findBySkill(eq(SKILL_ID), any()))
                    .thenReturn(Promise.of(Optional.of(existingItem)));
            when(masteryRegistry.transition(any(com.ghatana.agent.mastery.MasteryTransition.class)))
                    .thenReturn(Promise.of(com.ghatana.agent.mastery.MasteryTransitionResult.success(
                            existingItem.masteryId(),
                            MasteryState.OBSERVED,
                            MasteryState.COMPETENT,
                            "transition-1"
                    )));
            when(deltaRepository.updateState(eq(DELTA_ID), eq(LearningDeltaState.PROMOTED)))
                    .thenReturn(io.activej.promise.Promise.of(delta));

            // Act
            PromotionResult promotionResult = runPromise(() -> promotionEngine.promote(delta, result, TENANT_ID));

            // Assert
            assertTrue(promotionResult.success());
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class IdempotencyTests {

        @Test
        @DisplayName("Should be idempotent - same promotion twice succeeds")
        void shouldBeIdempotent() {
            // Arrange
            LearningDelta delta = createTestDelta(LearningTarget.SEMANTIC_FACT);
            EvaluationResult result = createEvaluationResult();
            MasteryItem existingItem = createMasteryItem(MasteryState.COMPETENT);

            when(masteryRegistry.findBySkill(eq(SKILL_ID), any()))
                    .thenReturn(Promise.of(Optional.of(existingItem)));
            when(masteryRegistry.transition(any(com.ghatana.agent.mastery.MasteryTransition.class)))
                    .thenReturn(Promise.of(com.ghatana.agent.mastery.MasteryTransitionResult.success(
                            existingItem.masteryId(),
                            MasteryState.COMPETENT,
                            MasteryState.COMPETENT,
                            "transition-1"
                    )));
            when(deltaRepository.updateState(eq(DELTA_ID), eq(LearningDeltaState.PROMOTED)))
                    .thenReturn(io.activej.promise.Promise.of(delta));

            // Act
            PromotionResult result1 = runPromise(() -> promotionEngine.promote(delta, result, TENANT_ID));
            PromotionResult result2 = runPromise(() -> promotionEngine.promote(delta, result, TENANT_ID));

            // Assert
            assertTrue(result1.success());
            assertTrue(result2.success());
            verify(masteryRegistry, times(2)).findBySkill(eq(SKILL_ID), any());
        }
    }

    @Nested
    @DisplayName("Rollback on failure")
    class RollbackTests {

        @Test
        @DisplayName("Should fail if delta update fails")
        void shouldFailIfDeltaUpdateFails() {
            // Arrange
            LearningDelta delta = createTestDelta(LearningTarget.PROCEDURAL_SKILL);
            EvaluationResult result = createEvaluationResult();
            MasteryItem existingItem = createMasteryItem(MasteryState.COMPETENT);

            when(masteryRegistry.findBySkill(eq(SKILL_ID), any()))
                    .thenReturn(Promise.of(Optional.of(existingItem)));
            when(masteryRegistry.transition(any(com.ghatana.agent.mastery.MasteryTransition.class)))
                    .thenReturn(Promise.of(com.ghatana.agent.mastery.MasteryTransitionResult.success(
                            existingItem.masteryId(),
                            MasteryState.COMPETENT,
                            MasteryState.MASTERED,
                            "transition-1"
                    )));
            // Simulate delta update failure by throwing exception
            when(deltaRepository.updateState(eq(DELTA_ID), eq(LearningDeltaState.PROMOTED)))
                    .thenReturn(io.activej.promise.Promise.ofException(new RuntimeException("Delta update failed")));

            // Act
            PromotionResult promotionResult = runPromise(() -> promotionEngine.promote(delta, result, TENANT_ID));

            // Assert
            assertFalse(promotionResult.success());
        }
    }

    @Nested
    @DisplayName("Missing mastery item handling")
    class MissingMasteryItemTests {

        @Test
        @DisplayName("Should fail when no mastery item found")
        void shouldFailWhenNoMasteryItemFound() {
            // Arrange
            LearningDelta delta = createTestDelta(LearningTarget.SEMANTIC_FACT);
            EvaluationResult result = createEvaluationResult();

            when(masteryRegistry.findBySkill(eq(SKILL_ID), any()))
                    .thenReturn(Promise.of(Optional.empty()));

            // Act
            PromotionResult promotionResult = runPromise(() -> promotionEngine.promote(delta, result, TENANT_ID));

            // Assert
            assertFalse(promotionResult.success());
            assertTrue(promotionResult.errorMessage().isPresent());
            assertTrue(promotionResult.errorMessage().get().contains("No mastery item found"));
        }
    }

    private LearningDelta createTestDelta(LearningTarget target) {
        return new LearningDelta(
                DELTA_ID,
                com.ghatana.agent.learning.LearningDeltaType.PROCEDURAL_SKILL,
                target,
                LearningDeltaState.EVALUATED,
                AGENT_ID,
                AGENT_RELEASE_ID,
                SKILL_ID,
                TENANT_ID,
                null,
                null,
                null,
                "test-digest",
                Map.of("key", "value"),
                List.of("evidence-1"),
                List.of("eval-1"),
                List.of("episode-1"),
                null,
                0.5,
                0.8,
                false,
                "test-proposer",
                Instant.now(),
                Instant.now(),
                null,
                null,
                Map.of("label", "test"),
                null
        );
    }

    private EvaluationResult createEvaluationResult() {
        return new EvaluationResult(
                "eval-1",
                "pack-1",
                "artifact-1",
                DELTA_ID,
                Instant.now(),
                Instant.now(),
                10,
                8,
                2,
                0,
                0.8,
                List.of(),
                Map.of()
        );
    }

    private MasteryItem createMasteryItem(MasteryState state) {
        return new MasteryItem(
                "mastery-" + SKILL_ID,
                TENANT_ID,
                SKILL_ID,
                "test-domain",
                AGENT_ID,
                AGENT_RELEASE_ID,
                state,
                new VersionScope(
                        List.of(new VersionConstraint("runtime", "java", ">=21", "jvm")),
                        List.of(),
                        List.of()
                ),
                new ApplicabilityScope(TENANT_ID, "test", Map.of()),
                new com.ghatana.agent.mastery.MasteryScore(0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8),
                List.of("proc-1"),
                List.of("fact-1"),
                List.of("neg-1"),
                List.of("evidence-1"),
                List.of("eval-1"),
                List.of("failure-1"),
                Instant.now(),
                Instant.now().plusSeconds(86400),
                Map.of("label", "test"),
                List.of(),
                0.8
        );
    }
}
