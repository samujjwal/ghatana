package com.ghatana.platform.cache;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * Generic distributed cache port — primary abstraction for cache operations.
 *
 * <p>All implementations MUST be safe for concurrent use from multiple threads.
 * All IO-bound methods MUST be non-blocking and return {@code Promise<T>};
 * callers must wrap blocking back-ends in {@code Promise.ofBlocking(executor, ...)}.</p>
 *
 * <p>Cache entries are stored under a logical {@code namespace} prefix so that
 * multiple feature areas can share the same physical cache store without key
 * collisions. The namespace is set at construction time and is transparent to
 * the caller.</p>
 *
 * <h3>Invalidation contract</h3>
 * <ul>
 *   <li>To invalidate a single key: call {@link #invalidate(Object)}.</li>
 *   <li>To invalidate all entries for a namespace: call {@link #invalidateAll()}.</li>
 *   <li>TTL-based expiry is automatically enforced by the underlying store.    </li>
 * </ul>
 *
 * @param <K> cache key type — must have a stable, collision-free {@code toString()} representation
 * @param <V> cache value type — must be serializable by the configured serializer
 *
 * @doc.type interface
 * @doc.purpose Generic distributed cache port for multi-node safe caching (KRQ-05)
 * @doc.layer platform
 * @doc.pattern Port (Hexagonal Architecture)
 * @since 1.0.0
 */
public interface DistributedCachePort<K, V> {

    /**
     * Retrieves the cached value for the given key.
     *
     * @param key non-null cache key
     * @return a Promise resolving to {@code Optional.empty()} if the key is absent or expired
     */
    Promise<Optional<V>> get(K key);

    /**
     * Stores a value in the cache using the default TTL configured for this port instance.
     *
     * @param key   non-null cache key
     * @param value non-null cache value
     * @return a Promise that completes when the entry is stored
     */
    Promise<Void> put(K key, V value);

    /**
     * Stores a value with a caller-supplied TTL, overriding the port-level default.
     *
     * @param key   non-null cache key
     * @param value non-null cache value
     * @param ttl   positive duration; the cached entry will expire after this interval
     * @return a Promise that completes when the entry is stored
     */
    Promise<Void> put(K key, V value, Duration ttl);

    /**
     * Retrieves a cached value, or computes it via {@code loader}, stores it, and returns it.
     *
     * <p>This is NOT an atomic compare-and-set — under concurrent load two threads may
     * both execute the loader for the same key. The last writer wins. This is acceptable
     * for read-heavy caches where slightly stale or duplicated computation is preferable
     * to distributed locking overhead.</p>
     *
     * @param key    non-null cache key
     * @param loader function to compute the value when the key is absent. May be called
     *               concurrently if multiple nodes race. Must not return {@code null}.
     * @return a Promise resolving to the cached or freshly computed value
     */
    Promise<V> getOrLoad(K key, Function<K, Promise<V>> loader);

    /**
     * Removes the entry for the given key.
     *
     * @param key non-null cache key
     * @return a Promise that completes when the entry is removed (or was already absent)
     */
    Promise<Void> invalidate(K key);

    /**
     * Removes all entries in this port's namespace.
     *
     * <p>Use with care in production — namespace-level flush will temporarily degrade
     * cache hit-rate until entries are re-populated.</p>
     *
     * @return a Promise that completes when the namespace has been cleared
     */
    Promise<Void> invalidateAll();

    /**
     * Returns {@code true} when the underlying connection to the cache store is healthy.
     */
    boolean isHealthy();
}
