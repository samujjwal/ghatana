package com.ghatana.appplatform.eventstore.idempotency;

import java.util.Optional;

/**
 * Port for idempotency key tracking. Guards against duplicate event processing
 * across retries and at-least-once delivery.
 *
 * @doc.type interface
 * @doc.purpose Idempotency key store port (STORY-K05-013)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface IdempotencyStore {

    /**
     * Attempt to claim an idempotency key for a given tenant.
     * Returns {@code true} if the key was newly claimed; {@code false} if already seen.
     *
     * @param tenantId       Tenant scope for isolation
     * @param idempotencyKey Unique client-supplied request key
     * @param responseHash   SHA-256 hash of the response payload for cache replay
     * @param ttlSeconds     Time-to-live for the entry
     */
    boolean claim(String tenantId, String idempotencyKey, String responseHash, int ttlSeconds);

    /**
     * Retrieve a previously stored response hash for cache replay.
     *
     * @return response hash or empty if not found / expired
     */
    Optional<String> getResponseHash(String tenantId, String idempotencyKey);
}
