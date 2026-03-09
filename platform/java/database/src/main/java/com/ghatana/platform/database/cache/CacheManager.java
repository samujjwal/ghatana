package com.ghatana.platform.database.cache;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Cache manager interface for EventCloud platform.
 * Supports both synchronous and asynchronous operations with TTL and namespace management.
 *
 * <p>This interface provides Promise-based async cache operations with full lifecycle
 * management, pattern matching, and statistics tracking. Implementations MUST be thread-safe
 * and support multi-tenant isolation via namespacing.
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Promise-based async operations (no blocking)</li>
 *   <li>TTL support with default and per-key expiration</li>
 *   <li>Pattern-based key matching and deletion</li>
 *   <li>Get-or-compute pattern with automatic caching</li>
 *   <li>Atomic increment operations</li>
 *   <li>Cache statistics (hit/miss rates, evictions)</li>
 *   <li>Namespace isolation for multi-tenancy</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * CacheManager cache = new RedisCacheManager(jedisPool, objectMapper, "tenant1");
 * 
 * // Simple get/put
 * cache.put("user:123", user, Duration.ofMinutes(5))
 *     .then(() -> cache.get("user:123", User.class))
 *     .whenResult(opt -> opt.ifPresent(u -> logger.info("Found: {}", u)));
 * 
 * // Get-or-compute pattern
 * cache.getOrCompute("config", Config.class, Duration.ofHours(1),
 *     () -> loadConfigFromDatabase())
 *     .whenResult(config -> logger.info("Config loaded"));
 * 
 * // Pattern-based operations
 * cache.clearPattern("session:*")
 *     .whenResult(deleted -> logger.info("Cleared {} sessions", deleted));
 * }</pre>
 *
 * <h2>Thread Safety:</h2>
 * All implementations MUST be thread-safe and support concurrent access.
 *
 * <h2>Null Safety:</h2>
 * - {@code null} keys are NOT allowed (throw IllegalArgumentException)
 * - {@code null} values are allowed (stored as special marker)
 * - {@code null} TTL uses default TTL from implementation
 *
 * <h2>Performance Considerations:</h2>
 * - Pattern matching ({@code keys()}, {@code clearPattern()}) is O(N) - use sparingly
 * - Increment operations are atomic and thread-safe
 * - {@code getOrCompute()} prevents thundering herd via single-threaded computation
 *
 * @since 1.0.0
 * @doc.type interface
 * @doc.purpose Promise-based cache abstraction with TTL, patterns, and multi-tenancy support
 * @doc.layer core
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface CacheManager {
    
    /**
     * Get a value from cache.
     *
     * @param key  the cache key (namespace will be prepended by implementation)
     * @param type the expected value type
     * @param <T>  the value type
     * @return Promise of Optional containing value if found, empty otherwise
     * @throws IllegalArgumentException if key is null or empty
     */
    <T> Promise<Optional<T>> get(String key, Class<T> type);
    
    /**
     * Put a value in cache with default TTL.
     *
     * @param key   the cache key
     * @param value the value to cache (null allowed)
     * @param <T>   the value type
     * @return Promise that completes when value is cached
     */
    <T> Promise<Void> put(String key, T value);
    
    /**
     * Put a value in cache with specific TTL.
     *
     * @param key   the cache key
     * @param value the value to cache
     * @param ttl   time-to-live (null uses default TTL)
     * @param <T>   the value type
     * @return Promise that completes when value is cached
     */
    <T> Promise<Void> put(String key, T value, Duration ttl);
    
    /**
     * Remove a value from cache.
     *
     * @param key the cache key
     * @return Promise of true if key existed and was deleted, false otherwise
     */
    Promise<Boolean> remove(String key);
    
    /**
     * Check if key exists in cache.
     *
     * @param key the cache key
     * @return Promise of true if key exists, false otherwise
     */
    Promise<Boolean> exists(String key);
    
    /**
     * Get all keys matching a pattern.
     * <p><strong>Warning:</strong> O(N) operation - use sparingly in production.
     *
     * @param pattern glob pattern (e.g., "user:*", "session:12345:*")
     * @return Promise of Set of matching keys (without namespace prefix)
     */
    Promise<Set<String>> keys(String pattern);
    
    /**
     * Clear all cache entries.
     * <p><strong>Warning:</strong> O(N) operation - destructive.
     *
     * @return Promise that completes when all entries are cleared
     */
    Promise<Void> clear();
    
    /**
     * Clear all cache entries matching a pattern.
     * <p><strong>Warning:</strong> O(N) operation - use sparingly.
     *
     * @param pattern glob pattern (e.g., "session:*")
     * @return Promise of number of keys deleted
     */
    Promise<Long> clearPattern(String pattern);
    
    /**
     * Get cache statistics.
     *
     * @return current cache statistics (immutable snapshot)
     */
    CacheStats getStats();
    
    /**
     * Get or compute a value if not present in cache.
     * <p>If key exists, returns cached value. If not, executes supplier,
     * caches result with default TTL, and returns it. Prevents thundering
     * herd by serializing computation per key.
     *
     * @param key      the cache key
     * @param type     the expected value type
     * @param supplier computation that produces value if not cached
     * @param <T>      the value type
     * @return Promise of computed or cached value
     */
    <T> Promise<T> getOrCompute(String key, Class<T> type, java.util.function.Supplier<Promise<T>> supplier);
    
    /**
     * Get or compute a value with specific TTL if not present in cache.
     *
     * @param key      the cache key
     * @param type     the expected value type
     * @param ttl      time-to-live for newly computed value
     * @param supplier computation that produces value if not cached
     * @param <T>      the value type
     * @return Promise of computed or cached value
     */
    <T> Promise<T> getOrCompute(String key, Class<T> type, Duration ttl, java.util.function.Supplier<Promise<T>> supplier);
    
    /**
     * Increment a numeric value in cache.
     * <p>If key doesn't exist, initializes to 0 then increments to 1.
     *
     * @param key the cache key
     * @return Promise of new value after increment
     */
    Promise<Long> increment(String key);
    
    /**
     * Increment a numeric value by a specific amount.
     *
     * @param key   the cache key
     * @param delta amount to add (can be negative for decrement)
     * @return Promise of new value after increment
     */
    Promise<Long> increment(String key, long delta);
    
    /**
     * Set expiration time for a key.
     *
     * @param key the cache key
     * @param ttl time-to-live
     * @return Promise of true if expiration was set, false if key doesn't exist
     */
    Promise<Boolean> expire(String key, Duration ttl);
    
    /**
     * Get time to live for a key.
     *
     * @param key the cache key
     * @return Promise of TTL (ZERO if key doesn't exist, MAX_VALUE if no expiration)
     */
    Promise<Duration> ttl(String key);
    
    /**
     * Cache statistics interface.
     * <p>Provides immutable snapshot of cache performance metrics.
     *
     * @immutability Snapshot is immutable (values may change on next call)
     * @thread-safety Thread-safe reads (atomic counters)
     */
    interface CacheStats {
        /**
         * Total number of cache hits.
         *
         * @return hit count since cache initialization
         */
        long getHitCount();
        
        /**
         * Total number of cache misses.
         *
         * @return miss count since cache initialization
         */
        long getMissCount();
        
        /**
         * Cache hit rate (hits / total requests).
         *
         * @return hit rate between 0.0 and 1.0
         */
        double getHitRate();
        
        /**
         * Total number of entries evicted (via TTL or manual deletion).
         *
         * @return eviction count since cache initialization
         */
        long getEvictionCount();
        
        /**
         * Current number of entries in cache.
         *
         * @return cache size, or -1 if not supported by implementation
         */
        long getSize();
        
        /**
         * Average time to load (compute) values via getOrCompute().
         *
         * @return average load time, or ZERO if no loads occurred
         */
        Duration getAverageLoadTime();
    }
}
