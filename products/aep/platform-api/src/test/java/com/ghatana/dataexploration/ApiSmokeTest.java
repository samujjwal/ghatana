package com.ghatana.dataexploration;

import com.ghatana.dataexploration.model.CorrelatedEventGroup;
import com.ghatana.dataexploration.model.EventStreamStatistics;
import com.ghatana.dataexploration.model.ExplorationEvent;
import com.ghatana.dataexploration.model.NormalizedEvent;
import com.ghatana.dataexploration.model.PreprocessingConfig;
import com.ghatana.dataexploration.model.TemporalFeatures;
import com.ghatana.dataexploration.preprocessing.impl.BasicDataPreprocessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for AEP Platform API data exploration module.
 *
 * @doc.type class
 * @doc.purpose Smoke tests for data exploration models and preprocessor
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Platform API — Smoke Tests")
class ApiSmokeTest {

    @Test
    @DisplayName("BasicDataPreprocessor can be instantiated")
    void basicDataPreprocessorInstantiates() {
        var preprocessor = new BasicDataPreprocessor();
        assertNotNull(preprocessor);
    }

    @Test
    @DisplayName("ExplorationEvent can be constructed and fields accessed")
    void explorationEventFields() {
        var now = Instant.now();
        var event = new ExplorationEvent("ev-1", "click", now, Map.of("page", "/home"), "tenant-1");
        assertEquals("ev-1", event.getId());
        assertEquals("click", event.getType());
        assertEquals(now, event.getTimestamp());
        assertEquals("tenant-1", event.getTenantId());
    }

    @Test
    @DisplayName("NormalizedEvent can be constructed and fields accessed")
    void normalizedEventFields() {
        var now = Instant.now();
        var event = new NormalizedEvent("nev-1", "click", now, Map.of("page", "/home"), "tenant-1", 0.95);
        assertEquals("nev-1", event.getEventId());
        assertEquals(0.95, event.getConfidence());
    }

    @Test
    @DisplayName("PreprocessingConfig can be built with defaults")
    void preprocessingConfigBuilder() {
        var config = new PreprocessingConfig(
                Duration.ofMinutes(5), 0.7, 1000, true, true, Map.of());
        assertEquals(Duration.ofMinutes(5), config.getTimeWindow());
        assertEquals(0.7, config.getMinConfidence());
        assertEquals(1000, config.getMaxEvents());
    }

    @Test
    @DisplayName("EventStreamStatistics can be constructed")
    void eventStreamStatisticsFields() {
        var now = Instant.now();
        var stats = new EventStreamStatistics(
                100L, 5, Duration.ofMinutes(10), 10.0,
                Map.of("click", 50L, "view", 50L),
                Map.of("click", 5.0, "view", 5.0),
                now.minus(Duration.ofMinutes(10)), now, 1.0);
        assertEquals(100L, stats.getTotalEvents());
        assertEquals(5, stats.getUniqueEventTypes());
    }

    @Test
    @DisplayName("TemporalFeatures can be constructed")
    void temporalFeaturesFields() {
        var now = Instant.now();
        var features = new TemporalFeatures(
                "click", Duration.ofSeconds(30), Duration.ofSeconds(25),
                100L, now.minus(Duration.ofHours(1)), now,
                3.33, Map.of("hourly", 0.9), 0.5);
        assertEquals("click", features.getEventType());
        assertEquals(100L, features.getEventCount());
    }

    @Test
    @DisplayName("CorrelatedEventGroup and CorrelationRule can be constructed")
    void correlatedEventGroupFields() {
        var rule = new CorrelatedEventGroup.CorrelationRule("click", "purchase", 0.8, 1.5);
        assertEquals("click", rule.getAntecedent());
        assertEquals(0.8, rule.getConfidence());

        var group = new CorrelatedEventGroup(
                Set.of("click", "purchase"), 0.85, 0.3,
                Map.of("click-purchase", 0.85), List.of(rule), "group-1");
        assertEquals(0.85, group.getConfidence());
        assertEquals("group-1", group.getGroupId());
    }
}
