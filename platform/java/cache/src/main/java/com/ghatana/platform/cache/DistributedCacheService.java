package com.ghatana.platform.cache;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * @doc.type class
 * @doc.purpose Redis-backed distributed caching service for platform-wide use
 * @doc.layer platform
 * @doc.pattern Service
 */
public class DistributedCacheService {

    private static final Logger log = LoggerFactory.getLogger(DistributedCacheService.class);

    // Redis client would be injected in production
    private final CacheBackend backend;
    private final String tenantId;

    public DistributedCacheService(CacheBackend backend, String tenantId) {
        this.backend = backend;
        this.tenantId = tenantId;
    }

    /**
     * Get value from cache with explicit key
     */
    public <T> Optional<T> get(String key, Class<T> valueType) {
        try {
            String finalKey = buildTenantKey(key);
            String value = backend.getValue(finalKey);

            if (value != null) {
                log.debug("Cache hit for key: {}", key);
                MDC.put("cacheHit", "true");
                return Optional.of(deserialize(value, valueType));
            }

            log.debug("Cache miss for key: {}", key);
            MDC.put("cacheMiss", "true");
            return Optional.empty();
        } catch (Exception e) {
            log.error("Cache retrieval error for key: {}", key, e);
            return Optional.empty();
        }
    }

    /**
     * Put value in cache with TTL
     */
    public <T> void put(String key, T value, long ttlSeconds) {
        try {
            String finalKey = buildTenantKey(key);
            String serialized = serialize(value);
            backend.setValue(finalKey, serialized, ttlSeconds);

            log.debug("Cached key: {} with TTL: {}s", key, ttlSeconds);
            MDC.put("cachePut", "true");
        } catch (Exception e) {
            log.error("Cache write error for key: {}", key, e);
            // Non-critical - log and continue
        }
    }

    /**
     * Invalidate cache entry
     */
    public void invalidate(String key) {
        try {
            String finalKey = buildTenantKey(key);
            backend.deleteKey(finalKey);

            log.debug("Invalidated cache key: {}", key);
            MDC.put("cacheInvalidate", "true");
        } catch (Exception e) {
            log.error("Cache invalidation error for key: {}", key, e);
        }
    }

    /**
     * Invalidate pattern (all keys matching pattern)
     */
    public void invalidatePattern(String pattern) {
        try {
            String finalPattern = buildTenantKey(pattern);
            int invalidatedCount = backend.deletePattern(finalPattern);

            log.info("Invalidated {} cache keys matching pattern: {}", invalidatedCount, pattern);
            MDC.put("invalidatedCount", String.valueOf(invalidatedCount));
        } catch (Exception e) {
            log.error("Cache pattern invalidation error for pattern: {}", pattern, e);
        }
    }

    /**
     * Get or compute - retrieve from cache or compute and cache
     */
    public <T> T getOrCompute(String key, long ttlSeconds, CacheLoader<T> loader, Class<T> valueType) {
        // Try cache first
        Optional<T> cached = get(key, valueType);
        if (cached.isPresent()) {
            return cached.get();
        }

        // Compute and cache
        try {
            T value = loader.load();
            if (value != null) {
                put(key, value, ttlSeconds);
            }
            return value;
        } catch (Exception e) {
            log.error("Cache loader error for key: {}", key, e);
            throw new CacheException("Failed to load value for key: " + key, e);
        }
    }

    /**
     * Get cache statistics
     */
    public CacheStatistics getStatistics(String keyPattern) {
        try {
            String finalPattern = buildTenantKey(keyPattern);
            long totalKeys = backend.getKeyCount(finalPattern);
            long cacheSize = backend.getCacheSize(finalPattern);

            return new CacheStatistics(totalKeys, cacheSize);
        } catch (Exception e) {
            log.error("Error retrieving cache statistics", e);
            return new CacheStatistics(0, 0);
        }
    }

    /**
     * Build tenant-scoped cache key
     */
    private String buildTenantKey(String key) {
        return String.format("tenant:%s:%s", tenantId, key);
    }

    /**
     * Serialize object to string (implementation delegated to backend)
     */
    private <T> String serialize(T value) {
        // In production, use Jackson or similar
        return backend.serialize(value);
    }

    /**
     * Deserialize string to object (implementation delegated to backend)
     */
    private <T> T deserialize(String value, Class<T> type) {
        // In production, use Jackson or similar
        return backend.deserialize(value, type);
    }

    /**
     * Functional interface for cache loading
     */
    @FunctionalInterface
    public interface CacheLoader<T> {
        T load() throws Exception;
    }

    /**
     * Cache statistics record
     */
    public static class CacheStatistics {
        public final long totalKeys;
        public final long totalSize;

        public CacheStatistics(long totalKeys, long totalSize) {
            this.totalKeys = totalKeys;
            this.totalSize = totalSize;
        }
    }

    /**
     * Cache backend abstraction
     */
    public interface CacheBackend {
        String getValue(String key);
        void setValue(String key, String value, long ttlSeconds);
        void deleteKey(String key);
        int deletePattern(String pattern);
        long getKeyCount(String pattern);
        long getCacheSize(String pattern);
        <T> String serialize(T value);
        <T> T deserialize(String value, Class<T> type);
    }

    /**
     * Cache exception
     */
    public static class CacheException extends RuntimeException {
        public CacheException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
