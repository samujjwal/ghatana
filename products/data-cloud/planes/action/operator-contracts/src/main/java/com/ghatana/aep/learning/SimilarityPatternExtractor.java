package com.ghatana.aep.learning;

import com.ghatana.aep.model.CanonicalEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts ranked candidate PatternSpecs from correlated event groups.
 *
 * @doc.type class
 * @doc.purpose Converts explored event groups into candidate PatternSpec definitions for review
 * @doc.layer product
 * @doc.pattern Service
 */
public final class SimilarityPatternExtractor {

    public List<PatternCandidate> extract(List<CorrelatedEventGroup> groups) {
        List<PatternCandidate> candidates = new ArrayList<>();
        for (CorrelatedEventGroup group : List.copyOf(groups != null ? groups : List.of())) {
            if (group.events().size() < 2) {
                continue;
            }
            candidates.add(new PatternCandidate(
                "candidate-" + group.groupId(),
                group.tenantId(),
                "similarity_based_pattern_extraction",
                patternSpec(group),
                score(group),
                group.events().stream().map(CanonicalEvent::eventId).toList()));
        }
        return candidates.stream()
            .sorted((left, right) -> Double.compare(
                right.score().recommendationScore(),
                left.score().recommendationScore()))
            .toList();
    }

    private static Map<String, Object> patternSpec(CorrelatedEventGroup group) {
        List<Map<String, Object>> operands = group.events().stream()
            .map(event -> Map.of("event", (Object) event.eventType()))
            .toList();
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("apiVersion", "aep.ghatana.io/v1");
        spec.put("kind", "PatternSpec");
        spec.put("metadata", Map.of(
            "name", "candidate-" + sanitize(group.correlationId()),
            "tenantId", group.tenantId(),
            "sourceGroupId", group.groupId()));
        spec.put("semantics", Map.of(
            "timePolicy", Map.of("timeMode", "EVENT_TIME"),
            "uncertaintyPolicy", Map.of("threshold", 0.7),
            "replayPolicy", Map.of("mode", "RECORDED_AGENT_OUTPUT")));
        spec.put("pattern", Map.of(
            "operator", "SEQ",
            "within", "PT" + Math.max(1, java.time.Duration.between(group.windowStart(), group.windowEnd()).toMinutes()) + "M",
            "operands", operands));
        spec.put("emit", Map.of("eventType", "pattern.candidate_matched", "outputSchema", "CandidatePatternMatch"));
        spec.put("lifecycle", Map.of("state", "CANDIDATE"));
        spec.put("governance", Map.of("reviewPolicy", "human_required"));
        spec.put("observability", Map.of("metrics", true, "tracing", true));
        return spec;
    }

    private static PatternScore score(CorrelatedEventGroup group) {
        double confidence = Math.min(1.0, group.support() + group.correlation() * 0.25);
        double novelty = Math.max(0.1, group.searchSpaceReductionRatio());
        return new PatternScore(
            group.support(),
            confidence,
            1.0 + group.correlation(),
            confidence,
            novelty,
            0.75,
            0.5,
            0.5,
            Math.max(0.1, 1.0 - group.searchSpaceReductionRatio()),
            Math.max(0.05, 1.0 - confidence),
            0.25,
            0.75,
            Map.of(
                "groupId", group.groupId(),
                "eventTypes", group.metadata().getOrDefault("eventTypes", List.of()),
                "method", "similarity_by_temporal_order_and_correlation"));
    }

    private static String sanitize(String value) {
        return value.toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }
}
