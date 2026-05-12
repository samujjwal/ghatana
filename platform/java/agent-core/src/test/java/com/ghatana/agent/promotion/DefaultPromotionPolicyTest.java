/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.evaluation.EvaluationResult;
import com.ghatana.agent.learning.delta.LearningDelta;
import com.ghatana.agent.learning.delta.LearningChangeType;
import com.ghatana.agent.learning.delta.LearningDeltaState;
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

    private LearningDelta createDelta() {
        return LearningDelta.propose(
                "agent-123",
                "release-1",
                LearningTarget.PROCEDURAL_SKILL,
                LearningChangeType.CREATE,
                "skill-123",
                "artifact-123",
                List.of("episode-1", "episode-2")
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
