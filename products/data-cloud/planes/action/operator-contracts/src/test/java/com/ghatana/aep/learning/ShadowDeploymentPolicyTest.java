package com.ghatana.aep.learning;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShadowDeploymentPolicyTest {

    @Test
    void evaluatesCandidateWithoutEnablingSideEffects() {
        ShadowDeploymentPolicy policy = new ShadowDeploymentPolicy();

        ShadowPatternEvaluation evaluation = policy.evaluate(
            candidate(patternSpec(Map.of("state", "SHADOW"), List.of(Map.of("type", "ticket.propose")))),
            List.of("incident-1", "incident-2", "incident-3"),
            List.of("incident-2", "incident-3", "incident-4"));

        assertThat(evaluation.sideEffectsEnabled()).isFalse();
        assertThat(evaluation.matchCount()).isEqualTo(3);
        assertThat(evaluation.truePositiveCount()).isEqualTo(2);
        assertThat(evaluation.falsePositiveCount()).isEqualTo(1);
        assertThat(evaluation.falseNegativeCount()).isEqualTo(1);
        assertThat(evaluation.precision()).isEqualTo(2.0 / 3.0);
        assertThat(evaluation.recall()).isEqualTo(2.0 / 3.0);
        assertThat(evaluation.reviewPacket()).containsEntry("actionsSuppressed", true);
    }

    @Test
    void rejectsAlreadyActivePatternForShadowEvaluation() {
        ShadowDeploymentPolicy policy = new ShadowDeploymentPolicy();

        assertThatThrownBy(() -> policy.evaluate(
            candidate(patternSpec(Map.of("state", "ACTIVE"), List.of())),
            List.of("incident-1"),
            List.of("incident-1")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("active");
    }

    @Test
    void shadowEvaluationCannotRepresentEnabledSideEffects() {
        assertThatThrownBy(() -> new ShadowPatternEvaluation(
            "candidate-1",
            "tenant-a",
            1,
            1,
            0,
            0,
            1.0,
            1.0,
            true,
            List.of("incident-1"),
            Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("side effects");
    }

    private static PatternCandidate candidate(Map<String, Object> patternSpec) {
        return new PatternCandidate(
            "candidate-1",
            "tenant-a",
            "shadow_test",
            patternSpec,
            new PatternScore(
                0.9,
                0.88,
                1.4,
                0.86,
                0.78,
                0.9,
                0.85,
                0.84,
                0.2,
                0.12,
                0.18,
                0.95,
                Map.of("summary", "strong support with acceptable risk")),
            List.of("event-group-1"));
    }

    private static Map<String, Object> patternSpec(Map<String, Object> lifecycle, List<Map<String, Object>> actions) {
        return Map.of(
            "apiVersion", "aep.ghatana.io/v1",
            "kind", "PatternSpec",
            "metadata", Map.of("name", "candidate"),
            "lifecycle", lifecycle,
            "actions", actions);
    }
}
