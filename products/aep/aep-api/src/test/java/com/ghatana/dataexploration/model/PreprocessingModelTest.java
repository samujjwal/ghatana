package com.ghatana.dataexploration.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Preprocessing Model Tests [GH-90000]")
class PreprocessingModelTest {

    @Test
    @DisplayName("PreprocessingConfig builder provides documented defaults [GH-90000]")
    void preprocessingConfigBuilderProvidesDocumentedDefaults() { // GH-90000
        PreprocessingConfig config = PreprocessingConfig.builder().build(); // GH-90000

        assertEquals(Duration.ofMinutes(10), config.getTimeWindow()); // GH-90000
        assertEquals(0.7, config.getMinConfidence()); // GH-90000
        assertEquals(1000, config.getMaxEvents()); // GH-90000
        assertTrue(config.isNormalizeTimestamps()); // GH-90000
        assertTrue(config.isExtractTemporalFeatures()); // GH-90000
        assertEquals(Map.of(), config.getCustomParameters()); // GH-90000
    }

    @Test
    @DisplayName("PreprocessingConfig builder applies overrides [GH-90000]")
    void preprocessingConfigBuilderAppliesOverrides() { // GH-90000
        Map<String, Object> customParameters = Map.of("mode", "strict"); // GH-90000

        PreprocessingConfig config = PreprocessingConfig.builder() // GH-90000
                .timeWindow(Duration.ofMinutes(2)) // GH-90000
                .minConfidence(0.9) // GH-90000
                .maxEvents(250) // GH-90000
                .normalizeTimestamps(false) // GH-90000
                .extractTemporalFeatures(false) // GH-90000
                .customParameters(customParameters) // GH-90000
                .build(); // GH-90000

        assertEquals(Duration.ofMinutes(2), config.getTimeWindow()); // GH-90000
        assertEquals(0.9, config.getMinConfidence()); // GH-90000
        assertEquals(250, config.getMaxEvents()); // GH-90000
        assertFalse(config.isNormalizeTimestamps()); // GH-90000
        assertFalse(config.isExtractTemporalFeatures()); // GH-90000
        assertEquals(customParameters, config.getCustomParameters()); // GH-90000
    }

    @Test
    @DisplayName("PreprocessedEventBatch exposes convenience accessors [GH-90000]")
    void preprocessedEventBatchExposesConvenienceAccessors() { // GH-90000
        PreprocessedEventBatch nonEmptyBatch = new PreprocessedEventBatch( // GH-90000
                List.of(new NormalizedEvent( // GH-90000
                        "evt-1",
                        "click",
                        Instant.parse("2026-04-02T00:00:00Z [GH-90000]"),
                        Map.of("page", "home"), // GH-90000
                        "tenant-1",
                        1.0
                )),
                Map.of(), // GH-90000
                EventStreamStatistics.builder().totalEvents(1).build(), // GH-90000
                Instant.parse("2026-04-02T00:05:00Z [GH-90000]"),
                "batch-1"
        );
        PreprocessedEventBatch emptyBatch = new PreprocessedEventBatch( // GH-90000
                List.of(), // GH-90000
                Map.of(), // GH-90000
                EventStreamStatistics.builder().totalEvents(0).build(), // GH-90000
                Instant.parse("2026-04-02T00:05:00Z [GH-90000]"),
                "batch-2"
        );

        assertEquals(1, nonEmptyBatch.getEventCount()); // GH-90000
        assertFalse(nonEmptyBatch.isEmpty()); // GH-90000
        assertEquals(0, emptyBatch.getEventCount()); // GH-90000
        assertTrue(emptyBatch.isEmpty()); // GH-90000
    }
}
