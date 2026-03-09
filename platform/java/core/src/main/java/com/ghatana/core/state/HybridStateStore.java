package com.ghatana.core.state;

import io.activej.promise.Promise;

import java.util.Objects;
import java.util.Optional;

/**
 * Hybrid state store that coordinates between a local (fast) store
 * and a central (durable) store with configurable sync strategy.
 *
 * @param <K> key type
 * @param <V> value type
 *
 * @doc.type class
 * @doc.purpose State store combining local and central persistence
 * @doc.layer platform
 * @doc.pattern Service
 */
public class HybridStateStore<K, V> {

    private final InMemoryStateStore<K, V> localStore;
    private final InMemoryStateStore<K, V> centralStore;
    private final SyncStrategy syncStrategy;

    private HybridStateStore(Builder<K, V> builder) {
        this.localStore = Objects.requireNonNull(builder.localStore, "localStore");
        this.centralStore = Objects.requireNonNull(builder.centralStore, "centralStore");
        this.syncStrategy = builder.syncStrategy != null ? builder.syncStrategy : SyncStrategy.BATCHED;
    }

    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    /**
     * Gets value from local store first, falls back to central store.
     */
    public Promise<Optional<V>> get(K key) {
        return localStore.get(key)
                .then(optValue -> {
                    if (optValue.isPresent()) {
                        return Promise.of(optValue);
                    }
                    return centralStore.get(key)
                            .map(centralOpt -> {
                                centralOpt.ifPresent(v -> localStore.put(key, v));
                                return centralOpt;
                            });
                });
    }

    /**
     * Puts value in local store and syncs to central based on strategy.
     */
    public void put(K key, V value) {
        localStore.put(key, value);
        if (syncStrategy == SyncStrategy.IMMEDIATE) {
            centralStore.put(key, value);
        }
    }

    public SyncStrategy getSyncStrategy() {
        return syncStrategy;
    }

    public static class Builder<K, V> {
        private InMemoryStateStore<K, V> localStore;
        private InMemoryStateStore<K, V> centralStore;
        private SyncStrategy syncStrategy;

        public Builder<K, V> localStore(InMemoryStateStore<K, V> localStore) {
            this.localStore = localStore;
            return this;
        }

        public Builder<K, V> centralStore(InMemoryStateStore<K, V> centralStore) {
            this.centralStore = centralStore;
            return this;
        }

        public Builder<K, V> syncStrategy(SyncStrategy syncStrategy) {
            this.syncStrategy = syncStrategy;
            return this;
        }

        public HybridStateStore<K, V> build() {
            return new HybridStateStore<>(this);
        }
    }
}
