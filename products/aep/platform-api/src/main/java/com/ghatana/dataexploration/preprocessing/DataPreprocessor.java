package com.ghatana.dataexploration.preprocessing;

import com.ghatana.dataexploration.model.CorrelatedEventGroup;
import com.ghatana.dataexploration.model.EventStreamStatistics;
import com.ghatana.dataexploration.model.NormalizedEvent;
import com.ghatana.dataexploration.model.PreprocessedEventBatch;
import com.ghatana.dataexploration.model.PreprocessingConfig;
import com.ghatana.dataexploration.model.ExplorationEvent;
import com.ghatana.dataexploration.model.TemporalFeatures;
import io.activej.promise.Promise;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for preprocessing event data to extract patterns and prepare for group mining.
 * 
 * Day 28 Implementation: Data preprocessing for pattern exploration and correlated group mining.
 */
public interface DataPreprocessor {
    
    /**
     * Preprocesses a batch of events for pattern analysis.
     * Performs normalization, filtering, and feature extraction.
     * 
     * @param events Raw events to preprocess
     * @param config Preprocessing configuration
     * @return Promise of preprocessed event data
     */
    Promise<PreprocessedEventBatch> preprocessEvents(List<ExplorationEvent> events, PreprocessingConfig config);
    
    /**
     * Extracts temporal features from events for pattern correlation.
     * 
     * @param events Events to analyze
     * @param timeWindow Time window for feature extraction
     * @return Promise of temporal feature map
     */
    Promise<Map<String, TemporalFeatures>> extractTemporalFeatures(List<ExplorationEvent> events, Duration timeWindow);
    
    /**
     * Identifies correlated event types based on co-occurrence patterns.
     * 
     * @param events Events to analyze for correlations
     * @param minConfidence Minimum confidence threshold for correlations
     * @return Promise of correlated event type sets
     */
    Promise<Set<CorrelatedEventGroup>> findCorrelatedEventTypes(List<ExplorationEvent> events, double minConfidence);
    
    /**
     * Normalizes event properties to standardized format for pattern matching.
     * 
     * @param events Events to normalize
     * @return Promise of normalized events
     */
    Promise<List<NormalizedEvent>> normalizeEvents(List<ExplorationEvent> events);
    
    /**
     * Calculates statistical metrics for event stream analysis.
     * 
     * @param events Events to analyze
     * @param analysisWindow Time window for statistical analysis
     * @return Promise of statistical metrics
     */
    Promise<EventStreamStatistics> calculateStreamStatistics(List<ExplorationEvent> events, Duration analysisWindow);
}