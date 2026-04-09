package com.ghatana.dataexploration.preprocessing.impl;

import com.ghatana.dataexploration.model.CorrelatedEventGroup;
import com.ghatana.dataexploration.model.EventStreamStatistics;
import com.ghatana.dataexploration.model.ExplorationEvent;
import com.ghatana.dataexploration.model.NormalizedEvent;
import com.ghatana.dataexploration.model.PreprocessedEventBatch;
import com.ghatana.dataexploration.model.PreprocessingConfig;
import com.ghatana.dataexploration.model.TemporalFeatures;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BasicDataPreprocessor Tests")
class BasicDataPreprocessorTest extends EventloopTestBase {

    private final BasicDataPreprocessor preprocessor = new BasicDataPreprocessor();

    @Test
    @DisplayName("normalizeEvents standardizes property keys and values")
    void normalizeEventsStandardizesPropertyKeysAndValues() {
        ExplorationEvent event = event(
                "evt-1",
                "LOGIN",
                instant(0),
                Map.of(
                        "User Name!", "  Alice ",
                        "Count", 7,
                        "Active?", true,
                        "Score", 9.5
                )
        );

        List<NormalizedEvent> normalizedEvents = runPromise(() -> preprocessor.normalizeEvents(List.of(event)));

        assertEquals(1, normalizedEvents.size());

        NormalizedEvent normalizedEvent = normalizedEvents.get(0);
        assertEquals("evt-1", normalizedEvent.getEventId());
        assertEquals("LOGIN", normalizedEvent.getEventType());
        assertEquals(1.0, normalizedEvent.getConfidence());
        assertEquals("alice", normalizedEvent.getNormalizedProperties().get("user_name_"));
        assertEquals(7.0, normalizedEvent.getNormalizedProperties().get("count"));
        assertEquals("true", normalizedEvent.getNormalizedProperties().get("active_"));
        assertEquals(9.5, normalizedEvent.getNormalizedProperties().get("score"));
    }

    @Test
    @DisplayName("extractTemporalFeatures computes intervals and skips singleton event types")
    void extractTemporalFeaturesComputesIntervalsAndSkipsSingletonTypes() {
        List<ExplorationEvent> events = List.of(
                event("c1", "click", instant(0), Map.of()),
                event("c2", "click", instant(60), Map.of()),
                event("c3", "click", instant(180), Map.of()),
                event("v1", "view", instant(30), Map.of())
        );

        Map<String, TemporalFeatures> features = runPromise(() ->
                preprocessor.extractTemporalFeatures(events, Duration.ofMinutes(10)));

        assertEquals(1, features.size());
        assertTrue(features.containsKey("click"));
        assertFalse(features.containsKey("view"));

        TemporalFeatures clickFeatures = features.get("click");
        assertNotNull(clickFeatures);
        assertEquals(Duration.ofSeconds(90), clickFeatures.getAverageInterval());
        assertEquals(Duration.ofSeconds(120), clickFeatures.getMedianInterval());
        assertEquals(3L, clickFeatures.getEventCount());
        assertEquals(instant(0), clickFeatures.getFirstOccurrence());
        assertEquals(instant(180), clickFeatures.getLastOccurrence());
        assertEquals(1.0, clickFeatures.getFrequency(), 0.0001);
        assertEquals(0.3333, clickFeatures.getBurstiness(), 0.01);
        assertTrue(clickFeatures.isRegular());
        assertFalse(clickFeatures.isBursty());
    }

    @Test
    @DisplayName("findCorrelatedEventTypes returns groups above confidence threshold")
    void findCorrelatedEventTypesReturnsGroupsAboveConfidenceThreshold() {
        List<ExplorationEvent> events = List.of(
                event("a1", "login", instant(0), Map.of()),
                event("a2", "login", instant(600), Map.of()),
                event("b1", "purchase", instant(120), Map.of()),
                event("b2", "purchase", instant(720), Map.of()),
                event("c1", "logout", instant(2400), Map.of())
        );

        Set<CorrelatedEventGroup> correlatedGroups = runPromise(() ->
                preprocessor.findCorrelatedEventTypes(events, 0.75));

        assertEquals(1, correlatedGroups.size());

        CorrelatedEventGroup group = correlatedGroups.iterator().next();
        assertEquals(Set.of("login", "purchase"), group.getEventTypes());
        assertEquals(1.0, group.getConfidence(), 0.0001);
        assertEquals(0.1, group.getSupport(), 0.0001);
        assertEquals(1, group.getRules().size());
        assertTrue(group.contains("login"));
        assertTrue(group.contains("purchase"));
    }

    @Test
    @DisplayName("calculateStreamStatistics summarizes event distribution")
    void calculateStreamStatisticsSummarizesEventDistribution() {
        List<ExplorationEvent> events = List.of(
                event("e1", "click", instant(0), Map.of()),
                event("e2", "click", instant(60), Map.of()),
                event("e3", "view", instant(120), Map.of())
        );

        EventStreamStatistics statistics = runPromise(() ->
                preprocessor.calculateStreamStatistics(events, Duration.ofMinutes(5)));

        assertEquals(3L, statistics.getTotalEvents());
        assertEquals(2, statistics.getUniqueEventTypes());
        assertEquals(Duration.ofMinutes(2), statistics.getTimeSpan());
        assertEquals(1.5, statistics.getAverageFrequency(), 0.0001);
        assertEquals(2L, statistics.getEventTypeCounts().get("click"));
        assertEquals(1L, statistics.getEventTypeCounts().get("view"));
        assertEquals(1.0, statistics.getEventTypeFrequencies().get("click"), 0.0001);
        assertEquals(0.5, statistics.getEventTypeFrequencies().get("view"), 0.0001);
        assertEquals("click", statistics.getMostFrequentEventType());
        assertEquals(2.0 / 3.0, statistics.getDiversity(), 0.0001);
        assertFalse(statistics.isEmpty());
        assertTrue(statistics.getEntropy() > 0.9 && statistics.getEntropy() < 0.92);
    }

    @Test
    @DisplayName("calculateStreamStatistics returns empty defaults for empty event lists")
    void calculateStreamStatisticsReturnsEmptyDefaultsForEmptyEventLists() {
        EventStreamStatistics statistics = runPromise(() ->
                preprocessor.calculateStreamStatistics(List.of(), Duration.ofMinutes(5)));

        assertTrue(statistics.isEmpty());
        assertEquals(0L, statistics.getTotalEvents());
        assertEquals(0, statistics.getUniqueEventTypes());
        assertEquals(Duration.ZERO, statistics.getTimeSpan());
        assertEquals(0.0, statistics.getAverageFrequency());
        assertEquals(Map.of(), statistics.getEventTypeCounts());
        assertEquals(Map.of(), statistics.getEventTypeFrequencies());
        assertNotNull(statistics.getWindowStart());
        assertNotNull(statistics.getWindowEnd());
        assertNull(statistics.getMostFrequentEventType());
    }

    @Test
    @DisplayName("preprocessEvents assembles normalized events statistics and optional temporal features")
    void preprocessEventsAssemblesNormalizedEventsStatisticsAndOptionalTemporalFeatures() {
        List<ExplorationEvent> events = List.of(
                event("evt-1", "click", instant(0), Map.of("Message", "  HELLO  ")),
                event("evt-2", "click", instant(60), Map.of("Message", "World"))
        );
        PreprocessingConfig config = PreprocessingConfig.builder()
                .extractTemporalFeatures(false)
                .build();

        PreprocessedEventBatch batch = runPromise(() -> preprocessor.preprocessEvents(events, config));

        assertEquals(2, batch.getEventCount());
        assertFalse(batch.isEmpty());
        assertEquals(2L, batch.getStatistics().getTotalEvents());
        assertEquals(Map.of(), batch.getTemporalFeatures());
        assertNotNull(batch.getProcessingTimestamp());
        assertNotNull(batch.getBatchId());
        assertEquals("hello", batch.getEvents().get(0).getNormalizedProperties().get("message"));
    }

    private static ExplorationEvent event(String id, String type, Instant timestamp, Map<String, Object> properties) {
        return new ExplorationEvent(id, type, timestamp, properties, "tenant-1");
    }

    private static Instant instant(long seconds) {
        return Instant.parse("2026-04-02T00:00:00Z").plusSeconds(seconds);
    }
}
