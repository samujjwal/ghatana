package com.ghatana.core.state;

import io.activej.promise.Promise;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory state store backed by ConcurrentHashMap.
 *
 * @param <K> key type
 * @param <V> value type
 *
 * @doc.type class
 * @doc.purpose In-memory state store for testing and lightweight usage
 * @doc.layer platform
 * @doc.pattern Service
 */
public class InMemoryStateStore<K, V> {

    private final Map<K, V> store = new ConcurrentHashMap<>();

    public Promise<Optional<V>> get(K key) {
        return Promise.of(Optional.ofNullable(store.get(key)));
    }

    public void put(K key, V value) {
        Objects.requireNonNull(key, "key must not be null");
        store.put(key, value);
    }

    public void remove(K key) {
        store.remove(key);
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }
}
