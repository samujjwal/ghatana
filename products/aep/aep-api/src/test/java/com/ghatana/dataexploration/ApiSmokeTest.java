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
@DisplayName("Platform API — Smoke Tests [GH-90000]")
class ApiSmokeTest {

    @Test
    @DisplayName("BasicDataPreprocessor can be instantiated [GH-90000]")
    void basicDataPreprocessorInstantiates() { // GH-90000
        var preprocessor = new BasicDataPreprocessor(); // GH-90000
        assertNotNull(preprocessor); // GH-90000
    }

    @Test
    @DisplayName("ExplorationEvent can be constructed and fields accessed [GH-90000]")
    void explorationEventFields() { // GH-90000
        var now = Instant.now(); // GH-90000
        var event = new ExplorationEvent("ev-1", "click", now, Map.of("page", "/home"), "tenant-1"); // GH-90000
        assertEquals("ev-1", event.getId()); // GH-90000
        assertEquals("click", event.getType()); // GH-90000
        assertEquals(now, event.getTimestamp()); // GH-90000
        assertEquals("tenant-1", event.getTenantId()); // GH-90000
    }

    @Test
    @DisplayName("NormalizedEvent can be constructed and fields accessed [GH-90000]")
    void normalizedEventFields() { // GH-90000
        var now = Instant.now(); // GH-90000
        var event = new NormalizedEvent("nev-1", "click", now, Map.of("page", "/home"), "tenant-1", 0.95); // GH-90000
        assertEquals("nev-1", event.getEventId()); // GH-90000
        assertEquals(0.95, event.getConfidence()); // GH-90000
    }

    @Test
    @DisplayName("PreprocessingConfig can be built with defaults [GH-90000]")
    void preprocessingConfigBuilder() { // GH-90000
        var config = new PreprocessingConfig( // GH-90000
                Duration.ofMinutes(5), 0.7, 1000, true, true, Map.of()); // GH-90000
        assertEquals(Duration.ofMinutes(5), config.getTimeWindow()); // GH-90000
        assertEquals(0.7, config.getMinConfidence()); // GH-90000
        assertEquals(1000, config.getMaxEvents()); // GH-90000
    }

    @Test
    @DisplayName("EventStreamStatistics can be constructed [GH-90000]")
    void eventStreamStatisticsFields() { // GH-90000
        var now = Instant.now(); // GH-90000
        var stats = new EventStreamStatistics( // GH-90000
                100L, 5, Duration.ofMinutes(10), 10.0, // GH-90000
                Map.of("click", 50L, "view", 50L), // GH-90000
                Map.of("click", 5.0, "view", 5.0), // GH-90000
                now.minus(Duration.ofMinutes(10)), now, 1.0); // GH-90000
        assertEquals(100L, stats.getTotalEvents()); // GH-90000
        assertEquals(5, stats.getUniqueEventTypes()); // GH-90000
    }

    @Test
    @DisplayName("TemporalFeatures can be constructed [GH-90000]")
    void temporalFeaturesFields() { // GH-90000
        var now = Instant.now(); // GH-90000
        var features = new TemporalFeatures( // GH-90000
                "click", Duration.ofSeconds(30), Duration.ofSeconds(25), // GH-90000
                100L, now.minus(Duration.ofHours(1)), now, // GH-90000
                3.33, Map.of("hourly", 0.9), 0.5); // GH-90000
        assertEquals("click", features.getEventType()); // GH-90000
        assertEquals(100L, features.getEventCount()); // GH-90000
    }

    @Test
    @DisplayName("CorrelatedEventGroup and CorrelationRule can be constructed [GH-90000]")
    void correlatedEventGroupFields() { // GH-90000
        var rule = new CorrelatedEventGroup.CorrelationRule("click", "purchase", 0.8, 1.5); // GH-90000
        assertEquals("click", rule.getAntecedent()); // GH-90000
        assertEquals(0.8, rule.getConfidence()); // GH-90000

        var group = new CorrelatedEventGroup( // GH-90000
                Set.of("click", "purchase"), 0.85, 0.3, // GH-90000
                Map.of("click-purchase", 0.85), List.of(rule), "group-1"); // GH-90000
        assertEquals(0.85, group.getConfidence()); // GH-90000
        assertEquals("group-1", group.getGroupId()); // GH-90000
    }
}
