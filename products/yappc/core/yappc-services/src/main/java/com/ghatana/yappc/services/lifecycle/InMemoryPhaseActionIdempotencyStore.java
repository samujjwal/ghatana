/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.lifecycle;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory phase action idempotency store for local/dev service composition.
 *
 * @doc.type class
 * @doc.purpose Provides a scoped phase action idempotency store for retry-safe local execution.
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class InMemoryPhaseActionIdempotencyStore implements PhaseActionIdempotencyStore {

    private final ConcurrentMap<String, PhaseActionIdempotencyRecord> records = new ConcurrentHashMap<>();

    @Override
    public Promise<Optional<PhaseActionIdempotencyRecord>> find(@NotNull String scopedKey) {
        return Promise.of(Optional.ofNullable(records.get(scopedKey)));
    }

    @Override
    public Promise<Void> save(@NotNull PhaseActionIdempotencyRecord record) {
        records.putIfAbsent(record.scopedKey(), record);
        return Promise.complete();
    }
}
