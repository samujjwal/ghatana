package com.ghatana.dataexploration.model;

import java.time.Instant;
import java.util.Map;

/**
 * Normalized event with standardized properties for pattern analysis.
 * 
 * Day 28 Implementation: Standardized event representation for correlation analysis.
 */
public class NormalizedEvent {
    
    private final String eventId;
    private final String eventType;
    private final Instant timestamp;
    private final Map<String, Object> normalizedProperties;
    private final String tenantId;
    private final Double confidence;
    
    public NormalizedEvent(String eventId, String eventType, Instant timestamp, 
                          Map<String, Object> normalizedProperties, String tenantId, Double confidence) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.normalizedProperties = normalizedProperties;
        this.tenantId = tenantId;
        this.confidence = confidence;
    }
    
    public String getEventId() {
        return eventId;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public Map<String, Object> getNormalizedProperties() {
        return normalizedProperties;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public Double getConfidence() {
        return confidence;
    }
    
    @Override
    public String toString() {
        return String.format("NormalizedEvent{id='%s', type='%s', timestamp=%s, properties=%d}", 
                           eventId, eventType, timestamp, normalizedProperties.size());
    }
}