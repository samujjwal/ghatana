/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DefaultLearningDeltaEvaluator covering target-specific validation
 * and the pendingHumanReview pathway for low-confidence procedural skills.
 *
 * @doc.type class
 * @doc.purpose Tests for DefaultLearningDeltaEvaluator
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("DefaultLearningDeltaEvaluator Tests")
class DefaultLearningDeltaEvaluatorTest extends EventloopTestBase {

    private DefaultLearningDeltaEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new DefaultLearningDeltaEvaluator();
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private static LearningDelta buildDelta(
            LearningDeltaType type,
            LearningTarget target,
            String procedureId,
            String semanticFactId,
            String negativeKnowledgeId,
            List<String> evidenceRefs,
            double confidenceAfter,
            Map<String, Object> proposedContent
    ) {
        return new LearningDelta(
                "delta-test",
                type,
                target,
                LearningDeltaState.PROPOSED,
                "agent-1", "release-1", "skill-1", "tenant-1",
                procedureId, semanticFactId, negativeKnowledgeId,
                "sha256-test",
                proposedContent,
                evidenceRefs,
                List.of(),
                List.of(),
                null,
                0.5, confidenceAfter,
                false,
                "test-engine",
                Instant.now(),
                null, null, null,
                Map.of(),
                null
        );
    }

    // ─── evidence gate ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Should reject delta with no evidence references")
    void shouldRejectWhenNoEvidence() {
        LearningDelta delta = buildDelta(
                LearningDeltaType.SEMANTIC_FACT,
                LearningTarget.SEMANTIC_FACT,
                null, "fact-1", null,
                List.of(),   // no evidence
                0.9,
                Map.of("fact", "value")
        );

        LearningDeltaEvaluator.EvaluationResult result = runPromise(() -> evaluator.evaluate(delta));

        assertThat(result.approved()).isFalse();
        assertThat(result.recommendation()).startsWith("Reject:");
    }

    // ─── procedural skill ───────────────────────────────────────────────────

    @Test
    @DisplayName("Should route procedural skill with confidence 0.75 and evidence to pending human review")
    void shouldRouteProceduralSkillLowConfidenceWithEvidenceToPendingReview() {
        LearningDelta delta = buildDelta(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "procedure-001", null, null,
                List.of("ev-1"),   // has evidence
                0.75,              // below 0.8 threshold
                Map.of("step", "do-something")
        );

        LearningDeltaEvaluator.EvaluationResult result = runPromise(() -> evaluator.evaluate(delta));

        assertThat(result.approved()).isFalse();
        assertThat(result.recommendation()).startsWith("Pending human review:");
        assertThat(result.confidence()).isEqualTo(0.75);
    }

    @Test
    @DisplayName("Should approve procedural skill with confidence >= 0.8 and evidence")
    void shouldApproveProceduralSkillHighConfidence() {
        // PROCEDURAL_SKILL is an execution target — rollbackRef is required by the evaluator
        LearningDelta delta = new LearningDelta(
                "delta-test",
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                LearningDeltaState.PROPOSED,
                "agent-1", "release-1", "skill-1", "tenant-1",
                "procedure-001", null, null,
                "sha256-test",
                Map.of("step", "do-something"),
                List.of("ev-1"),
                List.of(),
                List.of(),
                "rollback-ref-001",   // required for execution target
                0.5, 0.85,
                false,
                "test-engine",
                Instant.now(),
                null, null, null,
                Map.of(),
                null
        );

        LearningDeltaEvaluator.EvaluationResult result = runPromise(() -> evaluator.evaluate(delta));

        assertThat(result.approved()).isTrue();
    }

    @Test
    @DisplayName("Should reject procedural skill without procedureId")
    void shouldRejectProceduralSkillWithoutProcedureId() {
        LearningDelta delta = buildDelta(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                null,   // missing procedureId
                null, null,
                List.of("ev-1"),
                0.9,
                Map.of("step", "do-something")
        );

        LearningDeltaEvaluator.EvaluationResult result = runPromise(() -> evaluator.evaluate(delta));

        assertThat(result.approved()).isFalse();
        assertThat(result.recommendation()).startsWith("Reject:");
        assertThat(result.reason()).contains("procedureId");
    }

    // ─── semantic fact ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Should reject semantic fact delta without semanticFactId")
    void shouldRejectSemanticFactWithoutSemanticFactId() {
        LearningDelta delta = buildDelta(
                LearningDeltaType.SEMANTIC_FACT,
                LearningTarget.SEMANTIC_FACT,
                null, null, null,   // missing semanticFactId
                List.of("ev-1"),
                0.9,
                Map.of("fact", "value")
        );

        LearningDeltaEvaluator.EvaluationResult result = runPromise(() -> evaluator.evaluate(delta));

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).contains("semanticFactId");
    }

    @Test
    @DisplayName("Should approve semantic fact delta with required fields")
    void shouldApproveSemanticFactWithRequiredFields() {
        LearningDelta delta = buildDelta(
                LearningDeltaType.SEMANTIC_FACT,
                LearningTarget.SEMANTIC_FACT,
                null, "fact-001", null,
                List.of("ev-1"),
                0.8,
                Map.of("fact", "value")
        );

        LearningDeltaEvaluator.EvaluationResult result = runPromise(() -> evaluator.evaluate(delta));

        assertThat(result.approved()).isTrue();
    }

    // ─── planner / model-adapter strict gates ───────────────────────────────

    @Test
    @DisplayName("Planner policy target requires rollback reference for safety")
    void shouldRejectPlannerPolicyWithoutRollbackRef() {
        // Planner policy passes validatePlannerPolicy only when evaluationRefs is non-empty;
        // after that the outer guard rejects it for missing rollbackRef (execution target).
        LearningDelta delta = new LearningDelta(
                "delta-test",
                LearningDeltaType.ROUTING_POLICY,
                LearningTarget.PLANNER_POLICY,
                LearningDeltaState.PROPOSED,
                "agent-1", "release-1", "skill-1", "tenant-1",
                null, null, null,
                "sha256-test",
                Map.of("plan", "route-dense"),
                List.of("ev-1"),
                List.of("eval-ref-1"),   // required by validatePlannerPolicy
                List.of(),
                null,                    // no rollbackRef — triggers execution-target check
                0.5, 0.9,
                false,
                "test-engine",
                Instant.now(),
                null, null, null,
                Map.of(),
                null
        );

        LearningDeltaEvaluator.EvaluationResult result = runPromise(() -> evaluator.evaluate(delta));

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).contains("rollback");
    }
}
