package com.ghatana.platform.database.cache.warming;

import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;

/**
 * Interface for cache warming strategies.
 *
 * <p><b>Purpose</b><br>
 * Defines contract for different cache warming approaches to reduce cold-start latency.
 *
 * <p><b>Strategies</b><br>
 * - Aggressive: Preload all data immediately
 * - Lazy: Load on-demand with background fill
 * - Scheduled: Periodic warming at intervals
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CacheWarmingStrategy strategy = new AggressiveCacheWarmer();
 * strategy.warm(cache, "tenant-123").whenComplete((count, error) -> {
 *   if (error == null) {
 *     logger.info("Warmed {} items", count);
 *   }
 * });
 * }</pre>
 *
 * @see AggressiveCacheWarmer
 * @see LazyCacheWarmer
 * @see ScheduledCacheWarmer
 * @doc.type interface
 * @doc.purpose Cache warming strategy contract
 * @doc.layer core
 * @doc.pattern Strategy
 */
public interface CacheWarmingStrategy {

    /**
     * Warm the cache with data for a specific tenant.
     *
     * @param cache Cache to warm
     * @param tenantId Tenant ID
     * @return Promise with count of items loaded
     */
    Promise<Integer> warm(Object cache, String tenantId);

    /**
     * Warm multiple tenants concurrently.
     *
     * @param cache Cache to warm
     * @param tenantIds List of tenant IDs
     * @return Promise with map of tenant → item count
     */
    Promise<Map<String, Integer>> warmMultiple(Object cache, List<String> tenantIds);

    /**
     * Get strategy name.
     *
     * @return Strategy name
     */
    String getStrategyName();

    /**
     * Check if strategy supports background warming.
     *
     * @return True if background warming supported
     */
    boolean supportsBackgroundWarming();

    /**
     * Estimate warming time for given data size.
     *
     * @param itemCount Estimated number of items
     * @return Estimated milliseconds to complete
     */
    long estimateWarmingTime(int itemCount);
}
