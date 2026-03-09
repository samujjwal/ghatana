package com.ghatana.datacloud.client;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Canonical learning signal for AI/ML training.
 *
 * <p>
 * <b>Purpose</b><br>
 * Data-Cloud stores <b>signals, not models</b>. Learning signals are immutable
 * observations that feed:
 * <ul>
 * <li>Offline ML model training</li>
 * <li>Online learning systems</li>
 * <li>A/B testing and experimentation</li>
 * <li>Performance analysis and optimization</li>
 * </ul>
 *
 * <p>
 * <b>Signal Types</b><br>
 * <ul>
 * <li><b>QUERY</b> - Query patterns, features, execution stats</li>
 * <li><b>PERFORMANCE</b> - Latency, throughput, resource usage</li>
 * <li><b>FEEDBACK</b> - User acceptance/rejection of recommendations</li>
 * <li><b>GOVERNANCE</b> - Policy violations, access patterns</li>
 * <li><b>DATA_QUALITY</b> - Schema violations, null rates</li>
 * <li><b>OPERATIONAL</b> - System health, errors, incidents</li>
 * </ul>
 *
 * <p>
 * <b>Storage Strategy</b><br>
 * Learning signals are stored as EVENT records in a dedicated collection:
 * <ul>
 * <li>Collection: {@code learning-signals}</li>
 * <li>RecordType: {@code EVENT} (immutable, append-only)</li>
 * <li>Retention: Configurable, typically 90+ days</li>
 * <li>Indexing: By tenantId, signalType, timestamp, source.plugin</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Capture query pattern signal
 * LearningSignal signal = LearningSignal.builder()
 *     .signalType(SignalType.QUERY)
 *     .tenantId("tenant-123")
 *     .source(SignalSource.builder()
 *         .plugin("postgresql")
 *         .collection("events")
 *         .build())
 *     .features(Map.of(
 *         "hasTimeFilter", true,
 *         "joinCount", 2,
 *         "aggregationCount", 3,
 *         "estimatedRows", 1000
 *     ))
 *     .metrics(Map.of(
 *         "latencyMs", 45.2,
 *         "rowsScanned", 10000,
 *         "rowsReturned", 100
 *     ))
 *     .build();
 *
 * // Store via StoragePlugin
 * learningSignalStore.insert(signal);
 * }</pre>
 *
 * @see com.ghatana.datacloud.spi.StoragePlugin
 * @doc.type record
 * @doc.purpose Canonical learning signal model
 * @doc.layer core
 * @doc.pattern Value Object
 */
@Value
@Builder
public class LearningSignal {

    /**
     * Unique signal ID.
     */
    @Builder.Default
    UUID signalId = UUID.randomUUID();

    /**
     * Timestamp when signal was captured.
     */
    @Builder.Default
    Instant timestamp = Instant.now();

    /**
     * Tenant ID for multi-tenancy.
     */
    String tenantId;

    /**
     * Type of learning signal.
     */
    SignalType signalType;

    /**
     * Source of the signal (plugin, collection, operation).
     */
    SignalSource source;

    // ═══════════════════════════════════════════════════════════════════════════
    // Brain/Feedback Loop Fields
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Direct reference to source entity (for brain feedback signals).
     */
    String sourceId;

    /**
     * Type of the source entity (e.g., "pattern", "reflex", "prediction").
     */
    String sourceType;

    /**
     * Category for organizing signals.
     */
    String category;

    /**
     * Signal strength (0.0 to 1.0).
     */
    @Builder.Default
    double strength = 1.0;

    /**
     * Confidence level of the signal (0.0 to 1.0).
     */
    @Builder.Default
    double confidence = 1.0;

    /**
     * Tags for categorization and filtering.
     */
    java.util.Set<String> tags;

    /**
     * Feature vector extracted from the observation.
     * <p>
     * Examples:
     * <ul>
     * <li>Query features: filterCount, joinCount, hasAggregation, etc.</li>
     * <li>Performance features: cpuUsage, memoryUsage, diskIO</li>
     * <li>Data quality features: nullRate, distinctCount, outlierRate</li>
     * </ul>
     */
    Map<String, Object> features;

    /**
     * Outcome metrics (labels for supervised learning).
     * <p>
     * Examples:
     * <ul>
     * <li>Query metrics: latencyMs, rowsScanned, cost</li>
     * <li>Recommendation metrics: accepted, impact, userRating</li>
     * <li>Anomaly metrics: severity, falsePositive, resolution</li>
     * </ul>
     */
    Map<String, Object> metrics;

    /**
     * Additional context for the signal.
     */
    Map<String, Object> context;

    /**
     * Metadata map (alternative to context, for brain feedback signals).
     */
    Map<String, Object> metadata;

    /**
     * Correlation ID for tracing.
     */
    String correlationId;

    /**
     * Signal version for schema evolution.
     */
    @Builder.Default
    int version = 1;

    /**
     * Types of learning signals.
     */
    public enum SignalType {
        /**
         * Query pattern and execution.
         */
        QUERY,

        /**
         * Performance metrics.
         */
        PERFORMANCE,

        /**
         * User feedback on recommendations.
         */
        FEEDBACK,

        /**
         * Governance events.
         */
        GOVERNANCE,

        /**
         * Data quality observations.
         */
        DATA_QUALITY,

        /**
         * Operational metrics.
         */
        OPERATIONAL,

        /**
         * Prediction outcomes.
         */
        PREDICTION_OUTCOME,

        /**
         * Anomaly detection events.
         */
        ANOMALY,

        /**
         * Custom plugin-specific signal.
         */
        CUSTOM,

        // ═══════════════════════════════════════════════════════════════════════════
        // Brain/Feedback Loop Signal Types
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Positive reinforcement signal - behavior should be repeated.
         */
        REINFORCEMENT,

        /**
         * Correction signal - behavior should be adjusted.
         */
        CORRECTION,

        /**
         * Error signal - something went wrong.
         */
        ERROR,

        /**
         * Observation signal - passive learning from behavior.
         */
        OBSERVATION,

        /**
         * Adaptation signal - system should adapt.
         */
        ADAPTATION,

        /**
         * Exploration signal - try new approaches.
         */
        EXPLORATION,

        /**
         * Instruction signal - explicit teaching from expert.
         */
        INSTRUCTION
    }

    /**
     * Source information for a learning signal.
     */
    @Value
    @Builder
    public static class SignalSource {
        /**
         * Plugin ID that generated the signal.
         */
        String plugin;

        /**
         * Collection name (if applicable).
         */
        String collection;

        /**
         * Operation that triggered the signal.
         */
        String operation;

        /**
         * User or service that initiated the operation.
         */
        String actor;

        /**
         * Additional source metadata.
         */
        Map<String, String> metadata;
    }

    /**
     * Converts this signal to a map for storage.
     *
     * @return Map representation suitable for JSONB storage
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("signalId", signalId.toString());
        result.put("timestamp", timestamp.toString());
        result.put("tenantId", tenantId != null ? tenantId : "");
        result.put("signalType", signalType != null ? signalType.name() : SignalType.CUSTOM.name());
        result.put("source", source != null ? sourceToMap() : Map.of());
        result.put("features", features != null ? features : Map.of());
        result.put("metrics", metrics != null ? metrics : Map.of());
        result.put("context", context != null ? context : Map.of());
        result.put("correlationId", correlationId != null ? correlationId : "");
        result.put("version", version);
        return result;
    }

    private Map<String, Object> sourceToMap() {
        if (source == null) {
            return Map.of();
        }
        return Map.of(
                "plugin", source.plugin != null ? source.plugin : "",
                "collection", source.collection != null ? source.collection : "",
                "operation", source.operation != null ? source.operation : "",
                "actor", source.actor != null ? source.actor : "",
                "metadata", source.metadata != null ? source.metadata : Map.of()
        );
    }
}

