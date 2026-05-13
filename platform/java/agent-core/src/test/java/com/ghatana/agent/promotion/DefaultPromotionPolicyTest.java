/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.evaluation.EvaluationResult;
import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaType;
import com.ghatana.agent.learning.LearningDeltaState;
import com.ghatana.agent.learning.LearningTarget;
import com.ghatana.agent.mastery.MasteryState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for DefaultPromotionPolicy
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("DefaultPromotionPolicy Tests")
class DefaultPromotionPolicyTest {

    private final DefaultPromotionPolicy policy = new DefaultPromotionPolicy();

    @Test
    @DisplayName("Safety test failure blocks promotion")
    void safetyFailureBlocksPromotion() {
        LearningDelta delta = createDelta();
        EvaluationResult result = createResultWithFailedSafety();

        assertThat(policy.canPromote(delta, result)).isFalse();
        assertThat(policy.targetState(delta, result)).isEqualTo(MasteryState.QUARANTINED);
    }

    @Test
    @DisplayName("Regression test failure blocks promotion")
    void regressionFailureBlocksPromotion() {
        LearningDelta delta = createDelta();
        EvaluationResult result = createResultWithFailedRegression();

        assertThat(policy.canPromote(delta, result)).isFalse();
    }

    @Test
    @DisplayName("All tests passed leads to MASTERED")
    void allPassedLeadsToMastered() {
        LearningDelta delta = createDelta();
        EvaluationResult result = createResultAllPassed();

        assertThat(policy.canPromote(delta, result)).isTrue();
        assertThat(policy.targetState(delta, result)).isEqualTo(MasteryState.MASTERED);
    }

    @Test
    @DisplayName("Regression and safety passed with other failures leads to COMPETENT")
    void regressionAndSafetyPassedLeadsToCompetent() {
        LearningDelta delta = createDelta();
        EvaluationResult result = createResultWithRegressionAndSafetyPassed();

        assertThat(policy.canPromote(delta, result)).isTrue();
        assertThat(policy.targetState(delta, result)).isEqualTo(MasteryState.COMPETENT);
    }

    @Test
    @DisplayName("Pass rate below 80% blocks promotion")
    void lowPassRateBlocksPromotion() {
        LearningDelta delta = createDelta();
        EvaluationResult result = createResultWithLowPassRate();

        assertThat(policy.canPromote(delta, result)).isFalse();
    }

    // ── Acceptance tests (README §10.2) ────────────────────────────────────

    @Test
    @DisplayName("Cannot promote to MASTERED without eval refs on the delta")
    void cannotPromoteToMasteredWithoutEvalRefs() {
        LearningDelta deltaNoRefs = createDeltaWithoutEvalRefs();
        EvaluationResult allPassed = createResultAllPassed();

        // canPromote may return true, but targetState must NOT return MASTERED
        MasteryState target = policy.targetState(deltaNoRefs, allPassed);
        assertThat(target).isNotEqualTo(MasteryState.MASTERED);
        // downgraded to COMPETENT when eval refs are absent
        assertThat(target).isEqualTo(MasteryState.COMPETENT);
    }

    @Test
    @DisplayName("Cannot quarantine without safety evidence (no safety test cases)")
    void cannotQuarantineWithoutSafetyEvidence() {
        LearningDelta delta = createDelta();
        // All tests fail but none are safety tests — no basis for quarantine
        EvaluationResult noSafetyTests = new EvaluationResult(
                "result-1", "pack-1", "artifact-123", "delta-1",
                Instant.now(), Instant.now(),
                2, 0, 2, 0, 0.0,
                List.of(
                        new EvaluationResult.TestCaseResult("case-1", "regression test", false, "", "fail", 100),
                        new EvaluationResult.TestCaseResult("case-2", "performance test", false, "", "fail", 100)
                ),
                Map.of());

        MasteryState target = policy.targetState(delta, noSafetyTests);
        assertThat(target).isNotEqualTo(MasteryState.QUARANTINED);
    }

    @Test
    @DisplayName("Safety test failure with safety case → QUARANTINED (evidence present)")
    void quarantineRequiresSafetyTestCaseToBePresent() {
        LearningDelta delta = createDelta();
        EvaluationResult withSafetyFailure = createResultWithFailedSafety();

        assertThat(policy.targetState(delta, withSafetyFailure)).isEqualTo(MasteryState.QUARANTINED);
    }

    @Test
    @DisplayName("Cannot retire directly from MASTERED — default policy blocks it")
    void cannotRetireDirectlyFromMastered() {
        assertThat(policy.canRetireFromMastered(MasteryState.MASTERED)).isFalse();
    }

    @Test
    @DisplayName("Retirement is allowed from MAINTENANCE_ONLY or OBSOLETE states")
    void retirementAllowedFromDegradedStates() {
        assertThat(policy.canRetireFromMastered(MasteryState.MAINTENANCE_ONLY)).isTrue();
        assertThat(policy.canRetireFromMastered(MasteryState.OBSOLETE)).isTrue();
        assertThat(policy.canRetireFromMastered(MasteryState.QUARANTINED)).isTrue();
    }

    private LearningDelta createDeltaWithoutEvalRefs() {
        return new LearningDelta(
                "delta-no-refs",
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                LearningDeltaState.PROPOSED,
                "agent-123",
                "release-1",
                "skill-123",
                "tenant-123",
                null,
                null,
                null,
                "digest-123",
                Map.of("content", "test"),
                List.of("evidence-1", "evidence-2", "evidence-3"),
                List.of(),    // no eval refs
                List.of("episode-1", "episode-2"),
                "rollback-1",
                0.5,
                0.8,
                false,
                "system",
                Instant.now(),
                Instant.now(),
                null,
                null,
                Map.of("label", "test"),
                null
        );
    }

    private LearningDelta createDelta() {
        return new LearningDelta(
                "delta-1",
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                LearningDeltaState.PROPOSED,
                "agent-123",
                "release-1",
                "skill-123",
                "tenant-123",
                null,
                null,
                null,
                "digest-123",
                Map.of("content", "test"),
                List.of("evidence-1", "evidence-2", "evidence-3"),
                List.of("eval-1"),
                List.of("episode-1", "episode-2"),
                "rollback-1",
                0.5,
                0.8,
                false,
                "system",
                Instant.now(),
                Instant.now(),
                null,
                null,
                Map.of("label", "test"),
                null
        );
    }

    private EvaluationResult createResultWithFailedSafety() {
        return new EvaluationResult(
                "result-1",
                "pack-1",
                "artifact-123",
                "delta-1",
                Instant.now(),
                Instant.now(),
                10,
                9,
                1,
                0,
                0.9,
                List.of(
                        new EvaluationResult.TestCaseResult("case-1", "regression test", true, "output", "", 100),
                        new EvaluationResult.TestCaseResult("case-2", "safety test", false, "output", "failed", 100)
                ),
                Map.of()
        );
    }

    private EvaluationResult createResultWithFailedRegression() {
        return new EvaluationResult(
                "result-1",
                "pack-1",
                "artifact-123",
                "delta-1",
                Instant.now(),
                Instant.now(),
                10,
                9,
                1,
                0,
                0.9,
                List.of(
                        new EvaluationResult.TestCaseResult("case-1", "regression test", false, "output", "failed", 100),
                        new EvaluationResult.TestCaseResult("case-2", "safety test", true, "output", "", 100)
                ),
                Map.of()
        );
    }

    private EvaluationResult createResultAllPassed() {
        return new EvaluationResult(
                "result-1",
                "pack-1",
                "artifact-123",
                "delta-1",
                Instant.now(),
                Instant.now(),
                10,
                10,
                0,
                0,
                1.0,
                List.of(
                        new EvaluationResult.TestCaseResult("case-1", "regression test", true, "output", "", 100),
                        new EvaluationResult.TestCaseResult("case-2", "safety test", true, "output", "", 100)
                ),
                Map.of()
        );
    }

    private EvaluationResult createResultWithRegressionAndSafetyPassed() {
        return new EvaluationResult(
                "result-1",
                "pack-1",
                "artifact-123",
                "delta-1",
                Instant.now(),
                Instant.now(),
                10,
                8,
                2,
                0,
                0.8,
                List.of(
                        new EvaluationResult.TestCaseResult("case-1", "regression test", true, "output", "", 100),
                        new EvaluationResult.TestCaseResult("case-2", "safety test", true, "output", "", 100),
                        new EvaluationResult.TestCaseResult("case-3", "performance test", false, "output", "failed", 100)
                ),
                Map.of()
        );
    }

    private EvaluationResult createResultWithLowPassRate() {
        return new EvaluationResult(
                "result-1",
                "pack-1",
                "artifact-123",
                "delta-1",
                Instant.now(),
                Instant.now(),
                10,
                7,
                3,
                0,
                0.7,
                List.of(),
                Map.of()
        );
    }
}
