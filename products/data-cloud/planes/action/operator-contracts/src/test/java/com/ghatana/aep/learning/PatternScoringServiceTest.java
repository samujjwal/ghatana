package com.ghatana.aep.learning;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatternScoringServiceTest {

    private final PatternScoringService service = new PatternScoringService(Clock.fixed(
        Instant.parse("2026-05-23T15:00:00Z"),
        ZoneOffset.UTC));

    @Test
    void recordsCandidateScoreHistory() {
        PatternCandidate candidate = candidate(baseScore());

        PatternScoreRecord record = service.recordCandidateScore(candidate);

        assertThat(record.source()).isEqualTo("candidate_score");
        assertThat(record.occurredAt()).isEqualTo(Instant.parse("2026-05-23T15:00:00Z"));
        assertThat(record.explanation()).containsKey("scoreExplanation");
        assertThat(service.latest("tenant-a", "candidate-1")).contains(record);
        assertThat(service.history("tenant-a", "candidate-1")).containsExactly(record);
    }

    @Test
    void recordsShadowEvaluationScoreWithoutActivatingPattern() {
        PatternCandidate candidate = candidate(baseScore());
        service.recordCandidateScore(candidate);

        PatternScoreRecord shadowRecord = service.recordShadowEvaluationScore(candidate, new ShadowPatternEvaluation(
            "candidate-1",
            "tenant-a",
            4,
            3,
            1,
            1,
            0.75,
            0.75,
            false,
            List.of("incident-1", "incident-2", "incident-3"),
            Map.of()));

        assertThat(shadowRecord.source()).isEqualTo("shadow_evaluation_score");
        assertThat(shadowRecord.score().falsePositiveRisk()).isEqualTo(0.25);
        assertThat(shadowRecord.score().falseNegativeRisk()).isEqualTo(0.25);
        assertThat(shadowRecord.explanation()).containsEntry("shadowPrecision", 0.75);
        assertThat(service.history("tenant-a", "candidate-1")).hasSize(2);
    }

    @Test
    void rejectsShadowEvaluationForDifferentCandidate() {
        PatternCandidate candidate = candidate(baseScore());
        ShadowPatternEvaluation evaluation = new ShadowPatternEvaluation(
            "candidate-2",
            "tenant-a",
            1,
            1,
            0,
            0,
            1.0,
            1.0,
            false,
            List.of("incident-1"),
            Map.of());

        assertThatThrownBy(() -> service.recordShadowEvaluationScore(candidate, evaluation))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must belong to the candidate");
    }

    private static PatternCandidate candidate(PatternScore score) {
        return new PatternCandidate(
            "candidate-1",
            "tenant-a",
            "similarity_based_pattern_extraction",
            Map.of(
                "apiVersion", "aep.ghatana.io/v1",
                "kind", "PatternSpec",
                "metadata", Map.of("name", "candidate-1"),
                "lifecycle", Map.of("state", "CANDIDATE")),
            score,
            List.of("event-1", "event-2"));
    }

    private static PatternScore baseScore() {
        return new PatternScore(
            0.8,
            0.7,
            1.2,
            0.7,
            0.6,
            0.8,
            0.5,
            0.5,
            0.25,
            0.4,
            0.3,
            0.8,
            Map.of("summary", "candidate extracted from correlated event group"));
    }
}
