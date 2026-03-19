package com.ghatana.kernel.health;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the health status of a kernel component.
 *
 * <p>Health status provides information about the operational state of kernel
 * modules, plugins, and extensions. It includes overall status, detailed checks,
 * and diagnostic information.</p>
 *
 * @doc.type class
 * @doc.purpose Health status representation for kernel component monitoring
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class HealthStatus {

    private final Status status;
    private final String message;
    private final Instant timestamp;
    private final Map<String, HealthCheck> checks;

    /**
     * Creates a health status.
     *
     * @param status the overall health status
     * @param message human-readable status message
     * @param timestamp when the status was recorded
     * @param checks detailed health checks
     */
    private HealthStatus(Status status, String message, Instant timestamp, Map<String, HealthCheck> checks) {
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.message = message != null ? message : "";
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.checks = checks != null ? Collections.unmodifiableMap(new HashMap<>(checks)) : Collections.emptyMap();
    }

    /**
     * Creates a healthy status.
     *
     * @return healthy status
     */
    public static HealthStatus healthy() {
        return new HealthStatus(Status.HEALTHY, "Component is healthy", Instant.now(), Collections.emptyMap());
    }

    /**
     * Creates a healthy status with message.
     *
     * @param message status message
     * @return healthy status
     */
    public static HealthStatus healthy(String message) {
        return new HealthStatus(Status.HEALTHY, message, Instant.now(), Collections.emptyMap());
    }

    /**
     * Creates an unhealthy status.
     *
     * @param message error message
     * @return unhealthy status
     */
    public static HealthStatus unhealthy(String message) {
        return new HealthStatus(Status.UNHEALTHY, message, Instant.now(), Collections.emptyMap());
    }

    /**
     * Creates a degraded status.
     *
     * @param message status message
     * @return degraded status
     */
    public static HealthStatus degraded(String message) {
        return new HealthStatus(Status.DEGRADED, message, Instant.now(), Collections.emptyMap());
    }

    /**
     * Creates a builder for constructing complex health status.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, HealthCheck> getChecks() { return checks; }

    /**
     * Checks if the status is healthy.
     *
     * @return true if healthy
     */
    public boolean isHealthy() {
        return status == Status.HEALTHY;
    }

    /**
     * Checks if the status is unhealthy.
     *
     * @return true if unhealthy
     */
    public boolean isUnhealthy() {
        return status == Status.UNHEALTHY;
    }

    /**
     * Checks if the status is degraded.
     *
     * @return true if degraded
     */
    public boolean isDegraded() {
        return status == Status.DEGRADED;
    }

    /**
     * Gets a specific health check.
     *
     * @param name the check name
     * @return the health check or null
     */
    public HealthCheck getCheck(String name) {
        return checks.get(name);
    }

    @Override
    public String toString() {
        return String.format("HealthStatus{status=%s, message='%s', timestamp=%s, checks=%d}",
            status, message, timestamp, checks.size());
    }

    /**
     * Health status levels.
     */
    public enum Status {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }

    /**
     * Individual health check result.
     */
    public static class HealthCheck {
        private final String name;
        private final Status status;
        private final String message;
        private final long responseTimeMs;

        public HealthCheck(String name, Status status, String message, long responseTimeMs) {
            this.name = Objects.requireNonNull(name);
            this.status = Objects.requireNonNull(status);
            this.message = message != null ? message : "";
            this.responseTimeMs = responseTimeMs;
        }

        public String getName() { return name; }
        public Status getStatus() { return status; }
        public String getMessage() { return message; }
        public long getResponseTimeMs() { return responseTimeMs; }

        public boolean isHealthy() { return status == Status.HEALTHY; }
    }

    /**
     * Builder for constructing health status.
     */
    public static class Builder {
        private Status status = Status.UNKNOWN;
        private String message = "";
        private Instant timestamp = Instant.now();
        private Map<String, HealthCheck> checks = new HashMap<>();

        public Builder withStatus(Status status) {
            this.status = status;
            return this;
        }

        public Builder withMessage(String message) {
            this.message = message;
            return this;
        }

        public Builder withTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder withCheck(String name, Status status, String message, long responseTimeMs) {
            this.checks.put(name, new HealthCheck(name, status, message, responseTimeMs));
            return this;
        }

        public HealthStatus build() {
            return new HealthStatus(status, message, timestamp, checks);
        }
    }
}
