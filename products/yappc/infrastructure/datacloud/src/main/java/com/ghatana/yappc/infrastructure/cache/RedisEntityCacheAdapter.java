package com.ghatana.yappc.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.cache.DistributedCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed entity cache adapter for YAPPC infrastructure.
 *
 * <p>Delegates to the platform {@link DistributedCacheService} for tenant-scoped key management,
 * TTL enforcement, serialization, and invalidation. Keys are namespaced by collection name to
 * avoid cross-collection key collisions within the same tenant.</p>
 *
 * @param <T> The entity type stored in the cache
 * @doc.type class
 * @doc.purpose Redis-backed cache adapter for YAPPC entity reads; delegates to platform DistributedCacheService.
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class RedisEntityCacheAdapter<T> {

    private static final Logger LOG = LoggerFactory.getLogger(RedisEntityCacheAdapter.class);

    private final DistributedCacheService cacheService;
    private final ObjectMapper objectMapper;
    private final Duration defaultTtl;
    private final String collectionName;
    private final Class<T> entityClass;

    /**
     * Primary constructor. Callers that do not know the entity class at construction time
     * must use {@link #get(String, Class)} for retrieval.
     */
    public RedisEntityCacheAdapter(
            DistributedCacheService cacheService,
            ObjectMapper objectMapper,
            Duration defaultTtl,
            String collectionName) {
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.defaultTtl = defaultTtl;
        this.collectionName = collectionName;
        this.entityClass = null;
    }

    /**
     * Typed constructor for cases where the entity class is known at construction time,
     * enabling the convenience {@link #get(String)} overload.
     */
    public RedisEntityCacheAdapter(
            DistributedCacheService cacheService,
            ObjectMapper objectMapper,
            Duration defaultTtl,
            String collectionName,
            Class<T> entityClass) {
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.defaultTtl = defaultTtl;
        this.collectionName = collectionName;
        this.entityClass = entityClass;
    }

    /**
     * Retrieve an entity from the cache.
     *
     * @param key  entity key (unscoped — collection namespace is prepended automatically)
     * @param type entity class for deserialization
     * @return {@code Optional.of(entity)} on a cache hit, {@code Optional.empty()} on a miss
     */
    public Optional<T> get(String key, Class<T> type) {
        String scopedKey = scopedKey(key);
        Optional<T> result = cacheService.get(scopedKey, type);
        if (result.isPresent()) {
            LOG.debug("Cache hit for {}:{}", collectionName, key);
        } else {
            LOG.debug("Cache miss for {}:{}", collectionName, key);
        }
        return result;
    }

    /**
     * Retrieve an entity from the cache using the entity class provided at construction time.
     *
     * @throws IllegalStateException if no entity class was provided at construction time
     */
    public Optional<T> get(String key) {
        if (entityClass == null) {
            throw new IllegalStateException(
                    "Entity class not set — use get(String, Class<T>) or the typed constructor");
        }
        return get(key, entityClass);
    }

    /**
     * Cache an entity with the default TTL.
     */
    public void put(String key, T value) {
        String scopedKey = scopedKey(key);
        cacheService.put(scopedKey, value, defaultTtl.getSeconds());
        LOG.debug("Cached {}:{} (TTL {}s)", collectionName, key, defaultTtl.getSeconds());
    }

    /**
     * Cache an entity with an explicit TTL.
     */
    public void put(String key, T value, Duration ttl) {
        String scopedKey = scopedKey(key);
        cacheService.put(scopedKey, value, ttl.getSeconds());
        LOG.debug("Cached {}:{} (TTL {}s)", collectionName, key, ttl.getSeconds());
    }

    /**
     * Invalidate a single cache entry.
     */
    public void invalidate(String key) {
        String scopedKey = scopedKey(key);
        cacheService.invalidate(scopedKey);
        LOG.debug("Invalidated {}:{}", collectionName, key);
    }

    /**
     * Invalidate all entries for this collection (pattern-based).
     */
    public void clear() {
        cacheService.invalidatePattern(collectionName + ":*");
        LOG.debug("Cleared all cache entries for collection {}", collectionName);
    }

    /**
     * Return cache statistics scoped to this collection.
     */
    public CacheStatistics getStatistics() {
        DistributedCacheService.CacheStatistics stats =
                cacheService.getStatistics(collectionName + ":*");
        return new CacheStatistics(stats.totalKeys, stats.totalSize);
    }

    private String scopedKey(String key) {
        return collectionName + ":" + key;
    }

    /**
     * Cache statistics for this adapter's collection scope.
     *
     * @param keyCount  number of keys currently in cache for this collection
     * @param sizeBytes approximate memory footprint in bytes
     */
    public record CacheStatistics(long keyCount, long sizeBytes) {}
}
