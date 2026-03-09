package com.ghatana.platform.database.cache.warming;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggressive cache warming strategy.
 *
 * <p><b>Purpose</b><br>
 * Preloads all popular data into cache immediately to minimize cold-start latency.
 * Best for production environments with predictable access patterns.
 *
 * <p><b>Behavior</b><br>
 * - Loads all configured keys on startup
 * - Blocks until warming completes
 * - Tracks warming progress and metrics
 * - Supports configurable batch sizes
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AggressiveCacheWarmer warmer = new AggressiveCacheWarmer(
 *     metricsCollector,
 *     popularKeys,
 *     100 // batch size
 * );
 *
 * warmer.warm(cache, "tenant-123").whenComplete((count, error) -> {
 *   logger.info("Warmed {} items in cache", count);
 * });
 * }</pre>
 *
 * <p><b>Configuration</b><br>
 * - Popular keys list (most accessed data)
 * - Batch size for parallel loading
 * - Timeout for individual items
 *
 * @doc.type class
 * @doc.purpose Aggressive cache preloading
 * @doc.layer core
 * @doc.pattern Strategy
 */
public class AggressiveCacheWarmer implements CacheWarmingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(AggressiveCacheWarmer.class);

    private final MetricsCollector metrics;
    private final List<String> popularKeys;
    private final int batchSize;
    private final long timeoutMs;
    private final Map<String, WarmingStats> stats;

    /**
     * Create aggressive cache warmer.
     *
     * @param metrics Metrics collector
     * @param popularKeys List of popular keys to preload
     * @param batchSize Number of keys to load in parallel
     */
    public AggressiveCacheWarmer(
            MetricsCollector metrics,
            List<String> popularKeys,
            int batchSize) {
        this(metrics, popularKeys, batchSize, 5000L);
    }

    /**
     * Create aggressive cache warmer with custom timeout.
     *
     * @param metrics Metrics collector
     * @param popularKeys List of popular keys to preload
     * @param batchSize Number of keys to load in parallel
     * @param timeoutMs Timeout per item in milliseconds
     */
    public AggressiveCacheWarmer(
            MetricsCollector metrics,
            List<String> popularKeys,
            int batchSize,
            long timeoutMs) {
        this.metrics = metrics;
        this.popularKeys = new ArrayList<>(popularKeys);
        this.batchSize = batchSize;
        this.timeoutMs = timeoutMs;
        this.stats = new ConcurrentHashMap<>();

        logger.info("Initialized AggressiveCacheWarmer: {} keys, batch size {}",
                popularKeys.size(), batchSize);
    }

    @Override
    public Promise<Integer> warm(Object cache, String tenantId) {
        long startTime = System.currentTimeMillis();
        logger.info("Starting aggressive cache warming for tenant: {}", tenantId);

        metrics.incrementCounter("cache.warming.started",
                "strategy", "aggressive",
                "tenant", tenantId);

        // Create warming stats
        WarmingStats warmingStats = new WarmingStats(tenantId, popularKeys.size());
        stats.put(tenantId, warmingStats);

        // Split keys into batches
        List<List<String>> batches = createBatches(popularKeys, batchSize);

        // Warm batches sequentially
        return Promises.toList(batches.stream()
                .map(batch -> warmBatch(cache, tenantId, batch)))
                .map(results -> results.stream().mapToInt(Integer::intValue).sum())
                .whenComplete((totalCount, error) -> {
            long duration = System.currentTimeMillis() - startTime;

            if (error == null) {
                warmingStats.markComplete(totalCount, duration);

                logger.info("Cache warming complete for {}: {} items in {}ms",
                        tenantId, totalCount, duration);

                metrics.incrementCounter("cache.warming.completed",
                        "strategy", "aggressive",
                        "tenant", tenantId,
                        "items", String.valueOf(totalCount));

                metrics.recordTimer("cache.warming.duration", duration);
            } else {
                warmingStats.markFailed(error);

                logger.error("Cache warming failed for {}: {}", tenantId, error.getMessage());

                metrics.incrementCounter("cache.warming.failed",
                        "strategy", "aggressive",
                        "tenant", tenantId,
                        "error", error.getClass().getSimpleName());
            }
        });
    }

    @Override
    public Promise<Map<String, Integer>> warmMultiple(Object cache, List<String> tenantIds) {
        logger.info("Warming cache for {} tenants", tenantIds.size());

        // Warm all tenants in parallel
        List<Promise<Map.Entry<String, Integer>>> promises = new ArrayList<>();

        for (String tenantId : tenantIds) {
            Promise<Map.Entry<String, Integer>> promise = warm(cache, tenantId)
                    .map(count -> Map.entry(tenantId, count));
            promises.add(promise);
        }

        return Promises.toList(promises)
                .map(entries -> {
                    Map<String, Integer> results = new HashMap<>();
                    for (Map.Entry<String, Integer> entry : entries) {
                        results.put(entry.getKey(), entry.getValue());
                    }
                    return results;
                });
    }

    @Override
    public String getStrategyName() {
        return "aggressive";
    }

    @Override
    public boolean supportsBackgroundWarming() {
        return false; // Aggressive = foreground only
    }

    @Override
    public long estimateWarmingTime(int itemCount) {
        // Estimate: 10ms per item + batch overhead
        int batches = (int) Math.ceil((double) itemCount / batchSize);
        return (itemCount * 10L) + (batches * 50L);
    }

    /**
     * Warm a batch of keys.
     *
     * @param cache Cache instance
     * @param tenantId Tenant ID
     * @param keys Keys to warm
     * @return Promise with count of warmed items
     */
    private Promise<Integer> warmBatch(Object cache, String tenantId, List<String> keys) {
        logger.debug("Warming batch of {} keys for tenant: {}", keys.size(), tenantId);

        // Simulate loading (in real implementation, call cache.get() for each key)
        List<Promise<Boolean>> promises = new ArrayList<>();

        for (String key : keys) {
            Promise<Boolean> promise = loadKey(cache, tenantId, key)
                    .map(success -> {
                        if (success) {
                            stats.get(tenantId).incrementLoaded();
                        }
                        return success;
                    })
                    .whenComplete((success, error) -> {
                        if (error != null) {
                            logger.warn("Failed to load key {} for tenant {}: {}",
                                    key, tenantId, error.getMessage());
                        }
                    });
            promises.add(promise);
        }

        return Promises.toList(promises)
                .map(results -> (int) results.stream()
                        .filter(Objects::nonNull)
                        .filter(Boolean.TRUE::equals)
                        .count());
    }

    /**
     * Load a single key into cache.
     *
{{ ... }}
     * @param tenantId Tenant ID
     * @param key Key to load
     * @return Promise with success status
     */
    private Promise<Boolean> loadKey(Object cache, String tenantId, String key) {
        // In real implementation:
        // 1. Check if key already in cache
        // 2. If not, load from database
        // 3. Put in cache
        // 4. Return success

        // Create a new thread pool for this operation
        Executor executor = command -> {
            Thread t = new Thread(command, "cache-warmer-blocking-" + tenantId);
            t.setDaemon(true);
            t.start();
        };

        return Promise.ofBlocking(executor, () -> {
            // TODO: Replace with actual cache loading logic
            return true;
        }).mapException(error -> {
            logger.warn("Failed to load key {} for tenant {}: {}",
                    key, tenantId, error.getMessage());
            return new RuntimeException("Failed to load key: " + error.getMessage(), error);
        });
    }

    /**
     * Create batches from list of keys.
     *
     * @param keys All keys
     * @param batchSize Batch size
     * @return List of batches
     */
    private List<List<String>> createBatches(List<String> keys, int batchSize) {
        List<List<String>> batches = new ArrayList<>();

        for (int i = 0; i < keys.size(); i += batchSize) {
            int end = Math.min(i + batchSize, keys.size());
            batches.add(keys.subList(i, end));
        }

        return batches;
    }

    /**
     * Get warming statistics for a tenant.
     *
     * @param tenantId Tenant ID
     * @return Warming stats or null
     */
    public WarmingStats getStats(String tenantId) {
        return stats.get(tenantId);
    }

    /**
     * Warming statistics tracker.
     */
    public static class WarmingStats {
        private final String tenantId;
        private final int totalKeys;
        private int loadedKeys;
        private long startTime;
        private long endTime;
        private boolean complete;
        private Throwable error;

        public WarmingStats(String tenantId, int totalKeys) {
            this.tenantId = tenantId;
            this.totalKeys = totalKeys;
            this.loadedKeys = 0;
            this.startTime = System.currentTimeMillis();
            this.complete = false;
        }

        public synchronized void incrementLoaded() {
            loadedKeys++;
        }

        public synchronized void markComplete(int finalCount, long duration) {
            this.loadedKeys = finalCount;
            this.endTime = System.currentTimeMillis();
            this.complete = true;
        }

        public synchronized void markFailed(Throwable error) {
            this.error = error;
            this.endTime = System.currentTimeMillis();
            this.complete = true;
        }

        public String getTenantId() {
            return tenantId;
        }

        public int getTotalKeys() {
            return totalKeys;
        }

        public int getLoadedKeys() {
            return loadedKeys;
        }

        public double getProgress() {
            return totalKeys > 0 ? (double) loadedKeys / totalKeys : 0.0;
        }

        public long getDuration() {
            return (endTime > 0 ? endTime : System.currentTimeMillis()) - startTime;
        }

        public boolean isComplete() {
            return complete;
        }

        public boolean isFailed() {
            return error != null;
        }

        public Throwable getError() {
            return error;
        }
    }
}
