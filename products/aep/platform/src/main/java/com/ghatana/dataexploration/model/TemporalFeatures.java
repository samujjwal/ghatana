package com.ghatana.dataexploration.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Temporal features extracted from event streams for pattern correlation.
 * 
 * Day 28 Implementation: Time-based features for correlated group mining.
 */
public class TemporalFeatures {
    
    private final String eventType;
    private final Duration averageInterval;
    private final Duration medianInterval;
    private final long eventCount;
    private final Instant firstOccurrence;
    private final Instant lastOccurrence;
    private final double frequency; // events per minute
    private final Map<String, Double> periodicityScores;
    private final double burstiness; // coefficient of variation for intervals
    
    public TemporalFeatures(String eventType, Duration averageInterval, Duration medianInterval,
                           long eventCount, Instant firstOccurrence, Instant lastOccurrence,
                           double frequency, Map<String, Double> periodicityScores, double burstiness) {
        this.eventType = eventType;
        this.averageInterval = averageInterval;
        this.medianInterval = medianInterval;
        this.eventCount = eventCount;
        this.firstOccurrence = firstOccurrence;
        this.lastOccurrence = lastOccurrence;
        this.frequency = frequency;
        this.periodicityScores = periodicityScores;
        this.burstiness = burstiness;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public Duration getAverageInterval() {
        return averageInterval;
    }
    
    public Duration getMedianInterval() {
        return medianInterval;
    }
    
    public long getEventCount() {
        return eventCount;
    }
    
    public Instant getFirstOccurrence() {
        return firstOccurrence;
    }
    
    public Instant getLastOccurrence() {
        return lastOccurrence;
    }
    
    public double getFrequency() {
        return frequency;
    }
    
    public Map<String, Double> getPeriodicityScores() {
        return periodicityScores;
    }
    
    public double getBurstiness() {
        return burstiness;
    }
    
    public Duration getTimeSpan() {
        return Duration.between(firstOccurrence, lastOccurrence);
    }
    
    public boolean isRegular() {
        return burstiness < 0.5; // Low coefficient of variation indicates regularity
    }
    
    public boolean isBursty() {
        return burstiness > 1.5; // High coefficient of variation indicates burstiness
    }
}