package com.ghatana.yappc.client;

import java.time.Instant;
import java.util.Map;

/**
 * Health status of YAPPC system.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles health status operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class HealthStatus {
    
    private final boolean healthy;
    private final String status;
    private final Instant timestamp;
    private final Map<String, ComponentHealth> components;
    
    private HealthStatus(Builder builder) {
        this.healthy = builder.healthy;
        this.status = builder.status;
        this.timestamp = builder.timestamp;
        this.components = Map.copyOf(builder.components);
    }
    
    public boolean isHealthy() {
        return healthy;
    }
    
    public String getStatus() {
        return status;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public Map<String, ComponentHealth> getComponents() {
        return components;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private boolean healthy = true;
        private String status = "UP";
        private Instant timestamp = Instant.now();
        private Map<String, ComponentHealth> components = Map.of();
        
        public Builder healthy(boolean healthy) {
            this.healthy = healthy;
            return this;
        }
        
        public Builder status(String status) {
            this.status = status;
            return this;
        }
        
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder components(Map<String, ComponentHealth> components) {
            this.components = components;
            return this;
        }
        
        public HealthStatus build() {
            return new HealthStatus(this);
        }
    }
    
    public static final class ComponentHealth {
        private final boolean healthy;
        private final String status;
        private final String message;
        
        public ComponentHealth(boolean healthy, String status, String message) {
            this.healthy = healthy;
            this.status = status;
            this.message = message;
        }
        
        public boolean isHealthy() {
            return healthy;
        }
        
        public String getStatus() {
            return status;
        }
        
        public String getMessage() {
            return message;
        }
    }
}
