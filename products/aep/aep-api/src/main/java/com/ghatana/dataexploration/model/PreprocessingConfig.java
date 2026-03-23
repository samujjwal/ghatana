package com.ghatana.dataexploration.model;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration for data preprocessing operations.
 * 
 * Day 28 Implementation: Configurable preprocessing parameters for pattern exploration.
 */
public class PreprocessingConfig {
    
    private final Duration timeWindow;
    private final double minConfidence;
    private final int maxEvents;
    private final boolean normalizeTimestamps;
    private final boolean extractTemporalFeatures;
    private final Map<String, Object> customParameters;
    
    public PreprocessingConfig(Duration timeWindow, double minConfidence, int maxEvents, 
                             boolean normalizeTimestamps, boolean extractTemporalFeatures,
                             Map<String, Object> customParameters) {
        this.timeWindow = timeWindow;
        this.minConfidence = minConfidence;
        this.maxEvents = maxEvents;
        this.normalizeTimestamps = normalizeTimestamps;
        this.extractTemporalFeatures = extractTemporalFeatures;
        this.customParameters = customParameters;
    }
    
    public Duration getTimeWindow() {
        return timeWindow;
    }
    
    public double getMinConfidence() {
        return minConfidence;
    }
    
    public int getMaxEvents() {
        return maxEvents;
    }
    
    public boolean isNormalizeTimestamps() {
        return normalizeTimestamps;
    }
    
    public boolean isExtractTemporalFeatures() {
        return extractTemporalFeatures;
    }
    
    public Map<String, Object> getCustomParameters() {
        return customParameters;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Duration timeWindow = Duration.ofMinutes(10);
        private double minConfidence = 0.7;
        private int maxEvents = 1000;
        private boolean normalizeTimestamps = true;
        private boolean extractTemporalFeatures = true;
        private Map<String, Object> customParameters = Map.of();
        
        public Builder timeWindow(Duration timeWindow) {
            this.timeWindow = timeWindow;
            return this;
        }
        
        public Builder minConfidence(double minConfidence) {
            this.minConfidence = minConfidence;
            return this;
        }
        
        public Builder maxEvents(int maxEvents) {
            this.maxEvents = maxEvents;
            return this;
        }
        
        public Builder normalizeTimestamps(boolean normalizeTimestamps) {
            this.normalizeTimestamps = normalizeTimestamps;
            return this;
        }
        
        public Builder extractTemporalFeatures(boolean extractTemporalFeatures) {
            this.extractTemporalFeatures = extractTemporalFeatures;
            return this;
        }
        
        public Builder customParameters(Map<String, Object> customParameters) {
            this.customParameters = customParameters;
            return this;
        }
        
        public PreprocessingConfig build() {
            return new PreprocessingConfig(timeWindow, minConfidence, maxEvents, 
                                         normalizeTimestamps, extractTemporalFeatures, customParameters);
        }
    }
}