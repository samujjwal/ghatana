package com.ghatana.yappc.plugin;

/**
 * Health status of a plugin.
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
    private final String message;
    
    public HealthStatus(boolean healthy, String status, String message) {
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
    
    public static HealthStatus healthy() {
        return new HealthStatus(true, "UP", "Plugin is healthy");
    }
    
    public static HealthStatus unhealthy(String message) {
        return new HealthStatus(false, "DOWN", message);
    }
}
