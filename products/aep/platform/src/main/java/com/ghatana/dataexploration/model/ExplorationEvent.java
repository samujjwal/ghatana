package com.ghatana.dataexploration.model;

import java.time.Instant;
import java.util.Map;

/**
 * Simple event representation for data preprocessing.
 * This will be replaced with proper protobuf Event in production.
 * 
 * Day 28 Implementation: Temporary event model for preprocessing demo.
 */
public class ExplorationEvent {
    private final String id;
    private final String type;
    private final Instant timestamp;
    private final Map<String, Object> properties;
    private final String tenantId;
    
    public ExplorationEvent(String id, String type, Instant timestamp, 
                      Map<String, Object> properties, String tenantId) {
        this.id = id;
        this.type = type;
        this.timestamp = timestamp;
        this.properties = properties;
        this.tenantId = tenantId;
    }
    
    public String getId() { 
        return id; 
    }
    
    public String getType() { 
        return type; 
    }
    
    public Instant getTimestamp() { 
        return timestamp; 
    }
    
    public Map<String, Object> getProperties() { 
        return properties; 
    }
    
    public String getTenantId() { 
        return tenantId; 
    }
    
    @Override
    public String toString() {
        return String.format("ExplorationEvent{id='%s', type='%s', timestamp=%s}", id, type, timestamp);
    }
}