/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Durable storage abstraction for pattern-matching state.
 *
 * <p>Pattern operators maintain partial-match state in memory during normal operation.
 * When a process restarts or scales out, that in-memory state is lost, causing
 * pattern detection gaps. This interface decouples the storage medium from the
 * pattern logic so that operators can either use the lightweight
 * {@link InMemoryPatternStateStore} (testing/single-node) or the durable
 * {@link EventCloudPatternStateStore} (production, backed by EventCloud).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PatternStateStore store = new InMemoryPatternStateStore();
 *
 * // Save state after each event
 * store.save("tenant-1", "threshold-pattern", state)
 *     .whenComplete(() -> log.debug("State persisted"));
 *
 * // Restore on operator restart
 * store.load("tenant-1", "threshold-pattern")
 *     .whenResult(opt -> opt.ifPresent(s -> restoreFrom(s)));
 * }</pre>
 *
 * @param <S> the serialisable state type maintained by the pattern operator
 *
 * @doc.type interface
 * @doc.purpose Durable persistence for pattern-operator partial-match state
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface PatternStateStore<S> {

    /**
     * Persist state for the given tenant and pattern.
     *
     * @param tenantId   tenant scope
     * @param patternId  unique pattern identifier within the tenant
     * @param state      the state to persist — must not be {@code null}
     * @return promise that completes when the state has been durably written
     */
    @NotNull
    Promise<Void> save(@NotNull String tenantId, @NotNull String patternId, @NotNull S state);

    /**
     * Load the last-persisted state for the given tenant and pattern.
     *
     * @param tenantId  tenant scope
     * @param patternId unique pattern identifier within the tenant
     * @return promise containing the persisted state, or {@link Optional#empty()} if none exists
     */
    @NotNull
    Promise<Optional<S>> load(@NotNull String tenantId, @NotNull String patternId);

    /**
     * Delete the state for the given tenant and pattern.
     *
     * <p>No-op if the state does not exist.
     *
     * @param tenantId  tenant scope
     * @param patternId unique pattern identifier within the tenant
     * @return promise that completes when deletion is acknowledged
     */
    @NotNull
    Promise<Void> delete(@NotNull String tenantId, @NotNull String patternId);

    /**
     * Check whether state exists for the given tenant and pattern.
     *
     * @param tenantId  tenant scope
     * @param patternId unique pattern identifier within the tenant
     * @return promise that resolves to {@code true} if state exists
     */
    @NotNull
    Promise<Boolean> exists(@NotNull String tenantId, @NotNull String patternId);
}
