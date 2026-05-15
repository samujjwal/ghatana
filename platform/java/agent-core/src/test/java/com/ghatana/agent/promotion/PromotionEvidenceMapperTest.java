/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.evaluation.EvaluationResult;
import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaFactory;
import com.ghatana.agent.learning.LearningDeltaType;
import com.ghatana.agent.learning.LearningTarget;
import com.ghatana.agent.mastery.MasteryState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromotionEvidenceMapper Tests")
class PromotionEvidenceMapperTest {

    @Test
    @DisplayName("COMPETENT to MASTERED should require explicit category metadata or category case")
    void shouldRequireExplicitCategoryEvidenceForMasteredTransition() {
        LearningDelta delta = buildDelta();
        EvaluationResult resultWithoutCategoryEvidence = new EvaluationResult(
                "eval-1",
                "pack-1",
                "artifact-1",
                delta.deltaId(),
                Instant.now(),
                Instant.now(),
                2,
                2,
                0,
                0,
                1.0,
                List.of(
                        new EvaluationResult.TestCaseResult("case-1", "general test", true, "ok", "", 12),
                        new EvaluationResult.TestCaseResult("case-2", "another test", true, "ok", "", 8)
                ),
                Map.of()
        );

        Map<String, String> evidence = PromotionEvidenceMapper.toEvidenceMap(
                delta,
                MasteryState.COMPETENT,
                MasteryState.MASTERED,
                resultWithoutCategoryEvidence);

        assertThat(evidence.get("regression_passed")).isEqualTo("false");
        assertThat(evidence.get("safety_passed")).isEqualTo("false");
        assertThat(evidence.get("recovery_passed")).isEqualTo("false");
        assertThat(evidence.get("compatibility_passed")).isEqualTo("false");
    }

    @Test
    @DisplayName("COMPETENT to MASTERED should pass with explicit category metadata")
    void shouldUseCategoryMetadataWhenPresent() {
        LearningDelta delta = buildDelta();
        EvaluationResult categoryMetadataResult = new EvaluationResult(
                "eval-2",
                "pack-1",
                "artifact-1",
                delta.deltaId(),
                Instant.now(),
                Instant.now(),
                4,
                4,
                0,
                0,
                1.0,
                List.of(
                        new EvaluationResult.TestCaseResult("case-1", "general test", true, "ok", "", 10)
                ),
                Map.of(
                        "regression_passed", "true",
                        "safety_passed", "true",
                        "recovery_passed", "true",
                        "compatibility_passed", "true"
                )
        );

        Map<String, String> evidence = PromotionEvidenceMapper.toEvidenceMap(
                delta,
                MasteryState.COMPETENT,
                MasteryState.MASTERED,
                categoryMetadataResult);

        assertThat(evidence.get("regression_passed")).isEqualTo("true");
        assertThat(evidence.get("safety_passed")).isEqualTo("true");
        assertThat(evidence.get("recovery_passed")).isEqualTo("true");
        assertThat(evidence.get("compatibility_passed")).isEqualTo("true");
    }

    @Test
    @DisplayName("COMPETENT to MASTERED should pass when category test cases exist and pass")
    void shouldUseCategoryCaseResultsWhenMetadataMissing() {
        LearningDelta delta = buildDelta();
        EvaluationResult categoryCaseResult = new EvaluationResult(
                "eval-3",
                "pack-1",
                "artifact-1",
                delta.deltaId(),
                Instant.now(),
                Instant.now(),
                4,
                4,
                0,
                0,
                1.0,
                List.of(
                        new EvaluationResult.TestCaseResult("regression-001", "regression suite", true, "ok", "", 10),
                        new EvaluationResult.TestCaseResult("safety-001", "safety suite", true, "ok", "", 11),
                        new EvaluationResult.TestCaseResult("recovery-001", "recovery suite", true, "ok", "", 12),
                        new EvaluationResult.TestCaseResult("compatibility-001", "compatibility suite", true, "ok", "", 13)
                ),
                Map.of()
        );

        Map<String, String> evidence = PromotionEvidenceMapper.toEvidenceMap(
                delta,
                MasteryState.COMPETENT,
                MasteryState.MASTERED,
                categoryCaseResult);

        assertThat(evidence.get("regression_passed")).isEqualTo("true");
        assertThat(evidence.get("safety_passed")).isEqualTo("true");
        assertThat(evidence.get("recovery_passed")).isEqualTo("true");
        assertThat(evidence.get("compatibility_passed")).isEqualTo("true");
    }

    private static LearningDelta buildDelta() {
        return LearningDeltaFactory.propose(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-1",
                "release-1",
                "skill-1",
                "tenant-1",
                Map.of("action", "update"),
                List.of("evidence-1"),
                "tester"
        );
    }
}
