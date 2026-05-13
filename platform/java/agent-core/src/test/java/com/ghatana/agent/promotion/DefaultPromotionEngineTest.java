/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.evaluation.EvaluationResult;
import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaFactory;
import com.ghatana.agent.learning.LearningDeltaRepository;
import com.ghatana.agent.learning.LearningDeltaState;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryScore;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.MasteryTransitionResult;
import com.ghatana.agent.mastery.ApplicabilityScope;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultPromotionEngine}.
 *
 * @doc.type class
 * @doc.purpose Tests for DefaultPromotionEngine promotion flow and mastery transition
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("DefaultPromotionEngine Tests")
@ExtendWith(MockitoExtension.class)
class DefaultPromotionEngineTest extends EventloopTestBase {

    @Mock
    private MasteryRegistry masteryRegistry;

    @Mock
    private LearningDeltaRepository deltaRepository;

    private DefaultPromotionEngine promotionEngine;

    private static LearningDelta buildDelta(String deltaId) {
        // Use proposeProceduralSkill to satisfy DefaultPromotionPolicy checks:
        //   - evidenceRefs.size() >= 3
        //   - rollbackRef not blank
        //   - procedureId not blank
        return LearningDeltaFactory.proposeProceduralSkill(
                "agent-123",
                "release-1.0.0",
                "skill-abc",
                "tenant-1",
                "proc-1",
                "rollback-1.0.0",
                Map.of("action", "do-something"),
                List.of("ev-1", "ev-2", "ev-3"),
                List.of("episode-1"),
                0.4, 0.8,
                "learning-engine"
        );
    }

    private static MasteryItem buildMasteryItem(String masteryId) {
        Instant now = Instant.now();
        return new MasteryItem(
                masteryId,
                "skill-abc",
                "skill-abc",
                "agent-123",
                "release-1.0.0",
                MasteryState.OBSERVED,
                VersionScope.empty(),
                ApplicabilityScope.minimal("tenant-1", "production"),
                MasteryScore.zero(),
                List.of(),
                List.of(),
                List.of(),
                List.of("ev-1"),
                List.of(),
                List.of(),
                List.of(),
                now,
                now.plusSeconds(86400),
                Map.of()
        );
    }

    private static EvaluationResult buildPassingEvaluation(String deltaId) {
        Instant now = Instant.now();
        return new EvaluationResult(
                "result-1",
                "pack-1",
                "artifact-1",
                deltaId,
                now,
                now.plusSeconds(1),
                5,
                5,
                0,
                0,
                100.0,
                List.of(),
                Map.of()
        );
    }

    @BeforeEach
    void setUp() {
        promotionEngine = new DefaultPromotionEngine(masteryRegistry, deltaRepository);
        // Lenient: not all tests need save to be called (only when item is missing)
        lenient().when(masteryRegistry.save(any())).thenAnswer(inv ->
                Promise.of(inv.getArgument(0, MasteryItem.class)));
    }

    @Test
    @DisplayName("Promotion queries mastery registry without null environment (uses MasteryQuery)")
    void promotionQueriesRegistryWithMasteryQuery() {
        LearningDelta delta = buildDelta("delta-1");
        EvaluationResult evaluation = buildPassingEvaluation(delta.deltaId());

        when(masteryRegistry.query(any(MasteryQuery.class)))
                .thenReturn(Promise.of(List.of()));
        when(masteryRegistry.transition(any()))
                .thenReturn(Promise.of(MasteryTransitionResult.success(
                        "mastery-new", MasteryState.UNKNOWN, MasteryState.COMPETENT, "txn-1")));
        when(deltaRepository.updateState(any(), eq(LearningDeltaState.PROMOTED)))
                .thenReturn(Promise.of(delta));

        runPromise(() -> promotionEngine.promote(delta, evaluation));

        ArgumentCaptor<MasteryQuery> queryCaptor = ArgumentCaptor.forClass(MasteryQuery.class);
        verify(masteryRegistry).query(queryCaptor.capture());
        MasteryQuery capturedQuery = queryCaptor.getValue();
        // Query must be a proper MasteryQuery (not null), containing the skill and tenant
        assertThat(capturedQuery).isNotNull();
        assertThat(capturedQuery.skillId()).isEqualTo("skill-abc");
        assertThat(capturedQuery.tenantId()).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("Existing mastery item transitions correctly and delta is marked PROMOTED")
    void existingItemTransitionsCorrectly() {
        LearningDelta delta = buildDelta("delta-2");
        EvaluationResult evaluation = buildPassingEvaluation(delta.deltaId());
        MasteryItem existingItem = buildMasteryItem("existing-mastery-1");

        when(masteryRegistry.query(any(MasteryQuery.class)))
                .thenReturn(Promise.of(List.of(existingItem)));
        when(masteryRegistry.transition(any()))
                .thenReturn(Promise.of(MasteryTransitionResult.success(
                        "existing-mastery-1", MasteryState.OBSERVED, MasteryState.COMPETENT, "txn-2")));
        when(deltaRepository.updateState(any(), eq(LearningDeltaState.PROMOTED)))
                .thenReturn(Promise.of(delta));

        PromotionResult result = runPromise(() -> promotionEngine.promote(delta, evaluation));

        assertThat(result.success()).isTrue();
        // save() must NOT be called when item already exists
        verify(masteryRegistry, never()).save(any());
    }

    @Test
    @DisplayName("Missing mastery item creates initial item then transitions")
    void missingItemCreatesInitialItemAndTransitions() {
        LearningDelta delta = buildDelta("delta-3");
        EvaluationResult evaluation = buildPassingEvaluation(delta.deltaId());

        when(masteryRegistry.query(any(MasteryQuery.class)))
                .thenReturn(Promise.of(List.of()));
        when(masteryRegistry.transition(any()))
                .thenReturn(Promise.of(MasteryTransitionResult.success(
                        "new-mastery-id", MasteryState.UNKNOWN, MasteryState.COMPETENT, "txn-3")));
        when(deltaRepository.updateState(any(), eq(LearningDeltaState.PROMOTED)))
                .thenReturn(Promise.of(delta));

        PromotionResult result = runPromise(() -> promotionEngine.promote(delta, evaluation));

        assertThat(result.success()).isTrue();
        // save() must be called to bootstrap the initial item
        verify(masteryRegistry).save(any());
    }

    @Test
    @DisplayName("Failed mastery transition does not mark delta as PROMOTED")
    void failedTransitionDoesNotMarkDeltaPromoted() {
        LearningDelta delta = buildDelta("delta-4");
        EvaluationResult evaluation = buildPassingEvaluation(delta.deltaId());
        MasteryItem existingItem = buildMasteryItem("mastery-fail");

        when(masteryRegistry.query(any(MasteryQuery.class)))
                .thenReturn(Promise.of(List.of(existingItem)));
        when(masteryRegistry.transition(any()))
                .thenReturn(Promise.of(MasteryTransitionResult.failure(
                        "mastery-fail", MasteryState.OBSERVED, "Transition constraint violated")));

        PromotionResult result = runPromise(() -> promotionEngine.promote(delta, evaluation));

        assertThat(result.success()).isFalse();
        // updateState to PROMOTED must NOT be called when transition fails
        verify(deltaRepository, never()).updateState(any(), eq(LearningDeltaState.PROMOTED));
    }

    @Test
    @DisplayName("Delta state updates to PROMOTED only after mastery transition succeeds")
    void deltaUpdatedToPromotedOnlyAfterSuccessfulTransition() {
        LearningDelta delta = buildDelta("delta-5");
        EvaluationResult evaluation = buildPassingEvaluation(delta.deltaId());
        MasteryItem existingItem = buildMasteryItem("mastery-seq");

        when(masteryRegistry.query(any(MasteryQuery.class)))
                .thenReturn(Promise.of(List.of(existingItem)));
        when(masteryRegistry.transition(any()))
                .thenReturn(Promise.of(MasteryTransitionResult.success(
                        "mastery-seq", MasteryState.OBSERVED, MasteryState.COMPETENT, "txn-5")));
        when(deltaRepository.updateState(eq(delta.deltaId()), eq(LearningDeltaState.PROMOTED)))
                .thenReturn(Promise.of(delta));

        PromotionResult result = runPromise(() -> promotionEngine.promote(delta, evaluation));

        assertThat(result.success()).isTrue();
        verify(deltaRepository).updateState(delta.deltaId(), LearningDeltaState.PROMOTED);
    }
}
