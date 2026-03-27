/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
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

    private HealthStatus(Status status, String message, Instant timestamp, Map<String, HealthCheck> checks) {
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.message = message != null ? message : "";
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.checks = checks != null ? Collections.unmodifiableMap(new HashMap<>(checks)) : Collections.emptyMap();
    }

    public static HealthStatus healthy() {
        return new HealthStatus(Status.HEALTHY, "Component is healthy", Instant.now(), Collections.emptyMap());
    }

    public static HealthStatus healthy(String message) {
        return new HealthStatus(Status.HEALTHY, message, Instant.now(), Collections.emptyMap());
    }

    public static HealthStatus unhealthy(String message) {
        return new HealthStatus(Status.UNHEALTHY, message, Instant.now(), Collections.emptyMap());
    }

    public static HealthStatus degraded(String message) {
        return new HealthStatus(Status.DEGRADED, message, Instant.now(), Collections.emptyMap());
    }

    public static Builder builder() {
        return new Builder();
    }

    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, HealthCheck> getChecks() { return checks; }

    public boolean isHealthy() { return status == Status.HEALTHY; }
    public boolean isUnhealthy() { return status == Status.UNHEALTHY; }
    public boolean isDegraded() { return status == Status.DEGRADED; }

    /**
     * Maps this kernel-local status onto the canonical platform health contract.
     */
    public com.ghatana.platform.health.HealthStatus toPlatformHealthStatus() {
        com.ghatana.platform.health.HealthStatus.Builder builder =
            com.ghatana.platform.health.HealthStatus.builder()
                .withStatus(toPlatformStatus(status))
                .withMessage(message)
                .withTimestamp(timestamp);

        checks.forEach((name, check) -> builder.withCheck(
            name,
            toPlatformStatus(check.getStatus()),
            check.getMessage(),
            check.getResponseTimeMs()));

        return builder.build();
    }

    /**
     * Rebuilds the kernel-local status from the canonical platform health contract.
     */
    public static HealthStatus fromPlatformHealthStatus(com.ghatana.platform.health.HealthStatus status) {
        Objects.requireNonNull(status, "status cannot be null");

        Builder builder = builder()
            .withStatus(fromPlatformStatus(status.getStatus()))
            .withMessage(status.getMessage())
            .withTimestamp(status.getTimestamp());

        status.getChecks().forEach((name, check) -> builder.withCheck(
            name,
            fromPlatformStatus(check.getStatus()),
            check.getMessage(),
            check.getResponseTimeMs()));

        return builder.build();
    }

    public HealthCheck getCheck(String name) { return checks.get(name); }

    @Override
    public String toString() {
        return String.format("HealthStatus{status=%s, message='%s', timestamp=%s, checks=%d}",
            status, message, timestamp, checks.size());
    }

    public enum Status {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }

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

    public static class Builder {
        private Status status = Status.UNKNOWN;
        private String message = "";
        private Instant timestamp = Instant.now();
        private final Map<String, HealthCheck> checks = new HashMap<>();

        public Builder withStatus(Status status) { this.status = status; return this; }
        public Builder withMessage(String message) { this.message = message; return this; }
        public Builder withTimestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

        public Builder withCheck(String name, Status status, String message, long responseTimeMs) {
            checks.put(name, new HealthCheck(name, status, message, responseTimeMs));
            return this;
        }

        public HealthStatus build() {
            return new HealthStatus(status, message, timestamp, checks);
        }
    }

    private static com.ghatana.platform.health.HealthStatus.Status toPlatformStatus(Status status) {
        return switch (status) {
            case HEALTHY -> com.ghatana.platform.health.HealthStatus.Status.HEALTHY;
            case DEGRADED -> com.ghatana.platform.health.HealthStatus.Status.DEGRADED;
            case UNHEALTHY -> com.ghatana.platform.health.HealthStatus.Status.UNHEALTHY;
            case UNKNOWN -> com.ghatana.platform.health.HealthStatus.Status.UNKNOWN;
        };
    }

    private static Status fromPlatformStatus(com.ghatana.platform.health.HealthStatus.Status status) {
        return switch (status) {
            case HEALTHY -> Status.HEALTHY;
            case DEGRADED -> Status.DEGRADED;
            case UNHEALTHY -> Status.UNHEALTHY;
            case UNKNOWN -> Status.UNKNOWN;
        };
    }
}
