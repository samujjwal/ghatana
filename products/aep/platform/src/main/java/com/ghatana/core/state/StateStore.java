package com.ghatana.core.state;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Interface for operator state storage.
 *
 * <p><b>Purpose</b><br>
 * Provides abstraction for storing and retrieving operator state,
 * supporting both local (fast) and centralized (durable) storage backends.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * StateStore<String, UserProfile> store = HybridStateStore.builder()
 *     .localStore(new RocksDBStateStore())
 *     .centralStore(new RedisStateStore())
 *     .syncStrategy(SyncStrategy.BATCHED)
 *     .build();
 *
 * // Store state
 * store.put("user:123", userProfile).getResult();
 *
 * // Retrieve state
 * Optional<UserProfile> profile = store.get("user:123").getResult();
 * }</pre>
 *
 * <p><b>Implementation Requirements</b><br>
 * Implementations must be:
 * <ul>
 *   <li>Thread-safe for concurrent access</li>
 *   <li>Support for TTL (time-to-live)</li>
 *   <li>Partition-aware for distributed systems</li>
 *   <li>Recoverable (persistent or centralized)</li>
 * </ul>
 *
 * <p><b>State Key Format</b><br>
 * Keys should follow format: {@code {tenant}:{operatorId}:{stateType}:{partition}:{key}}
 *
 * @param <K> Key type
 * @param <V> Value type
 * @see HybridStateStore
 * @doc.type interface
 * @doc.purpose State storage abstraction
 * @doc.layer core
 * @doc.pattern Repository
 */
public interface StateStore<K, V> {

    /**
     * Store value for key with optional TTL.
     *
     * @param key State key
     * @param value State value
     * @param ttl Optional time-to-live duration (state expires after duration)
     * @return Promise of completion
     */
    Promise<Void> put(K key, V value, Optional<Duration> ttl);

    /**
     * Store value for key without TTL (persistent until deleted).
     *
     * @param key State key
     * @param value State value
     * @return Promise of completion
     */
    default Promise<Void> put(K key, V value) {
        return put(key, value, Optional.empty());
    }

    /**
     * Retrieve value for key with type safety.
     *
     * @param key State key
     * @param valueType Expected value type (for deserialization)
     * @return Promise of optional value (empty if not found or expired)
     */
    Promise<Optional<V>> get(K key, Class<V> valueType);

    /**
     * Retrieve value for key.
     *
     * <p>Note: This method may not work correctly if the value type
     * requires explicit type information for deserialization.
     * Prefer {@link #get(Object, Class)} when possible.
     *
     * @param key State key
     * @return Promise of optional value
     */
    Promise<Optional<V>> get(K key);

    /**
     * Remove value for key.
     *
     * @param key State key
     * @return Promise of completion
     */
    Promise<Void> remove(K key);

    /**
     * Delete value for key (alias for {@link #remove(Object)}).
     *
     * @param key State key
     * @return Promise of completion
     */
    default Promise<Void> delete(K key) {
        return remove(key);
    }

    /**
     * Check if key exists.
     *
     * @param key State key
     * @return Promise of existence check
     */
    Promise<Boolean> contains(K key);

    /**
     * Check if key exists (alias for {@link #contains(Object)}).
     *
     * @param key State key
     * @return Promise of true if key exists, false otherwise
     */
    default Promise<Boolean> exists(K key) {
        return contains(key);
    }

    /**
     * Get all keys in store.
     *
     * @return Promise of key set
     */
    Promise<Set<K>> keys();

    /**
     * Get all key-value pairs.
     *
     * @return Promise of state map
     */
    Promise<Map<K, V>> getAll();

    /**
     * Put all key-value pairs (bulk operation).
     *
     * @param entries Entries to store
     * @return Promise of completion
     */
    Promise<Void> putAll(Map<K, V> entries);

    /**
     * Clear all state.
     *
     * @return Promise of completion
     */
    Promise<Void> clear();

    /**
     * Get current size of state.
     *
     * @return Promise of size
     */
    Promise<Long> size();

    /**
     * Flush any pending changes.
     *
     * <p>For stores with write-behind caching, this ensures all
     * changes are persisted.
     *
     * @return Promise of completion
     */
    Promise<Void> flush();

    /**
     * Close state store and release resources.
     *
     * @return Promise of completion
     */
    Promise<Void> close();

    /**
     * Check if store is healthy.
     *
     * @return true if store is operational
     */
    boolean isHealthy();
}

