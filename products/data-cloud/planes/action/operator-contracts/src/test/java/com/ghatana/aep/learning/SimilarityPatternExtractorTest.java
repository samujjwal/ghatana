package com.ghatana.aep.learning;

import com.ghatana.aep.model.CanonicalEvent;
import com.ghatana.aep.pattern.spec.PatternSpecValidator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SimilarityPatternExtractorTest {

    @Test
    void extractsCandidatePatternSpecsFromCorrelatedGroups() {
        CorrelatedEventGroup group = new CorrelatedEventGroupMiner().mine(List.of(
            event("event-1", "deploy.started", "corr-a", "2026-05-23T00:00:00Z"),
            event("event-2", "service.error_rate_elevated", "corr-a", "2026-05-23T00:05:00Z"),
            event("event-3", "pager.alert_created", "corr-a", "2026-05-23T00:07:00Z")),
            Duration.ofMinutes(30),
            2).get(0);

        List<PatternCandidate> candidates = new SimilarityPatternExtractor().extract(List.of(group));

        assertThat(candidates).hasSize(1);
        PatternCandidate candidate = candidates.get(0);
        assertThat(candidate.source()).isEqualTo("similarity_based_pattern_extraction");
        assertThat(candidate.evidenceRefs()).containsExactly("event-1", "event-2", "event-3");
        assertThat(PatternSpecValidator.validate(candidate.patternSpec()).valid()).isTrue();
        assertThat(candidate.patternSpec()).containsEntry("kind", "PatternSpec");
        assertThat(candidate.score().explanation()).containsEntry("method", "similarity_by_temporal_order_and_correlation");
    }

    @Test
    void ranksCandidatesByRecommendationScore() {
        CorrelatedEventGroupMiner miner = new CorrelatedEventGroupMiner();
        List<CorrelatedEventGroup> groups = miner.mine(List.of(
            event("a-1", "deploy.started", "corr-a", "2026-05-23T00:00:00Z"),
            event("a-2", "service.error_rate_elevated", "corr-a", "2026-05-23T00:05:00Z"),
            event("a-3", "pager.alert_created", "corr-a", "2026-05-23T00:07:00Z"),
            event("b-1", "deploy.started", "corr-b", "2026-05-23T00:00:00Z"),
            event("b-2", "service.error_rate_elevated", "corr-b", "2026-05-23T00:05:00Z")),
            Duration.ofMinutes(30),
            2);

        List<PatternCandidate> candidates = new SimilarityPatternExtractor().extract(groups);

        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).score().recommendationScore())
            .isGreaterThanOrEqualTo(candidates.get(1).score().recommendationScore());
    }

    private static CanonicalEvent event(String eventId, String eventType, String correlationId, String eventTime) {
        return new CanonicalEvent(
            eventId,
            "tenant-a",
            eventType,
            "1.0.0",
            Instant.parse(eventTime),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of("system", "test"),
            List.of(),
            correlationId,
            Optional.empty(),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of(),
            eventId + "-idempotency");
    }
}
