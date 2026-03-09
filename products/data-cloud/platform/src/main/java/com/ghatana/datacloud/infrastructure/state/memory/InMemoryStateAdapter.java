package com.ghatana.datacloud.infrastructure.state.memory;

import com.ghatana.datacloud.entity.state.StateAdapter;
import com.ghatana.platform.observability.NoopMetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of StateAdapter for testing and development.
 *
 * <p><b>Purpose</b><br>
 * Provides lightweight thread-safe in-memory state storage suitable for unit tests,
 * integration tests, and local development. All data volatile (lost on restart).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * StateAdapter adapter = new InMemoryStateAdapter();
 * Promise<Void> put = adapter.put("key1", "value1", 0);
 * Promise<Optional<String>> get = adapter.get("key1");
 * }</pre>
 *
 * <p><b>Performance</b><br>
 * - Put: <1ms (immediate in-memory)
 * - Get: <1ms (direct hash map lookup)
 * - Batch put: <0.1ms per entry
 *
 * <p><b>Features</b><br>
 * - TTL support: Auto-expiry after TTL (via expiration check on access)
 * - Thread-safe: ConcurrentHashMap for concurrent access
 * - No disk I/O: Everything in memory
 * - Testing: Suitable for unit tests and development
 *
 * @see StateAdapter
 * @doc.type class
 * @doc.purpose In-memory state adapter for testing
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class InMemoryStateAdapter implements StateAdapter<String, String> {
    private static final Logger logger = LoggerFactory.getLogger(InMemoryStateAdapter.class);

    /**
     * Entry with expiration timestamp.
     */
    private static class Entry {
        final String value;
        final long expiresAt;

        Entry(String value, long ttlMillis) {
            this.value = value;
            this.expiresAt = ttlMillis > 0
                ? System.currentTimeMillis() + ttlMillis
                : Long.MAX_VALUE;  // Never expires if ttlMillis <= 0
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    // Map: {key -> Entry with value and expiration}
    private final Map<String, Entry> store;
    private boolean closed = false;

    /**
     * Construct in-memory adapter with empty store.
     */
    public InMemoryStateAdapter() {
        this.store = new ConcurrentHashMap<>();
    }

    /**
     * Put value in memory with TTL.
     *
     * GIVEN: Key, value, and TTL
     * WHEN: put is called
     * THEN: Value stored in memory
     *
     * @param key State key
     * @param value State value
     * @param ttlMillis Time-to-live in milliseconds
     * @return Promise completing when stored
     */
    @Override
    public Promise<Void> put(String key, String value, long ttlMillis) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        try {
            store.put(key, new Entry(value, ttlMillis));
            logger.debug("Put key {} in memory (ttl={}ms)", key, ttlMillis);
            return Promise.of(null);
        } catch (Exception e) {
            logger.error("Failed to put key {} in memory", key, e);
            return Promise.ofException(e);
        }
    }

    /**
     * Batch put multiple key-value pairs.
     *
     * GIVEN: Map of entries and TTL
     * WHEN: putAll is called
     * THEN: All entries stored in memory
     *
     * @param entries Key-value pairs to store
     * @param ttlMillis Time-to-live for all entries
     * @return Promise completing when batch stored
     */
    @Override
    public Promise<Void> putAll(Map<String, String> entries, long ttlMillis) {
        Objects.requireNonNull(entries, "entries cannot be null");

        try {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                store.put(entry.getKey(), new Entry(entry.getValue(), ttlMillis));
            }
            logger.debug("Batch put {} entries in memory", entries.size());
            return Promise.of(null);
        } catch (Exception e) {
            logger.error("Failed to batch put entries in memory", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Get value from memory.
     *
     * GIVEN: Key
     * WHEN: get is called
     * THEN: Returns value if exists and not expired
     *
     * @param key State key
     * @return Promise<Optional<String>> with value or empty
     */
    @Override
    public Promise<Optional<String>> get(String key) {
        Objects.requireNonNull(key, "key cannot be null");

        try {
            Entry entry = store.get(key);
            if (entry != null) {
                if (entry.isExpired()) {
                    store.remove(key);
                    logger.debug("Key {} expired, removed from memory", key);
                    return Promise.of(Optional.empty());
                }
                logger.debug("Got key {} from memory", key);
                return Promise.of(Optional.of(entry.value));
            }
            return Promise.of(Optional.empty());
        } catch (Exception e) {
            logger.error("Failed to get key {} from memory", key, e);
            return Promise.ofException(e);
        }
    }

    /**
     * Batch get multiple keys.
     *
     * GIVEN: List of keys
     * WHEN: getAll is called
     * THEN: Returns map of found values (excluding expired)
     *
     * @param keys List of state keys
     * @return Promise<Map<String, String>> with found values
     */
    @Override
    public Promise<Map<String, String>> getAll(Collection<String> keys) {
        Objects.requireNonNull(keys, "keys cannot be null");

        try {
            Map<String, String> result = new HashMap<>();
            for (String key : keys) {
                Entry entry = store.get(key);
                if (entry != null && !entry.isExpired()) {
                    result.put(key, entry.value);
                }
            }
            logger.debug("Batch got {} of {} keys from memory", result.size(), keys.size());
            return Promise.of(result);
        } catch (Exception e) {
            logger.error("Failed to batch get keys from memory", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Delete value from memory.
     *
     * GIVEN: Key
     * WHEN: delete is called
     * THEN: Value removed from store
     *
     * @param key State key
     * @return Promise completing when deleted
     */
    @Override
    public Promise<Void> delete(String key) {
        Objects.requireNonNull(key, "key cannot be null");

        try {
            store.remove(key);
            logger.debug("Deleted key {} from memory", key);
            return Promise.of(null);
        } catch (Exception e) {
            logger.error("Failed to delete key {} from memory", key, e);
            return Promise.ofException(e);
        }
    }

    /**
     * Batch delete multiple keys.
     *
     * GIVEN: List of keys
     * WHEN: deleteAll is called
     * THEN: All keys removed
     *
     * @param keys List of state keys
     * @return Promise completing when batch deleted
     */
    @Override
    public Promise<Void> deleteAll(Collection<String> keys) {
        Objects.requireNonNull(keys, "keys cannot be null");

        try {
            for (String key : keys) {
                store.remove(key);
            }
            logger.debug("Batch deleted {} keys from memory", keys.size());
            return Promise.of(null);
        } catch (Exception e) {
            logger.error("Failed to batch delete keys from memory", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Clear all entries from memory.
     *
     * GIVEN: Store with entries
     * WHEN: clear is called
     * THEN: All entries removed
     *
     * @return Promise completing when cleared
     */
    @Override
    public Promise<Void> clear() {
        logger.warn("DESTRUCTIVE OPERATION: clear() called on InMemoryStateAdapter — "
                + "removing ALL entries ({} keys) across all tenants.", store.size());

        try {
            store.clear();
            logger.warn("DESTRUCTIVE OPERATION COMPLETED: In-memory store cleared");
            return Promise.of(null);
        } catch (Exception e) {
            logger.error("Failed to clear in-memory store", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Check if key exists.
     *
     * GIVEN: Key
     * WHEN: exists is called
     * THEN: Returns true if exists and not expired
     *
     * @param key State key
     * @return Promise<Boolean> true if exists
     */
    @Override
    public Promise<Boolean> exists(String key) {
        Objects.requireNonNull(key, "key cannot be null");

        try {
            Entry entry = store.get(key);
            if (entry != null && !entry.isExpired()) {
                return Promise.of(true);
            }
            return Promise.of(false);
        } catch (Exception e) {
            logger.error("Failed to check existence of key {}", key, e);
            return Promise.ofException(e);
        }
    }

    /**
     * Get statistics about in-memory store.
     *
     * GIVEN: Store state
     * WHEN: getStatistics is called
     * THEN: Returns stats map
     *
     * @return Promise<Map<String, Object>> with statistics
     */
    @Override
    public Promise<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("adapter_type", "InMemory");
            stats.put("entry_count", store.size());
            stats.put("timestamp", System.currentTimeMillis());

            // Count non-expired entries
            long nonExpiredCount = store.values().stream()
                .filter(entry -> !entry.isExpired())
                .count();
            stats.put("non_expired_count", nonExpiredCount);

            return Promise.of(stats);
        } catch (Exception e) {
            logger.error("Failed to get in-memory statistics", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Get approximate size of in-memory store.
     *
     * @return Promise<Long> approximate size in bytes
     */
    @Override
    public Promise<Long> getSize() {
        try {
            // Rough estimate: 40 bytes overhead per entry + key + value lengths
            long size = store.entrySet().stream()
                .mapToLong(entry -> 40L + entry.getKey().length() + entry.getValue().value.length())
                .sum();
            return Promise.of(size);
        } catch (Exception e) {
            logger.error("Failed to get in-memory size", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Get entry count.
     *
     * @return Promise<Long> number of entries
     */
    @Override
    public Promise<Long> getCount() {
        try {
            return Promise.of((long) store.size());
        } catch (Exception e) {
            logger.error("Failed to get in-memory entry count", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Close adapter and release resources.
     *
     * GIVEN: In-memory store
     * WHEN: close is called
     * THEN: Store cleared and adapter marked closed
     *
     * @return Promise completing when closed
     */
    @Override
    public Promise<Void> close() {
        try {
            store.clear();
            closed = true;
            logger.debug("Closed in-memory adapter");
            return Promise.of(null);
        } catch (Exception e) {
            logger.error("Failed to close in-memory adapter", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Get adapter type identifier.
     *
     * @return "InMemory"
     */
    @Override
    public String getAdapterType() {
        return "InMemory";
    }

    /**
     * Check if adapter is healthy.
     *
     * @return Promise<Boolean> true if not closed
     */
    @Override
    public Promise<Boolean> isHealthy() {
        return Promise.of(!closed);
    }
}
