package com.ghatana.platform.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.activej.promise.Promise;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * In-process fallback implementation of {@link DistributedCachePort} backed by Caffeine.
 *
 * <p>Use this implementation when:</p>
 * <ul>
 *   <li>Running in <b>single-node deployments</b> where multi-node invalidation is not needed.</li>
 *   <li>Writing <b>unit tests</b> — no Redis required.</li>
 *   <li>During <b>graceful Redis failover</b> — the {@link WriteThroughDistributedCache} uses this
 *       as a local fallback layer when the remote store is unavailable.</li>
 * </ul>
 *
 * <p>This implementation is <b>not</b> suitable for horizontally-scaled deployments where
 * key invalidation must propagate across nodes. Use {@link RedisDistributedCacheAdapter} instead.</p>
 *
 * @param <K> cache key type
 * @param <V> cache value type
 *
 * @doc.type class
 * @doc.purpose In-process Caffeine-backed fallback for DistributedCachePort (tests + single-node)
 * @doc.layer platform
 * @doc.pattern Adapter (Port Driven Fallback)
 * @since 1.0.0
 */
public class InMemoryCacheAdapter<K, V> implements DistributedCachePort<K, V> {

    private final Cache<K, V> delegate;

    /**
     * Constructs an in-memory cache with caller-specified size and TTL bounds.
     *
     * @param maximumSize maximum number of entries before eviction kicks in
     * @param defaultTtl  time-to-live per entry after write
     */
    public InMemoryCacheAdapter(long maximumSize, Duration defaultTtl) {
        Objects.requireNonNull(defaultTtl, "defaultTtl must not be null");
        if (maximumSize <= 0) throw new IllegalArgumentException("maximumSize must be positive");
        if (defaultTtl.isNegative() || defaultTtl.isZero()) {
            throw new IllegalArgumentException("defaultTtl must be positive");
        }
        this.delegate = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(defaultTtl)
                .build();
    }

    @Override
    public Promise<Optional<V>> get(K key) {
        return Promise.of(Optional.ofNullable(delegate.getIfPresent(key)));
    }

    @Override
    public Promise<Void> put(K key, V value) {
        delegate.put(key, value);
        return Promise.complete();
    }

    @Override
    public Promise<Void> put(K key, V value, Duration ttl) {
        // Caffeine does not support per-key TTL in the standard builder; store with default TTL.
        // For test and single-node use-cases this is acceptable.
        delegate.put(key, value);
        return Promise.complete();
    }

    @Override
    public Promise<V> getOrLoad(K key, Function<K, Promise<V>> loader) {
        V cached = delegate.getIfPresent(key);
        if (cached != null) {
            return Promise.of(cached);
        }
        return loader.apply(key)
                .then(value -> {
                    delegate.put(key, value);
                    return Promise.of(value);
                });
    }

    @Override
    public Promise<Void> invalidate(K key) {
        delegate.invalidate(key);
        return Promise.complete();
    }

    @Override
    public Promise<Void> invalidateAll() {
        delegate.invalidateAll();
        return Promise.complete();
    }

    @Override
    public boolean isHealthy() {
        return true;
    }
}
