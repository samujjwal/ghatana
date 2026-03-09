package com.ghatana.core.database.health;

import java.time.Duration;
import java.time.Instant;

/**
 * Immutable value object representing database connection health status with diagnostic details.
 *
 * <p><b>Purpose</b><br>
 * Captures health check results including status (healthy/unhealthy/unknown),
 * response time, error details, and timestamp. Used for monitoring, alerting,
 * and health endpoint reporting.
 *
 * <p><b>Architecture Role</b><br>
 * Value object in core/database/health for health monitoring.
 * Used by:
 * - DatabaseHealthCheck - Returns health check results
 * - Health Endpoints - Expose /health API with database status
 * - Monitoring Systems - Track database availability/latency
 * - Alerting - Trigger alerts on unhealthy status
 * - Dashboards - Display real-time health metrics
 *
 * <p><b>Health States</b><br>
 * - <b>HEALTHY</b>: Database responsive, validation query succeeded
 * - <b>UNHEALTHY</b>: Database unreachable, query failed, or timeout
 * - <b>UNKNOWN</b>: Unable to determine status (partial failure)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Create healthy status
 * HealthDetails details = HealthDetails.builder()
 *     .addDetail("database", "PostgreSQL")
 *     .addDetail("version", "14.5")
 *     .addDetail("connections_active", 15)
 *     .addDetail("connections_max", 100)
 *     .build();
 * 
 * HealthStatus healthy = HealthStatus.healthy(
 *     "Database connection validated",
 *     Duration.ofMillis(25),
 *     details
 * );
 * 
 * // Create unhealthy status
 * HealthStatus unhealthy = HealthStatus.unhealthy(
 *     "Connection timeout",
 *     Duration.ofSeconds(5),
 *     new SQLException("Connection refused")
 * );
 * 
 * // Create unknown status
 * HealthStatus unknown = HealthStatus.unknown(
 *     "Validation query returned unexpected result",
 *     Duration.ofMillis(100)
 * );
 * 
 * // Expose in health endpoint
 * @GetMapping("/health/database")
 * public ResponseEntity<HealthResponse> getDatabaseHealth() {
 *     HealthStatus status = healthCheck.check();
 *     
 *     return ResponseEntity
 *         .status(status.isHealthy() ? 200 : 503)
 *         .body(new HealthResponse(
 *             status.getStatus().name(),
 *             status.getMessage(),
 *             status.getResponseTime().toMillis(),
 *             status.getTimestamp()
 *         ));
 * }
 * 
 * // Check in monitoring loop
 * HealthStatus status = healthCheck.check();
 * if (!status.isHealthy()) {
 *     alerting.sendAlert(
 *         "Database unhealthy: " + status.getMessage(),
 *         status.getException()
 *     );
 *     metrics.recordUnhealthy(status.getResponseTime());
 * } else {
 *     metrics.recordHealthy(status.getResponseTime());
 * }
 * }</pre>
 *
 * <p><b>Response Time Tracking</b><br>
 * - <b>Healthy</b>: Actual query execution time (baseline: 10-50ms)
 * - <b>Unhealthy</b>: Time until failure/timeout (max: configured timeout)
 * - <b>SLO Tracking</b>: Monitor p50/p95/p99 response times for SLA compliance
 *
 * <p><b>Details Payload</b><br>
 * HealthDetails provides additional diagnostic information:
 * - Database type and version
 * - Connection pool statistics
 * - Active/idle connection counts
 * - Query execution plan (for slow queries)
 * - Lock contention metrics
 *
 * <p><b>Health Endpoint Integration</b><br>
 * <pre>
 * GET /actuator/health/database
 * {
 *   "status": "UP",
 *   "details": {
 *     "message": "Database connection validated",
 *     "responseTime": 25,
 *     "timestamp": "2025-11-06T10:30:00Z",
 *     "database": "PostgreSQL",
 *     "version": "14.5",
 *     "connections": {
 *       "active": 15,
 *       "idle": 5,
 *       "max": 100
 *     }
 *   }
 * }
 * </pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object - all fields final. Safe to share across threads.
 *
 * @see DatabaseHealthCheck
 * @see HealthDetails
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Immutable health status value object with diagnostics
 * @doc.layer core
 * @doc.pattern Value Object
 */
public final class HealthStatus {
    
    /**
     * Health status enumeration.
     */
    public enum HealthState {
        HEALTHY,
        UNHEALTHY,
        UNKNOWN
    }
    
    private final HealthState status;
    private final String message;
    private final Duration responseTime;
    private final Instant timestamp;
    private final HealthDetails details;
    private final Throwable exception;
    
    private HealthStatus(HealthState status, String message, Duration responseTime, 
                        HealthDetails details, Throwable exception) {
        this.status = status;
        this.message = message;
        this.responseTime = responseTime;
        this.timestamp = Instant.now();
        this.details = details;
        this.exception = exception;
    }
    
    /**
     * Creates a healthy status.
     * 
     * @param message The status message
     * @param responseTime The response time
     * @param details Additional health details
     * @return Healthy status
     */
    public static HealthStatus healthy(String message, Duration responseTime, HealthDetails details) {
        return new HealthStatus(HealthState.HEALTHY, message, responseTime, details, null);
    }
    
    /**
     * Creates an unhealthy status.
     * 
     * @param message The status message
     * @param responseTime The response time
     * @param exception The exception that caused the unhealthy status
     * @return Unhealthy status
     */
    public static HealthStatus unhealthy(String message, Duration responseTime, Throwable exception) {
        return new HealthStatus(HealthState.UNHEALTHY, message, responseTime, null, exception);
    }
    
    /**
     * Creates an unknown status.
     * 
     * @param message The status message
     * @param responseTime The response time
     * @return Unknown status
     */
    public static HealthStatus unknown(String message, Duration responseTime) {
        return new HealthStatus(HealthState.UNKNOWN, message, responseTime, null, null);
    }
    
    /**
     * Gets the health status.
     * 
     * @return The status
     */
    public HealthState getStatus() {
        return status;
    }
    
    /**
     * Gets the status message.
     * 
     * @return The message
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * Gets the response time.
     * 
     * @return The response time
     */
    public Duration getResponseTime() {
        return responseTime;
    }
    
    /**
     * Gets the timestamp when this status was created.
     * 
     * @return The timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }
    
    /**
     * Gets additional health details.
     * 
     * @return The health details, or null if not available
     */
    public HealthDetails getDetails() {
        return details;
    }
    
    /**
     * Gets the exception that caused the unhealthy status.
     * 
     * @return The exception, or null if not available
     */
    public Throwable getException() {
        return exception;
    }
    
    /**
     * Checks if the status is healthy.
     * 
     * @return true if healthy
     */
    public boolean isHealthy() {
        return status == HealthState.HEALTHY;
    }
    
    /**
     * Checks if the status is unhealthy.
     * 
     * @return true if unhealthy
     */
    public boolean isUnhealthy() {
        return status == HealthState.UNHEALTHY;
    }
    
    @Override
    public String toString() {
        return String.format("HealthStatus{status=%s, message='%s', responseTime=%dms, timestamp=%s}",
                status, message, responseTime.toMillis(), timestamp);
    }
}
