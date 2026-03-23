package com.ghatana.validation.ai;

import com.ghatana.validation.ai.anomaly.DefaultAnomalyDetector;
import com.ghatana.validation.ai.detectors.CorrelationPatternDetector;
import com.ghatana.validation.ai.detectors.FrequencyPatternDetector;
import com.ghatana.validation.ai.detectors.SequencePatternDetector;
import com.ghatana.validation.ai.detectors.TemporalPatternDetector;
import com.ghatana.validation.ai.model.DetectedPattern;
import com.ghatana.validation.ai.model.PatternSuggestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for AEP Platform Analytics module.
 *
 * @doc.type class
 * @doc.purpose Smoke tests for analytics module classes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Platform Analytics — Smoke Tests")
class AnalyticsSmokeTest {

    @Test
    @DisplayName("DefaultAnomalyDetector can be instantiated")
    void defaultAnomalyDetectorInstantiates() {
        var detector = new DefaultAnomalyDetector();
        assertNotNull(detector);
    }

    @Test
    @DisplayName("DetectedPattern can be constructed and fields accessed")
    void detectedPatternFields() {
        var pattern = new DetectedPattern(
                "pattern-1", "Test Pattern", "sequence",
                "A test pattern", 0.85, Map.of("key", "value"));
        assertEquals("pattern-1", pattern.getId());
        assertEquals("Test Pattern", pattern.getName());
        assertEquals("sequence", pattern.getPatternType());
        assertEquals(0.85, pattern.getConfidence());
    }

    @Test
    @DisplayName("PatternSuggestion can be constructed and fields accessed")
    void patternSuggestionFields() {
        var suggestion = new PatternSuggestion(
                "suggest-1", "Correlation Suggestion", "Consider correlation pattern",
                0.75, Map.of(), "correlation", "High correlation detected",
                Map.of("score", 0.8));
        assertEquals("suggest-1", suggestion.getId());
        assertEquals(0.75, suggestion.getConfidenceScore());
        assertEquals("correlation", suggestion.getPatternType());
    }

    @Test
    @DisplayName("SequencePatternDetector can be instantiated")
    void sequencePatternDetectorInstantiates() {
        var detector = new SequencePatternDetector();
        assertNotNull(detector);
    }

    @Test
    @DisplayName("FrequencyPatternDetector can be instantiated")
    void frequencyPatternDetectorInstantiates() {
        var detector = new FrequencyPatternDetector();
        assertNotNull(detector);
    }

    @Test
    @DisplayName("CorrelationPatternDetector can be instantiated")
    void correlationPatternDetectorInstantiates() {
        var detector = new CorrelationPatternDetector();
        assertNotNull(detector);
    }

    @Test
    @DisplayName("TemporalPatternDetector can be instantiated")
    void temporalPatternDetectorInstantiates() {
        var detector = new TemporalPatternDetector();
        assertNotNull(detector);
    }
}
