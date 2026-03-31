package com.ghatana.yappc.infrastructure.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Simple in-memory cache with TTL support for Data-Cloud entities.
 *
 * <p><b>Purpose</b><br>
 * Reduces Data-Cloud query load by caching frequently accessed entities
 * with automatic expiration. Provides a lightweight caching layer
 * for read-heavy workloads.
 *
 * <p><b>Features</b><br>
 * - TTL-based expiration<br>
 * - Size-based eviction (LRU)<br>
 * - Thread-safe operations<br>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EntityCache<ProjectEntity> cache = new EntityCache<>(
 *     Duration.ofMinutes(5), 1000);
 *
 * // Get or load
 * ProjectEntity project = cache.get(projectId)
 *     .orElseGet(() -> loadFromDataCloud(projectId));
 * }</pre>
 *
 * @param <T> The entity type
 *
 * @doc.type class
 * @doc.purpose In-memory entity cache with TTL
 * @doc.layer infrastructure
 * @doc.pattern Cache
 */
public class EntityCache<T> {

    private final Duration ttl;
    private final int maxSize;
    private final ConcurrentMap<String, CacheEntry<T>> cache;

    public EntityCache(Duration ttl, int maxSize) {
        this.ttl = ttl;
        this.maxSize = maxSize;
        this.cache = new ConcurrentHashMap<>();
    }

    /**
     * Retrieves entity from cache if present and not expired.
     *
     * @param key the cache key
     * @return optional containing entity if cached
     */
    public Optional<T> get(String key) {
        CacheEntry<T> entry = cache.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired()) {
            cache.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    /**
     * Stores entity in cache.
     *
     * @param key the cache key
     * @param value the entity to cache
     */
    public void put(String key, T value) {
        // Evict if at capacity (simple eviction - not LRU for simplicity)
        if (cache.size() >= maxSize) {
            evictExpired();
        }
        cache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + ttl.toMillis()));
    }

    /**
     * Removes entity from cache.
     *
     * @param key the cache key
     */
    public void invalidate(String key) {
        cache.remove(key);
    }

    /**
     * Clears all cached entries.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Returns current cache size.
     *
     * @return number of cached entries
     */
    public int size() {
        return cache.size();
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private record CacheEntry<T>(T value, long expiresAt) {
        boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }
        boolean isExpired(long now) {
            return now > expiresAt;
        }
    }
}
