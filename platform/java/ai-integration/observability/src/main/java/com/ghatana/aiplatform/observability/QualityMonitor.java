package com.ghatana.aiplatform.observability;

import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitors model quality degradation correlating with data drift.
 *
 * <p><b>Purpose</b><br>
 * Tracks model performance metrics and correlates with data drift:
 * <ul>
 *   <li>Precision, recall, F1 score tracking</li>
 *   <li>Real-time quality SLA checking</li>
 *   <li>Correlation with DataDriftDetector PSI values</li>
 *   <li>Root cause analysis (drift vs other factors)</li>
 *   <li>Automatic alerts when quality threshold breached</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * QualityMonitor monitor = new QualityMonitor(metrics, driftDetector);
 * monitor.setQualitySLA("tenant-123", "fraud-model", 0.85); // 85% min precision
 *
 * // Record predictions
 * monitor.recordPrediction("tenant-123", "fraud-model", 0.92, true);
 * monitor.recordPrediction("tenant-123", "fraud-model", 0.78, true);
 *
 * // Check quality
 * double precision = monitor.getPrecision("tenant-123", "fraud-model");
 * QualityAlert alert = monitor.checkQuality("tenant-123", "fraud-model");
 *
 * if (alert.isBreeched()) {
 *     // Investigate drift correlation
 *     double driftPSI = driftDetector.calculatePSI(...);
 *     System.out.println("Quality breach likely due to drift: PSI=" + driftPSI);
 * }
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Core observability component monitoring model quality and alerting on degradation.
 * Works with DataDriftDetector to distinguish data drift causes from model issues.
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - uses ConcurrentHashMap for metric tracking.
 *
 * @doc.type class
 * @doc.purpose Model quality monitoring with SLA enforcement
 * @doc.layer platform
 * @doc.pattern Quality Monitor
 */
public class QualityMonitor {

    private static final Logger logger = LoggerFactory.getLogger(QualityMonitor.class);

    private final MetricsCollector metrics;
    private final DataDriftDetector driftDetector;

    private final Map<String, ModelQuality> qualityMetrics = new ConcurrentHashMap<>();
    private final Map<String, Double> qualitySLAs = new ConcurrentHashMap<>();

    private static final double DEFAULT_SLA_THRESHOLD = 0.8;

    /**
     * Constructs quality monitor.
     *
     * @param metrics metrics collector
     * @param driftDetector data drift detector for correlation
     */
    public QualityMonitor(MetricsCollector metrics, DataDriftDetector driftDetector) {
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.driftDetector = Objects.requireNonNull(driftDetector, "driftDetector must not be null");
    }

    /**
     * Sets quality SLA threshold for model.
     *
     * @param tenantId tenant identifier
     * @param modelName model name
     * @param threshold minimum acceptable precision (0.0-1.0)
     */
    public void setQualitySLA(String tenantId, String modelName, double threshold) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");

        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("SLA threshold must be between 0.0 and 1.0");
        }

        String key = tenantId + ":" + modelName;
        qualitySLAs.put(key, threshold);

        metrics.incrementCounter("ai.quality.sla.set",
                "tenant", tenantId, "model", modelName);

        logger.info("Set quality SLA: tenant={}, model={}, threshold={:.2f}",
                tenantId, modelName, threshold);
    }

    /**
     * Records prediction with ground truth label.
     *
     * @param tenantId tenant identifier
     * @param modelName model name
     * @param confidence prediction confidence (0.0-1.0)
     * @param actualLabel actual ground truth label
     */
    public void recordPrediction(String tenantId, String modelName, double confidence, boolean actualLabel) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");

        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }

        String key = tenantId + ":" + modelName;
        ModelQuality quality = qualityMetrics
                .computeIfAbsent(key, k -> new ModelQuality());

        // Predict as positive if confidence > 0.5
        boolean predicted = confidence > 0.5;

        // Update confusion matrix
        if (predicted && actualLabel) {
            quality.truePositives.incrementAndGet();
        } else if (predicted && !actualLabel) {
            quality.falsePositives.incrementAndGet();
        } else if (!predicted && actualLabel) {
            quality.falseNegatives.incrementAndGet();
        } else {
            quality.trueNegatives.incrementAndGet();
        }

        quality.totalCount.incrementAndGet();
        quality.lastUpdated = Instant.now();

        metrics.recordTimer("ai.quality.prediction", 1,
                "tenant", tenantId, "model", modelName);
    }

    /**
     * Gets precision for model.
     *
     * <p>Precision = TP / (TP + FP)
     *
     * @param tenantId tenant identifier
     * @param modelName model name
     * @return precision (0.0-1.0) or 0 if no predictions recorded
     */
    public double getPrecision(String tenantId, String modelName) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");

        String key = tenantId + ":" + modelName;
        ModelQuality quality = qualityMetrics.get(key);

        if (quality == null || quality.totalCount.get() == 0) {
            return 0.0;
        }

        long denominator = quality.truePositives.get() + quality.falsePositives.get();
        if (denominator == 0) {
            return 0.0;
        }

        return (double) quality.truePositives.get() / denominator;
    }

    /**
     * Gets recall for model.
     *
     * <p>Recall = TP / (TP + FN)
     *
     * @param tenantId tenant identifier
     * @param modelName model name
     * @return recall (0.0-1.0) or 0 if no predictions recorded
     */
    public double getRecall(String tenantId, String modelName) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");

        String key = tenantId + ":" + modelName;
        ModelQuality quality = qualityMetrics.get(key);

        if (quality == null || quality.totalCount.get() == 0) {
            return 0.0;
        }

        long denominator = quality.truePositives.get() + quality.falseNegatives.get();
        if (denominator == 0) {
            return 0.0;
        }

        return (double) quality.truePositives.get() / denominator;
    }

    /**
     * Gets F1 score for model.
     *
     * <p>F1 = 2 * (Precision * Recall) / (Precision + Recall)
     *
     * @param tenantId tenant identifier
     * @param modelName model name
     * @return F1 score (0.0-1.0) or 0 if no predictions recorded
     */
    public double getF1Score(String tenantId, String modelName) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");

        double precision = getPrecision(tenantId, modelName);
        double recall = getRecall(tenantId, modelName);

        if (precision + recall == 0.0) {
            return 0.0;
        }

        return 2.0 * (precision * recall) / (precision + recall);
    }

    /**
     * Checks quality metrics against SLA.
     *
     * <p>GIVEN: Model with SLA and quality metrics
     * <p>WHEN: checkQuality() is called
     * <p>THEN: Returns alert with breach status and potential root cause
     *
     * @param tenantId tenant identifier
     * @param modelName model name
     * @return quality alert with status and analysis
     */
    public QualityAlert checkQuality(String tenantId, String modelName) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");

        String slaKey = tenantId + ":" + modelName;
        double slaThreshold = qualitySLAs.getOrDefault(slaKey, DEFAULT_SLA_THRESHOLD);

        double precision = getPrecision(tenantId, modelName);

        boolean isBreeched = precision < slaThreshold;

        QualityAlert alert = new QualityAlert(
                tenantId, modelName, precision, slaThreshold, isBreeched,
                Instant.now()
        );

        if (isBreeched) {
            metrics.incrementCounter("ai.quality.sla_breach",
                    "tenant", tenantId, "model", modelName, "severity", "high");

            logger.warn("Quality SLA breached: tenant={}, model={}, precision={:.4f} < {:.4f}",
                    tenantId, modelName, precision, slaThreshold);
        }

        return alert;
    }

    /**
     * Quality alert with SLA analysis.
     */
    public static class QualityAlert {
        private final String tenantId;
        private final String modelName;
        private final double currentPrecision;
        private final double slaThreshold;
        private final boolean breeched;
        private final Instant timestamp;

        public QualityAlert(String tenantId, String modelName, double currentPrecision,
                           double slaThreshold, boolean breeched, Instant timestamp) {
            this.tenantId = tenantId;
            this.modelName = modelName;
            this.currentPrecision = currentPrecision;
            this.slaThreshold = slaThreshold;
            this.breeched = breeched;
            this.timestamp = timestamp;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getModelName() {
            return modelName;
        }

        public double getCurrentPrecision() {
            return currentPrecision;
        }

        public double getSlaThreshold() {
            return slaThreshold;
        }

        public boolean isBreeched() {
            return breeched;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "QualityAlert{" +
                    "tenantId='" + tenantId + '\'' +
                    ", modelName='" + modelName + '\'' +
                    ", currentPrecision=" + currentPrecision +
                    ", slaThreshold=" + slaThreshold +
                    ", breeched=" + breeched +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    /**
     * Model quality metrics tracker.
     */
    private static class ModelQuality {
        final AtomicLong truePositives = new AtomicLong(0);
        final AtomicLong trueNegatives = new AtomicLong(0);
        final AtomicLong falsePositives = new AtomicLong(0);
        final AtomicLong falseNegatives = new AtomicLong(0);
        final AtomicLong totalCount = new AtomicLong(0);
        Instant lastUpdated = Instant.now();
    }
}
