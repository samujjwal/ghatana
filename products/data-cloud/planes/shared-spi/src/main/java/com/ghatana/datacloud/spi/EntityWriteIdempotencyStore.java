package com.ghatana.datacloud.spi;

import java.util.Map;
import java.util.Optional;

/**
 * Idempotency store for entity write operations.
 *
 * <p>Records the HTTP response body for entity write requests identified by a caller-supplied
 * idempotency key so that retried requests return the original outcome without re-executing
 * the write. Entries are scoped by tenant and collection and expire after the configured TTL.
 *
 * <p>Implementations must be thread-safe. The in-memory implementation is appropriate for
 * embedded and local profiles only; all non-embedded profiles must use a durable implementation
 * that survives process restarts.
 *
 * @doc.type interface
 * @doc.purpose Idempotency deduplication for entity write requests
 * @doc.layer spi
 * @doc.pattern Port
 */
public interface EntityWriteIdempotencyStore {

    /**
     * Returns the cached response body for the given idempotency key, or empty if not found or
     * the entry has expired.
     *
     * @param tenantId     tenant owning the entry
     * @param collection   collection name the entry belongs to
     * @param idempotencyKey caller-supplied idempotency key
     * @return cached response body, or empty if absent/expired
     */
    Optional<Map<String, Object>> get(String tenantId, String collection, String idempotencyKey);

    /**
     * Records the response body for the given idempotency key. If an entry for this key already
     * exists it is silently overwritten (last-write-wins).
     *
     * @param tenantId       tenant owning the entry
     * @param collection     collection name the entry belongs to
     * @param idempotencyKey caller-supplied idempotency key
     * @param responseBody   response body to cache
     */
    void put(String tenantId, String collection, String idempotencyKey, Map<String, Object> responseBody);
}
