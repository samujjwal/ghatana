package com.ghatana.platform.observability.health;

import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Interface for health checks in the EventCloud platform.
 * Supports both synchronous and asynchronous health checks with detailed status information.
 *
 * <p>Provides standardized health checking abstraction for liveness (is service alive?)
 * and readiness (can service handle requests?) probes. Used by {@link HealthCheckRegistry}
 * to aggregate system health status.
 *
 * <h2>Key Concepts:</h2>
 * <ul>
 *   <li><strong>Liveness</strong>: Internal component health (JVM, critical services)</li>
 *   <li><strong>Readiness</strong>: External dependency health (DB, cache, message queue)</li>
 *   <li><strong>Status</strong>: UP (healthy), DOWN (unhealthy), DEGRADED (partial), UNKNOWN</li>
 *   <li><strong>Critical</strong>: Failure prevents service from functioning</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * public class MyServiceHealthCheck implements HealthCheck {
 *     private final MyService service;
 *     
 *     @Override
 *     public String getName() {
 *         return "my-service";
 *     }
 *     
 *     @Override
 *     public boolean isCritical() {
 *         return true; // Required for liveness
 *     }
 *     
 *     @Override
 *     public Promise<HealthCheckResult> check() {
 *         return Promise.ofBlocking(() -> {
 *             if (service.isConnected()) {
 *                 return HealthCheckResult.healthy("Service is connected");
 *             } else {
 *                 return HealthCheckResult.unhealthy("Service is disconnected");
 *             }
 *         });
 *     }
 * }
 * 
 * // Register and use
 * HealthCheckRegistry registry = HealthCheckRegistry.getInstance();
 * registry.register(new MyServiceHealthCheck(service));
 * 
 * // Liveness check (critical components only)
 * registry.liveness()
 *     .whenResult(result -> logger.info("Liveness: {}", result.isHealthy()));
 * 
 * // Readiness check (all components)
 * registry.readiness()
 *     .whenResult(result -> logger.info("Readiness: {}", result.isHealthy()));
 * }</pre>
 *
 * <h2>Best Practices:</h2>
 * - Health checks should complete quickly (<100ms typical, <5s max)
 * - Use {@code isCritical=true} only for components required for liveness
 * - Database/cache checks should be {@code isCritical=false} (readiness only)
 * - Include meaningful details in result (versions, connection counts, etc.)
 * - Avoid expensive operations (count queries, full scans)
 *
 * <h2>Thread Safety:</h2>
 * Implementations MUST be thread-safe (may be called concurrently).
 *
 * @since 1.0.0
 * @doc.type interface
 * @doc.purpose Standardized health checking interface for liveness/readiness probes with ActiveJ Promise support
 * @doc.layer observability
 * @doc.pattern Port (SPI for health check implementations)
 */
public interface HealthCheck {
    
        /**
     * Performs the health check operation.
     * 
     * @return Promise resolving to health check result with status and details
     */
    Promise<HealthCheckResult> check();

    /**
     * Returns the unique name of this health check.
     * Used in health endpoints and logging (e.g., "database", "redis", "kafka").
     * 
     * @return non-null health check name
     */
    String getName();

    /**
     * Returns the maximum time allowed for this check to complete.
     * Default implementation returns 5 seconds.
     * 
     * @return non-null timeout duration
     */
    default Duration getTimeout() {
        return Duration.ofSeconds(5);
    }

    /**
     * Indicates whether this check is critical for service liveness.
     * 
     * <p><strong>Critical checks (true)</strong>:
     * - JVM health (memory, threads)
     * - Internal observability (metrics, tracing)
     * - Core services required for basic operation
     * 
     * <p><strong>Non-critical checks (false)</strong>:
     * - External databases (readiness only)
     * - External caches (readiness only)
     * - Message queues (readiness only)
     * 
     * @return true if check is critical for liveness, false for readiness-only
     */
    boolean isCritical();

    /**
     * Result of a health check execution.
     * Immutable value object capturing status, message, details, timing, and errors.
     * 
     * <h2>Usage Example:</h2>
     * <pre>{@code
     * // Healthy with details
     * HealthCheckResult result = HealthCheckResult.healthy("Connected to database")
     *     .withDetail("connections", 10)
     *     .withDetail("version", "PostgreSQL 14.5");
     * 
     * // Unhealthy with error
     * HealthCheckResult result = HealthCheckResult.unhealthy("Connection timeout")
     *     .withError(new SocketTimeoutException("Read timed out"));
     * 
     * // Degraded (partial functionality)
     * HealthCheckResult result = HealthCheckResult.degraded("Replica lag > 10MB")
     *     .withDetail("lag_bytes", 15_000_000);
     * }</pre>
     * 
     * @layer Infrastructure
     * @category Health
     * @subcategory Result
     * @status Stable
     * @concurrency Immutable (thread-safe)
     * @nullability Non-null status/timestamp, nullable message/error
     */
    class HealthCheckResult {        private final Status status;
        private final String message;
        private final Map<String, Object> details;
        private final Instant timestamp;
        private final Duration duration;
        private final Throwable error;
        
        public HealthCheckResult(Status status, String message, Map<String, Object> details, 
                               Duration duration, Throwable error) {
            this.status = status;
            this.message = message;
            this.details = details != null ? Map.copyOf(details) : Map.of();
            this.timestamp = Instant.now();
            this.duration = duration;
            this.error = error;
        }
        
        /**
         * Creates a healthy result with message only.
         * 
         * @param message description of healthy status
         * @return healthy result with UP status
         */
        public static HealthCheckResult healthy(String message) {
            return healthy(message, Map.of(), Duration.ZERO);
        }
        
        /**
         * Creates a healthy result with message, details, and duration.
         * 
         * @param message description of healthy status
         * @param details additional metadata (e.g., version, connection count)
         * @param duration time taken to execute check
         * @return healthy result with UP status
         */
        public static HealthCheckResult healthy(String message, Map<String, Object> details, Duration duration) {
            return new HealthCheckResult(Status.UP, message, details, duration, null);
        }
        
        /**
         * Creates an unhealthy result with message only.
         * 
         * @param message description of unhealthy status
         * @return unhealthy result with DOWN status
         */
        public static HealthCheckResult unhealthy(String message) {
            return unhealthy(message, Map.of(), Duration.ZERO, null);
        }
        
        /**
         * Creates an unhealthy result with message and error.
         * 
         * @param message description of unhealthy status
         * @param error exception that caused failure
         * @return unhealthy result with DOWN status
         */
        public static HealthCheckResult unhealthy(String message, Throwable error) {
            return unhealthy(message, Map.of(), Duration.ZERO, error);
        }
        
        /**
         * Creates an unhealthy result with full details.
         * 
         * @param message description of unhealthy status
         * @param details additional metadata
         * @param duration time taken to execute check
         * @param error exception that caused failure (nullable)
         * @return unhealthy result with DOWN status
         */
        public static HealthCheckResult unhealthy(String message, Map<String, Object> details, 
                                                Duration duration, Throwable error) {
            return new HealthCheckResult(Status.DOWN, message, details, duration, error);
        }
        
        /**
         * Creates a degraded result indicating partial functionality.
         * 
         * @param message description of degraded status
         * @param details additional metadata
         * @param duration time taken to execute check
         * @return degraded result with DEGRADED status
         */
        public static HealthCheckResult degraded(String message, Map<String, Object> details, Duration duration) {
            return new HealthCheckResult(Status.DEGRADED, message, details, duration, null);
        }
        
        // Getters
        
        /**
         * Returns the health status (UP, DOWN, DEGRADED, UNKNOWN).
         * 
         * @return non-null status
         */
        public Status getStatus() { return status; }
        
        /**
         * Returns the human-readable message describing status.
         * 
         * @return nullable message
         */
        public String getMessage() { return message; }
        
        /**
         * Returns additional metadata about the check result.
         * May include version info, connection counts, error details, etc.
         * 
         * @return non-null (possibly empty) immutable map
         */
        public Map<String, Object> getDetails() { return details; }
        
        /**
         * Returns the timestamp when this result was created.
         * 
         * @return non-null timestamp
         */
        public Instant getTimestamp() { return timestamp; }
        
        /**
         * Returns the time taken to execute the health check.
         * 
         * @return nullable duration
         */
        public Duration getDuration() { return duration; }
        
        /**
         * Returns the error that caused failure (if unhealthy).
         * 
         * @return nullable throwable
         */
        public Throwable getError() { return error; }
        
        /**
         * Convenience method to check if status is UP.
         * 
         * @return true if status is UP, false otherwise
         */
        public boolean isHealthy() {
            return status == Status.UP;
        }
        
        /**
         * Convenience method to check if status is DOWN.
         * 
         * @return true if status is DOWN, false otherwise
         */
        public boolean isUnhealthy() {
            return status == Status.DOWN;
        }
        
        /**
         * Convenience method to check if status is DEGRADED.
         * 
         * @return true if status is DEGRADED, false otherwise
         */
        public boolean isDegraded() {
            return status == Status.DEGRADED;
        }
    }
    
    /**
     * Health status enumeration.
     * 
     * <h2>Status Semantics:</h2>
     * <ul>
     *   <li><strong>UP</strong>: Component is healthy and fully functional</li>
     *   <li><strong>DOWN</strong>: Component is unhealthy and non-functional</li>
     *   <li><strong>DEGRADED</strong>: Component is partially functional (e.g., high latency, replica lag)</li>
     *   <li><strong>UNKNOWN</strong>: Health status cannot be determined (e.g., timeout, exception)</li>
     * </ul>
     * 
     * @layer Infrastructure
     * @category Health
     * @subcategory Status
     * @status Stable
     * @concurrency Immutable (thread-safe enum)
     */
    enum Status {
        UP("UP"),
        DOWN("DOWN"),
        DEGRADED("DEGRADED"),
        UNKNOWN("UNKNOWN");
        
        private final String value;
        
        Status(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        @Override
        public String toString() {
            return value;
        }
    }
}
