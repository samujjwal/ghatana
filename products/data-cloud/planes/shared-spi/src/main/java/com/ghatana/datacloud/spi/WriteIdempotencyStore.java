package com.ghatana.datacloud.spi;

import java.util.Map;
import java.util.Optional;

/**
 * Generic idempotency store for mutating HTTP requests.
 *
 * <p>Records the HTTP response body for mutating requests (POST, PUT, DELETE, PATCH) identified by
 * a caller-supplied idempotency key so that retried requests return the original outcome without
 * re-executing the write. Entries are scoped by tenant and operation scope to prevent cross-tenant
 * and cross-operation collisions.
 *
 * <p>Implementations must be thread-safe. The in-memory implementation is appropriate for
 * embedded and local profiles only; all non-embedded profiles must use a durable implementation
 * that survives process restarts.
 *
 * <h2>DC-BE-002: Idempotency Infrastructure</h2>
 * This generic store replaces the entity-specific EntityWriteIdempotencyStore to support
 * idempotency across all mutating routes:
 * - Entity CRUD (POST /api/v1/entities/:collection)
 * - Pipelines (POST /api/v1/pipelines)
 * - Events (POST /api/v1/events)
 * - Governance (POST /api/v1/governance/*)
 * - Analytics (POST /api/v1/analytics/*)
 *
 * @doc.type interface
 * @doc.purpose Generic idempotency deduplication for all mutating HTTP requests
 * @doc.layer spi
 * @doc.pattern Port
 */
public interface WriteIdempotencyStore {

    /**
     * Returns the cached response body for the given idempotency key, or empty if not found or
     * the entry has expired.
     *
     * @param tenantId        tenant owning the entry
     * @param operationScope  scope identifier for the operation (e.g., "entities:collection", "pipelines")
     * @param idempotencyKey  caller-supplied idempotency key
     * @return cached response body, or empty if absent/expired
     */
    Optional<Map<String, Object>> get(String tenantId, String operationScope, String idempotencyKey);

    /**
     * Records the response body for the given idempotency key. If an entry for this key already
     * exists it is silently overwritten (last-write-wins).
     *
     * @param tenantId        tenant owning the entry
     * @param operationScope  scope identifier for the operation (e.g., "entities:collection", "pipelines")
     * @param idempotencyKey  caller-supplied idempotency key
     * @param responseBody    response body to cache
     */
    void put(String tenantId, String operationScope, String idempotencyKey, Map<String, Object> responseBody);
}
