package com.ghatana.platform.cache;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.Optional;

/**
 * @doc.type interface
 * @doc.purpose Async distributed cache contract for tenant-aware adapters.
 * @doc.layer platform
 * @doc.pattern Port
 */
public interface DistributedCachePort<K, V> {

    Promise<Optional<V>> get(K key);

    Promise<Void> put(K key, V value, Duration ttl);

    Promise<Void> invalidateAll();
}

