/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.health;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical health status representation for all platform components.
 *
 * <p>Consolidates health reporting across kernel modules, plugins, and infrastructure
 * components into a single, consistent value object. Provides both a rich builder API
 * for complex checks and convenient factory methods for common patterns.
 *
 * <p><b>Basic usage:</b>
 * <pre>{@code
 * // Simple factory methods
 * HealthStatus ok = HealthStatus.healthy("Service operational");
 * HealthStatus bad = HealthStatus.unhealthy("Connection refused");
 *
 * // Plugin-style factory aliases
 * HealthStatus pluginOk = HealthStatus.ok();
 * HealthStatus pluginErr = HealthStatus.error("S3 unreachable", ioException);
 *
 * // Builder pattern for composite checks
 * HealthStatus composite = HealthStatus.builder()
 *     .withStatus(HealthStatus.Status.HEALTHY)
 *     .withCheck("db", HealthStatus.Status.HEALTHY, "Connected", 12)
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Canonical health status value object for platform-wide health reporting
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class HealthStatus {

    private final Status status;
    private final String message;
    private final Instant timestamp;
    private final Map<String, HealthCheck> checks;
    private final Map<String, Object> details;
    private final Throwable exception;

    private HealthStatus(Status status, String message, Instant timestamp,
                         Map<String, HealthCheck> checks, Map<String, Object> details,
                         Throwable exception) {
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.message = message != null ? message : "";
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.checks = checks != null ? Collections.unmodifiableMap(new HashMap<>(checks)) : Collections.emptyMap();
        this.details = details != null ? Collections.unmodifiableMap(new HashMap<>(details)) : Collections.emptyMap();
        this.exception = exception;
    }

    // ─── Factory methods ───────────────────────────────────────────────────────

    /** Creates a healthy status with default message. */
    public static HealthStatus healthy() {
        return new HealthStatus(Status.HEALTHY, "Component is healthy", Instant.now(), null, null, null);
    }

    /** Creates a healthy status with a descriptive message. */
    public static HealthStatus healthy(String message) {
        return new HealthStatus(Status.HEALTHY, message, Instant.now(), null, null, null);
    }

    /** Creates a healthy status with a message and additional detail map. */
    public static HealthStatus healthy(String message, Map<String, Object> details) {
        return new HealthStatus(Status.HEALTHY, message, Instant.now(), null, details, null);
    }

    /** Creates an unhealthy status with an error message. */
    public static HealthStatus unhealthy(String message) {
        return new HealthStatus(Status.UNHEALTHY, message, Instant.now(), null, null, null);
    }

    /** Creates an unhealthy status with an error message and detail map. */
    public static HealthStatus unhealthy(String message, Map<String, Object> details) {
        return new HealthStatus(Status.UNHEALTHY, message, Instant.now(), null, details, null);
    }

    /** Creates an unhealthy status with an error message and causing exception. */
    public static HealthStatus unhealthy(String message, Throwable cause) {
        return new HealthStatus(Status.UNHEALTHY, message, Instant.now(), null, null, cause);
    }

    /** Creates a degraded (partially healthy) status. */
    public static HealthStatus degraded(String message) {
        return new HealthStatus(Status.DEGRADED, message, Instant.now(), null, null, null);
    }

    /** Creates an unknown status. */
    public static HealthStatus unknown(String message) {
        return new HealthStatus(Status.UNKNOWN, message, Instant.now(), null, null, null);

    }

    // ─── Plugin-style factory aliases ─────────────────────────────────────────

    /** Alias for {@link #healthy()}. */
    public static HealthStatus ok() {
        return new HealthStatus(Status.HEALTHY, "OK", Instant.now(), null, null, null);
    }

    /** Alias for {@link #healthy(String)}. */
    public static HealthStatus ok(String message) {
        return healthy(message);
    }

    /** Alias for {@link #healthy(String, Map)}. */
    public static HealthStatus ok(String message, Map<String, Object> details) {
        return healthy(message, details);
    }

    /** Alias for {@link #unhealthy(String)}. */
    public static HealthStatus error(String message) {
        return unhealthy(message);
    }

    /** Alias for {@link #unhealthy(String, Throwable)}. */
    public static HealthStatus error(String message, Throwable cause) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("error", cause.getClass().getSimpleName());
        if (cause.getMessage() != null) details.put("errorMessage", cause.getMessage());
        return new HealthStatus(Status.UNHEALTHY, message, Instant.now(), null, details, cause);
    }

    /** Alias for {@link #unhealthy(String, Map)}. */
    public static HealthStatus error(String message, Map<String, Object> details) {
        return unhealthy(message, details);
    }

    /** Creates a builder for constructing complex composite health status. */
    public static Builder builder() {
        return new Builder();
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    public Status getStatus() { return status; }

    public String getMessage() { return message; }

    public Instant getTimestamp() { return timestamp; }

    public Map<String, HealthCheck> getChecks() { return checks; }

    public Map<String, Object> getDetails() { return details; }

    public Throwable getException() { return exception; }

    /** Returns true if this status represents a healthy component. */
    public boolean isHealthy() { return status == Status.HEALTHY; }

    /** Returns true if this status represents an unhealthy component. */
    public boolean isUnhealthy() { return status == Status.UNHEALTHY; }

    /** Returns true if this status represents a degraded component. */
    public boolean isDegraded() { return status == Status.DEGRADED; }

    /** Returns a specific named health check, or {@code null} if not present. */
    public HealthCheck getCheck(String name) { return checks.get(name); }

    @Override
    public String toString() {
        return String.format("HealthStatus{status=%s, message='%s', timestamp=%s, checks=%d}",
                status, message, timestamp, checks.size());
    }

    // ─── Nested types ─────────────────────────────────────────────────────────

    /**
     * Health status severity levels.
     */
    public enum Status {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }

    /**
     * Result of an individual health check within a composite status.
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
     * Builder for constructing composite {@link HealthStatus} with individual checks.
     */
    public static class Builder {
        private Status status = Status.UNKNOWN;
        private String message = "";
        private Instant timestamp = Instant.now();
        private final Map<String, HealthCheck> checks = new HashMap<>();
        private final Map<String, Object> details = new HashMap<>();

        public Builder withStatus(Status status) {
            this.status = Objects.requireNonNull(status);
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
            checks.put(name, new HealthCheck(name, status, message, responseTimeMs));
            return this;
        }

        public Builder withDetail(String key, Object value) {
            details.put(key, value);
            return this;
        }

        public HealthStatus build() {
            return new HealthStatus(status, message, timestamp, checks, details, null);
        }
    }
}
