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

@DisplayName("BasicDataPreprocessor Tests [GH-90000]")
class BasicDataPreprocessorTest extends EventloopTestBase {

    private final BasicDataPreprocessor preprocessor = new BasicDataPreprocessor(); // GH-90000

    @Test
    @DisplayName("normalizeEvents standardizes property keys and values [GH-90000]")
    void normalizeEventsStandardizesPropertyKeysAndValues() { // GH-90000
        ExplorationEvent event = event( // GH-90000
                "evt-1",
                "LOGIN",
                instant(0), // GH-90000
                Map.of( // GH-90000
                        "User Name!", "  Alice ",
                        "Count", 7,
                        "Active?", true,
                        "Score", 9.5
                )
        );

        List<NormalizedEvent> normalizedEvents = runPromise(() -> preprocessor.normalizeEvents(List.of(event))); // GH-90000

        assertEquals(1, normalizedEvents.size()); // GH-90000

        NormalizedEvent normalizedEvent = normalizedEvents.get(0); // GH-90000
        assertEquals("evt-1", normalizedEvent.getEventId()); // GH-90000
        assertEquals("LOGIN", normalizedEvent.getEventType()); // GH-90000
        assertEquals(1.0, normalizedEvent.getConfidence()); // GH-90000
        assertEquals("alice", normalizedEvent.getNormalizedProperties().get("user_name_ [GH-90000]"));
        assertEquals(7.0, normalizedEvent.getNormalizedProperties().get("count [GH-90000]"));
        assertEquals("true", normalizedEvent.getNormalizedProperties().get("active_ [GH-90000]"));
        assertEquals(9.5, normalizedEvent.getNormalizedProperties().get("score [GH-90000]"));
    }

    @Test
    @DisplayName("extractTemporalFeatures computes intervals and skips singleton event types [GH-90000]")
    void extractTemporalFeaturesComputesIntervalsAndSkipsSingletonTypes() { // GH-90000
        List<ExplorationEvent> events = List.of( // GH-90000
                event("c1", "click", instant(0), Map.of()), // GH-90000
                event("c2", "click", instant(60), Map.of()), // GH-90000
                event("c3", "click", instant(180), Map.of()), // GH-90000
                event("v1", "view", instant(30), Map.of()) // GH-90000
        );

        Map<String, TemporalFeatures> features = runPromise(() -> // GH-90000
                preprocessor.extractTemporalFeatures(events, Duration.ofMinutes(10))); // GH-90000

        assertEquals(1, features.size()); // GH-90000
        assertTrue(features.containsKey("click [GH-90000]"));
        assertFalse(features.containsKey("view [GH-90000]"));

        TemporalFeatures clickFeatures = features.get("click [GH-90000]");
        assertNotNull(clickFeatures); // GH-90000
        assertEquals(Duration.ofSeconds(90), clickFeatures.getAverageInterval()); // GH-90000
        assertEquals(Duration.ofSeconds(120), clickFeatures.getMedianInterval()); // GH-90000
        assertEquals(3L, clickFeatures.getEventCount()); // GH-90000
        assertEquals(instant(0), clickFeatures.getFirstOccurrence()); // GH-90000
        assertEquals(instant(180), clickFeatures.getLastOccurrence()); // GH-90000
        assertEquals(1.0, clickFeatures.getFrequency(), 0.0001); // GH-90000
        assertEquals(0.3333, clickFeatures.getBurstiness(), 0.01); // GH-90000
        assertTrue(clickFeatures.isRegular()); // GH-90000
        assertFalse(clickFeatures.isBursty()); // GH-90000
    }

    @Test
    @DisplayName("findCorrelatedEventTypes returns groups above confidence threshold [GH-90000]")
    void findCorrelatedEventTypesReturnsGroupsAboveConfidenceThreshold() { // GH-90000
        List<ExplorationEvent> events = List.of( // GH-90000
                event("a1", "login", instant(0), Map.of()), // GH-90000
                event("a2", "login", instant(600), Map.of()), // GH-90000
                event("b1", "purchase", instant(120), Map.of()), // GH-90000
                event("b2", "purchase", instant(720), Map.of()), // GH-90000
                event("c1", "logout", instant(2400), Map.of()) // GH-90000
        );

        Set<CorrelatedEventGroup> correlatedGroups = runPromise(() -> // GH-90000
                preprocessor.findCorrelatedEventTypes(events, 0.75)); // GH-90000

        assertEquals(1, correlatedGroups.size()); // GH-90000

        CorrelatedEventGroup group = correlatedGroups.iterator().next(); // GH-90000
        assertEquals(Set.of("login", "purchase"), group.getEventTypes()); // GH-90000
        assertEquals(1.0, group.getConfidence(), 0.0001); // GH-90000
        assertEquals(0.1, group.getSupport(), 0.0001); // GH-90000
        assertEquals(1, group.getRules().size()); // GH-90000
        assertTrue(group.contains("login [GH-90000]"));
        assertTrue(group.contains("purchase [GH-90000]"));
    }

    @Test
    @DisplayName("calculateStreamStatistics summarizes event distribution [GH-90000]")
    void calculateStreamStatisticsSummarizesEventDistribution() { // GH-90000
        List<ExplorationEvent> events = List.of( // GH-90000
                event("e1", "click", instant(0), Map.of()), // GH-90000
                event("e2", "click", instant(60), Map.of()), // GH-90000
                event("e3", "view", instant(120), Map.of()) // GH-90000
        );

        EventStreamStatistics statistics = runPromise(() -> // GH-90000
                preprocessor.calculateStreamStatistics(events, Duration.ofMinutes(5))); // GH-90000

        assertEquals(3L, statistics.getTotalEvents()); // GH-90000
        assertEquals(2, statistics.getUniqueEventTypes()); // GH-90000
        assertEquals(Duration.ofMinutes(2), statistics.getTimeSpan()); // GH-90000
        assertEquals(1.5, statistics.getAverageFrequency(), 0.0001); // GH-90000
        assertEquals(2L, statistics.getEventTypeCounts().get("click [GH-90000]"));
        assertEquals(1L, statistics.getEventTypeCounts().get("view [GH-90000]"));
        assertEquals(1.0, statistics.getEventTypeFrequencies().get("click [GH-90000]"), 0.0001);
        assertEquals(0.5, statistics.getEventTypeFrequencies().get("view [GH-90000]"), 0.0001);
        assertEquals("click", statistics.getMostFrequentEventType()); // GH-90000
        assertEquals(2.0 / 3.0, statistics.getDiversity(), 0.0001); // GH-90000
        assertFalse(statistics.isEmpty()); // GH-90000
        assertTrue(statistics.getEntropy() > 0.9 && statistics.getEntropy() < 0.92); // GH-90000
    }

    @Test
    @DisplayName("calculateStreamStatistics returns empty defaults for empty event lists [GH-90000]")
    void calculateStreamStatisticsReturnsEmptyDefaultsForEmptyEventLists() { // GH-90000
        EventStreamStatistics statistics = runPromise(() -> // GH-90000
                preprocessor.calculateStreamStatistics(List.of(), Duration.ofMinutes(5))); // GH-90000

        assertTrue(statistics.isEmpty()); // GH-90000
        assertEquals(0L, statistics.getTotalEvents()); // GH-90000
        assertEquals(0, statistics.getUniqueEventTypes()); // GH-90000
        assertEquals(Duration.ZERO, statistics.getTimeSpan()); // GH-90000
        assertEquals(0.0, statistics.getAverageFrequency()); // GH-90000
        assertEquals(Map.of(), statistics.getEventTypeCounts()); // GH-90000
        assertEquals(Map.of(), statistics.getEventTypeFrequencies()); // GH-90000
        assertNotNull(statistics.getWindowStart()); // GH-90000
        assertNotNull(statistics.getWindowEnd()); // GH-90000
        assertNull(statistics.getMostFrequentEventType()); // GH-90000
    }

    @Test
    @DisplayName("preprocessEvents assembles normalized events statistics and optional temporal features [GH-90000]")
    void preprocessEventsAssemblesNormalizedEventsStatisticsAndOptionalTemporalFeatures() { // GH-90000
        List<ExplorationEvent> events = List.of( // GH-90000
                event("evt-1", "click", instant(0), Map.of("Message", "  HELLO  ")), // GH-90000
                event("evt-2", "click", instant(60), Map.of("Message", "World")) // GH-90000
        );
        PreprocessingConfig config = PreprocessingConfig.builder() // GH-90000
                .extractTemporalFeatures(false) // GH-90000
                .build(); // GH-90000

        PreprocessedEventBatch batch = runPromise(() -> preprocessor.preprocessEvents(events, config)); // GH-90000

        assertEquals(2, batch.getEventCount()); // GH-90000
        assertFalse(batch.isEmpty()); // GH-90000
        assertEquals(2L, batch.getStatistics().getTotalEvents()); // GH-90000
        assertEquals(Map.of(), batch.getTemporalFeatures()); // GH-90000
        assertNotNull(batch.getProcessingTimestamp()); // GH-90000
        assertNotNull(batch.getBatchId()); // GH-90000
        assertEquals("hello", batch.getEvents().get(0).getNormalizedProperties().get("message [GH-90000]"));
    }

    private static ExplorationEvent event(String id, String type, Instant timestamp, Map<String, Object> properties) { // GH-90000
        return new ExplorationEvent(id, type, timestamp, properties, "tenant-1"); // GH-90000
    }

    private static Instant instant(long seconds) { // GH-90000
        return Instant.parse("2026-04-02T00:00:00Z [GH-90000]").plusSeconds(seconds);
    }
}
