package com.ghatana.datacloud.infrastructure.cache;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis cache manager for distributed caching.
 *
 * <p><b>Purpose</b><br>
 * Manages Redis connections, cache operations, and TTL management.
 * Provides cache-aside and write-through patterns with metrics.
 *
 * <p><b>Features</b><br>
 * - Redis connection pooling
 * - Cache key generation
 * - TTL configuration
 * - Cache invalidation
 * - Metrics emission
 * - Error handling and recovery
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RedisCacheManager cacheManager = new RedisCacheManager(
 *     redisClient,
 *     metrics,
 *     "collection-entity-system"
 * );
 *
 * // Cache-aside pattern
 * Promise<String> value = cacheManager.getOrCompute(
 *     "collection:orders",
 *     () -> loadFromDatabase(),
 *     5, TimeUnit.MINUTES
 * );
 *
 * // Set value
 * cacheManager.set("collection:orders", data, 5, TimeUnit.MINUTES);
 *
 * // Invalidate
 * cacheManager.invalidate("collection:orders");
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Caching layer in infrastructure
 * - Uses core/redis-cache module
 * - Integrates with MetricsCollector
 * - Supports multi-tenancy
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. All operations are atomic via Redis.
 *
 * @see com.ghatana.redis.RedisClient
 * @see MetricsCollector
 * @doc.type class
 * @doc.purpose Redis cache manager for distributed caching
 * @doc.layer product
 * @doc.pattern Caching (Infrastructure Layer)
 */
public class RedisCacheManager {

    private static final Logger logger = LoggerFactory.getLogger(RedisCacheManager.class);

    private final RedisClient redisClient;
    private final MetricsCollector metrics;
    private final String namespace;
    private final long defaultTtlSeconds;

    /**
     * Creates a new Redis cache manager.
     *
     * @param redisClient the Redis client (required)
     * @param metrics the metrics collector (required)
     * @param namespace the cache namespace (required)
     * @throws NullPointerException if any parameter is null
     */
    public RedisCacheManager(RedisClient redisClient, MetricsCollector metrics, String namespace) {
        this.redisClient = Objects.requireNonNull(redisClient, "RedisClient must not be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
        this.namespace = Objects.requireNonNull(namespace, "Namespace must not be null");
        this.defaultTtlSeconds = 300; // 5 minutes default
    }

    /**
     * Creates a new Redis cache manager with custom default TTL.
     *
     * @param redisClient the Redis client (required)
     * @param metrics the metrics collector (required)
     * @param namespace the cache namespace (required)
     * @param defaultTtlSeconds the default TTL in seconds
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if TTL is invalid
     */
    public RedisCacheManager(RedisClient redisClient, MetricsCollector metrics, String namespace, long defaultTtlSeconds) {
        this.redisClient = Objects.requireNonNull(redisClient, "RedisClient must not be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
        this.namespace = Objects.requireNonNull(namespace, "Namespace must not be null");
        if (defaultTtlSeconds <= 0) {
            throw new IllegalArgumentException("Default TTL must be positive");
        }
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    /**
     * Gets value from cache or computes and caches it.
     *
     * <p><b>Cache-Aside Pattern</b><br>
     * 1. Try to get from cache
     * 2. If miss, compute value
     * 3. Store in cache
     * 4. Return value
     *
     * @param key the cache key
     * @param loader the value loader function
     * @param ttl the time to live
     * @param unit the time unit
     * @return Promise of cached value
     */
    public <T> Promise<T> getOrCompute(
            String key,
            java.util.function.Supplier<Promise<T>> loader,
            long ttl,
            TimeUnit unit) {

        Objects.requireNonNull(key, "Key must not be null");
        Objects.requireNonNull(loader, "Loader must not be null");
        Objects.requireNonNull(unit, "TimeUnit must not be null");

        String cacheKey = generateKey(key);

        return get(cacheKey)
            .then(cached -> {
                if (cached != null) {
                    metrics.incrementCounter("cache.hit", "namespace", namespace);
                    return Promise.of((T) cached);
                }

                metrics.incrementCounter("cache.miss", "namespace", namespace);

                return loader.get()
                    .then(value -> {
                        if (value != null) {
                            return set(cacheKey, value, ttl, unit)
                                .map(v -> value);
                        }
                        return Promise.of(value);
                    });
            })
            .then(
                result -> Promise.of(result),
                ex -> {
                    logger.warn("Cache error for key {}: {}", cacheKey, ex.getMessage());
                    metrics.incrementCounter("cache.error", "namespace", namespace);
                    // Return loader result on cache error
                    return loader.get();
                }
            );
    }

    /**
     * Gets value from cache.
     *
     * @param key the cache key
     * @return Promise of cached value or null
     */
    public Promise<Object> get(String key) {
        Objects.requireNonNull(key, "Key must not be null");

        String cacheKey = generateKey(key);

        try {
            Object value = redisClient.get(cacheKey);
            if (value != null) {
                logger.debug("Cache hit for key: {}", cacheKey);
                metrics.incrementCounter("cache.get.success", "namespace", namespace);
            } else {
                logger.debug("Cache miss for key: {}", cacheKey);
                metrics.incrementCounter("cache.get.miss", "namespace", namespace);
            }
            return Promise.of(value);
        } catch (Exception ex) {
            logger.error("Error getting from cache: {}", cacheKey, ex);
            metrics.incrementCounter("cache.get.error", "namespace", namespace);
            return Promise.ofException(ex);
        }
    }

    /**
     * Sets value in cache.
     *
     * @param key the cache key
     * @param value the value
     * @param ttl the time to live
     * @param unit the time unit
     * @return Promise of void
     */
    public Promise<Void> set(String key, Object value, long ttl, TimeUnit unit) {
        Objects.requireNonNull(key, "Key must not be null");
        Objects.requireNonNull(value, "Value must not be null");
        Objects.requireNonNull(unit, "TimeUnit must not be null");

        String cacheKey = generateKey(key);
        long ttlSeconds = unit.toSeconds(ttl);

        try {
            redisClient.set(cacheKey, value, ttlSeconds);
            logger.debug("Cache set for key: {} with TTL: {}s", cacheKey, ttlSeconds);
            metrics.incrementCounter("cache.set.success", "namespace", namespace);
            return Promise.of(null);
        } catch (Exception ex) {
            logger.error("Error setting cache: {}", cacheKey, ex);
            metrics.incrementCounter("cache.set.error", "namespace", namespace);
            return Promise.ofException(ex);
        }
    }

    /**
     * Sets value in cache with default TTL.
     *
     * @param key the cache key
     * @param value the value
     * @return Promise of void
     */
    public Promise<Void> set(String key, Object value) {
        return set(key, value, defaultTtlSeconds, TimeUnit.SECONDS);
    }

    /**
     * Invalidates cache entry.
     *
     * @param key the cache key
     * @return Promise of void
     */
    public Promise<Void> invalidate(String key) {
        Objects.requireNonNull(key, "Key must not be null");

        String cacheKey = generateKey(key);

        try {
            redisClient.delete(cacheKey);
            logger.debug("Cache invalidated for key: {}", cacheKey);
            metrics.incrementCounter("cache.invalidate.success", "namespace", namespace);
            return Promise.of(null);
        } catch (Exception ex) {
            logger.error("Error invalidating cache: {}", cacheKey, ex);
            metrics.incrementCounter("cache.invalidate.error", "namespace", namespace);
            return Promise.ofException(ex);
        }
    }

    /**
     * Invalidates multiple cache entries.
     *
     * @param keys the cache keys
     * @return Promise of void
     */
    public Promise<Void> invalidateAll(Collection<String> keys) {
        Objects.requireNonNull(keys, "Keys must not be null");

        try {
            List<String> cacheKeys = keys.stream()
                .map(this::generateKey)
                .toList();

            redisClient.deleteAll(cacheKeys);
            logger.debug("Cache invalidated for {} keys", keys.size());
            metrics.incrementCounter("cache.invalidate_all.success",
                "namespace", namespace,
                "count", String.valueOf(keys.size()));
            return Promise.of(null);
        } catch (Exception ex) {
            logger.error("Error invalidating cache for {} keys", keys.size(), ex);
            metrics.incrementCounter("cache.invalidate_all.error", "namespace", namespace);
            return Promise.ofException(ex);
        }
    }

    /**
     * Clears all cache entries for namespace.
     *
     * @return Promise of void
     */
    public Promise<Void> clear() {
        try {
            String pattern = namespace + ":*";
            redisClient.deleteByPattern(pattern);
            logger.info("Cache cleared for namespace: {}", namespace);
            metrics.incrementCounter("cache.clear.success", "namespace", namespace);
            return Promise.of(null);
        } catch (Exception ex) {
            logger.error("Error clearing cache for namespace: {}", namespace, ex);
            metrics.incrementCounter("cache.clear.error", "namespace", namespace);
            return Promise.ofException(ex);
        }
    }

    /**
     * Gets cache statistics.
     *
     * @return cache statistics
     */
    public Promise<CacheStats> getStats() {
        try {
            long size = redisClient.getSize(namespace + ":*");
            long memory = redisClient.getMemoryUsage(namespace + ":*");

            CacheStats stats = new CacheStats(
                size,
                memory,
                System.currentTimeMillis()
            );

            logger.debug("Cache stats: size={}, memory={}", size, memory);
            return Promise.of(stats);
        } catch (Exception ex) {
            logger.error("Error getting cache stats", ex);
            return Promise.ofException(ex);
        }
    }

    /**
     * Generates cache key with namespace.
     *
     * @param key the key
     * @return namespaced key
     */
    private String generateKey(String key) {
        return namespace + ":" + key;
    }

    /**
     * Cache statistics.
     *
     * @doc.type class
     * @doc.purpose Cache statistics
     */
    public static class CacheStats {
        public long size;
        public long memory;
        public long timestamp;

        public CacheStats(long size, long memory, long timestamp) {
            this.size = size;
            this.memory = memory;
            this.timestamp = timestamp;
        }
    }

    /**
     * Redis client interface.
     *
     * <p><b>Note</b><br>
     * This is a placeholder. In production, use core/redis-cache module.
     *
     * @doc.type interface
     * @doc.purpose Redis client abstraction
     */
    public interface RedisClient {
        Object get(String key);
        void set(String key, Object value, long ttlSeconds);
        void delete(String key);
        void deleteAll(Collection<String> keys);
        void deleteByPattern(String pattern);
        long getSize(String pattern);
        long getMemoryUsage(String pattern);
    }
}
