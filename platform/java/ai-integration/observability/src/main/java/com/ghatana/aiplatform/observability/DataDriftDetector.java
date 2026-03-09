package com.ghatana.aiplatform.observability;

import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Detects data distribution shifts using Population Stability Index (PSI).
 *
 * <p><b>Purpose</b><br>
 * Monitors feature distributions for significant changes:
 * <ul>
 *   <li>PSI = Σ(Expected% - Actual%) * ln(Expected% / Actual%)</li>
 *   <li>PSI < 0.1: No drift detected</li>
 *   <li>PSI 0.1-0.25: Small drift warning</li>
 *   <li>PSI > 0.25: Significant drift alert</li>
 *   <li>Per-feature and per-tenant tracking</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * DataDriftDetector detector = new DataDriftDetector(metrics);
 *
 * // Establish baseline distribution
 * detector.setBaseline("tenant-123", "amount",
 *     Map.of("0-100", 0.3, "100-1000", 0.5, "1000+", 0.2));
 *
 * // Record current observations
 * detector.recordObservation("tenant-123", "amount", "50");
 * detector.recordObservation("tenant-123", "amount", "500");
 * detector.recordObservation("tenant-123", "amount", "5000");
 *
 * // Check for drift
 * double psi = detector.calculatePSI("tenant-123", "amount");
 * if (psi > 0.25) {
 *     System.out.println("Data drift detected! PSI = " + psi);
 * }
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Core observability component detecting feature distribution shifts.
 * Used with QualityMonitor to correlate data drift with model quality degradation.
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - uses ConcurrentHashMap for distribution tracking.
 *
 * @doc.type class
 * @doc.purpose Data distribution shift detection via PSI
 * @doc.layer platform
 * @doc.pattern Drift Detector
 */
public class DataDriftDetector {

    private static final Logger logger = LoggerFactory.getLogger(DataDriftDetector.class);

    private final Map<String, FeatureDistribution> baselineDistributions = new ConcurrentHashMap<>();
    private final Map<String, FeatureObservations> currentObservations = new ConcurrentHashMap<>();
    private final MetricsCollector metrics;

    private static final double PSI_WARNING_THRESHOLD = 0.1;
    private static final double PSI_ALERT_THRESHOLD = 0.25;

    /**
     * Constructs data drift detector.
     *
     * @param metrics metrics collector
     */
    public DataDriftDetector(MetricsCollector metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * Sets baseline distribution for feature.
     *
     * <p>GIVEN: Tenant, feature name, and baseline distribution
     * <p>WHEN: setBaseline() is called
     * <p>THEN: Baseline is stored for PSI calculation
     *
     * @param tenantId tenant identifier
     * @param featureName feature name
     * @param distribution map of bucket -&gt; probability (should sum to 1.0)
     */
    public void setBaseline(String tenantId, String featureName, Map<String, Double> distribution) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(featureName, "featureName must not be null");
        Objects.requireNonNull(distribution, "distribution must not be null");

        String key = tenantId + ":" + featureName;
        baselineDistributions.put(key, new FeatureDistribution(distribution));

        metrics.incrementCounter("ai.drift.baseline.set",
                "tenant", tenantId, "feature", featureName);

        logger.info("Set baseline distribution: tenant={}, feature={}, buckets={}",
                tenantId, featureName, distribution.size());
    }

    /**
     * Records feature observation for current distribution.
     *
     * @param tenantId tenant identifier
     * @param featureName feature name
     * @param bucket bucket/category of observation
     */
    public void recordObservation(String tenantId, String featureName, String bucket) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(featureName, "featureName must not be null");
        Objects.requireNonNull(bucket, "bucket must not be null");

        String key = tenantId + ":" + featureName;
        FeatureObservations observations = currentObservations
                .computeIfAbsent(key, k -> new FeatureObservations());

        observations.recordObservation(bucket);
    }

    /**
     * Calculates PSI between baseline and current observations.
     *
     * <p>GIVEN: Baseline and current distributions exist
     * <p>WHEN: calculatePSI() is called
     * <p>THEN: Returns PSI value; emits warning/alert if threshold exceeded
     *
     * @param tenantId tenant identifier
     * @param featureName feature name
     * @return PSI value
     */
    public double calculatePSI(String tenantId, String featureName) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(featureName, "featureName must not be null");

        String key = tenantId + ":" + featureName;

        FeatureDistribution baseline = baselineDistributions.get(key);
        if (baseline == null) {
            logger.warn("No baseline distribution for feature: tenant={}, feature={}", tenantId, featureName);
            return 0.0;
        }

        FeatureObservations observations = currentObservations.getOrDefault(key, new FeatureObservations());
        if (observations.getTotalCount() == 0) {
            logger.warn("No current observations for feature: tenant={}, feature={}", tenantId, featureName);
            return 0.0;
        }

        // Calculate PSI
        double psi = 0.0;
        for (String bucket : baseline.distribution.keySet()) {
            double expectedPct = baseline.distribution.get(bucket);
            double actualPct = observations.getPercentage(bucket);

            // Avoid log(0) by using small epsilon
            if (actualPct > 0) {
                psi += (actualPct - expectedPct) * Math.log(actualPct / expectedPct);
            } else if (expectedPct > 0) {
                // If no observations but expected some, treat as drift
                psi += expectedPct * Math.log(1.0 / 0.001); // Using small epsilon
            }
        }

        // Emit metrics and alerts
        metrics.recordTimer("ai.drift.data.psi", (long)(psi * 10000),
                "tenant", tenantId, "feature", featureName);

        if (psi > PSI_ALERT_THRESHOLD) {
            metrics.incrementCounter("ai.drift.alert",
                    "tenant", tenantId, "feature", featureName, "severity", "high");
            logger.warn("Data drift ALERT: tenant={}, feature={}, psi={:.4f} > {:.4f}",
                    tenantId, featureName, psi, PSI_ALERT_THRESHOLD);
        } else if (psi > PSI_WARNING_THRESHOLD) {
            metrics.incrementCounter("ai.drift.warning",
                    "tenant", tenantId, "feature", featureName, "severity", "low");
            logger.info("Data drift WARNING: tenant={}, feature={}, psi={:.4f} > {:.4f}",
                    tenantId, featureName, psi, PSI_WARNING_THRESHOLD);
        }

        return psi;
    }

    /**
     * Resets current observations for feature.
     *
     * @param tenantId tenant identifier
     * @param featureName feature name
     */
    public void resetObservations(String tenantId, String featureName) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(featureName, "featureName must not be null");

        String key = tenantId + ":" + featureName;
        currentObservations.remove(key);

        logger.info("Reset observations: tenant={}, feature={}", tenantId, featureName);
    }

    /**
     * Feature baseline distribution.
     */
    private static class FeatureDistribution {
        final Map<String, Double> distribution;

        FeatureDistribution(Map<String, Double> distribution) {
            this.distribution = new HashMap<>(distribution);
        }
    }

    /**
     * Tracks current feature observations and calculates distribution.
     */
    private static class FeatureObservations {
        private final Map<String, Long> bucketCounts = new ConcurrentHashMap<>();
        private final AtomicLong totalCount = new AtomicLong();

        void recordObservation(String bucket) {
            bucketCounts.merge(bucket, 1L, Long::sum);
            totalCount.incrementAndGet();
        }

        double getPercentage(String bucket) {
            long count = bucketCounts.getOrDefault(bucket, 0L);
            long total = totalCount.get();
            return total == 0 ? 0.0 : (double) count / total;
        }

        long getTotalCount() {
            return totalCount.get();
        }
    }
}
