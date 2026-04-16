package com.ghatana.yappc.infrastructure.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

/**
 * Stub cache adapter - caching disabled until platform cache module is fixed.
 *
 * @param <T> The entity type
 * @doc.type class
 * @doc.purpose Stub cache adapter (caching disabled)
 * @doc.layer infrastructure
 */
public class RedisEntityCacheAdapter<T> {

    private static final Logger LOG = LoggerFactory.getLogger(RedisEntityCacheAdapter.class);
    private final String collectionName;

    public RedisEntityCacheAdapter(
            Object ignored1,
            Object ignored2,
            Duration ignored3,
            String collectionName) {
        this.collectionName = collectionName;
        LOG.debug("Cache stub created for {}", collectionName);
    }

    public Optional<T> get(String key) {
        LOG.debug("Cache miss (stub) for {}:{}", collectionName, key);
        return Optional.empty();
    }

    public void put(String key, T value) {
        LOG.debug("Cache put (stub) for {}:{}", collectionName, key);
    }

    public void put(String key, T value, Duration ttl) {
        LOG.debug("Cache put with TTL (stub) for {}:{}", collectionName, key);
    }

    public void invalidate(String key) {
        LOG.debug("Cache invalidate (stub) for {}:{}", collectionName, key);
    }

    public void clear() {
        LOG.debug("Cache clear (stub) for {}", collectionName);
    }

    public CacheStatistics getStatistics() {
        return new CacheStatistics(0, 0);
    }

    public record CacheStatistics(long keyCount, long sizeBytes) {}
}
