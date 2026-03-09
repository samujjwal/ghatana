package com.ghatana.datacloud.spi.ai;

import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Optional AI capability for plugins that detect anomalies.
 *
 * <p>
 * <b>Purpose</b><br>
 * Enables detection of anomalies and outliers in:
 * <ul>
 * <li>Query patterns (unusual queries)</li>
 * <li>Performance metrics (latency spikes, throughput drops)</li>
 * <li>Data quality (schema violations, null rates)</li>
 * <li>Security events (unusual access patterns)</li>
 * <li>Governance violations (policy breaches)</li>
 * </ul>
 *
 * <p>
 * <b>Detection Methods</b><br>
 * Implementations may use:
 * <ul>
 * <li>Statistical methods (Z-score, IQR)</li>
 * <li>Time-series analysis (ARIMA, Prophet)</li>
 * <li>ML models (Isolation Forest, Autoencoders)</li>
 * <li>Rule-based detection</li>
 * </ul>
 *
 * <p>
 * <b>Safety Contract</b><br>
 * Anomaly detection is <b>observational only</b>:
 * <ul>
 * <li>Never blocks operations automatically</li>
 * <li>Provides severity and confidence scores</li>
 * <li>Includes context for investigation</li>
 * <li>Logs all detections as learning signals</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * if (plugin instanceof AnomalyDetectionCapability detector) {
 *     List<Anomaly> anomalies = detector.detect(
 *         AnomalyContext.builder()
 *             .tenantId("tenant-123")
 *             .detectionType(DetectionType.PERFORMANCE)
 *             .timeWindow(Duration.ofHours(1))
 *             .threshold(3.0) // Z-score threshold
 *             .build()
 *     ).getResult();
 *
 *     for (Anomaly anomaly : anomalies) {
 *         if (anomaly.severity() == Severity.CRITICAL) {
 *             // Alert operations team
 *         }
 *     }
 * }
 * }</pre>
 *
 * @see com.ghatana.datacloud.spi.StoragePlugin
 * @see PredictionCapability
 * @doc.type interface
 * @doc.purpose AI anomaly detection capability
 * @doc.layer core
 * @doc.pattern Capability Interface
 */
public interface AnomalyDetectionCapability {

    /**
     * Detects anomalies based on the provided context.
     *
     * @param context Anomaly detection context
     * @return Promise with list of detected anomalies
     */
    Promise<List<Anomaly>> detect(AnomalyContext context);

    /**
     * Updates the baseline for anomaly detection (continuous learning).
     *
     * @param tenantId Tenant ID
     * @param collectionName Collection name
     * @return Promise completing when baseline is updated
     */
    Promise<Void> updateBaseline(String tenantId, String collectionName);

    /**
     * Gets the current baseline statistics for a metric.
     *
     * @param tenantId Tenant ID
     * @param collectionName Collection name
     * @param metricName Metric name
     * @return Promise with baseline statistics
     */
    Promise<BaselineStatistics> getBaseline(String tenantId, String collectionName, String metricName);

    /**
     * Gets supported detection types.
     *
     * @return Promise with list of supported types
     */
    Promise<List<DetectionType>> getSupportedDetectionTypes();

    /**
     * Context for anomaly detection.
     */
    @Value
    @Builder
    class AnomalyContext {
        /**
         * Tenant ID.
         */
        String tenantId;

        /**
         * Collection name (optional).
         */
        String collectionName;

        /**
         * Type of anomaly detection.
         */
        DetectionType detectionType;

        /**
         * Time window to analyze.
         */
        java.time.Duration timeWindow;

        /**
         * Detection threshold (algorithm-specific).
         */
        double threshold;

        /**
         * Additional context data.
         */
        Map<String, Object> metadata;

        /**
         * Correlation ID for tracing.
         */
        String correlationId;

        /**
         * Timestamp of request.
         */
        @Builder.Default
        Instant timestamp = Instant.now();
    }

    /**
     * Detected anomaly with details.
     */
    @Value
    @Builder
    class Anomaly {
        /**
         * Unique anomaly ID.
         */
        String anomalyId;

        /**
         * Type of anomaly.
         */
        DetectionType type;

        /**
         * Severity level.
         */
        Severity severity;

        /**
         * Confidence score (0.0 to 1.0).
         */
        double confidence;

        /**
         * Anomaly score (algorithm-specific, e.g., Z-score).
         */
        double anomalyScore;

        /**
         * Human-readable title.
         */
        String title;

        /**
         * Detailed description.
         */
        String description;

        /**
         * Detection method used.
         */
        String detectionMethod;

        /**
         * Affected metric or entity.
         */
        String affectedEntity;

        /**
         * Observed value.
         */
        Object observedValue;

        /**
         * Expected value or baseline.
         */
        Object expectedValue;

        /**
         * Deviation from baseline.
         */
        double deviation;

        /**
         * Time when anomaly occurred.
         */
        Instant occurrenceTime;

        /**
         * Supporting evidence and context.
         */
        Map<String, Object> evidence;

        /**
         * Suggested actions to investigate/resolve.
         */
        List<String> suggestedActions;

        /**
         * Timestamp of detection.
         */
        @Builder.Default
        Instant detectedAt = Instant.now();
    }

    /**
     * Baseline statistics for a metric.
     */
    @Value
    @Builder
    class BaselineStatistics {
        /**
         * Metric name.
         */
        String metricName;

        /**
         * Mean value.
         */
        double mean;

        /**
         * Standard deviation.
         */
        double standardDeviation;

        /**
         * Median value.
         */
        double median;

        /**
         * 25th percentile.
         */
        double p25;

        /**
         * 75th percentile.
         */
        double p75;

        /**
         * 95th percentile.
         */
        double p95;

        /**
         * 99th percentile.
         */
        double p99;

        /**
         * Minimum value.
         */
        double min;

        /**
         * Maximum value.
         */
        double max;

        /**
         * Sample count.
         */
        long sampleCount;

        /**
         * Last updated timestamp.
         */
        Instant lastUpdated;
    }

    /**
     * Types of anomaly detection.
     */
    enum DetectionType {
        /**
         * Query performance anomalies.
         */
        PERFORMANCE,

        /**
         * Data quality anomalies.
         */
        DATA_QUALITY,

        /**
         * Security/access anomalies.
         */
        SECURITY,

        /**
         * Governance violations.
         */
        GOVERNANCE,

        /**
         * Resource usage anomalies.
         */
        RESOURCE_USAGE,

        /**
         * Pattern/behavior anomalies.
         */
        BEHAVIORAL,

        /**
         * Custom plugin-specific detection.
         */
        CUSTOM
    }

    /**
     * Severity levels for anomalies.
     */
    enum Severity {
        /**
         * Critical - immediate attention required.
         */
        CRITICAL,

        /**
         * High - investigate soon.
         */
        HIGH,

        /**
         * Medium - monitor closely.
         */
        MEDIUM,

        /**
         * Low - informational.
         */
        LOW,

        /**
         * Info - normal variation.
         */
        INFO
    }
}

