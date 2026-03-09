package com.ghatana.datacloud.spi.capability;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Capability interface for plugins that support storage operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines standard storage operations that plugins can implement:
 * <ul>
 * <li>Read/write operations</li>
 * <li>Batch operations</li>
 * <li>Range queries</li>
 * </ul>
 *
 * @param <K> the key type
 * @param <V> the value type
 * @see com.ghatana.datacloud.spi.Plugin
 * @doc.type interface
 * @doc.purpose Storage capability for plugins
 * @doc.layer spi
 * @doc.pattern Capability
 */
public interface StorageCapability<K, V> {

    /**
     * Gets a value by key.
     *
     * @param key the key
     * @return the value, or null if not found
     */
    Promise<V> get(K key);

    /**
     * Gets multiple values by keys.
     *
     * @param keys the keys
     * @return map of key to value for found entries
     */
    Promise<Map<K, V>> getAll(List<K> keys);

    /**
     * Puts a value.
     *
     * @param key the key
     * @param value the value
     * @return empty promise on success
     */
    Promise<Void> put(K key, V value);

    /**
     * Puts multiple values.
     *
     * @param entries the entries to put
     * @return empty promise on success
     */
    Promise<Void> putAll(Map<K, V> entries);

    /**
     * Deletes a value by key.
     *
     * @param key the key
     * @return true if deleted, false if not found
     */
    Promise<Boolean> delete(K key);

    /**
     * Deletes multiple values by keys.
     *
     * @param keys the keys to delete
     * @return number of entries deleted
     */
    Promise<Integer> deleteAll(List<K> keys);

    /**
     * Checks if a key exists.
     *
     * @param key the key
     * @return true if exists
     */
    Promise<Boolean> exists(K key);

    /**
     * Gets the count of stored entries.
     *
     * @return the count
     */
    Promise<Long> count();
}
