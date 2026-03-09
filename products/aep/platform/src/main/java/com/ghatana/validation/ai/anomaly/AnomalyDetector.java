package com.ghatana.validation.ai.anomaly;

import com.ghatana.platform.domain.domain.event.Event;

import java.util.List;
import java.util.Map;

/**
 * Interface for anomaly detection algorithms.
 * Consolidated from event-core anomaly detection capabilities.
 
 *
 * @doc.type interface
 * @doc.purpose Anomaly detector
 * @doc.layer core
 * @doc.pattern Interface
*/
public interface AnomalyDetector {
    
    /**
     * Detect anomalies in the given events.
     * 
     * @param events Events to analyze for anomalies
     * @param config Configuration for anomaly detection
     * @return List of detected anomalies
     */
    ValidationAnomalyDetectionResult detectAnomalies(List<Event> events, ValidationAnomalyDetectionConfig config);
    
    /**
     * Get model metrics and statistics.
     * 
     * @return Map of metric names to values
     */
    Map<String, Object> getModelMetrics();
    
    /**
     * Update the baseline model with new events.
     * 
     * @param events Events to use for updating the baseline
     */
    void updateBaseline(List<Event> events);
    
    /**
     * Get the supported anomaly detection algorithms.
     * 
     * @return List of algorithm names
     */
    List<String> getSupportedAlgorithms();
}