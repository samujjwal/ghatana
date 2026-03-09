package com.ghatana.validation.ai.detectors;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.validation.ai.AIPatternDetectionService.*;

import java.util.List;

/**
 * Base interface for pattern detection algorithms.
 * Consolidated from event-core pattern detection capabilities.
 
 *
 * @doc.type interface
 * @doc.purpose Pattern detector
 * @doc.layer core
 * @doc.pattern Interface
*/
public interface PatternDetector {
    
    /**
     * Detect patterns in the given events using this detector's algorithm.
     * 
     * @param events Events to analyze for patterns
     * @param config Configuration for pattern analysis
     * @return List of detected patterns
     */
    List<DetectedPattern> detect(List<Event> events, PatternAnalysisConfig config);
    
    /**
     * Get the type of patterns this detector can find.
     * 
     * @return Pattern type
     */
    PatternType getPatternType();
    
    /**
     * Get detector-specific configuration requirements.
     * 
     * @return Map of configuration parameter names to descriptions
     */
    java.util.Map<String, String> getConfigurationRequirements();
}