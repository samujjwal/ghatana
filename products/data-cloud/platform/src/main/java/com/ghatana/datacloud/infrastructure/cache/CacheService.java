package com.ghatana.datacloud.infrastructure.cache;

import com.ghatana.datacloud.entity.Entity;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

/**
 * In-memory cache service for entity data with metrics.
 *
 * <p><b>Purpose</b><br>
 * Provides read-through caching for entity queries with hit/miss tracking.
 * Invalidates on writes (create/update/delete) to maintain consistency.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CacheService cache = new CacheService();
 * Promise<Optional<Entity>> entity = cache.get(tenantId, collectionName, entityId);
 * cache.invalidate(tenantId, collectionName, entityId);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Cache adapter in infrastructure layer
 * - Transparent to application layer
 * - Metrics for hit/miss ratios
 *
 * @doc.type class
 * @doc.purpose In-memory cache for entity queries
 * @doc.layer infrastructure
 * @doc.pattern Cache (Infrastructure Layer)
 */
public class CacheService {

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);
    private static final int MAX_CACHE_SIZE = 10000;
    private static final long CACHE_TTL_MS = 300000; // 5 minutes

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private volatile long hits = 0;
    private volatile long misses = 0;

    /**
     * Gets entity from cache.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @param entityId entity ID
     * @return cached entity or empty
     */
    public Promise<Optional<Entity>> get(String tenantId, String collectionName, UUID entityId) {
        return Promise.ofBlocking(new ForkJoinPool(), () -> {
            String key = buildKey(tenantId, collectionName, entityId);
            CacheEntry entry = cache.get(key);

            if (entry != null && !entry.isExpired()) {
                hits++;
                logger.debug("Cache hit: {}", key);
                return Optional.of(entry.entity());
            }

            misses++;
            if (entry != null) {
                cache.remove(key);
            }
            logger.debug("Cache miss: {}", key);
            return Optional.empty();
        });
    }

    /**
     * Puts entity in cache.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @param entity entity to cache
     */
    public Promise<Void> put(String tenantId, String collectionName, Entity entity) {
        return Promise.ofBlocking(new ForkJoinPool(), () -> {
            if (cache.size() >= MAX_CACHE_SIZE) {
                evictOldest();
            }

            String key = buildKey(tenantId, collectionName, entity.getId());
            cache.put(key, new CacheEntry(entity, System.currentTimeMillis()));
            logger.debug("Cached entity: {}", key);
            return null;
        });
    }

    /**
     * Invalidates cache entry.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @param entityId entity ID
     */
    public Promise<Void> invalidate(String tenantId, String collectionName, UUID entityId) {
        return Promise.ofBlocking(new ForkJoinPool(), () -> {
            String key = buildKey(tenantId, collectionName, entityId);
            cache.remove(key);
            logger.debug("Invalidated cache: {}", key);
            return null;
        });
    }

    /**
     * Invalidates all entries for a collection.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     */
    public Promise<Void> invalidateCollection(String tenantId, String collectionName) {
        return Promise.ofBlocking(new ForkJoinPool(), () -> {
            String prefix = tenantId + ":" + collectionName + ":";
            cache.keySet().removeIf(key -> key.startsWith(prefix));
            logger.debug("Invalidated collection cache: {}", collectionName);
            return null;
        });
    }

    /**
     * Gets cache statistics.
     *
     * @return cache stats
     */
    public CacheStats getStats() {
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total : 0.0;
        return new CacheStats(hits, misses, cache.size(), hitRate);
    }

    /**
     * Clears entire cache.
     */
    public Promise<Void> clear() {
        return Promise.ofBlocking(new ForkJoinPool(), () -> {
            cache.clear();
            logger.info("Cache cleared");
            return null;
        });
    }

    private String buildKey(String tenantId, String collectionName, UUID entityId) {
        return tenantId + ":" + collectionName + ":" + entityId;
    }

    private void evictOldest() {
        cache.entrySet().stream()
            .min(Comparator.comparingLong(e -> e.getValue().timestamp()))
            .ifPresent(e -> cache.remove(e.getKey()));
        logger.debug("Evicted oldest cache entry");
    }

    /**
     * Cache entry record.
     */
    record CacheEntry(Entity entity, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    /**
     * Cache statistics record.
     */
    public record CacheStats(long hits, long misses, int size, double hitRate) {}
}
