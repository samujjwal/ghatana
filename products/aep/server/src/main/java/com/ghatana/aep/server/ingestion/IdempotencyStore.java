/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.ingestion;

import io.activej.promise.Promise;

import java.time.Duration;

/**
 * T-09: Durable idempotency key store for the AEP event ingestion pipeline.
 *
 * <p>Replaces the in-memory {@code Set<String>} so that duplicate detection
 * survives process restarts in production.  Implementations must be:
 * <ul>
 *   <li>Tenant-scoped — keys are partitioned per tenant.</li>
 *   <li>TTL-aware — keys expire after the configured deduplication window so
 *       the store does not grow unboundedly.</li>
 *   <li>Idempotent — concurrent calls for the same key must safely resolve to
 *       "already seen" without double-processing.</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Durable idempotency key store for event deduplication
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface IdempotencyStore {

    /**
     * Checks whether the given key has already been processed, and atomically
     * records it if it has not.
     *
     * <p>Implementations must guarantee that if this method returns
     * {@code false} (i.e. "not a duplicate") the key is atomically inserted so
     * a concurrent call for the same key will observe it.
     *
     * @param tenantId       the tenant this key belongs to
     * @param idempotencyKey the caller-supplied deduplication key
     * @param ttl            how long to retain the key before expiry
     * @return a promise of {@code true} if the key was already present
     *         (duplicate), or {@code false} if it was freshly recorded
     */
    Promise<Boolean> isDuplicate(String tenantId, String idempotencyKey, Duration ttl);
}
