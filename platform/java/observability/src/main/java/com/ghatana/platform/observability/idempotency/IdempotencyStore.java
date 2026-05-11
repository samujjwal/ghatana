package com.ghatana.platform.observability.idempotency;

import io.activej.promise.Promise;

/**
 * Contract for storing and retrieving idempotency keys for idempotent operations.
 *
 * <p>P0-07: Idempotency prevents duplicate work on retries for mutating operations.
 * The store tracks idempotency keys scoped by tenant, route action, resource ID,
 * and principal/client ID to ensure safe replay of operations.
 *
 * <p>Implementations should be durable in production profiles (e.g., database-backed)
 * to survive restarts. In-memory implementations are acceptable for local/test profiles.
 *
 * @doc.type interface
 * @doc.purpose Idempotency key storage contract for idempotent operations
 * @doc.layer platform
 * @doc.pattern Repository
 */
public interface IdempotencyStore {

    /**
     * Checks if an idempotency key has been used and returns the cached response if available.
     *
     * @param tenantId the tenant ID
     * @param scope the idempotency scope (route action + resource ID)
     * @param idempotencyKey the client-provided idempotency key
     * @param principalId the principal/client ID
     * @return a promise that completes with the cached entry, or null if not found
     */
    Promise<IdempotencyEntry> get(String tenantId, String scope, String idempotencyKey, String principalId);

    /**
     * Stores an idempotency entry for a successful operation.
     *
     * @param tenantId the tenant ID
     * @param scope the idempotency scope (route action + resource ID)
     * @param idempotencyKey the client-provided idempotency key
     * @param principalId the principal/client ID
     * @param payloadHash hash of the request payload for conflict detection
     * @param response the response to cache
     * @return a promise that completes when the entry is stored
     */
    Promise<Void> put(String tenantId, String scope, String idempotencyKey, String principalId,
                      String payloadHash, Object response);

    /**
     * Checks if an idempotency key exists with a different payload hash (conflict).
     *
     * @param tenantId the tenant ID
     * @param scope the idempotency scope (route action + resource ID)
     * @param idempotencyKey the client-provided idempotency key
     * @param principalId the principal/client ID
     * @param payloadHash hash of the current request payload
     * @return a promise that completes with true if there's a conflict, false otherwise
     */
    Promise<Boolean> hasConflict(String tenantId, String scope, String idempotencyKey, String principalId, String payloadHash);
}
