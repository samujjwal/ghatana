package com.ghatana.appplatform.eventstore.kafka;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Routing configuration for the Dead Letter Queue (DLQ) per consumer group.
 *
 * <p>Controls whether DLQ routing is enabled, how many retries are attempted before
 * routing to the DLQ, and which exception class names are treated as permanent errors
 * (skipping retries entirely and going straight to the DLQ).
 *
 * <p>Instances are immutable. Use the {@link Builder} to construct them, or
 * {@link #defaults()} for a sensible starting configuration.
 *
 * <h2>K-02 integration</h2>
 * <p>Read K-02 config values, then build via:
 * <pre>{@code
 * DlqRoutingConfig config = DlqRoutingConfig.builder()
 *     .dlqEnabled(Boolean.parseBoolean(configValues.get("event-store.dlq.enabled").value()))
 *     .maxRetriesBeforeDlq(Integer.parseInt(configValues.get("event-store.dlq.maxRetries").value()))
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Configures DLQ routing behaviour per consumer (STORY-K05-028)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class DlqRoutingConfig {

    // ── K-02 config key constants ─────────────────────────────────────────────

    /** K-02 key: enable/disable DLQ routing. Value: {@code "true"} or {@code "false"}. */
    public static final String KEY_DLQ_ENABLED = "event-store.dlq.enabled";

    /** K-02 key: max transient retries before routing to DLQ. Value: integer string. */
    public static final String KEY_MAX_RETRIES = "event-store.dlq.maxRetries";

    /**
     * K-02 key: comma-separated fully-qualified exception class names to treat as
     * permanent errors (no retry, routed straight to DLQ). Value: CSV string.
     */
    public static final String KEY_PERMANENT_ERRORS = "event-store.dlq.permanentErrorClasses";

    // ── Defaults ──────────────────────────────────────────────────────────────

    /** Default max transient retries before dead-lettering. */
    public static final int DEFAULT_MAX_RETRIES = 3;

    private final boolean dlqEnabled;
    private final int maxRetriesBeforeDlq;

    /**
     * Fully-qualified class names of exceptions that are treated as permanent failures.
     * For these, retry is skipped and the record goes directly to the DLQ.
     */
    private final Set<String> permanentErrorClasses;

    private DlqRoutingConfig(Builder builder) {
        this.dlqEnabled           = builder.dlqEnabled;
        this.maxRetriesBeforeDlq  = builder.maxRetriesBeforeDlq;
        this.permanentErrorClasses = Collections.unmodifiableSet(builder.permanentErrorClasses);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Whether DLQ routing is active for this consumer. */
    public boolean isDlqEnabled() { return dlqEnabled; }

    /** Max transient delivery attempts before sending to the DLQ. */
    public int maxRetriesBeforeDlq() { return maxRetriesBeforeDlq; }

    /**
     * Returns true if the given exception should be treated as a permanent (non-retriable) error.
     * Permanent errors skip retries and go directly to the DLQ.
     *
     * @param throwable the exception from a failed consumer handler
     */
    public boolean isPermanentError(Throwable throwable) {
        if (throwable == null) return false;
        String className = throwable.getClass().getName();
        for (String pattern : permanentErrorClasses) {
            if (className.equals(pattern) || className.startsWith(pattern)) {
                return true;
            }
        }
        return false;
    }

    /** Returns a default configuration (DLQ enabled, 3 retries, no permanent error classes). */
    public static DlqRoutingConfig defaults() {
        return builder().build();
    }

    /**
     * Builds a {@link DlqRoutingConfig} from a resolved K-02 config map.
     *
     * @param k02Values resolved config values from {@code ConfigStore#resolve}
     * @return config object derived from K-02, falling back to defaults for missing keys
     */
    public static DlqRoutingConfig fromK02Values(Map<String, String> k02Values) {
        Objects.requireNonNull(k02Values, "k02Values");
        Builder builder = builder();

        String enabled = k02Values.get(KEY_DLQ_ENABLED);
        if (enabled != null) builder.dlqEnabled(Boolean.parseBoolean(enabled.trim()));

        String maxRetries = k02Values.get(KEY_MAX_RETRIES);
        if (maxRetries != null) {
            try {
                builder.maxRetriesBeforeDlq(Integer.parseInt(maxRetries.trim()));
            } catch (NumberFormatException ignored) { /* keep default */ }
        }

        String permanentErrors = k02Values.get(KEY_PERMANENT_ERRORS);
        if (permanentErrors != null && !permanentErrors.isBlank()) {
            for (String cls : permanentErrors.split(",")) {
                String trimmed = cls.strip();
                if (!trimmed.isEmpty()) builder.addPermanentErrorClass(trimmed);
            }
        }

        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean dlqEnabled = true;
        private int maxRetriesBeforeDlq = DEFAULT_MAX_RETRIES;
        private final Set<String> permanentErrorClasses = new java.util.LinkedHashSet<>();

        public Builder dlqEnabled(boolean enabled) {
            this.dlqEnabled = enabled;
            return this;
        }

        public Builder maxRetriesBeforeDlq(int max) {
            if (max < 0) throw new IllegalArgumentException("maxRetriesBeforeDlq must be >= 0; got " + max);
            this.maxRetriesBeforeDlq = max;
            return this;
        }

        public Builder addPermanentErrorClass(String fullyQualifiedClassName) {
            this.permanentErrorClasses.add(Objects.requireNonNull(fullyQualifiedClassName));
            return this;
        }

        public DlqRoutingConfig build() {
            return new DlqRoutingConfig(this);
        }
    }
}
