package com.ghatana.platform.observability.idempotency;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link IdempotencyStore}.
 *
 * <p>P0-07: Thread-safe in-memory store suitable for local/test profiles.
 * Production deployments should use a durable database-backed implementation
 * to survive restarts and provide horizontal scalability.
 *
 * <p>This implementation automatically expires entries after their TTL.
 *
 * @doc.type class
 * @doc.purpose In-memory idempotency store for local/test profiles
 * @doc.layer platform
 * @doc.pattern InMemoryRepository
 */
public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryIdempotencyStore.class);

    // Composite key: tenantId:scope:idempotencyKey:principalId
    private final Map<String, IdempotencyEntry> store = new ConcurrentHashMap<>();

    @Override
    public Promise<IdempotencyEntry> get(String tenantId, String scope, String idempotencyKey, String principalId) {
        String key = compositeKey(tenantId, scope, idempotencyKey, principalId);
        IdempotencyEntry entry = store.get(key);
        
        if (entry == null) {
            return Promise.of(null);
        }
        
        // Clean up expired entries
        if (entry.isExpired()) {
            store.remove(key);
            return Promise.of(null);
        }
        
        return Promise.of(entry);
    }

    @Override
    public Promise<Void> put(String tenantId, String scope, String idempotencyKey, String principalId,
                           String payloadHash, Object response) {
        String key = compositeKey(tenantId, scope, idempotencyKey, principalId);
        IdempotencyEntry entry = new IdempotencyEntry(idempotencyKey, payloadHash, response);
        store.put(key, entry);
        log.debug("[Idempotency] Stored entry for key={}", key);
        return Promise.of(null);
    }

    @Override
    public Promise<Boolean> hasConflict(String tenantId, String scope, String idempotencyKey, String principalId, String payloadHash) {
        String key = compositeKey(tenantId, scope, idempotencyKey, principalId);
        IdempotencyEntry entry = store.get(key);
        
        if (entry == null || entry.isExpired()) {
            return Promise.of(false);
        }
        
        boolean hasConflict = !entry.payloadHash().equals(payloadHash);
        if (hasConflict) {
            log.warn("[Idempotency] Conflict detected for key={} - payload hash mismatch", key);
        }
        
        return Promise.of(hasConflict);
    }

    /**
     * Clears all entries. Useful for testing.
     */
    public void clear() {
        store.clear();
    }

    /**
     * Returns the current number of stored entries.
     */
    public int size() {
        return store.size();
    }

    private String compositeKey(String tenantId, String scope, String idempotencyKey, String principalId) {
        return tenantId + ":" + scope + ":" + idempotencyKey + ":" + principalId;
    }
}
