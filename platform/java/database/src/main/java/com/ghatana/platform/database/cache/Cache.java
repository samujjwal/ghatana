package com.ghatana.platform.database.cache;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Generic cache interface supporting both sync and async operations.
 * 
 * Implementations can use in-memory caching, Redis, or other backends.
 *
 * @param <K> the key type
 * @param <V> the value type
 *
 * @doc.type interface
 * @doc.purpose Async caching abstraction with Promise-based API
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface Cache<K, V> {
    
    /**
     * Get a value from the cache.
     *
     * @param key the key
     * @return the value, or empty if not found
     */
    Optional<V> get(@NotNull K key);
    
    /**
     * Get a value from the cache, or compute and store it if not present.
     *
     * @param key the key
     * @param loader the function to compute the value if not present
     * @return the value (from cache or newly computed)
     */
    V getOrCompute(@NotNull K key, @NotNull Supplier<V> loader);
    
    /**
     * Get a value from the cache, or compute and store it with TTL if not present.
     *
     * @param key the key
     * @param loader the function to compute the value if not present
     * @param ttl the time-to-live for the cached value
     * @return the value (from cache or newly computed)
     */
    V getOrCompute(@NotNull K key, @NotNull Supplier<V> loader, @NotNull Duration ttl);
    
    /**
     * Put a value in the cache.
     *
     * @param key the key
     * @param value the value
     */
    void put(@NotNull K key, @NotNull V value);
    
    /**
     * Put a value in the cache with a time-to-live.
     *
     * @param key the key
     * @param value the value
     * @param ttl the time-to-live
     */
    void put(@NotNull K key, @NotNull V value, @NotNull Duration ttl);
    
    /**
     * Put a value in the cache only if the key doesn't exist.
     *
     * @param key the key
     * @param value the value
     * @return true if the value was put, false if the key already existed
     */
    boolean putIfAbsent(@NotNull K key, @NotNull V value);
    
    /**
     * Put a value in the cache only if the key doesn't exist, with TTL.
     *
     * @param key the key
     * @param value the value
     * @param ttl the time-to-live
     * @return true if the value was put, false if the key already existed
     */
    boolean putIfAbsent(@NotNull K key, @NotNull V value, @NotNull Duration ttl);
    
    /**
     * Remove a value from the cache.
     *
     * @param key the key
     * @return the removed value, or empty if not found
     */
    Optional<V> remove(@NotNull K key);
    
    /**
     * Check if a key exists in the cache.
     *
     * @param key the key
     * @return true if the key exists
     */
    boolean contains(@NotNull K key);
    
    /**
     * Clear all entries from the cache.
     */
    void clear();
    
    /**
     * Get the number of entries in the cache.
     *
     * @return the size
     */
    long size();
    
    // ==========================================================================
    // Async Operations (ActiveJ Promise)
    // ==========================================================================

    /**
     * Get a value from the cache asynchronously.
     *
     * @param key the key
     * @return a Promise containing the value, or empty if not found
     */
    default Promise<Optional<V>> getAsync(@NotNull K key) {
        return Promise.ofBlocking(defaultExecutor(), () -> get(key));
    }
    
    /**
     * Put a value in the cache asynchronously.
     *
     * @param key the key
     * @param value the value
     * @return a Promise that completes when the value is stored
     */
    default Promise<Void> putAsync(@NotNull K key, @NotNull V value) {
        return Promise.ofBlocking(defaultExecutor(), () -> { put(key, value); return null; });
    }
    
    /**
     * Put a value in the cache asynchronously with TTL.
     *
     * @param key the key
     * @param value the value
     * @param ttl the time-to-live
     * @return a Promise that completes when the value is stored
     */
    default Promise<Void> putAsync(@NotNull K key, @NotNull V value, @NotNull Duration ttl) {
        return Promise.ofBlocking(defaultExecutor(), () -> { put(key, value, ttl); return null; });
    }
    
    /**
     * Remove a value from the cache asynchronously.
     *
     * @param key the key
     * @return a Promise containing the removed value, or empty if not found
     */
    default Promise<Optional<V>> removeAsync(@NotNull K key) {
        return Promise.ofBlocking(defaultExecutor(), () -> remove(key));
    }

    /**
     * Returns the default executor for async operations.
     * Implementations can override to provide a custom executor.
     *
     * @return the executor
     */
    private static Executor defaultExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
