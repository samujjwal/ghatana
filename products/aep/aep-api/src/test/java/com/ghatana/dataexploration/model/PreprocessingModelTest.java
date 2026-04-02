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

@DisplayName("Preprocessing Model Tests")
class PreprocessingModelTest {

    @Test
    @DisplayName("PreprocessingConfig builder provides documented defaults")
    void preprocessingConfigBuilderProvidesDocumentedDefaults() {
        PreprocessingConfig config = PreprocessingConfig.builder().build();

        assertEquals(Duration.ofMinutes(10), config.getTimeWindow());
        assertEquals(0.7, config.getMinConfidence());
        assertEquals(1000, config.getMaxEvents());
        assertTrue(config.isNormalizeTimestamps());
        assertTrue(config.isExtractTemporalFeatures());
        assertEquals(Map.of(), config.getCustomParameters());
    }

    @Test
    @DisplayName("PreprocessingConfig builder applies overrides")
    void preprocessingConfigBuilderAppliesOverrides() {
        Map<String, Object> customParameters = Map.of("mode", "strict");

        PreprocessingConfig config = PreprocessingConfig.builder()
                .timeWindow(Duration.ofMinutes(2))
                .minConfidence(0.9)
                .maxEvents(250)
                .normalizeTimestamps(false)
                .extractTemporalFeatures(false)
                .customParameters(customParameters)
                .build();

        assertEquals(Duration.ofMinutes(2), config.getTimeWindow());
        assertEquals(0.9, config.getMinConfidence());
        assertEquals(250, config.getMaxEvents());
        assertFalse(config.isNormalizeTimestamps());
        assertFalse(config.isExtractTemporalFeatures());
        assertEquals(customParameters, config.getCustomParameters());
    }

    @Test
    @DisplayName("PreprocessedEventBatch exposes convenience accessors")
    void preprocessedEventBatchExposesConvenienceAccessors() {
        PreprocessedEventBatch nonEmptyBatch = new PreprocessedEventBatch(
                List.of(new NormalizedEvent(
                        "evt-1",
                        "click",
                        Instant.parse("2026-04-02T00:00:00Z"),
                        Map.of("page", "home"),
                        "tenant-1",
                        1.0
                )),
                Map.of(),
                EventStreamStatistics.builder().totalEvents(1).build(),
                Instant.parse("2026-04-02T00:05:00Z"),
                "batch-1"
        );
        PreprocessedEventBatch emptyBatch = new PreprocessedEventBatch(
                List.of(),
                Map.of(),
                EventStreamStatistics.builder().totalEvents(0).build(),
                Instant.parse("2026-04-02T00:05:00Z"),
                "batch-2"
        );

        assertEquals(1, nonEmptyBatch.getEventCount());
        assertFalse(nonEmptyBatch.isEmpty());
        assertEquals(0, emptyBatch.getEventCount());
        assertTrue(emptyBatch.isEmpty());
    }
}