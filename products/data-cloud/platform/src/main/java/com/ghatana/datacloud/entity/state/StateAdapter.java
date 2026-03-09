package com.ghatana.datacloud.entity.state;

import io.activej.promise.Promise;

import java.util.*;

/**
 * Adapter interface for state storage implementations.
 *
 * <p><b>Purpose</b><br>
 * Provides pluggable interface for local and centralized state storage adapters.
 * Enables swapping implementations (RocksDB, H2, Redis, PostgreSQL) without changing
 * HybridStateStore logic.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * StateAdapter local = new RocksDBStateAdapter(dbPath);
 * StateAdapter central = new RedisStateAdapter(redisClient);
 * HybridStateStore store = new HybridStateStoreImpl(local, central, SyncStrategy.BATCHED);
 * }</pre>
 *
 * <p><b>Implementation Patterns</b><br>
 * Local Adapters (fast access):
 * - RocksDBStateAdapter: Embedded key-value store, millisecond latency
 * - H2StateAdapter: Embedded SQL database, ~5ms latency
 * - InMemoryStateAdapter: Testing/development, microsecond latency
 *
 * Central Adapters (fault tolerance):
 * - RedisStateAdapter: In-memory cache with persistence, 10-50ms latency
 * - PostgreSQLStateAdapter: Durable SQL store, 50-200ms latency
 * - HazelcastStateAdapter: Distributed grid, 10-100ms latency
 *
 * @param <K> Key type
 * @param <V> Value type
 * @see HybridStateStore
 * @doc.type interface
 * @doc.purpose Storage adapter interface for state implementations
 * @doc.layer domain
 * @doc.pattern Port
 */
public interface StateAdapter<K, V> {

    /**
     * Put value in storage.
     *
     * @param key State key (format: tenant:operatorId:partition:key)
     * @param value State value
     * @param ttlMillis Time-to-live in milliseconds (0 = no expiry)
     * @return Promise completing when stored
     * @throws NullPointerException if key or value is null
     */
    Promise<Void> put(K key, V value, long ttlMillis);

    /**
     * Put multiple values in batch (atomic or best-effort).
     *
     * @param entries Key-value pairs to store
     * @param ttlMillis Time-to-live for all entries
     * @return Promise completing when batch stored
     */
    Promise<Void> putAll(Map<K, V> entries, long ttlMillis);

    /**
     * Get value from storage.
     *
     * @param key State key
     * @return Promise<Optional<V>> with value or empty if not found
     */
    Promise<Optional<V>> get(K key);

    /**
     * Get multiple values in batch.
     *
     * @param keys List of state keys
     * @return Promise<Map<K, V>> with found values (missing keys excluded)
     */
    Promise<Map<K, V>> getAll(Collection<K> keys);

    /**
     * Delete value from storage.
     *
     * @param key State key
     * @return Promise completing when deleted
     */
    Promise<Void> delete(K key);

    /**
     * Delete multiple values in batch.
     *
     * @param keys List of state keys
     * @return Promise completing when batch deleted
     */
    Promise<Void> deleteAll(Collection<K> keys);

    /**
     * Delete all entries (clear storage).
     *
     * @return Promise completing when cleared
     */
    Promise<Void> clear();

    /**
     * Check if key exists in storage.
     *
     * @param key State key
     * @return Promise<Boolean> true if exists and not expired
     */
    Promise<Boolean> exists(K key);

    /**
     * Get storage statistics.
     *
     * @return Promise<Map<String, Object>> with size, count, etc.
     */
    Promise<Map<String, Object>> getStatistics();

    /**
     * Get storage size in bytes.
     *
     * @return Promise<Long> approximate size in bytes
     */
    Promise<Long> getSize();

    /**
     * Get entry count in storage.
     *
     * @return Promise<Long> number of entries
     */
    Promise<Long> getCount();

    /**
     * Close adapter and release resources.
     *
     * @return Promise completing when closed
     */
    Promise<Void> close();

    /**
     * Get adapter type/name (e.g., "RocksDB", "Redis", "H2").
     *
     * @return Adapter identifier
     */
    String getAdapterType();

    /**
     * Check if adapter is healthy.
     *
     * @return Promise<Boolean> true if healthy
     */
    Promise<Boolean> isHealthy();
}
