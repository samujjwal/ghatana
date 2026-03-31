package com.ghatana.aiplatform.featurestore;

import com.ghatana.platform.core.event.EventBusPort;
import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Statistical drift detection for ML feature distributions.
 *
 * <p>Computes Population Stability Index (PSI) and Kolmogorov–Smirnov (KS) statistics
 * between a reference distribution (from training data) and a current production window.
 * When drift exceeds the configured threshold, emits a {@code feature.drift_detected}
 * event via {@link EventBusPort} and records a metric.</p>
 *
 * <p><b>Thresholds (PSI convention):</b></p>
 * <ul>
 *   <li>PSI &lt; 0.1 — No significant drift (STABLE)</li>
 *   <li>0.1 ≤ PSI &lt; 0.2 — Moderate drift (WARNING)</li>
 *   <li>PSI ≥ 0.2 — Significant drift (DRIFT_DETECTED)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Statistical feature drift detection using PSI and KS tests
 * @doc.layer platform
 * @doc.pattern Service
 */
public class FeatureDriftDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureDriftDetector.class);

    static final double PSI_DRIFT_THRESHOLD = 0.2;
    static final double PSI_WARNING_THRESHOLD = 0.1;
    static final int DEFAULT_BUCKET_COUNT = 10;
    static final double EPSILON = 1e-6;

    private final MetricsCollector metrics;
    private final EventBusPort eventBus;
    private final int bucketCount;

    public FeatureDriftDetector(MetricsCollector metrics, EventBusPort eventBus) {
        this(metrics, eventBus, DEFAULT_BUCKET_COUNT);
    }

    public FeatureDriftDetector(MetricsCollector metrics, EventBusPort eventBus, int bucketCount) {
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
        if (bucketCount < 2) {
            throw new IllegalArgumentException("bucketCount must be >= 2");
        }
        this.bucketCount = bucketCount;
    }

    /**
     * Checks a feature for drift by comparing reference and current value distributions.
     *
     * @param featureName name of the feature being checked
     * @param referenceValues values from the training / reference distribution
     * @param currentValues   values from the current production window
     * @return drift result containing PSI, KS statistic, and status
     */
    public DriftResult check(String featureName, double[] referenceValues, double[] currentValues) {
        Objects.requireNonNull(featureName, "featureName must not be null");
        if (referenceValues == null || referenceValues.length == 0) {
            throw new IllegalArgumentException("referenceValues must not be null or empty");
        }
        if (currentValues == null || currentValues.length == 0) {
            throw new IllegalArgumentException("currentValues must not be null or empty");
        }

        double psi = computePSI(referenceValues, currentValues);
        double ks = computeKS(referenceValues, currentValues);
        DriftStatus status = classifyDrift(psi);

        DriftResult result = new DriftResult(featureName, psi, ks, status, Instant.now(),
                referenceValues.length, currentValues.length);

        // Emit metric
        metrics.increment("feature.drift.psi", psi, Map.of(
                "feature", featureName,
                "status", status.name()
        ));
        metrics.incrementCounter("feature.drift.check", "feature", featureName, "status", status.name());

        if (status == DriftStatus.DRIFT_DETECTED) {
            LOGGER.warn("Feature drift detected for '{}': PSI={}, KS={}", featureName, psi, ks);
            eventBus.publish(new FeatureDriftEvent(featureName, psi, ks, status,
                    referenceValues.length, currentValues.length, Instant.now()));
        } else if (status == DriftStatus.WARNING) {
            LOGGER.info("Feature drift warning for '{}': PSI={}, KS={}", featureName, psi, ks);
        }

        return result;
    }

    /**
     * Computes the Population Stability Index (PSI) between two distributions.
     * PSI = Σ (p_i - q_i) × ln(p_i / q_i competition) where p = current, q = reference.
     */
    double computePSI(double[] reference, double[] current) {
        double min = Math.min(minOf(reference), minOf(current));
        double max = Math.max(maxOf(reference), maxOf(current));

        if (max - min < EPSILON) {
            return 0.0;
        }

        double[] refProportions = bucketize(reference, min, max);
        double[] curProportions = bucketize(current, min, max);

        double psi = 0.0;
        for (int i = 0; i < bucketCount; i++) {
            double p = Math.max(curProportions[i], EPSILON);
            double q = Math.max(refProportions[i], EPSILON);
            psi += (p - q) * Math.log(p / q);
        }
        return psi;
    }

    /**
     * Computes the Kolmogorov–Smirnov statistic: maximum absolute difference
     * between the empirical CDFs of the two distributions.
     */
    double computeKS(double[] reference, double[] current) {
        double[] refSorted = reference.clone();
        double[] curSorted = current.clone();
        Arrays.sort(refSorted);
        Arrays.sort(curSorted);

        int n1 = refSorted.length;
        int n2 = curSorted.length;
        int i = 0, j = 0;
        double maxDiff = 0.0;

        while (i < n1 && j < n2) {
            double cdf1 = (double) (i + 1) / n1;
            double cdf2 = (double) (j + 1) / n2;

            if (refSorted[i] <= curSorted[j]) {
                maxDiff = Math.max(maxDiff, Math.abs(cdf1 - (double) j / n2));
                i++;
            } else {
                maxDiff = Math.max(maxDiff, Math.abs((double) i / n1 - cdf2));
                j++;
            }
        }

        while (i < n1) {
            maxDiff = Math.max(maxDiff, Math.abs((double) (i + 1) / n1 - 1.0));
            i++;
        }
        while (j < n2) {
            maxDiff = Math.max(maxDiff, Math.abs(1.0 - (double) (j + 1) / n2));
            j++;
        }

        return maxDiff;
    }

    private double[] bucketize(double[] values, double min, double max) {
        double[] proportions = new double[bucketCount];
        double width = (max - min) / bucketCount;

        for (double v : values) {
            int bucket = (int) ((v - min) / width);
            if (bucket >= bucketCount) {
                bucket = bucketCount - 1;
            }
            if (bucket < 0) {
                bucket = 0;
            }
            proportions[bucket]++;
        }

        for (int i = 0; i < bucketCount; i++) {
            proportions[i] /= values.length;
        }
        return proportions;
    }

    private static DriftStatus classifyDrift(double psi) {
        if (psi >= PSI_DRIFT_THRESHOLD) {
            return DriftStatus.DRIFT_DETECTED;
        } else if (psi >= PSI_WARNING_THRESHOLD) {
            return DriftStatus.WARNING;
        }
        return DriftStatus.STABLE;
    }

    private static double minOf(double[] arr) {
        double min = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] < min) min = arr[i];
        }
        return min;
    }

    private static double maxOf(double[] arr) {
        double max = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > max) max = arr[i];
        }
        return max;
    }

    // --- Domain types ---

    public enum DriftStatus {
        STABLE, WARNING, DRIFT_DETECTED
    }

    public record DriftResult(
            String featureName,
            double psi,
            double ksStatistic,
            DriftStatus status,
            Instant checkedAt,
            int referenceCount,
            int currentCount
    ) {}

    public record FeatureDriftEvent(
            String featureName,
            double psi,
            double ksStatistic,
            DriftStatus status,
            int referenceCount,
            int currentCount,
            Instant detectedAt
    ) {
        public String eventType() {
            return "feature.drift_detected";
        }
    }
}
