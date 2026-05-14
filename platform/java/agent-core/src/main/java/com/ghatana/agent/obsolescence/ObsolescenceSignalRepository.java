/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Persistence contract for {@link ObsolescenceSignal} instances.
 *
 * <p>Implementations must enforce tenant isolation: all read operations must
 * filter by {@code tenantId} and must never return signals from other tenants.
 *
 * @doc.type interface
 * @doc.purpose Persistence contract for obsolescence signals
 * @doc.layer agent-core
 * @doc.pattern Repository
 */
public interface ObsolescenceSignalRepository {

    /**
     * Persists a new obsolescence signal.
     *
     * @param signal the signal to save
     * @return promise that completes when the signal is durably stored
     */
    @NotNull
    Promise<Void> save(@NotNull ObsolescenceSignal signal);

    /**
     * Finds signals by mastery item ID.
     *
     * @param tenantId      tenant to query
     * @param masteryItemId mastery item identifier
     * @return promise of matching signals (empty list if none found)
     */
    @NotNull
    Promise<List<ObsolescenceSignal>> findByMasteryItem(
            @NotNull String tenantId,
            @NotNull String masteryItemId);

    /**
     * Finds signals detected after a given timestamp.
     *
     * @param tenantId     tenant to query
     * @param detectedAfter timestamp threshold
     * @return promise of matching signals (empty list if none found)
     */
    @NotNull
    Promise<List<ObsolescenceSignal>> findByDetectedAfter(
            @NotNull String tenantId,
            @NotNull Instant detectedAfter);

    /**
     * Finds high-severity signals.
     *
     * @param tenantId tenant to query
     * @return promise of high-severity signals (empty list if none found)
     */
    @NotNull
    Promise<List<ObsolescenceSignal>> findHighSeverity(@NotNull String tenantId);

    /**
     * Deletes signals by mastery item ID.
     *
     * @param tenantId      tenant that owns the signals
     * @param masteryItemId mastery item identifier
     * @return promise that completes when signals are removed
     */
    @NotNull
    Promise<Void> deleteByMasteryItem(
            @NotNull String tenantId,
            @NotNull String masteryItemId);
}
