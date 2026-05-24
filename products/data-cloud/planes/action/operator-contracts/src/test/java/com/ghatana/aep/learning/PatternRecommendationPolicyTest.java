package com.ghatana.aep.learning;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatternRecommendationPolicyTest {

    @Test
    void emitsPatternSuggestedForHighScoringCandidate() {
        PatternRecommendationPolicy policy = new PatternRecommendationPolicy(
            Clock.fixed(Instant.parse("2026-05-23T00:00:00Z"), ZoneOffset.UTC),
            0.7);

        PatternSuggestionEvent event = policy.suggest(candidate(highScore()));

        assertThat(event.eventType()).isEqualTo("pattern.suggested");
        assertThat(event.tenantId()).isEqualTo("tenant-a");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-05-23T00:00:00Z"));
        assertThat(event.reviewPacket()).containsKey("scoreExplanation");
    }

    @Test
    void doesNotActivateLowScoringCandidate() {
        PatternRecommendationPolicy policy = new PatternRecommendationPolicy(Clock.systemUTC(), 0.9);

        assertThat(policy.shouldRecommend(candidate(lowScore()))).isFalse();
        assertThatThrownBy(() -> policy.suggest(candidate(lowScore())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("below recommendation threshold");
    }

    @Test
    void rejectsLearningOutputThatIsNotPatternSuggested() {
        assertThatThrownBy(() -> new PatternSuggestionEvent(
            "event-1",
            "pattern.promoted",
            "tenant-a",
            "candidate-1",
            0.8,
            Instant.parse("2026-05-23T00:00:00Z"),
            Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("pattern.suggested");
    }

    @Test
    void scoreRejectsInvalidRiskDimension() {
        assertThatThrownBy(() -> new PatternScore(
            0.8,
            0.8,
            1.1,
            0.8,
            0.8,
            0.8,
            0.8,
            0.8,
            0.2,
            1.2,
            0.2,
            0.8,
            Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("falsePositiveRisk");
    }

    private static PatternCandidate candidate(PatternScore score) {
        return new PatternCandidate(
            "candidate-1",
            "tenant-a",
            "correlated_event_group_mining",
            Map.of(
                "apiVersion", "aep.ghatana.io/v1",
                "kind", "PatternSpec",
                "metadata", Map.of("name", "candidate")),
            score,
            List.of("event-group-1"));
    }

    private static PatternScore highScore() {
        return new PatternScore(
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
            Map.of("summary", "strong support with acceptable risk"));
    }

    private static PatternScore lowScore() {
        return new PatternScore(
            0.2,
            0.25,
            0.8,
            0.2,
            0.2,
            0.3,
            0.2,
            0.2,
            0.7,
            0.7,
            0.7,
            0.4,
            Map.of("summary", "weak support and high risk"));
    }
}
