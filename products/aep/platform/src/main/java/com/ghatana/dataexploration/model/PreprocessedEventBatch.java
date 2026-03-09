package com.ghatana.dataexploration.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Preprocessed batch of events ready for pattern analysis.
 * 
 * Day 28 Implementation: Container for preprocessed event data with metadata.
 */
public class PreprocessedEventBatch {
    
    private final List<NormalizedEvent> events;
    private final Map<String, TemporalFeatures> temporalFeatures;
    private final EventStreamStatistics statistics;
    private final Instant processingTimestamp;
    private final String batchId;
    
    public PreprocessedEventBatch(List<NormalizedEvent> events, 
                                Map<String, TemporalFeatures> temporalFeatures,
                                EventStreamStatistics statistics,
                                Instant processingTimestamp,
                                String batchId) {
        this.events = events;
        this.temporalFeatures = temporalFeatures;
        this.statistics = statistics;
        this.processingTimestamp = processingTimestamp;
        this.batchId = batchId;
    }
    
    public List<NormalizedEvent> getEvents() {
        return events;
    }
    
    public Map<String, TemporalFeatures> getTemporalFeatures() {
        return temporalFeatures;
    }
    
    public EventStreamStatistics getStatistics() {
        return statistics;
    }
    
    public Instant getProcessingTimestamp() {
        return processingTimestamp;
    }
    
    public String getBatchId() {
        return batchId;
    }
    
    public int getEventCount() {
        return events.size();
    }
    
    public boolean isEmpty() {
        return events.isEmpty();
    }
}