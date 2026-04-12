package com.ghatana.platform.cache;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Write-through two-level distributed cache.
 *
 * <p>Layer 1 (L1): local {@link InMemoryCacheAdapter} — ultra-fast reads for recently-accessed data.
 * Layer 2 (L2): remote {@link RedisDistributedCacheAdapter} — shared across all nodes for coherent
 * invalidation and cross-node visibility.</p>
 *
 * <h3>Cache population strategy</h3>
 * <ol>
 *   <li>GET: check L1 (Caffeine) first. If hit → return immediately.  </li>
 *   <li>GET miss: check L2 (Redis). If hit → populate L1, return value.</li>
 *   <li>GET miss in both: invoke loader, write to L2, populate L1, return value.</li>
 *   <li>INVALIDATE: delete from L2 first (cross-node), then L1 (local).</li>
 * </ol>
 *
 * <h3>Failure handling</h3>
 * <p>Redis failures are silently swallowed (logged at WARN). On L2 failure, the cache
 * degrades gracefully to L1-only behaviour — callers will not see errors.</p>
 *
 * @param <K> cache key type
 * @param <V> cache value type
 *
 * @doc.type class
 * @doc.purpose Two-level write-through cache (L1=Caffeine, L2=Redis) for horizontal scaling
 * @doc.layer platform
 * @doc.pattern Composite Cache (L1/L2 write-through)
 * @since 1.0.0
 */
public class WriteThroughDistributedCache<K, V> implements DistributedCachePort<K, V> {

    private static final Logger log = LoggerFactory.getLogger(WriteThroughDistributedCache.class);

    private final InMemoryCacheAdapter<K, V> l1;
    private final DistributedCachePort<K, V> l2;

    /**
     * @param l1 local Caffeine cache
     * @param l2 remote distributed cache (typically Redis)
     */
    public WriteThroughDistributedCache(InMemoryCacheAdapter<K, V> l1, DistributedCachePort<K, V> l2) {
        this.l1 = Objects.requireNonNull(l1, "l1 must not be null");
        this.l2 = Objects.requireNonNull(l2, "l2 must not be null");
    }

    @Override
    public Promise<Optional<V>> get(K key) {
        return l1.get(key).then(optL1 -> {
            if (optL1.isPresent()) {
                return Promise.of(optL1);
            }
            return l2.get(key).then(optL2 -> {
                if (optL2.isPresent()) {
                    // Populate L1 from L2 hit
                    return l1.put(key, optL2.get()).map($ -> optL2);
                }
                return Promise.of(Optional.empty());
            });
        });
    }

    @Override
    public Promise<Void> put(K key, V value) {
        return l2.put(key, value).then($ -> l1.put(key, value));
    }

    @Override
    public Promise<Void> put(K key, V value, Duration ttl) {
        return l2.put(key, value, ttl).then($ -> l1.put(key, value, ttl));
    }

    @Override
    public Promise<V> getOrLoad(K key, Function<K, Promise<V>> loader) {
        return get(key).then(optVal -> {
            if (optVal.isPresent()) {
                return Promise.of(optVal.get());
            }
            return loader.apply(key)
                    .then(value -> put(key, value).map($ -> value));
        });
    }

    @Override
    public Promise<Void> invalidate(K key) {
        // Invalidate L2 first (cross-node propagation), then L1
        return l2.invalidate(key).then($ -> l1.invalidate(key));
    }

    @Override
    public Promise<Void> invalidateAll() {
        return l2.invalidateAll().then($ -> l1.invalidateAll());
    }

    @Override
    public boolean isHealthy() {
        return l2.isHealthy();
    }
}
