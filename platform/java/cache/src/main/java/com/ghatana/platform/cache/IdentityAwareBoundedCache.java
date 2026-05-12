package com.ghatana.platform.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Objects;

/**
 * Identity-aware bounded cache for route entitlements and other user-specific data.
 * <p>
 * This cache uses principalId + tenantId + endpoint as the cache key to ensure
 * that cached data is properly scoped to the authenticated user and tenant.
 * It enforces size bounds and TTL to prevent unbounded memory growth.
 * </p>
 *
 * @doc.type class
 * @doc.purpose Identity-aware bounded cache with TTL and size limits
 * @doc.layer platform
 * @doc.pattern Cache
 */
public final class IdentityAwareBoundedCache<K, V> {

    private final int maxSize;
    private final long ttlMillis;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<CacheKey<K>, CacheEntry<V>> cache;

    /**
     * Creates a new identity-aware bounded cache.
     *
     * @param maxSize  the maximum number of entries in the cache
     * @param ttlMillis the time-to-live for cache entries in milliseconds
     */
    public IdentityAwareBoundedCache(int maxSize, long ttlMillis) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("ttlMillis must be positive");
        }
        this.maxSize = maxSize;
        this.ttlMillis = ttlMillis;
        this.cache = new LinkedHashMap<CacheKey<K>, CacheEntry<V>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey<K>, CacheEntry<V>> eldest) {
                return size() > maxSize;
            }
        };
    }

    /**
     * Gets a value from the cache.
     *
     * @param principalId the authenticated principal ID
     * @param tenantId    the tenant ID
     * @param endpoint    the endpoint identifier
     * @param key         the cache key
     * @return the cached value if present and not expired
     */
    public Optional<V> get(String principalId, String tenantId, String endpoint, K key) {
        Objects.requireNonNull(principalId, "principalId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(key, "key must not be null");

        CacheKey<K> cacheKey = new CacheKey<>(principalId, tenantId, endpoint, key);
        
        lock.readLock().lock();
        try {
            CacheEntry<V> entry = cache.get(cacheKey);
            if (entry == null) {
                return Optional.empty();
            }
            
            if (isExpired(entry)) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    cache.remove(cacheKey);
                } finally {
                    lock.writeLock().unlock();
                    lock.readLock().lock();
                }
                return Optional.empty();
            }
            
            return Optional.of(entry.value());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Puts a value into the cache.
     *
     * @param principalId the authenticated principal ID
     * @param tenantId    the tenant ID
     * @param endpoint    the endpoint identifier
     * @param key         the cache key
     * @param value       the value to cache
     */
    public void put(String principalId, String tenantId, String endpoint, K key, V value) {
        Objects.requireNonNull(principalId, "principalId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");

        CacheKey<K> cacheKey = new CacheKey<>(principalId, tenantId, endpoint, key);
        CacheEntry<V> entry = new CacheEntry<>(value, System.currentTimeMillis());
        
        lock.writeLock().lock();
        try {
            cache.put(cacheKey, entry);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Invalidates all cache entries for a specific principal.
     *
     * @param principalId the principal ID to invalidate
     */
    public void invalidatePrincipal(String principalId) {
        Objects.requireNonNull(principalId, "principalId must not be null");
        
        lock.writeLock().lock();
        try {
            cache.keySet().removeIf(key -> key.principalId().equals(principalId));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Invalidates all cache entries for a specific tenant.
     *
     * @param tenantId the tenant ID to invalidate
     */
    public void invalidateTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        
        lock.writeLock().lock();
        try {
            cache.keySet().removeIf(key -> key.tenantId().equals(tenantId));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clears all cache entries.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets the current size of the cache.
     *
     * @return the number of entries in the cache
     */
    public int size() {
        lock.readLock().lock();
        try {
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    private boolean isExpired(CacheEntry<V> entry) {
        return System.currentTimeMillis() - entry.timestamp() > ttlMillis;
    }

    private record CacheKey<T>(String principalId, String tenantId, String endpoint, T key) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey<?> cacheKey = (CacheKey<?>) o;
            return principalId.equals(cacheKey.principalId)
                && tenantId.equals(cacheKey.tenantId)
                && endpoint.equals(cacheKey.endpoint)
                && key.equals(cacheKey.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(principalId, tenantId, endpoint, key);
        }
    }

    private record CacheEntry<T>(T value, long timestamp) {
    }
}
