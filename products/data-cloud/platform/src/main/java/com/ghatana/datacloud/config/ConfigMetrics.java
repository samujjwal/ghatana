/*
 * Copyright (c) 2025 Ghatana Platforms, Inc. All rights reserved.
 */
package com.ghatana.datacloud.config;

import com.ghatana.platform.observability.MetricsCollector;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Enhanced metrics collection for configuration operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides comprehensive observability for configuration operations including:
 * <ul>
 * <li>Latency tracking with p50/p99/p999 percentiles</li>
 * <li>Operation counters (success/failure)</li>
 * <li>Cache statistics (hit/miss ratios)</li>
 * <li>Reload metrics and version tracking</li>
 * </ul>
 *
 * <p>
 * <b>Metric Naming Convention</b><br>
 * All metrics follow the pattern: {@code config.<operation>.<aspect>}
 * <pre>
 * config.lookup.duration      - Time to look up a config (nanoseconds)
 * config.load.duration        - Time to load+compile a config (milliseconds)
 * config.reload.duration      - Time to reload a config (milliseconds)
 * config.cache.hit            - Cache hit counter
 * config.cache.miss           - Cache miss counter
 * config.validation.failure   - Validation failure counter
 * </pre>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ConfigMetrics configMetrics = new ConfigMetrics(metricsCollector);
 *
 * // Time a lookup operation
 * CompiledConfig config = configMetrics.timeLookup("tenant-1", "users", () -> {
 *     return cache.get(key);
 * });
 *
 * // Time a load operation
 * CompiledConfig loaded = configMetrics.timeLoad("tenant-1", "users", () -> {
 *     return loader.load(tenantId, name);
 * });
 *
 * // Record a reload
 * configMetrics.recordReload("tenant-1", "users", durationMs, true);
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe. Uses atomic operations and concurrent data structures.
 *
 * @doc.type class
 * @doc.purpose Enhanced metrics collection for configuration observability
 * @doc.layer core
 * @doc.pattern Utility
 */
public class ConfigMetrics {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigMetrics.class);

    // Metric name constants
    public static final String LOOKUP_DURATION_NS = "config.lookup.duration.ns";
    public static final String LOAD_DURATION_MS = "config.load.duration.ms";
    public static final String RELOAD_DURATION_MS = "config.reload.duration.ms";
    public static final String CACHE_HIT = "config.cache.hit";
    public static final String CACHE_MISS = "config.cache.miss";
    public static final String LOAD_SUCCESS = "config.load.success";
    public static final String LOAD_FAILURE = "config.load.failure";
    public static final String RELOAD_SUCCESS = "config.reload.success";
    public static final String RELOAD_FAILURE = "config.reload.failure";
    public static final String VALIDATION_FAILURE = "config.validation.failure";
    public static final String COMPILATION_FAILURE = "config.compilation.failure";

    private final MetricsCollector metrics;

    // Internal counters for quick access
    private final AtomicLong totalLookups;
    private final AtomicLong totalCacheHits;
    private final AtomicLong totalCacheMisses;
    private final AtomicLong totalLoads;
    private final AtomicLong totalReloads;
    private final AtomicLong totalFailures;

    // Per-config type statistics
    private final ConcurrentHashMap<String, LatencyStats> lookupLatencies;
    private final ConcurrentHashMap<String, LatencyStats> loadLatencies;
    private final ConcurrentHashMap<String, LatencyStats> reloadLatencies;

    /**
     * Creates a new ConfigMetrics instance.
     *
     * @param metrics the underlying metrics collector
     */
    public ConfigMetrics(@NotNull MetricsCollector metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics required");

        this.totalLookups = new AtomicLong(0);
        this.totalCacheHits = new AtomicLong(0);
        this.totalCacheMisses = new AtomicLong(0);
        this.totalLoads = new AtomicLong(0);
        this.totalReloads = new AtomicLong(0);
        this.totalFailures = new AtomicLong(0);

        this.lookupLatencies = new ConcurrentHashMap<>();
        this.loadLatencies = new ConcurrentHashMap<>();
        this.reloadLatencies = new ConcurrentHashMap<>();
    }

    // =========================================================================
    // Lookup Operations (nanosecond precision)
    // =========================================================================
    /**
     * Times a config lookup operation and records metrics.
     *
     * @param tenantId the tenant identifier
     * @param configName the configuration name
     * @param configType the config type (collection, plugin, etc.)
     * @param supplier the lookup operation
     * @param <T> the return type
     * @return the result of the supplier
     */
    public <T> T timeLookup(String tenantId, String configName, String configType,
            Supplier<T> supplier) {
        long startNanos = System.nanoTime();
        try {
            T result = supplier.get();
            long durationNanos = System.nanoTime() - startNanos;

            recordLookupLatency(configType, durationNanos);
            metrics.recordTimer(LOOKUP_DURATION_NS, durationNanos,
                    "tenant", tenantId, "config", configName, "type", configType);

            totalLookups.incrementAndGet();

            if (result != null) {
                recordCacheHit(tenantId, configName, configType);
            } else {
                recordCacheMiss(tenantId, configName, configType);
            }

            return result;
        } catch (Exception e) {
            long durationNanos = System.nanoTime() - startNanos;
            recordLookupLatency(configType, durationNanos);
            throw e;
        }
    }

    /**
     * Records a cache hit.
     */
    public void recordCacheHit(String tenantId, String configName, String configType) {
        totalCacheHits.incrementAndGet();
        metrics.incrementCounter(CACHE_HIT,
                "tenant", tenantId, "config", configName, "type", configType);
    }

    /**
     * Records a cache miss.
     */
    public void recordCacheMiss(String tenantId, String configName, String configType) {
        totalCacheMisses.incrementAndGet();
        metrics.incrementCounter(CACHE_MISS,
                "tenant", tenantId, "config", configName, "type", configType);
    }

    // =========================================================================
    // Load Operations (millisecond precision)
    // =========================================================================
    /**
     * Times a config load operation and records metrics.
     *
     * @param tenantId the tenant identifier
     * @param configName the configuration name
     * @param configType the config type
     * @param supplier the load operation
     * @param <T> the return type
     * @return the result of the supplier
     */
    public <T> T timeLoad(String tenantId, String configName, String configType,
            Supplier<T> supplier) {
        Instant start = Instant.now();
        try {
            T result = supplier.get();
            long durationMs = Duration.between(start, Instant.now()).toMillis();

            recordLoadLatency(configType, durationMs);
            metrics.recordTimer(LOAD_DURATION_MS, durationMs,
                    "tenant", tenantId, "config", configName, "type", configType);
            metrics.incrementCounter(LOAD_SUCCESS,
                    "tenant", tenantId, "config", configName, "type", configType);

            totalLoads.incrementAndGet();

            LOG.debug("Loaded config {}/{} ({}) in {}ms", tenantId, configName, configType, durationMs);

            return result;
        } catch (Exception e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            recordLoadLatency(configType, durationMs);
            metrics.incrementCounter(LOAD_FAILURE,
                    "tenant", tenantId, "config", configName, "type", configType,
                    "error", e.getClass().getSimpleName());

            totalFailures.incrementAndGet();

            LOG.error("Failed to load config {}/{} ({}): {}", tenantId, configName, configType,
                    e.getMessage());
            throw e;
        }
    }

    // =========================================================================
    // Reload Operations
    // =========================================================================
    /**
     * Records a reload operation.
     *
     * @param tenantId the tenant identifier
     * @param configName the configuration name
     * @param configType the config type
     * @param durationMs the reload duration in milliseconds
     * @param success whether the reload succeeded
     */
    public void recordReload(String tenantId, String configName, String configType,
            long durationMs, boolean success) {
        recordReloadLatency(configType, durationMs);

        if (success) {
            metrics.recordTimer(RELOAD_DURATION_MS, durationMs,
                    "tenant", tenantId, "config", configName, "type", configType);
            metrics.incrementCounter(RELOAD_SUCCESS,
                    "tenant", tenantId, "config", configName, "type", configType);
            totalReloads.incrementAndGet();

            LOG.info("Reloaded config {}/{} ({}) in {}ms", tenantId, configName, configType, durationMs);
        } else {
            metrics.incrementCounter(RELOAD_FAILURE,
                    "tenant", tenantId, "config", configName, "type", configType);
            totalFailures.incrementAndGet();
        }
    }

    /**
     * Records a validation failure.
     */
    public void recordValidationFailure(String tenantId, String configName, String configType,
            String reason) {
        metrics.incrementCounter(VALIDATION_FAILURE,
                "tenant", tenantId, "config", configName, "type", configType, "reason", reason);
        totalFailures.incrementAndGet();

        LOG.warn("Validation failed for {}/{} ({}): {}", tenantId, configName, configType, reason);
    }

    /**
     * Records a compilation failure.
     */
    public void recordCompilationFailure(String tenantId, String configName, String configType,
            String reason) {
        metrics.incrementCounter(COMPILATION_FAILURE,
                "tenant", tenantId, "config", configName, "type", configType, "reason", reason);
        totalFailures.incrementAndGet();

        LOG.warn("Compilation failed for {}/{} ({}): {}", tenantId, configName, configType, reason);
    }

    // =========================================================================
    // Statistics Access
    // =========================================================================
    /**
     * Gets the overall cache hit ratio.
     *
     * @return hit ratio between 0.0 and 1.0, or 0.0 if no lookups
     */
    public double getCacheHitRatio() {
        long hits = totalCacheHits.get();
        long total = hits + totalCacheMisses.get();
        return total > 0 ? (double) hits / total : 0.0;
    }

    /**
     * Gets lookup latency statistics for a config type.
     *
     * @param configType the config type
     * @return latency stats, or null if no data
     */
    public LatencyStats getLookupLatencyStats(String configType) {
        return lookupLatencies.get(configType);
    }

    /**
     * Gets load latency statistics for a config type.
     *
     * @param configType the config type
     * @return latency stats, or null if no data
     */
    public LatencyStats getLoadLatencyStats(String configType) {
        return loadLatencies.get(configType);
    }

    /**
     * Gets reload latency statistics for a config type.
     *
     * @param configType the config type
     * @return latency stats, or null if no data
     */
    public LatencyStats getReloadLatencyStats(String configType) {
        return reloadLatencies.get(configType);
    }

    /**
     * Gets a summary of all metrics.
     *
     * @return metrics summary
     */
    public MetricsSummary getSummary() {
        return new MetricsSummary(
                totalLookups.get(),
                totalCacheHits.get(),
                totalCacheMisses.get(),
                totalLoads.get(),
                totalReloads.get(),
                totalFailures.get(),
                getCacheHitRatio());
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================
    private void recordLookupLatency(String configType, long nanos) {
        lookupLatencies.computeIfAbsent(configType, k -> new LatencyStats())
                .record(nanos);
    }

    private void recordLoadLatency(String configType, long millis) {
        loadLatencies.computeIfAbsent(configType, k -> new LatencyStats())
                .record(millis);
    }

    private void recordReloadLatency(String configType, long millis) {
        reloadLatencies.computeIfAbsent(configType, k -> new LatencyStats())
                .record(millis);
    }

    // =========================================================================
    // Inner classes
    // =========================================================================
    /**
     * Tracks latency statistics with approximate percentiles.
     *
     * <p>
     * Uses reservoir sampling for memory-efficient percentile estimation.
     * Suitable for high-throughput scenarios where exact percentiles are not
     * required.
     */
    public static class LatencyStats {

        private static final int RESERVOIR_SIZE = 1024;

        private final AtomicLong count;
        private final AtomicLong totalValue;
        private final AtomicLong minValue;
        private final AtomicLong maxValue;
        private final long[] reservoir;
        private final AtomicLong insertIndex;

        /**
         * Creates a new LatencyStats instance.
         */
        public LatencyStats() {
            this.count = new AtomicLong(0);
            this.totalValue = new AtomicLong(0);
            this.minValue = new AtomicLong(Long.MAX_VALUE);
            this.maxValue = new AtomicLong(Long.MIN_VALUE);
            this.reservoir = new long[RESERVOIR_SIZE];
            this.insertIndex = new AtomicLong(0);
        }

        /**
         * Records a latency value.
         *
         * @param value the latency value
         */
        public void record(long value) {
            count.incrementAndGet();
            totalValue.addAndGet(value);

            // Update min/max
            long currentMin;
            do {
                currentMin = minValue.get();
            } while (value < currentMin && !minValue.compareAndSet(currentMin, value));

            long currentMax;
            do {
                currentMax = maxValue.get();
            } while (value > currentMax && !maxValue.compareAndSet(currentMax, value));

            // Reservoir sampling
            int idx = (int) (insertIndex.getAndIncrement() % RESERVOIR_SIZE);
            reservoir[idx] = value;
        }

        /**
         * Gets the number of recorded values.
         */
        public long getCount() {
            return count.get();
        }

        /**
         * Gets the average latency.
         */
        public double getAverage() {
            long c = count.get();
            return c > 0 ? (double) totalValue.get() / c : 0.0;
        }

        /**
         * Gets the minimum recorded value.
         */
        public long getMin() {
            long m = minValue.get();
            return m == Long.MAX_VALUE ? 0 : m;
        }

        /**
         * Gets the maximum recorded value.
         */
        public long getMax() {
            long m = maxValue.get();
            return m == Long.MIN_VALUE ? 0 : m;
        }

        /**
         * Gets an approximate percentile value.
         *
         * @param percentile the percentile (0.0 to 1.0)
         * @return approximate percentile value
         */
        public long getPercentile(double percentile) {
            long c = count.get();
            if (c == 0) {
                return 0;
            }

            int sampleSize = (int) Math.min(c, RESERVOIR_SIZE);
            long[] sorted = new long[sampleSize];
            System.arraycopy(reservoir, 0, sorted, 0, sampleSize);
            java.util.Arrays.sort(sorted);

            int index = (int) Math.ceil(percentile * sampleSize) - 1;
            index = Math.max(0, Math.min(index, sampleSize - 1));
            return sorted[index];
        }

        /**
         * Gets the p50 (median) latency.
         */
        public long getP50() {
            return getPercentile(0.50);
        }

        /**
         * Gets the p90 latency.
         */
        public long getP90() {
            return getPercentile(0.90);
        }

        /**
         * Gets the p99 latency.
         */
        public long getP99() {
            return getPercentile(0.99);
        }

        /**
         * Gets the p999 latency.
         */
        public long getP999() {
            return getPercentile(0.999);
        }

        @Override
        public String toString() {
            return String.format("LatencyStats{count=%d, avg=%.2f, min=%d, max=%d, p50=%d, p99=%d, p999=%d}",
                    getCount(), getAverage(), getMin(), getMax(), getP50(), getP99(), getP999());
        }
    }

    /**
     * Summary of configuration metrics.
     */
    public record MetricsSummary(
            long totalLookups,
            long cacheHits,
            long cacheMisses,
            long totalLoads,
            long totalReloads,
            long totalFailures,
            double cacheHitRatio) {

        @Override
        public String toString
            
        () {
            return String.format(
                    "MetricsSummary{lookups=%d, cacheHits=%d, cacheMisses=%d, hitRatio=%.2f%%, "
                    + "loads=%d, reloads=%d, failures=%d}",
                    totalLookups, cacheHits, cacheMisses, cacheHitRatio * 100,
                    totalLoads, totalReloads, totalFailures);
        }
    }
}
