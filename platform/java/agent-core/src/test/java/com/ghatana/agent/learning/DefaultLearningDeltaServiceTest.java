/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultLearningDeltaService} covering contract guard, evaluation pipeline,
 * and state transitions.
 *
 * @doc.type class
 * @doc.purpose Tests for DefaultLearningDeltaService propose/evaluate lifecycle
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("DefaultLearningDeltaService Tests")
@ExtendWith(MockitoExtension.class)
class DefaultLearningDeltaServiceTest extends EventloopTestBase {

    @Mock
    private LearningDeltaRepository deltaRepository;

    @Mock
    private LearningDeltaEvaluator evaluator;

    private DefaultLearningDeltaService service;

    // Builds a valid delta. State is PROPOSED; target defaults to SEMANTIC_FACT.
    private static LearningDelta buildDelta(String deltaId, LearningTarget target, LearningDeltaState state) {
        return new LearningDelta(
                deltaId,
                LearningDeltaType.SEMANTIC_FACT,
                target,
                state,
                "agent-1", "release-1", "skill-1", "tenant-1",
                "proc-1", "fact-1", null,
                "sha256-test",
                Map.<String, Object>of("key", "value"),
                List.<String>of("ev-1", "ev-2"),
                List.<String>of(),
                List.<String>of(),
                null,
                0.5, 0.8,
                false,
                "test-engine",
                Instant.now(),
                null, null, null,
                Map.<String, String>of(),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    // L3 contract permitting SEMANTIC_FACT — covers most test scenarios.
    private static LearningContract permissiveContract() {
        return LearningContract.forNormalAgent(
                LearningLevel.L3,
                Set.of(
                        LearningTarget.SEMANTIC_FACT,
                        LearningTarget.PROCEDURAL_SKILL,
                        LearningTarget.NEGATIVE_KNOWLEDGE,
                        LearningTarget.EPISODIC_MEMORY
                ),
                true, true);
    }

    // L1 contract permitting only EPISODIC_MEMORY — will reject SEMANTIC_FACT.
    private static LearningContract restrictiveContract() {
        return LearningContract.forNormalAgent(
                LearningLevel.L1,
                Set.of(LearningTarget.EPISODIC_MEMORY),
                false, false);
    }

    // L3 contract that requires provenance.
    private static LearningContract provenanceRequiredContract() {
        // L2+ requires provenance: must pass provenanceRequired=true
        return LearningContract.forNormalAgent(
                LearningLevel.L3,
                Set.of(LearningTarget.SEMANTIC_FACT),
            true, true);
    }

    private static LearningDeltaEvaluator.EvaluationResult approvedResult(String deltaId) {
        return new LearningDeltaEvaluator.EvaluationResult(
                deltaId,
                true,
                0.9,
                LearningDeltaEvaluator.ReasonCode.APPROVED,
                "Evaluation passed",
                "Promote delta",
                LearningDeltaEvaluator.SafetyGrade.SAFE,
                List.of()
        );
    }

    private static LearningDeltaEvaluator.EvaluationResult pendingHumanReviewResult(String deltaId) {
        return new LearningDeltaEvaluator.EvaluationResult(
                deltaId,
                false,
                0.6,
                LearningDeltaEvaluator.ReasonCode.PENDING_HUMAN_REVIEW,
                "Low confidence, human review required",
                "Request human review",
                LearningDeltaEvaluator.SafetyGrade.SAFE,
                List.of()
        );
    }

    private static LearningDeltaEvaluator.EvaluationResult rejectedResult(String deltaId) {
        return new LearningDeltaEvaluator.EvaluationResult(
                deltaId,
                false,
                0.2,
                LearningDeltaEvaluator.ReasonCode.INSUFFICIENT_EVIDENCE,
                "Insufficient evidence",
                "Add more evidence",
                LearningDeltaEvaluator.SafetyGrade.SAFE,
                List.of()
        );
    }

    @BeforeEach
    void setUp() {
        service = new DefaultLearningDeltaService(deltaRepository, evaluator);
        // save() is called in propose() before evaluation — lenient because not every test reaches it
        lenient().when(deltaRepository.save(any())).thenAnswer(inv ->
                Promise.of(inv.getArgument(0, LearningDelta.class)));
        // updateState/updateStateWithRejection stubs — lenient as only rejection tests need them
        lenient().when(deltaRepository.updateState(anyString(), any(LearningDeltaState.class)))
                .thenAnswer(inv -> Promise.of(
                        buildDelta(inv.getArgument(0), LearningTarget.SEMANTIC_FACT,
                                inv.getArgument(1, LearningDeltaState.class))));
        lenient().when(deltaRepository.updateStateWithRejection(anyString(), any(), anyString()))
                .thenAnswer(inv -> Promise.of(
                        buildDelta(inv.getArgument(0), LearningTarget.SEMANTIC_FACT,
                                inv.getArgument(1, LearningDeltaState.class))));
    }

    // ─── Contract guard ───────────────────────────────────────────────────────

    @Test
    @DisplayName("propose rejects delta when contract does not permit the target")
    void proposeRejectsWhenContractDoesNotPermitTarget() {
        LearningDelta delta = buildDelta("delta-1", LearningTarget.SEMANTIC_FACT, LearningDeltaState.PROPOSED);
        LearningContract restrictive = restrictiveContract(); // only EPISODIC_MEMORY permitted

        assertThatThrownBy(() ->
                runPromise(() -> service.propose(delta, restrictive))
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("does not permit target");

        verify(deltaRepository, never()).save(any());
    }

    @Test
    @DisplayName("propose rejects delta when provenance is required but evidenceRefs are empty")
    void proposeRejectsWhenProvenanceRequiredButNoEvidence() {
        LearningDelta delta = new LearningDelta(
                "delta-2",
                LearningDeltaType.SEMANTIC_FACT,
                LearningTarget.SEMANTIC_FACT,
                LearningDeltaState.PROPOSED,
                "agent-1", "release-1", "skill-1", "tenant-1",
                null, "fact-1", null,
                "sha256-test",
                Map.<String, Object>of(),
                List.of(),  // empty evidence
                List.of(), List.of(),
                null, 0.5, 0.8, false, "test-engine",
                Instant.now(), null, null, null, Map.<String, String>of(), null,
                null, null, null, null, null
        );

        assertThatThrownBy(() ->
                runPromise(() -> service.propose(delta, provenanceRequiredContract()))
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("provenance");

        verify(deltaRepository, never()).save(any());
    }

    // ─── Evaluation pipeline state transitions ────────────────────────────────

    @Test
    @DisplayName("propose persists delta before running evaluation")
    void proposePersistsDeltaBeforeEvaluation() {
        LearningDelta delta = buildDelta("delta-3", LearningTarget.SEMANTIC_FACT, LearningDeltaState.PROPOSED);
        when(evaluator.evaluate(any())).thenReturn(Promise.of(approvedResult("delta-3")));

        runPromise(() -> service.propose(delta, permissiveContract()));

        verify(deltaRepository, atLeast(1)).save(any(LearningDelta.class));
    }

    @Test
    @DisplayName("propose transitions to EVALUATED when evaluation result is APPROVED")
    void proposeTransitionsToEvaluatedOnApproved() {
        LearningDelta delta = buildDelta("delta-4", LearningTarget.SEMANTIC_FACT, LearningDeltaState.PROPOSED);
        when(evaluator.evaluate(any())).thenReturn(Promise.of(approvedResult("delta-4")));

        LearningDelta result = runPromise(() -> service.propose(delta, permissiveContract()));

        assertThat(result.state()).isEqualTo(LearningDeltaState.EVALUATED);
    }

    @Test
    @DisplayName("propose transitions to PENDING_HUMAN_REVIEW when result requires human review")
    void proposeTransitionsToPendingHumanReview() {
        LearningDelta delta = buildDelta("delta-5", LearningTarget.SEMANTIC_FACT, LearningDeltaState.PROPOSED);
        when(evaluator.evaluate(any())).thenReturn(Promise.of(pendingHumanReviewResult("delta-5")));

        LearningDelta result = runPromise(() -> service.propose(delta, permissiveContract()));

        assertThat(result.state()).isEqualTo(LearningDeltaState.PENDING_HUMAN_REVIEW);
    }

    @Test
    @DisplayName("propose transitions to REJECTED when evaluation returns rejection reason")
    void proposeTransitionsToRejectedOnFailure() {
        LearningDelta delta = buildDelta("delta-6", LearningTarget.SEMANTIC_FACT, LearningDeltaState.PROPOSED);
        when(evaluator.evaluate(any())).thenReturn(Promise.of(rejectedResult("delta-6")));

        LearningDelta result = runPromise(() -> service.propose(delta, permissiveContract()));

        assertThat(result.state()).isEqualTo(LearningDeltaState.REJECTED);
    }

    // ─── evaluate() by ID ─────────────────────────────────────────────────────

    @Test
    @DisplayName("evaluate by ID returns evaluation result when delta exists")
    void evaluateByIdReturnsResult() {
        LearningDelta delta = buildDelta("delta-7", LearningTarget.SEMANTIC_FACT, LearningDeltaState.PENDING_EVALUATION);
        when(deltaRepository.findById("delta-7")).thenReturn(Promise.of(Optional.of(delta)));
        when(evaluator.evaluate(any())).thenReturn(Promise.of(approvedResult("delta-7")));

        LearningDeltaEvaluator.EvaluationResult result = runPromise(() -> service.evaluate("delta-7"));

        assertThat(result).isNotNull();
        assertThat(result.reasonCode()).isEqualTo(LearningDeltaEvaluator.ReasonCode.APPROVED);
    }

    @Test
    @DisplayName("evaluate by ID fails when delta not found")
    void evaluateByIdFailsWhenDeltaNotFound() {
        when(deltaRepository.findById("missing-id")).thenReturn(Promise.of(Optional.empty()));

        assertThatThrownBy(() -> runPromise(() -> service.evaluate("missing-id")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing-id");
    }
}
