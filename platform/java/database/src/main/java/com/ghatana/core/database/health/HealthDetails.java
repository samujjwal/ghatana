package com.ghatana.core.database.health;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable value object containing additional diagnostic details for health status.
 *
 * <p><b>Purpose</b><br>
 * Provides structured key-value diagnostic payload for health checks including
 * database version, connection pool metrics, performance counters, and configuration.
 * Enables rich health endpoint responses for monitoring and debugging.
 *
 * <p><b>Architecture Role</b><br>
 * Value object in core/database/health for diagnostic payload.
 * Used by:
 * - HealthStatus - Attach diagnostic details to health results
 * - DatabaseHealthCheck - Populate with connection pool stats
 * - Health Endpoints - Expose detailed health metrics
 * - Monitoring Dashboards - Display diagnostic information
 *
 * <p><b>Common Detail Keys</b><br>
 * - <b>database</b>: Database type (PostgreSQL, MySQL, Oracle)
 * - <b>version</b>: Database server version
 * - <b>driver</b>: JDBC driver version
 * - <b>connections_active</b>: Active connection count
 * - <b>connections_idle</b>: Idle connection count
 * - <b>connections_max</b>: Maximum pool size
 * - <b>query_time_ms</b>: Validation query execution time
 * - <b>connection_timeout_ms</b>: Configured timeout
 * - <b>schema</b>: Current database schema
 * - <b>catalog</b>: Current database catalog
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Build details for healthy status
 * HealthDetails details = HealthDetails.builder()
 *     .addDetail("database", "PostgreSQL")
 *     .addDetail("version", "14.5")
 *     .addDetail("driver", "postgresql-42.5.1.jar")
 *     .addDetail("connections_active", 15)
 *     .addDetail("connections_idle", 5)
 *     .addDetail("connections_max", 100)
 *     .addDetail("query_time_ms", 25)
 *     .addDetail("schema", "public")
 *     .build();
 * 
 * HealthStatus status = HealthStatus.healthy(
 *     "Database connection validated",
 *     Duration.ofMillis(25),
 *     details
 * );
 * 
 * // Retrieve details
 * String dbType = details.getDetailAsString("database");
 * Integer activeConns = (Integer) details.getDetail("connections_active");
 * 
 * // Check detail existence
 * if (details.hasDetail("connections_max")) {
 *     int maxConns = (Integer) details.getDetail("connections_max");
 *     int activeConns = (Integer) details.getDetail("connections_active");
 *     double utilization = (double) activeConns / maxConns;
 *     if (utilization > 0.8) {
 *         logger.warn("Connection pool utilization high: {}%", utilization * 100);
 *     }
 * }
 * 
 * // Add performance metrics
 * HealthDetails perfDetails = HealthDetails.builder()
 *     .addDetail("queries_per_second", 1250)
 *     .addDetail("avg_query_time_ms", 12.5)
 *     .addDetail("p95_query_time_ms", 45)
 *     .addDetail("p99_query_time_ms", 120)
 *     .addDetail("slow_queries_count", 3)
 *     .addDetail("locks_waiting", 0)
 *     .build();
 * }</pre>
 *
 * <p><b>JSON Serialization Example</b><br>
 * <pre>
 * {
 *   "database": "PostgreSQL",
 *   "version": "14.5",
 *   "connections": {
 *     "active": 15,
 *     "idle": 5,
 *     "max": 100,
 *     "utilization": 0.15
 *   },
 *   "performance": {
 *     "query_time_ms": 25,
 *     "avg_query_time_ms": 12.5,
 *     "p95_query_time_ms": 45
 *   }
 * }
 * </pre>
 *
 * <p><b>Builder Pattern</b><br>
 * Use builder for fluent construction with method chaining:
 * <pre>{@code
 * HealthDetails details = HealthDetails.builder()
 *     .addDetail("key1", "value1")
 *     .addDetail("key2", 123)
 *     .addDetail("key3", true)
 *     .build(); // Returns immutable instance
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object with unmodifiable map. Safe to share across threads.
 *
 * @see HealthStatus
 * @see DatabaseHealthCheck
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Diagnostic details value object for health status
 * @doc.layer core
 * @doc.pattern Value Object
 */
public final class HealthDetails {
    
    private final Map<String, Object> details;
    
    private HealthDetails(Map<String, Object> details) {
        this.details = Collections.unmodifiableMap(new HashMap<>(details));
    }
    
    /**
     * Gets all health details.
     * 
     * @return Immutable map of health details
     */
    public Map<String, Object> getDetails() {
        return details;
    }
    
    /**
     * Gets a specific detail value.
     * 
     * @param key The detail key
     * @return The detail value, or null if not present
     */
    public Object getDetail(String key) {
        return details.get(key);
    }
    
    /**
     * Gets a specific detail value as a string.
     * 
     * @param key The detail key
     * @return The detail value as string, or null if not present
     */
    public String getDetailAsString(String key) {
        Object value = details.get(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * Checks if a detail exists.
     * 
     * @param key The detail key
     * @return true if the detail exists
     */
    public boolean hasDetail(String key) {
        return details.containsKey(key);
    }
    
    /**
     * Creates a new builder for HealthDetails.
     * 
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for HealthDetails.
     */
    public static final class Builder {
        private final Map<String, Object> details = new HashMap<>();
        
        private Builder() {}
        
        /**
         * Adds a detail.
         * 
         * @param key The detail key
         * @param value The detail value
         * @return This builder
         */
        public Builder detail(String key, Object value) {
            if (key != null && value != null) {
                details.put(key, value);
            }
            return this;
        }
        
        /**
         * Adds all details from a map.
         * 
         * @param details The details to add
         * @return This builder
         */
        public Builder details(Map<String, Object> details) {
            if (details != null) {
                this.details.putAll(details);
            }
            return this;
        }
        
        /**
         * Builds the HealthDetails instance.
         * 
         * @return A new HealthDetails instance
         */
        public HealthDetails build() {
            return new HealthDetails(details);
        }
    }
    
    @Override
    public String toString() {
        return "HealthDetails{" + details + "}";
    }
}
