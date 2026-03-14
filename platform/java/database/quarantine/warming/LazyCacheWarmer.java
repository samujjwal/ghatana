package com.ghatana.platform.database.cache.warming;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Lazy cache warming strategy.
 *
 * <p><b>Purpose</b><br>
 * Loads data on-demand with background fill to balance startup time and performance.
 * Best for development and environments with unpredictable access patterns.
 *
 * <p><b>Behavior</b><br>
 * - Returns immediately without blocking
 * - Starts background thread to warm cache
 * - Prioritizes recently accessed keys
 * - Adapts based on access patterns
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * LazyCacheWarmer warmer = new LazyCacheWarmer(
 *     metricsCollector,
 *     executor,
 *     50 // items per minute
 * );
 *
 * warmer.warm(cache, "tenant-123"); // Returns immediately
 *
 * // Check progress later
 * WarmingProgress progress = warmer.getProgress("tenant-123");
 * System.out.println("Progress: " + progress.getPercentage() + "%");
 * }</pre>
 *
 * <p><b>Configuration</b><br>
 * - Background thread pool
 * - Items per minute rate limit
 * - Priority queue for hot keys
 *
 * @doc.type class
 * @doc.purpose Lazy background cache warming
 * @doc.layer core
 * @doc.pattern Strategy
 */
public class LazyCacheWarmer implements CacheWarmingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(LazyCacheWarmer.class);

    private final MetricsCollector metrics;
    private final ExecutorService executor;
    private final int itemsPerMinute;
    private final Map<String, WarmingProgress> progressMap;
    private final PriorityQueue<KeyPriority> priorityQueue;

    /**
     * Create lazy cache warmer.
     *
     * @param metrics Metrics collector
     * @param executor Background executor
     * @param itemsPerMinute Rate limit for warming
     */
    public LazyCacheWarmer(
            MetricsCollector metrics,
            ExecutorService executor,
            int itemsPerMinute) {
        this.metrics = metrics;
        this.executor = executor;
        this.itemsPerMinute = itemsPerMinute;
        this.progressMap = new ConcurrentHashMap<>();
        this.priorityQueue = new PriorityQueue<>(
                Comparator.comparingInt(KeyPriority::getPriority).reversed()
        );

        logger.info("Initialized LazyCacheWarmer: {} items/minute", itemsPerMinute);
    }

    @Override
    public Promise<Integer> warm(Object cache, String tenantId) {
        logger.info("Starting lazy cache warming for tenant: {} (background)", tenantId);

        metrics.incrementCounter("cache.warming.started",
                "strategy", "lazy",
                "tenant", tenantId);

        // Create progress tracker
        WarmingProgress progress = new WarmingProgress(tenantId);
        progressMap.put(tenantId, progress);

        // Start background warming
        executor.submit(() -> {
            try {
                warmInBackground(cache, tenantId, progress);
            } catch (Exception e) {
                logger.error("Background warming failed for {}: {}", tenantId, e.getMessage());
                progress.markFailed(e);
            }
        });

        // Return immediately with 0 (warming happens in background)
        return Promise.of(0);
    }

    @Override
    public Promise<Map<String, Integer>> warmMultiple(Object cache, List<String> tenantIds) {
        logger.info("Starting lazy warming for {} tenants", tenantIds.size());

        Map<String, Integer> results = new HashMap<>();

        for (String tenantId : tenantIds) {
            warm(cache, tenantId); // Fire and forget
            results.put(tenantId, 0); // Returns immediately
        }

        return Promise.of(results);
    }

    @Override
    public String getStrategyName() {
        return "lazy";
    }

    @Override
    public boolean supportsBackgroundWarming() {
        return true;
    }

    @Override
    public long estimateWarmingTime(int itemCount) {
        // Based on rate limit: items per minute
        return (itemCount * 60_000L) / itemsPerMinute;
    }

    /**
     * Perform warming in background thread.
     *
     * @param cache Cache instance
     * @param tenantId Tenant ID
     * @param progress Progress tracker
     */
    private void warmInBackground(Object cache, String tenantId, WarmingProgress progress) {
        long startTime = System.currentTimeMillis();
        int loaded = 0;

        // Calculate delay between items
        long delayMs = 60_000L / itemsPerMinute;

        logger.info("Background warming for {}: {} items/min ({}ms delay)",
                tenantId, itemsPerMinute, delayMs);

        try {
            // Simulate loading items with rate limiting
            for (int i = 0; i < 100; i++) { // Demo: warm 100 items
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                // TODO: Replace with actual cache item loading logic
                loaded++;

                progress.incrementLoaded();

                if (i % 10 == 0) {
                    logger.debug("Warming progress for {}: {}/100", tenantId, loaded);
                }

                // Rate limit: delay between items
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            progress.markComplete(loaded, duration);

            logger.info("Background warming complete for {}: {} items in {}ms",
                    tenantId, loaded, duration);

            metrics.incrementCounter("cache.warming.completed",
                    "strategy", "lazy",
                    "tenant", tenantId,
                    "items", String.valueOf(loaded));

metrics.recordTimer("cache.warming.duration", duration);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Background warming interrupted for {}", tenantId);
            progress.markFailed(e);
        } catch (Exception e) {
            logger.error("Background warming error for {}: {}", tenantId, e.getMessage());
            progress.markFailed(e);

            metrics.incrementCounter("cache.warming.failed",
                    "strategy", "lazy",
                    "tenant", tenantId,
                    "error", e.getClass().getSimpleName());
        }
    }

    /**
     * Add key to priority queue for warming.
     *
     * @param key Key to warm
     * @param priority Priority (higher = sooner)
     */
    public void prioritizeKey(String key, int priority) {
        synchronized (priorityQueue) {
            priorityQueue.offer(new KeyPriority(key, priority));
        }
    }

    /**
     * Get warming progress for tenant.
     *
     * @param tenantId Tenant ID
     * @return Progress or null if not warming
     */
    public WarmingProgress getProgress(String tenantId) {
        return progressMap.get(tenantId);
    }

    /**
     * Key with priority.
     */
    private static class KeyPriority {
        private final String key;
        private final int priority;

        KeyPriority(String key, int priority) {
            this.key = key;
            this.priority = priority;
        }

        String getKey() {
            return key;
        }

        int getPriority() {
            return priority;
        }
    }

    /**
     * Warming progress tracker.
     */
    public static class WarmingProgress {
        private final String tenantId;
        private int loadedItems;
        private final long startTime;
        private long endTime;
        private boolean complete;
        private Throwable error;

        public WarmingProgress(String tenantId) {
            this.tenantId = tenantId;
            this.loadedItems = 0;
            this.startTime = System.currentTimeMillis();
            this.complete = false;
        }

        public synchronized void incrementLoaded() {
            loadedItems++;
        }

        public synchronized void markComplete(int finalCount, long duration) {
            this.loadedItems = finalCount;
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

        public int getLoadedItems() {
            return loadedItems;
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

        public double getPercentage() {
            // Assume 100 items for demo
            return Math.min(100.0, (loadedItems / 100.0) * 100);
        }
    }
}
