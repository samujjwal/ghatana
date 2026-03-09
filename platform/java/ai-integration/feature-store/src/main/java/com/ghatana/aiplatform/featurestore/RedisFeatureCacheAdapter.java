package com.ghatana.aiplatform.featurestore;

import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis cache adapter for feature store using core/redis-cache abstractions.
 *
 * <p><b>Purpose</b><br>
 * Provides distributed caching for features with versioning and TTL support.
 * Implements cache-aside pattern with automatic expiration and tenant isolation.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RedisFeatureCacheAdapter cache = new RedisFeatureCacheAdapter(
 *     metrics,
 *     Duration.ofMinutes(5)  // TTL for volatile features
 * );
 *
 * // Cache feature with version
 * cache.set(
 *     "tenant-123",
 *     "user-456",
 *     "transaction_amount_7d_avg",
 *     542.50,
 *     1,  // version
 *     Duration.ofMinutes(5)
 * );
 *
 * // Retrieve feature
 * Optional<FeatureCacheEntry> entry = cache.get(
 *     "tenant-123",
 *     "user-456",
 *     "transaction_amount_7d_avg"
 * );
 *
 * if (entry.isPresent()) {
 *     double value = entry.get().value;
 * }
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Adapter between FeatureStoreService and core/redis-cache abstraction.
 * Enables high-performance distributed caching for multi-node deployments.
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe: uses ConcurrentHashMap for in-memory cache simulation.
 * Production deployment would use Jedis/Lettuce via core/redis-cache.
 *
 * @doc.type class
 * @doc.purpose Redis cache adapter for distributed feature storage
 * @doc.layer platform
 * @doc.pattern Adapter
 */
public class RedisFeatureCacheAdapter {

    private static final Logger log = LoggerFactory.getLogger(RedisFeatureCacheAdapter.class);

    private final MetricsCollector metrics;
    private final Duration defaultTtl;

    // Cache key format: tenant:{tenantId}:feature:{entityId}:{featureName}:v{version}
    // Value format: {value}#{expiresAtMs}
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    // Cache stats
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);

    /**
     * Constructor.
     *
     * @param metrics MetricsCollector for observability
     * @param defaultTtl default TTL for cached entries
     */
    public RedisFeatureCacheAdapter(MetricsCollector metrics, Duration defaultTtl) {
        this.metrics = metrics;
        this.defaultTtl = defaultTtl;
    }

    /**
     * Cache a feature value with versioning.
     *
     * GIVEN: Tenant, entity, feature name, and value
     * WHEN: set() is called
     * THEN: Feature is cached with version and TTL
     *
     * @param tenantId tenant identifier (scopes cache)
     * @param entityId entity identifier (e.g., user ID)
     * @param featureName feature name
     * @param value feature value (double)
     * @param version version number for feature
     * @param ttl time to live for this cache entry
     */
    public void set(String tenantId, String entityId, String featureName,
                    double value, int version, Duration ttl) {
        if (tenantId == null || entityId == null || featureName == null) {
            throw new NullPointerException("tenant, entity, and feature name cannot be null");
        }

        String key = buildCacheKey(tenantId, entityId, featureName, version);
        long expiresAtMs = System.currentTimeMillis() + ttl.toMillis();

        CacheEntry entry = new CacheEntry(value, version, expiresAtMs, Instant.now());
        cache.put(key, entry);

        metrics.incrementCounter(
            "ai.feature.cache.write",
            "tenant", tenantId,
            "feature", featureName
        );

        log.debug("Cached feature {}:{}:{}:v{} (expires in {}s)",
            tenantId, entityId, featureName, version, ttl.getSeconds());
    }

    /**
     * Retrieve a cached feature value.
     *
     * GIVEN: Tenant, entity, and feature name
     * WHEN: get() is called
     * THEN: Returns Optional containing cache entry if found and not expired
     *
     * @param tenantId tenant identifier
     * @param entityId entity identifier
     * @param featureName feature name
     * @return Optional containing FeatureCacheEntry if found and not expired
     */
    public Optional<FeatureCacheEntry> get(String tenantId, String entityId, String featureName) {
        if (tenantId == null || entityId == null || featureName == null) {
            throw new NullPointerException("tenant, entity, and feature name cannot be null");
        }

        // Try latest version (version 0 = latest)
        String key = buildCacheKey(tenantId, entityId, featureName, 0);
        CacheEntry entry = cache.get(key);

        if (entry == null) {
            recordMiss(tenantId, featureName);
            return Optional.empty();
        }

        // Check expiration
        if (System.currentTimeMillis() > entry.expiresAtMs) {
            cache.remove(key);
            recordEviction(tenantId, featureName);
            recordMiss(tenantId, featureName);
            return Optional.empty();
        }

        recordHit(tenantId, featureName);
        return Optional.of(new FeatureCacheEntry(
            entry.value,
            entry.version,
            entry.cachedAtMs
        ));
    }

    /**
     * Get cache entry by specific version.
     *
     * @param tenantId tenant identifier
     * @param entityId entity identifier
     * @param featureName feature name
     * @param version specific version to retrieve
     * @return Optional containing cache entry if found
     */
    public Optional<FeatureCacheEntry> getVersion(String tenantId, String entityId,
                                                   String featureName, int version) {
        String key = buildCacheKey(tenantId, entityId, featureName, version);
        CacheEntry entry = cache.get(key);

        if (entry == null || System.currentTimeMillis() > entry.expiresAtMs) {
            return Optional.empty();
        }

        return Optional.of(new FeatureCacheEntry(
            entry.value,
            entry.version,
            entry.cachedAtMs
        ));
    }

    /**
     * Invalidate cached feature.
     *
     * @param tenantId tenant identifier
     * @param entityId entity identifier
     * @param featureName feature name
     */
    public void invalidate(String tenantId, String entityId, String featureName) {
        String key = buildCacheKey(tenantId, entityId, featureName, 0);
        cache.remove(key);
        metrics.incrementCounter(
            "ai.feature.cache.invalidate",
            "tenant", tenantId,
            "feature", featureName
        );
    }

    /**
     * Invalidate all features for an entity.
     *
     * @param tenantId tenant identifier
     * @param entityId entity identifier
     */
    public void invalidateEntity(String tenantId, String entityId) {
        String prefix = "tenant:" + tenantId + ":feature:" + entityId + ":";
        cache.keySet().removeIf(key -> key.startsWith(prefix));
        metrics.incrementCounter(
            "ai.feature.cache.invalidate_entity",
            "tenant", tenantId
        );
    }

    /**
     * Get cache statistics.
     *
     * @return cache stats object
     */
    public CacheStats getStats() {
        return new CacheStats(
            hits.get(),
            misses.get(),
            evictions.get(),
            cache.size()
        );
    }

    /**
     * Emit cache metrics to observability system.
     *
     * @param tenantId tenant identifier
     */
    public void emitMetrics(String tenantId) {
        long hitCount = hits.get();
        long missCount = misses.get();
        long totalAccess = hitCount + missCount;

        if (totalAccess > 0) {
            double hitRate = (double) hitCount / totalAccess;
            metrics.recordTimer(
                "ai.feature.cache.hit_rate",
                (long)(hitRate * 100),
                "tenant", tenantId
            );
        }

        metrics.recordTimer(
            "ai.feature.cache.size",
            cache.size(),
            "tenant", tenantId
        );

        metrics.recordTimer(
            "ai.feature.cache.evictions",
            evictions.get(),
            "tenant", tenantId
        );

        log.debug("Cache stats for {}: hits={}, misses={}, evictions={}, size={}",
            tenantId, hitCount, missCount, evictions.get(), cache.size());
    }

    private void recordHit(String tenantId, String featureName) {
        hits.incrementAndGet();
        metrics.incrementCounter(
            "ai.feature.cache.hit",
            "tenant", tenantId,
            "feature", featureName
        );
    }

    private void recordMiss(String tenantId, String featureName) {
        misses.incrementAndGet();
        metrics.incrementCounter(
            "ai.feature.cache.miss",
            "tenant", tenantId,
            "feature", featureName
        );
    }

    private void recordEviction(String tenantId, String featureName) {
        evictions.incrementAndGet();
        metrics.incrementCounter(
            "ai.feature.cache.eviction",
            "tenant", tenantId,
            "feature", featureName
        );
    }

    private String buildCacheKey(String tenantId, String entityId, String featureName, int version) {
        return String.format(
            "tenant:%s:feature:%s:%s:v%d",
            tenantId, entityId, featureName, version
        );
    }

    /**
     * Immutable cache entry.
     */
    public static class FeatureCacheEntry {
        public final double value;
        public final int version;
        public final Instant cachedAtMs;

        public FeatureCacheEntry(double value, int version, Instant cachedAtMs) {
            this.value = value;
            this.version = version;
            this.cachedAtMs = cachedAtMs;
        }
    }

    /**
     * Immutable cache entry for internal storage.
     */
    private static class CacheEntry {
        final double value;
        final int version;
        final long expiresAtMs;
        final Instant cachedAtMs;

        CacheEntry(double value, int version, long expiresAtMs, Instant cachedAtMs) {
            this.value = value;
            this.version = version;
            this.expiresAtMs = expiresAtMs;
            this.cachedAtMs = cachedAtMs;
        }
    }

    /**
     * Cache statistics.
     */
    public static class CacheStats {
        public final long hits;
        public final long misses;
        public final long evictions;
        public final int size;

        public CacheStats(long hits, long misses, long evictions, int size) {
            this.hits = hits;
            this.misses = misses;
            this.evictions = evictions;
            this.size = size;
        }

        public double getHitRate() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }
    }
}
