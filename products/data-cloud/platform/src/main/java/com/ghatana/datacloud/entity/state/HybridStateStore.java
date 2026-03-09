package com.ghatana.datacloud.entity.state;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.*;

/**
 * Hybrid state store abstraction combining local and centralized storage.
 *
 * <p><b>Purpose</b><br>
 * Provides fast local access with cross-instance sharing and fault tolerance. Balances
 * performance (in-memory/embedded storage) with durability (Redis/PostgreSQL). Supports
 * TTL expiration, partitioned state, and checkpoint/recovery.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * HybridStateStore store = new HybridStateStoreImpl(localAdapter, centralAdapter, SyncStrategy.BATCHED);
 * Promise<Void> put = store.put("key1", "value1", Duration.ofHours(1), "partition-0");
 * Promise<Optional<String>> get = store.get("key1", "partition-0");
 * Promise<Void> checkpoint = store.checkpoint("partition-0");
 * Promise<Void> recover = store.recover("partition-0");
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Core abstraction for operator state management in distributed event processing.
 * Part of hybrid state management strategy: local tier (sub-millisecond access) + central tier (fault tolerance).
 * Implements checkpoint/savepoint semantics for recovery.
 *
 * <p><b>Sync Strategies</b><br>
 * - IMMEDIATE: Every write synced (safest, ~100ms latency)
 * - BATCHED: Batch syncs every 100-500ms (default, recommended)
 * - PERIODIC: Sync every T milliseconds (fast, eventual consistency)
 * - ON_CHECKPOINT: Only during checkpoint (maximum performance)
 *
 * <p><b>State Partitioning</b><br>
 * State keys use format: {tenant}:{operatorId}:{stateType}:{partition}:{key}
 * Each partition independently checkpointable for recovery.
 *
 * @param <K> Key type (typically String)
 * @param <V> Value type (typically String, Object, or Bytes)
 * @see StateAdapter
 * @doc.type interface
 * @doc.purpose Hybrid state store with local + centralized tiers
 * @doc.layer domain
 * @doc.pattern Port
 */
public interface HybridStateStore<K, V> {

    /**
     * Sync strategy for state consistency.
     */
    enum SyncStrategy {
        /** Every write synced to central store (safest, ~100ms latency) */
        IMMEDIATE,
        /** Batch syncs (default, recommended, ~100-500ms latency) */
        BATCHED,
        /** Periodic syncs every T milliseconds (fast, eventual consistency) */
        PERIODIC,
        /** Only sync during checkpoint (maximum performance, ~1ms access) */
        ON_CHECKPOINT
    }

    /**
     * State entry metadata.
     */
    class StateEntry<V> {
        private final V value;
        private final long createdAt;
        private final long expiresAt;
        private final String partition;
        private final long version;

        public StateEntry(V value, long createdAt, long expiresAt, String partition, long version) {
            this.value = value;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.partition = partition;
            this.version = version;
        }

        public V getValue() { return value; }
        public long getCreatedAt() { return createdAt; }
        public long getExpiresAt() { return expiresAt; }
        public String getPartition() { return partition; }
        public long getVersion() { return version; }
        public boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    }

    /**
     * Put value in state store with TTL.
     *
     * <p>GIVEN: Key, value, and TTL duration
     * <p>WHEN: put is called
     * <p>THEN: Value stored locally and synced per strategy
     *
     * @param key State key
     * @param value State value
     * @param ttl Time-to-live duration (auto-expiry after TTL)
     * @param partition Partition for state grouping
     * @return Promise completing when stored (local storage)
     * @throws NullPointerException if key, value, or partition is null
     */
    Promise<Void> put(K key, V value, Duration ttl, String partition);

    /**
     * Put multiple values in batch (atomic).
     *
     * <p>GIVEN: Map of key-value pairs and TTL
     * <p>WHEN: putAll is called
     * <p>THEN: All values stored as batch
     *
     * @param entries Key-value pairs to store
     * @param ttl Time-to-live for all entries
     * @param partition Partition for state grouping
     * @return Promise completing when batch stored
     */
    Promise<Void> putAll(Map<K, V> entries, Duration ttl, String partition);

    /**
     * Get value from state store.
     *
     * <p>GIVEN: Key and partition
     * <p>WHEN: get is called
     * <p>THEN: Returns value from local store (or fetch from central if evicted)
     *
     * @param key State key
     * @param partition Partition for state grouping
     * @return Promise<Optional<V>> with value or empty if not found/expired
     */
    Promise<Optional<V>> get(K key, String partition);

    /**
     * Get multiple values in batch.
     *
     * <p>GIVEN: List of keys
     * <p>WHEN: getAll is called
     * <p>THEN: Returns map of found values
     *
     * @param keys List of state keys
     * @param partition Partition for state grouping
     * @return Promise<Map<K, V>> with found values
     */
    Promise<Map<K, V>> getAll(Collection<K> keys, String partition);

    /**
     * Delete value from state store.
     *
     * <p>GIVEN: Key and partition
     * <p>WHEN: delete is called
     * <p>THEN: Value removed from local and central stores
     *
     * @param key State key
     * @param partition Partition for state grouping
     * @return Promise completing when deleted
     */
    Promise<Void> delete(K key, String partition);

    /**
     * Delete multiple values in batch.
     *
     * <p>GIVEN: List of keys
     * <p>WHEN: deleteAll is called
     * <p>THEN: All values deleted atomically
     *
     * @param keys List of state keys to delete
     * @param partition Partition for state grouping
     * @return Promise completing when batch deleted
     */
    Promise<Void> deleteAll(Collection<K> keys, String partition);

    /**
     * Clear all state for partition.
     *
     * <p>GIVEN: Partition identifier
     * <p>WHEN: clear is called
     * <p>THEN: All state for partition cleared
     *
     * @param partition Partition to clear
     * @return Promise completing when cleared
     */
    Promise<Void> clear(String partition);

    /**
     * Checkpoint state (save to durable central store).
     *
     * <p>GIVEN: Partition identifier
     * <p>WHEN: checkpoint is called
     * <p>THEN: All state synced to central store, savepoint created
     *
     * @param partition Partition to checkpoint
     * @return Promise completing when checkpoint saved
     */
    Promise<Void> checkpoint(String partition);

    /**
     * Recover state from checkpoint (e.g., after restart).
     *
     * <p>GIVEN: Partition identifier
     * <p>WHEN: recover is called
     * <p>THEN: All state loaded from central store (overwriting local)
     *
     * @param partition Partition to recover
     * @return Promise completing when recovered
     */
    Promise<Void> recover(String partition);

    /**
     * Get state statistics for partition.
     *
     * <p>GIVEN: Partition identifier
     * <p>WHEN: getStatistics is called
     * <p>THEN: Returns stats including size, entry count, sync lag
     *
     * @param partition Partition to query
     * @return Promise<Map<String, Object>> with stats
     */
    Promise<Map<String, Object>> getStatistics(String partition);

    /**
     * Check if key exists in state store.
     *
     * <p>GIVEN: Key and partition
     * <p>WHEN: exists is called
     * <p>THEN: Returns true if key exists and not expired
     *
     * @param key State key
     * @param partition Partition for state grouping
     * @return Promise<Boolean> true if exists
     */
    Promise<Boolean> exists(K key, String partition);

    /**
     * Get entry count for partition.
     *
     * <p>GIVEN: Partition identifier
     * <p>WHEN: size is called
     * <p>THEN: Returns number of entries in partition
     *
     * @param partition Partition to count
     * @return Promise<Long> count of entries
     */
    Promise<Long> size(String partition);

    /**
     * Close state store and release resources.
     *
     * <p>GIVEN: State store instance
     * <p>WHEN: close is called
     * <p>THEN: Local and central stores closed, data persisted
     *
     * @return Promise completing when closed
     */
    Promise<Void> close();

    /**
     * Flush pending writes to central store.
     *
     * <p>GIVEN: Pending changes in buffer
     * <p>WHEN: flush is called
     * <p>THEN: All buffered writes synced to central store
     *
     * @return Promise completing when flushed
     */
    Promise<Void> flush();
}
