/*
 * Copyright (c) 2026 Ghatana Inc. All rights reserved.
 */
package com.ghatana.datacloud.analytics.anomaly;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Production-grade statistical anomaly detector for entity collections.
 *
 * <p>Implements {@link AnomalyDetectionCapability} using two complementary methods:
 * <ol>
 *   <li><b>Z-score</b> (σ-distance) — robust for normally-distributed metrics;
 *       flags values whose |z| ≥ threshold (default {@value #DEFAULT_Z_THRESHOLD}).</li>
 *   <li><b>IQR fence</b> (Tukey fences) — robust for skewed distributions;
 *       flags values below Q1 − 1.5·IQR or above Q3 + 1.5·IQR.</li>
 * </ol>
 *
 * <p>Detection is scoped to numeric fields in the entity {@code data} map.
 * Non-numeric fields are silently skipped.
 *
 * <p><b>Baseline management</b><br>
 * Baselines are built on every {@link #updateBaseline} call by scanning the full
 * collection (up to {@value #MAX_SAMPLE_SIZE} entities) and computing descriptive
 * statistics per numeric field. Baselines are stored in-process; call
 * {@code updateBaseline} at startup or on a schedule.
 *
 * <p><b>Thread-safety</b><br>
 * All blocking work (repository scans, statistics computation) is dispatched to a
 * virtual-thread executor so the ActiveJ event loop is never blocked.
 *
 * <p><b>Observability</b><br>
 * Emits Micrometer metrics:
 * <ul>
 *   <li>{@code data_cloud.anomaly.detected} (counter, tags: detector, severity)</li>
 *   <li>{@code data_cloud.anomaly.baseline_updates} (counter)</li>
 *   <li>{@code data_cloud.anomaly.detection_errors} (counter)</li>
 *   <li>{@code data_cloud.anomaly.detection_duration} (timer)</li>
 * </ul>
 *
 * @doc.type service
 * @doc.purpose Statistical anomaly detection (Z-score + IQR) for entity collections
 * @doc.layer product
 * @doc.pattern Strategy, Observer
 */
public final class StatisticalAnomalyDetector implements AnomalyDetectionCapability {

    private static final Logger LOG = LoggerFactory.getLogger(StatisticalAnomalyDetector.class);

    /** Maximum entities fetched per baseline update or detection pass. */
    static final int MAX_SAMPLE_SIZE = 10_000;

    /**
     * Default Z-score threshold: values further than 3σ from the mean are anomalies.
     * Corresponds to ≈ 0.27% false-positive rate under a normal distribution.
     */
    public static final double DEFAULT_Z_THRESHOLD = 3.0;

    /** Tukey fence multiplier for IQR-based outlier detection. */
    static final double IQR_MULTIPLIER = 1.5;

    /** Minimum sample count required to compute meaningful baseline statistics. */
    static final int MIN_SAMPLE_COUNT = 5;

    // ── Dependencies ────────────────────────────────────────────────────────────

    private final EntityRepository entityRepository;
    private final Executor executor;
    private final double zThreshold;

    // ── Baseline storage ─────────────────────────────────────────────────────────
    // Composite key: "tenantId::collectionName::fieldName"
    private final ConcurrentHashMap<String, FieldBaseline> baselines = new ConcurrentHashMap<>();

    // ── Metrics ───────────────────────────────────────────────────────────────
    private final Counter detectedCounter;
    private final Counter detectedCritical;
    private final Counter baselineUpdateCounter;
    private final Counter detectionErrorCounter;
    private final Timer   detectionTimer;

    /**
     * Creates a detector backed by the given entity repository.
     *
     * @param entityRepository repository used for baseline population and detection scans
     * @param meterRegistry    Micrometer registry for operational metrics
     */
    public StatisticalAnomalyDetector(EntityRepository entityRepository,
                                      MeterRegistry meterRegistry) {
        this(entityRepository, meterRegistry, DEFAULT_Z_THRESHOLD,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Full constructor for testing or custom configuration.
     *
     * @param entityRepository repository for data access
     * @param meterRegistry    Micrometer registry for metrics
     * @param zThreshold       Z-score threshold (σ units); typical range 2.0 – 3.5
     * @param executor         executor for off-loop blocking work
     */
    StatisticalAnomalyDetector(EntityRepository entityRepository,
                               MeterRegistry meterRegistry,
                               double zThreshold,
                               Executor executor) {
        this.entityRepository = Objects.requireNonNull(entityRepository, "entityRepository");
        this.zThreshold = zThreshold;
        this.executor = Objects.requireNonNull(executor, "executor");

        this.detectedCounter = Counter.builder("data_cloud.anomaly.detected")
                .description("Total anomalies detected")
                .tag("detector", "statistical")
                .register(meterRegistry);
        this.detectedCritical = Counter.builder("data_cloud.anomaly.detected")
                .description("Critical anomalies detected")
                .tag("detector", "statistical")
                .tag("severity", "CRITICAL")
                .register(meterRegistry);
        this.baselineUpdateCounter = Counter.builder("data_cloud.anomaly.baseline_updates")
                .description("Baseline update operations")
                .register(meterRegistry);
        this.detectionErrorCounter = Counter.builder("data_cloud.anomaly.detection_errors")
                .description("Detection errors")
                .register(meterRegistry);
        this.detectionTimer = Timer.builder("data_cloud.anomaly.detection_duration")
                .description("End-to-end detection latency")
                .register(meterRegistry);
    }

    // ── AnomalyDetectionCapability ─────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Fetches the latest entities in the collection, then in a blocking executor
     * checks each numeric field value against the cached baseline using Z-score and
     * IQR fence algorithms.
     */
    @Override
    public Promise<List<Anomaly>> detect(AnomalyContext context) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(context.getTenantId(), "tenantId");
        Objects.requireNonNull(context.getCollectionName(), "collectionName");

        double threshold = context.getThreshold() > 0 ? context.getThreshold() : zThreshold;

        // Step 1 — fetch entities on the event loop (non-blocking DB call)
        return entityRepository.findAll(
                context.getTenantId(),
                context.getCollectionName(),
                Collections.emptyMap(),
                "createdAt:DESC",
                0,
                MAX_SAMPLE_SIZE
        )
        // Step 2 — do CPU-bound statistics in a virtual-thread executor
        .then(entities -> Promise.ofBlocking(executor, () -> {
            Timer.Sample sample = Timer.start();
            try {
                return detectSync(context, entities, threshold);
            } catch (Exception e) {
                detectionErrorCounter.increment();
                LOG.error("Anomaly detection failed for tenant={} collection={}",
                        context.getTenantId(), context.getCollectionName(), e);
                throw e;
            } finally {
                sample.stop(detectionTimer);
            }
        }));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Fetches the full collection (up to {@value #MAX_SAMPLE_SIZE} entities) on
     * the event loop, then recomputes baselines for all numeric fields in a
     * virtual-thread executor.
     */
    @Override
    public Promise<Void> updateBaseline(String tenantId, String collectionName) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(collectionName, "collectionName");

        return entityRepository.findAll(
                tenantId, collectionName,
                Collections.emptyMap(), "createdAt:DESC",
                0, MAX_SAMPLE_SIZE
        )
        .then(entities -> Promise.ofBlocking(executor, () -> {
            Map<String, List<Double>> fieldValues = collectNumericFields(entities);
            int updated = 0;
            for (Map.Entry<String, List<Double>> e : fieldValues.entrySet()) {
                if (e.getValue().size() < MIN_SAMPLE_COUNT) {
                    continue; // insufficient data for a reliable baseline
                }
                FieldBaseline baseline = computeBaseline(e.getKey(), e.getValue());
                baselines.put(baselineKey(tenantId, collectionName, e.getKey()), baseline);
                updated++;
            }
            baselineUpdateCounter.increment();
            LOG.debug("Baseline updated tenant={} collection={} fields={}", tenantId, collectionName, updated);
            return (Void) null;
        }));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the cached baseline for the given metric field, or a failed Promise
     * if no baseline has been computed yet.
     */
    @Override
    public Promise<BaselineStatistics> getBaseline(String tenantId,
                                                   String collectionName,
                                                   String metricName) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(collectionName, "collectionName");
        Objects.requireNonNull(metricName, "metricName");

        FieldBaseline fb = baselines.get(baselineKey(tenantId, collectionName, metricName));
        if (fb == null) {
            return Promise.ofException(new IllegalStateException(
                    "No baseline found for " + tenantId + "/" + collectionName + "/" + metricName
                            + ". Call updateBaseline() first."));
        }
        return Promise.of(BaselineStatistics.builder()
                .metricName(metricName)
                .mean(fb.mean)
                .standardDeviation(fb.stdDev)
                .median(fb.median)
                .p25(fb.p25)
                .p75(fb.p75)
                .p95(fb.p95)
                .p99(fb.p99)
                .min(fb.min)
                .max(fb.max)
                .sampleCount(fb.sampleCount)
                .lastUpdated(fb.lastUpdated)
                .build());
    }

    /** {@inheritDoc} */
    @Override
    public Promise<List<DetectionType>> getSupportedDetectionTypes() {
        return Promise.of(List.of(DetectionType.DATA_QUALITY, DetectionType.BEHAVIORAL));
    }

    // ── Detection logic (runs off event loop) ─────────────────────────────────

    private List<Anomaly> detectSync(AnomalyContext context,
                                     List<Entity> entities,
                                     double threshold) {
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }

        String tenantId  = context.getTenantId();
        String collection = context.getCollectionName();
        List<Anomaly> anomalies = new ArrayList<>();

        for (Entity entity : entities) {
            if (entity.getData() == null) continue;

            for (Map.Entry<String, Object> field : entity.getData().entrySet()) {
                Double value = toDouble(field.getValue());
                if (value == null) continue;

                String bKey = baselineKey(tenantId, collection, field.getKey());
                FieldBaseline baseline = baselines.get(bKey);
                if (baseline == null || baseline.sampleCount < MIN_SAMPLE_COUNT) {
                    continue; // skip fields without a valid baseline
                }

                // Z-score check
                double z = zScore(value, baseline.mean, baseline.stdDev);
                if (Math.abs(z) >= threshold) {
                    Anomaly a = buildZScoreAnomaly(entity, field.getKey(), value, baseline,
                            z, context.getDetectionType());
                    anomalies.add(a);
                    recordMetrics(a);
                    continue; // one anomaly per field per entity is sufficient
                }

                // IQR fence check (Tukey method)
                double lowerFence = baseline.p25 - IQR_MULTIPLIER * baseline.iqr;
                double upperFence = baseline.p75 + IQR_MULTIPLIER * baseline.iqr;
                if (value < lowerFence || value > upperFence) {
                    double deviation = value < lowerFence
                            ? (lowerFence - value) / Math.max(1.0, Math.abs(lowerFence))
                            : (value - upperFence) / Math.max(1.0, Math.abs(upperFence));
                    Anomaly a = buildIqrAnomaly(entity, field.getKey(), value, baseline,
                            deviation, lowerFence, upperFence, context.getDetectionType());
                    anomalies.add(a);
                    recordMetrics(a);
                }
            }
        }

        LOG.debug("Detection finished tenant={} collection={} entities={} anomalies={}",
                tenantId, collection, entities.size(), anomalies.size());
        return anomalies;
    }

    // ── Statistics helpers ─────────────────────────────────────────────────────

    private static Map<String, List<Double>> collectNumericFields(List<Entity> entities) {
        Map<String, List<Double>> result = new LinkedHashMap<>();
        for (Entity entity : entities) {
            if (entity.getData() == null) continue;
            for (Map.Entry<String, Object> e : entity.getData().entrySet()) {
                Double value = toDouble(e.getValue());
                if (value != null) {
                    result.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(value);
                }
            }
        }
        return result;
    }

    private static FieldBaseline computeBaseline(String fieldName, List<Double> values) {
        List<Double> sorted = values.stream().sorted().collect(Collectors.toList());
        int n = sorted.size();

        double mean = mean(values);
        double variance = values.stream()
                .mapToDouble(v -> (v - mean) * (v - mean))
                .average().orElse(0.0);
        double stdDev = Math.sqrt(variance);

        double p25    = percentile(sorted, 25);
        double p75    = percentile(sorted, 75);
        double iqr    = p75 - p25;

        return new FieldBaseline(
                fieldName, mean, stdDev,
                percentile(sorted, 50),
                p25, p75,
                percentile(sorted, 95), percentile(sorted, 99),
                sorted.get(0), sorted.get(n - 1),
                iqr, n, Instant.now()
        );
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /**
     * Computes the {@code p}-th percentile of a pre-sorted list using linear interpolation.
     *
     * @param sorted sorted list of values (ascending)
     * @param p      percentile in [0, 100]
     * @return interpolated percentile value
     */
    static double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return 0.0;
        if (sorted.size() == 1) return sorted.get(0);
        double index = (p / 100.0) * (sorted.size() - 1);
        int lower = (int) index;
        int upper = Math.min(lower + 1, sorted.size() - 1);
        double frac = index - lower;
        return sorted.get(lower) * (1 - frac) + sorted.get(upper) * frac;
    }

    private static double zScore(double value, double mean, double stdDev) {
        return stdDev == 0.0 ? 0.0 : (value - mean) / stdDev;
    }

    // ── Anomaly builders ───────────────────────────────────────────────────────

    private Anomaly buildZScoreAnomaly(Entity entity, String field, double value,
                                       FieldBaseline baseline, double z,
                                       DetectionType type) {
        double absZ = Math.abs(z);
        Severity severity = zToSeverity(absZ);
        Instant occurred = entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now();
        return Anomaly.builder()
                .anomalyId(UUID.randomUUID().toString())
                .type(type != null ? type : DetectionType.DATA_QUALITY)
                .severity(severity)
                .confidence(zToConfidence(absZ))
                .anomalyScore(absZ)
                .title("Numeric outlier in field '" + field + "'")
                .description(String.format(
                        "Value %.4f is %.2fσ from baseline mean %.4f (σ=%.4f)",
                        value, absZ, baseline.mean, baseline.stdDev))
                .detectionMethod("Z-Score")
                .affectedEntity(entity.getId() != null ? entity.getId().toString() : "unknown")
                .observedValue(value)
                .expectedValue(baseline.mean)
                .deviation(absZ)
                .occurrenceTime(occurred)
                .evidence(Map.of(
                        "field", field,
                        "zScore", z,
                        "mean", baseline.mean,
                        "stdDev", baseline.stdDev,
                        "sampleCount", baseline.sampleCount))
                .suggestedActions(suggestedActions(severity))
                .build();
    }

    private Anomaly buildIqrAnomaly(Entity entity, String field, double value,
                                    FieldBaseline baseline, double deviation,
                                    double lowerFence, double upperFence,
                                    DetectionType type) {
        Severity severity = deviationToSeverity(deviation);
        Instant occurred = entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now();
        return Anomaly.builder()
                .anomalyId(UUID.randomUUID().toString())
                .type(type != null ? type : DetectionType.DATA_QUALITY)
                .severity(severity)
                .confidence(Math.min(0.95, 0.5 + deviation * 0.2))
                .anomalyScore(deviation)
                .title("IQR outlier in field '" + field + "'")
                .description(String.format(
                        "Value %.4f is outside the Tukey fence [%.4f, %.4f] (IQR=%.4f)",
                        value, lowerFence, upperFence, baseline.iqr))
                .detectionMethod("IQR-Fence")
                .affectedEntity(entity.getId() != null ? entity.getId().toString() : "unknown")
                .observedValue(value)
                .expectedValue(String.format("[%.4f, %.4f]", lowerFence, upperFence))
                .deviation(deviation)
                .occurrenceTime(occurred)
                .evidence(Map.of(
                        "field", field,
                        "lowerFence", lowerFence,
                        "upperFence", upperFence,
                        "iqr", baseline.iqr,
                        "p25", baseline.p25,
                        "p75", baseline.p75))
                .suggestedActions(suggestedActions(severity))
                .build();
    }

    private static List<String> suggestedActions(Severity severity) {
        return switch (severity) {
            case CRITICAL -> List.of(
                    "Immediately inspect the record for data corruption or ingestion errors",
                    "Cross-reference with the upstream source system",
                    "Trigger a data quality alert to the responsible team");
            case HIGH -> List.of(
                    "Investigate the record against the source system",
                    "Check recent ingestion pipeline for schema changes");
            case MEDIUM -> List.of(
                    "Review as part of the next data quality cycle",
                    "Validate against business rules for this collection");
            default -> List.of("Monitor; this may be a valid edge case");
        };
    }

    // ── Severity/confidence mapping ────────────────────────────────────────────

    static Severity zToSeverity(double absZ) {
        if (absZ >= 5.0) return Severity.CRITICAL;
        if (absZ >= 4.0) return Severity.HIGH;
        if (absZ >= 3.0) return Severity.MEDIUM;
        return Severity.LOW;
    }

    private static double zToConfidence(double absZ) {
        // Empirical sigmoid: z=3→0.80, z=4→0.90, z=5→0.97
        return Math.min(0.99, 0.5 + 0.1 * absZ);
    }

    static Severity deviationToSeverity(double deviation) {
        if (deviation >= 2.0) return Severity.CRITICAL;
        if (deviation >= 1.0) return Severity.HIGH;
        if (deviation >= 0.5) return Severity.MEDIUM;
        return Severity.LOW;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    static String baselineKey(String tenantId, String collection, String field) {
        return tenantId + "::" + collection + "::" + field;
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                // non-numeric string — skip
            }
        }
        return null;
    }

    private void recordMetrics(Anomaly anomaly) {
        detectedCounter.increment();
        if (anomaly.getSeverity() == Severity.CRITICAL) {
            detectedCritical.increment();
        }
    }

    // ── Internal baseline record ───────────────────────────────────────────────

    /**
     * Immutable snapshot of descriptive statistics for one numeric field.
     *
     * @doc.type record
     * @doc.purpose Internal baseline stats snapshot
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    record FieldBaseline(
            String fieldName,
            double mean,
            double stdDev,
            double median,
            double p25,
            double p75,
            double p95,
            double p99,
            double min,
            double max,
            double iqr,
            long sampleCount,
            Instant lastUpdated
    ) {}
}
