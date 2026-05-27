/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.lifecycle;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Optional;

/**
 * Stores primary phase action results for scoped idempotent replay.
 *
 * @doc.type interface
 * @doc.purpose Persists scoped phase action idempotency records for safe retries.
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface PhaseActionIdempotencyStore {

    /**
     * Finds a previously recorded action result by scoped key.
     *
     * @param scopedKey tenant/project/action-scoped idempotency key
     * @return matching record when present
     */
    Promise<Optional<PhaseActionIdempotencyRecord>> find(@NotNull String scopedKey);

    /**
     * Stores a completed action result for future replay.
     *
     * @param record completed idempotency record
     * @return promise completed after persistence
     */
    Promise<Void> save(@NotNull PhaseActionIdempotencyRecord record);

    /**
     * Immutable phase action idempotency record.
     *
     * @param scopedKey tenant/project/action-scoped idempotency key
     * @param requestFingerprint canonical request fingerprint bound to the key
     * @param result completed transition result to replay
     * @param createdAt record creation time
     */
    record PhaseActionIdempotencyRecord(
            String scopedKey,
            String requestFingerprint,
            TransitionResult result,
            Instant createdAt
    ) {
    }
}
