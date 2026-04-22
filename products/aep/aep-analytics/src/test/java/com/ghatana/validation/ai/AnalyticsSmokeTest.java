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
@DisplayName("Platform Analytics — Smoke Tests [GH-90000]")
class AnalyticsSmokeTest {

    @Test
    @DisplayName("DefaultAnomalyDetector can be instantiated [GH-90000]")
    void defaultAnomalyDetectorInstantiates() { // GH-90000
        var detector = new DefaultAnomalyDetector(); // GH-90000
        assertNotNull(detector); // GH-90000
    }

    @Test
    @DisplayName("DetectedPattern can be constructed and fields accessed [GH-90000]")
    void detectedPatternFields() { // GH-90000
        var pattern = new DetectedPattern( // GH-90000
                "pattern-1", "Test Pattern", "sequence",
                "A test pattern", 0.85, Map.of("key", "value")); // GH-90000
        assertEquals("pattern-1", pattern.getId()); // GH-90000
        assertEquals("Test Pattern", pattern.getName()); // GH-90000
        assertEquals("sequence", pattern.getPatternType()); // GH-90000
        assertEquals(0.85, pattern.getConfidence()); // GH-90000
    }

    @Test
    @DisplayName("PatternSuggestion can be constructed and fields accessed [GH-90000]")
    void patternSuggestionFields() { // GH-90000
        var suggestion = new PatternSuggestion( // GH-90000
                "suggest-1", "Correlation Suggestion", "Consider correlation pattern",
                0.75, Map.of(), "correlation", "High correlation detected", // GH-90000
                Map.of("score", 0.8)); // GH-90000
        assertEquals("suggest-1", suggestion.getId()); // GH-90000
        assertEquals(0.75, suggestion.getConfidenceScore()); // GH-90000
        assertEquals("correlation", suggestion.getPatternType()); // GH-90000
    }

    @Test
    @DisplayName("SequencePatternDetector can be instantiated [GH-90000]")
    void sequencePatternDetectorInstantiates() { // GH-90000
        var detector = new SequencePatternDetector(); // GH-90000
        assertNotNull(detector); // GH-90000
    }

    @Test
    @DisplayName("FrequencyPatternDetector can be instantiated [GH-90000]")
    void frequencyPatternDetectorInstantiates() { // GH-90000
        var detector = new FrequencyPatternDetector(); // GH-90000
        assertNotNull(detector); // GH-90000
    }

    @Test
    @DisplayName("CorrelationPatternDetector can be instantiated [GH-90000]")
    void correlationPatternDetectorInstantiates() { // GH-90000
        var detector = new CorrelationPatternDetector(); // GH-90000
        assertNotNull(detector); // GH-90000
    }

    @Test
    @DisplayName("TemporalPatternDetector can be instantiated [GH-90000]")
    void temporalPatternDetectorInstantiates() { // GH-90000
        var detector = new TemporalPatternDetector(); // GH-90000
        assertNotNull(detector); // GH-90000
    }
}
