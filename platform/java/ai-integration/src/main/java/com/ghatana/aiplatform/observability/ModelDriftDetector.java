package com.ghatana.aiplatform.observability;

import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Detects model output drift and quality degradation over time windows.
 *
 * <p><b>Purpose</b><br>
 * Monitors for model behavior drift (output distribution changes) and quality drift
 * (prediction quality degradation) over sliding time windows. Distinct from DataDriftDetector
 * which monitors input feature distributions.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ModelDriftDetector modelDrift = new ModelDriftDetector(
 *     metrics,
 *     Duration.ofHours(1)  // 1-hour windows
 * );
 *
 * // Record prediction output
 * modelDrift.recordOutput("tenant-123", "model-v2", 0.95, "fraud");
 *
 * // Check output distribution over window
 * Optional<ModelDriftResult> result = modelDrift.checkOutputDrift("tenant-123", "model-v2");
 *
 * if (result.isPresent() && result.get().isDrift) {
 *     // Model behavior changed - investigate
 *     double outputShift = result.get().kldivergence;
 * }
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Complements DataDriftDetector: while DataDriftDetector monitors input features,
 * ModelDriftDetector monitors output distributions. Used by automated retraining
 * and rollback systems.
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe: uses ConcurrentHashMap and atomic counters.
 *
 * @doc.type class
 * @doc.purpose Model output drift detection over time windows
 * @doc.layer platform
 * @doc.pattern Monitor
 */
public class ModelDriftDetector {

    private static final Logger log = LoggerFactory.getLogger(ModelDriftDetector.class);

    private static final double KL_DIVERGENCE_WARNING_THRESHOLD = 0.15;
    private static final double KL_DIVERGENCE_ALERT_THRESHOLD = 0.40;

    private final MetricsCollector metrics;
    private final long windowDurationMs;

    // tenant:modelName -> OutputWindow
    private final ConcurrentHashMap<String, OutputWindow> currentWindows = new ConcurrentHashMap<>();

    // tenant:modelName -> BaselineOutputDistribution
    private final ConcurrentHashMap<String, OutputDistribution> baselines = new ConcurrentHashMap<>();

    /**
     * Constructor.
     *
     * @param metrics MetricsCollector for observability
     * @param windowDurationMs duration of time window in milliseconds
     */
    public ModelDriftDetector(MetricsCollector metrics, long windowDurationMs) {
        this.metrics = metrics;
        this.windowDurationMs = windowDurationMs;
    }

    /**
     * Record model prediction output.
     *
     * GIVEN: Tenant, model name, output, and class label
     * WHEN: recordOutput() is called
     * THEN: Output recorded in current time window
     *
     * @param tenantId tenant identifier
     * @param modelName model identifier
     * @param confidence output confidence/score
     * @param predictedClass predicted class/label
     */
    public void recordOutput(String tenantId, String modelName, double confidence, String predictedClass) {
        if (tenantId == null || modelName == null || predictedClass == null) {
            throw new NullPointerException("tenant, model, and class cannot be null");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be in [0.0, 1.0]");
        }

        String key = tenantId + ":" + modelName;
        OutputWindow window = currentWindows.computeIfAbsent(key, k -> new OutputWindow(
            System.currentTimeMillis() + windowDurationMs
        ));

        // Rotate window if expired
        if (System.currentTimeMillis() >= window.expiresAtMs) {
            window = new OutputWindow(System.currentTimeMillis() + windowDurationMs);
            currentWindows.put(key, window);
        }

        window.recordOutput(predictedClass, confidence);
    }

    /**
     * Set baseline output distribution for model.
     *
     * GIVEN: Model and baseline distribution (class -> frequency)
     * WHEN: setBaseline() is called
     * THEN: Baseline established for drift comparison
     *
     * @param tenantId tenant identifier
     * @param modelName model identifier
     * @param distribution baseline class distribution
     */
    public void setBaseline(String tenantId, String modelName, Map<String, Double> distribution) {
        if (tenantId == null || modelName == null || distribution == null) {
            throw new NullPointerException("tenant, model, and distribution cannot be null");
        }

        String key = tenantId + ":" + modelName;
        OutputDistribution baseline = new OutputDistribution(new HashMap<>(distribution));
        baselines.put(key, baseline);

        metrics.incrementCounter(
            "ai.modeldrift.baseline.set",
            "tenant", tenantId,
            "model", modelName
        );

        log.debug("Set baseline for {}:{} with {} classes", tenantId, modelName, distribution.size());
    }

    /**
     * Check for output drift in current window.
     *
     * GIVEN: Tenant and model name
     * WHEN: checkOutputDrift() is called
     * THEN: Returns drift analysis comparing current window to baseline
     *
     * @param tenantId tenant identifier
     * @param modelName model identifier
     * @return Optional containing ModelDriftResult if baseline exists
     */
    public Optional<ModelDriftResult> checkOutputDrift(String tenantId, String modelName) {
        String key = tenantId + ":" + modelName;
        OutputWindow window = currentWindows.get(key);
        OutputDistribution baseline = baselines.get(key);

        if (baseline == null) {
            return Optional.empty();
        }

        if (window == null || window.getTotalOutputs() == 0) {
            // No outputs in window
            return Optional.of(new ModelDriftResult(
                false,     // no drift without data
                0.0,       // KL divergence = 0
                0.0,       // Jensen-Shannon = 0
                Collections.emptyMap(),
                System.currentTimeMillis()
            ));
        }

        // Calculate KL divergence
        OutputDistribution current = window.toDistribution();
        double klDiv = calculateKLDivergence(baseline, current);
        double jsDist = calculateJensenShannonDistance(baseline, current);

        boolean isDrift = klDiv > KL_DIVERGENCE_WARNING_THRESHOLD;
        boolean isAlert = klDiv > KL_DIVERGENCE_ALERT_THRESHOLD;

        // Emit metrics
        metrics.recordTimer(
            "ai.modeldrift.kldivergence",
            (long)(klDiv * 100),  // Convert to 0-100 range for metrics
            "tenant", tenantId,
            "model", modelName
        );

        if (isAlert) {
            metrics.incrementCounter(
                "ai.modeldrift.alert",
                "tenant", tenantId,
                "model", modelName,
                "severity", "high"
            );
            log.warn("Model drift ALERT for {}:{}: KL={}, classes affected: {}",
                tenantId, modelName, String.format("%.3f", klDiv), current.distribution.keySet());
        } else if (isDrift) {
            metrics.incrementCounter(
                "ai.modeldrift.warning",
                "tenant", tenantId,
                "model", modelName,
                "severity", "medium"
            );
            log.info("Model drift WARNING for {}:{}: KL={}", tenantId, modelName,
                String.format("%.3f", klDiv));
        }

        return Optional.of(new ModelDriftResult(
            isDrift,
            klDiv,
            jsDist,
            current.distribution,
            System.currentTimeMillis()
        ));
    }

    /**
     * Get current output distribution stats.
     *
     * @param tenantId tenant identifier
     * @param modelName model identifier
     * @return distribution stats or empty if no outputs recorded
     */
    public Optional<OutputStats> getOutputStats(String tenantId, String modelName) {
        String key = tenantId + ":" + modelName;
        OutputWindow window = currentWindows.get(key);

        if (window == null) {
            return Optional.empty();
        }

        OutputDistribution dist = window.toDistribution();
        return Optional.of(new OutputStats(
            dist.distribution,
            window.getTotalOutputs(),
            window.getAverageConfidence()
        ));
    }

    /**
     * Reset window for next monitoring period.
     *
     * @param tenantId tenant identifier
     * @param modelName model identifier
     */
    public void resetWindow(String tenantId, String modelName) {
        String key = tenantId + ":" + modelName;
        currentWindows.remove(key);
        metrics.incrementCounter(
            "ai.modeldrift.window.reset",
            "tenant", tenantId,
            "model", modelName
        );
    }

    private double calculateKLDivergence(OutputDistribution baseline, OutputDistribution current) {
        double kl = 0.0;

        Set<String> allClasses = new HashSet<>(baseline.distribution.keySet());
        allClasses.addAll(current.distribution.keySet());

        for (String cls : allClasses) {
            double p = baseline.distribution.getOrDefault(cls, 0.0);
            double q = current.distribution.getOrDefault(cls, 0.0);

            // Add pseudocount to avoid log(0)
            p = Math.max(p, 0.0001);
            q = Math.max(q, 0.0001);

            kl += p * Math.log(p / q);
        }

        return kl;
    }

    private double calculateJensenShannonDistance(OutputDistribution baseline, OutputDistribution current) {
        double klPQ = calculateKLDivergence(baseline, current);
        double klQP = calculateKLDivergence(current, baseline);
        return Math.sqrt((klPQ + klQP) / 2.0);
    }

    /**
     * Time window for collecting outputs.
     */
    private static class OutputWindow {
        private final long expiresAtMs;
        private final Map<String, Long> classFrequencies = new ConcurrentHashMap<>();
        private final AtomicLong totalOutputs = new AtomicLong(0);
        private final AtomicLong confidenceSum = new AtomicLong(0);

        OutputWindow(long expiresAtMs) {
            this.expiresAtMs = expiresAtMs;
        }

        void recordOutput(String predictedClass, double confidence) {
            classFrequencies.compute(predictedClass, (k, v) -> (v == null ? 1 : v + 1));
            totalOutputs.incrementAndGet();
            confidenceSum.addAndGet((long)(confidence * 1000));  // Store as long for atomicity
        }

        long getTotalOutputs() {
            return totalOutputs.get();
        }

        double getAverageConfidence() {
            long total = totalOutputs.get();
            return total > 0 ? confidenceSum.get() / 1000.0 / total : 0.0;
        }

        OutputDistribution toDistribution() {
            long total = totalOutputs.get();
            Map<String, Double> dist = new HashMap<>();

            if (total > 0) {
                classFrequencies.forEach((cls, freq) ->
                    dist.put(cls, (double) freq / total)
                );
            }

            return new OutputDistribution(dist);
        }
    }

    /**
     * Output distribution (normalized probabilities).
     */
    private static class OutputDistribution {
        final Map<String, Double> distribution;

        OutputDistribution(Map<String, Double> distribution) {
            this.distribution = distribution;
        }
    }

    /**
     * Model drift detection result.
     */
    public static class ModelDriftResult {
        public final boolean isDrift;
        public final double klDivergence;
        public final double jensenShannonDistance;
        public final Map<String, Double> currentDistribution;
        public final long detectedAtMs;

        public ModelDriftResult(boolean isDrift, double klDivergence, double jensenShannonDistance,
                               Map<String, Double> currentDistribution, long detectedAtMs) {
            this.isDrift = isDrift;
            this.klDivergence = klDivergence;
            this.jensenShannonDistance = jensenShannonDistance;
            this.currentDistribution = Collections.unmodifiableMap(currentDistribution);
            this.detectedAtMs = detectedAtMs;
        }
    }

    /**
     * Output statistics for monitoring.
     */
    public static class OutputStats {
        public final Map<String, Double> classDistribution;
        public final long totalOutputs;
        public final double averageConfidence;

        public OutputStats(Map<String, Double> classDistribution, long totalOutputs, double averageConfidence) {
            this.classDistribution = classDistribution;
            this.totalOutputs = totalOutputs;
            this.averageConfidence = averageConfidence;
        }
    }
}
