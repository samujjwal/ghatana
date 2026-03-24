package com.ghatana.yappc.plugin;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the health status of a plugin.
 * 
 * <p>Used for monitoring and diagnostics to determine if a plugin is
 * functioning correctly.
 * 
 * @doc.type class
 * @doc.purpose Plugin health status
 
 * @doc.layer core
 * @doc.pattern Enum
*/
public final class PluginHealth {
    
    private final HealthStatus status;
    private final String message;
    private final Map<String, Object> details;
    private final Instant timestamp;
    
    private PluginHealth(Builder builder) {
        this.status = builder.status;
        this.message = builder.message;
        this.details = Collections.unmodifiableMap(new HashMap<>(builder.details));
        this.timestamp = Instant.now();
    }
    
    public HealthStatus getStatus() {
        return status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Map<String, Object> getDetails() {
        return details;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public boolean isHealthy() {
        return status == HealthStatus.HEALTHY;
    }
    
    /**
     * Creates a healthy status.
     */
    public static PluginHealth healthy() {
        return builder().status(HealthStatus.HEALTHY).message("Plugin is healthy").build();
    }
    
    /**
     * Creates a healthy status with message.
     */
    public static PluginHealth healthy(String message) {
        return builder().status(HealthStatus.HEALTHY).message(message).build();
    }
    
    /**
     * Creates a degraded status.
     */
    public static PluginHealth degraded(String message) {
        return builder().status(HealthStatus.DEGRADED).message(message).build();
    }
    
    /**
     * Creates an unhealthy status.
     */
    public static PluginHealth unhealthy(String message) {
        return builder().status(HealthStatus.UNHEALTHY).message(message).build();
    }
    
    /**
     * Creates an unhealthy status with error.
     */
    public static PluginHealth unhealthy(String message, Throwable error) {
        return builder()
            .status(HealthStatus.UNHEALTHY)
            .message(message)
            .addDetail("error", error.getMessage())
            .addDetail("errorType", error.getClass().getName())
            .build();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private HealthStatus status = HealthStatus.HEALTHY;
        private String message = "";
        private Map<String, Object> details = new HashMap<>();
        
        public Builder status(HealthStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }
        
        public Builder addDetail(String key, Object value) {
            this.details.put(key, value);
            return this;
        }
        
        public PluginHealth build() {
            return new PluginHealth(this);
        }
    }
    
    /**
     * Health status enumeration.
     */
    public enum HealthStatus {
        /** Plugin is fully operational */
        HEALTHY,
        
        /** Plugin is operational but with reduced functionality */
        DEGRADED,
        
        /** Plugin is not operational */
        UNHEALTHY
    }
}
