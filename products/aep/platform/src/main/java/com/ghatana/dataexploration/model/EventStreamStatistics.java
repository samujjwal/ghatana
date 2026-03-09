package com.ghatana.dataexploration.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Statistical metrics for event stream analysis.
 * 
 * Day 28 Implementation: Stream statistics for pattern exploration.
 */
public class EventStreamStatistics {
    
    private final long totalEvents;
    private final int uniqueEventTypes;
    private final Duration timeSpan;
    private final double averageFrequency;
    private final Map<String, Long> eventTypeCounts;
    private final Map<String, Double> eventTypeFrequencies;
    private final Instant windowStart;
    private final Instant windowEnd;
    private final double entropy; // Shannon entropy of event type distribution
    
    public EventStreamStatistics(long totalEvents, int uniqueEventTypes, Duration timeSpan,
                                double averageFrequency, Map<String, Long> eventTypeCounts,
                                Map<String, Double> eventTypeFrequencies, Instant windowStart,
                                Instant windowEnd, double entropy) {
        this.totalEvents = totalEvents;
        this.uniqueEventTypes = uniqueEventTypes;
        this.timeSpan = timeSpan;
        this.averageFrequency = averageFrequency;
        this.eventTypeCounts = eventTypeCounts;
        this.eventTypeFrequencies = eventTypeFrequencies;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.entropy = entropy;
    }
    
    public long getTotalEvents() {
        return totalEvents;
    }
    
    public int getUniqueEventTypes() {
        return uniqueEventTypes;
    }
    
    public Duration getTimeSpan() {
        return timeSpan;
    }
    
    public double getAverageFrequency() {
        return averageFrequency;
    }
    
    public Map<String, Long> getEventTypeCounts() {
        return eventTypeCounts;
    }
    
    public Map<String, Double> getEventTypeFrequencies() {
        return eventTypeFrequencies;
    }
    
    public Instant getWindowStart() {
        return windowStart;
    }
    
    public Instant getWindowEnd() {
        return windowEnd;
    }
    
    public double getEntropy() {
        return entropy;
    }
    
    public boolean isEmpty() {
        return totalEvents == 0;
    }
    
    public double getDiversity() {
        return totalEvents > 0 ? (double) uniqueEventTypes / totalEvents : 0.0;
    }
    
    public String getMostFrequentEventType() {
        return eventTypeCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private long totalEvents;
        private int uniqueEventTypes;
        private Duration timeSpan;
        private double averageFrequency;
        private Map<String, Long> eventTypeCounts;
        private Map<String, Double> eventTypeFrequencies;
        private Instant windowStart;
        private Instant windowEnd;
        private double entropy;
        
        public Builder totalEvents(long totalEvents) {
            this.totalEvents = totalEvents;
            return this;
        }
        
        public Builder uniqueEventTypes(int uniqueEventTypes) {
            this.uniqueEventTypes = uniqueEventTypes;
            return this;
        }
        
        public Builder timeSpan(Duration timeSpan) {
            this.timeSpan = timeSpan;
            return this;
        }
        
        public Builder averageFrequency(double averageFrequency) {
            this.averageFrequency = averageFrequency;
            return this;
        }
        
        public Builder eventTypeCounts(Map<String, Long> eventTypeCounts) {
            this.eventTypeCounts = eventTypeCounts;
            return this;
        }
        
        public Builder eventTypeFrequencies(Map<String, Double> eventTypeFrequencies) {
            this.eventTypeFrequencies = eventTypeFrequencies;
            return this;
        }
        
        public Builder windowStart(Instant windowStart) {
            this.windowStart = windowStart;
            return this;
        }
        
        public Builder windowEnd(Instant windowEnd) {
            this.windowEnd = windowEnd;
            return this;
        }
        
        public Builder entropy(double entropy) {
            this.entropy = entropy;
            return this;
        }
        
        public EventStreamStatistics build() {
            return new EventStreamStatistics(totalEvents, uniqueEventTypes, timeSpan, 
                                           averageFrequency, eventTypeCounts, eventTypeFrequencies,
                                           windowStart, windowEnd, entropy);
        }
    }
}