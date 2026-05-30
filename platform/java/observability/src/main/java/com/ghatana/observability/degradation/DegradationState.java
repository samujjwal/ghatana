package com.ghatana.observability.degradation;

import java.time.Instant;
import java.util.*;

/**
 * Degradation state for explicit degraded runtime truth.
 *
 * <p><b>Purpose</b><br>
 * Represents the degraded state of a component or service, converting
 * silent degradation into explicit degraded runtime truth that can be
 * surfaced to operators and users.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * DegradationState state = DegradationState.builder()
 *     .component("connector-sync")
 *     .degradationType("SERVICE_UNAVAILABLE")
 *     .severity(Severity.HIGH)
 *     .message("External connector service is unavailable")
 *     .build();
 *
 * if (state.isDegraded()) {
 *     // Surface to runtime truth
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Explicit degraded runtime truth representation
 * @doc.layer platform
 * @doc.pattern Degradation State
 */
public class DegradationState {

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum DegradationType {
        SERVICE_UNAVAILABLE,
        PERFORMANCE_DEGRADED,
        RATE_LIMITED,
        CIRCUIT_OPEN,
        TIMEOUT,
        DATA_INCONSISTENCY,
        FEATURE_DISABLED,
        DEPENDENCY_FAILURE,
        RESOURCE_EXHAUSTION,
        UNKNOWN
    }

    private final String component;
    private final DegradationType degradationType;
    private final Severity severity;
    private final String message;
    private final Map<String, String> details;
    private final Instant detectedAt;
    private final Instant lastUpdated;
    private final boolean resolved;
    private final Instant resolvedAt;
    private final String resolutionReason;

    private DegradationState(Builder builder) {
        this.component = builder.component;
        this.degradationType = builder.degradationType;
        this.severity = builder.severity;
        this.message = builder.message;
        this.details = Collections.unmodifiableMap(builder.details);
        this.detectedAt = builder.detectedAt != null ? builder.detectedAt : Instant.now();
        this.lastUpdated = builder.lastUpdated != null ? builder.lastUpdated : Instant.now();
        this.resolved = builder.resolved;
        this.resolvedAt = builder.resolvedAt;
        this.resolutionReason = builder.resolutionReason;
    }

    public String getComponent() { return component; }
    public DegradationType getDegradationType() { return degradationType; }
    public Severity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public Map<String, String> getDetails() { return details; }
    public Instant getDetectedAt() { return detectedAt; }
    public Instant getLastUpdated() { return lastUpdated; }
    public boolean isResolved() { return resolved; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolutionReason() { return resolutionReason; }

    /**
     * Checks if the component is currently degraded.
     */
    public boolean isDegraded() {
        return !resolved;
    }

    /**
     * Gets the duration of the degradation.
     */
    public java.time.Duration getDuration() {
        Instant endTime = resolved ? resolvedAt : Instant.now();
        return java.time.Duration.between(detectedAt, endTime);
    }

    /**
     * Creates a resolved copy of this degradation state.
     */
    public DegradationState resolve(String reason) {
        return new Builder(this)
                .resolved(true)
                .resolvedAt(Instant.now())
                .resolutionReason(reason)
                .build();
    }

    /**
     * Creates a builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for DegradationState.
     */
    public static class Builder {
        private String component;
        private DegradationType degradationType;
        private Severity severity;
        private String message;
        private Map<String, String> details = new HashMap<>();
        private Instant detectedAt;
        private Instant lastUpdated;
        private boolean resolved = false;
        private Instant resolvedAt;
        private String resolutionReason;

        public Builder() {}

        public Builder(DegradationState state) {
            this.component = state.component;
            this.degradationType = state.degradationType;
            this.severity = state.severity;
            this.message = state.message;
            this.details = new HashMap<>(state.details);
            this.detectedAt = state.detectedAt;
            this.lastUpdated = state.lastUpdated;
            this.resolved = state.resolved;
            this.resolvedAt = state.resolvedAt;
            this.resolutionReason = state.resolutionReason;
        }

        public Builder component(String component) {
            this.component = component;
            return this;
        }

        public Builder degradationType(DegradationType degradationType) {
            this.degradationType = degradationType;
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder details(Map<String, String> details) {
            this.details = details != null ? details : new HashMap<>();
            return this;
        }

        public Builder addDetail(String key, String value) {
            this.details.put(key, value);
            return this;
        }

        public Builder detectedAt(Instant detectedAt) {
            this.detectedAt = detectedAt;
            return this;
        }

        public Builder lastUpdated(Instant lastUpdated) {
            this.lastUpdated = lastUpdated;
            return this;
        }

        public Builder resolved(boolean resolved) {
            this.resolved = resolved;
            return this;
        }

        public Builder resolvedAt(Instant resolvedAt) {
            this.resolvedAt = resolvedAt;
            return this;
        }

        public Builder resolutionReason(String resolutionReason) {
            this.resolutionReason = resolutionReason;
            return this;
        }

        public DegradationState build() {
            if (component == null || component.isEmpty()) {
                throw new IllegalArgumentException("Component is required");
            }
            if (degradationType == null) {
                throw new IllegalArgumentException("Degradation type is required");
            }
            if (severity == null) {
                throw new IllegalArgumentException("Severity is required");
            }
            if (message == null || message.isEmpty()) {
                throw new IllegalArgumentException("Message is required");
            }
            return new DegradationState(this);
        }
    }

    @Override
    public String toString() {
        return "DegradationState{" +
                "component='" + component + '\'' +
                ", degradationType=" + degradationType +
                ", severity=" + severity +
                ", message='" + message + '\'' +
                ", resolved=" + resolved +
                ", detectedAt=" + detectedAt +
                '}';
    }
}
