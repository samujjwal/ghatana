/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * EventCloud-backed implementation of {@link PatternStateStore}.
 *
 * <p>Provides durable pattern state persistence suitable for multi-node deployments
 * and process restarts. State is serialised via a provided {@link Function} and
 * stored in the EventCloud event log, ensuring tenant-isolated, append-only durability.
 *
 * <p><b>Dependencies</b><br>
 * Requires an EventCloud client (or a compatible EventStore) and a
 * {@link java.util.concurrent.Executor} for non-blocking IO offloading via
 * {@code Promise.ofBlocking}.
 *
 * <blockquote><b>NOTE:</b> This is a structural stub. Full EventCloud integration
 * requires the EventCloud client to be injected. The interface contract is complete
 * and all method signatures are stable; the wiring to a real EventCloud connection
 * is deferred to the infrastructure layer.</blockquote>
 *
 * @param <S> the serialisable state type
 *
 * @doc.type class
 * @doc.purpose EventCloud-backed PatternStateStore for durable multi-node pattern state
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class EventCloudPatternStateStore<S> implements PatternStateStore<S> {

    private static final Logger log = LoggerFactory.getLogger(EventCloudPatternStateStore.class);

    private final Function<S, byte[]> serializer;
    private final Function<byte[], S> deserializer;
    private final ExecutorService ioExecutor;

    /**
     * Creates an EventCloud-backed store with custom serialization.
     *
     * @param serializer   converts state to bytes for storage
     * @param deserializer converts bytes back to state
     */
    public EventCloudPatternStateStore(
            Function<S, byte[]> serializer,
            Function<byte[], S> deserializer) {
        this.serializer = serializer;
        this.deserializer = deserializer;
        this.ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public @NotNull Promise<Void> save(@NotNull String tenantId,
                                       @NotNull String patternId,
                                       @NotNull S state) {
        log.debug("Persisting pattern state to EventCloud: tenant={} pattern={}", tenantId, patternId);
        return Promise.ofBlocking(ioExecutor, () -> {
            // TODO: Wire to real EventCloud client — append a state-snapshot event
            // eventCloudClient.append(tenantId, buildStateEvent(patternId, serializer.apply(state)));
            return null;
        });
    }

    @Override
    public @NotNull Promise<Optional<S>> load(@NotNull String tenantId,
                                               @NotNull String patternId) {
        log.debug("Loading pattern state from EventCloud: tenant={} pattern={}", tenantId, patternId);
        return Promise.ofBlocking(ioExecutor, () -> {
            // TODO: Wire to real EventCloud client — query latest state-snapshot event
            // byte[] bytes = eventCloudClient.queryLatestStateSnapshot(tenantId, patternId);
            // return bytes != null ? Optional.of(deserializer.apply(bytes)) : Optional.empty();
            return Optional.<S>empty(); // stub — returns empty until wired
        });
    }

    @Override
    public @NotNull Promise<Void> delete(@NotNull String tenantId,
                                          @NotNull String patternId) {
        log.debug("Deleting pattern state from EventCloud: tenant={} pattern={}", tenantId, patternId);
        return Promise.ofBlocking(ioExecutor, () -> {
            // TODO: Wire to real EventCloud client — append a state-deleted tombstone event
            return null;
        });
    }

    @Override
    public @NotNull Promise<Boolean> exists(@NotNull String tenantId,
                                             @NotNull String patternId) {
        return Promise.ofBlocking(ioExecutor, () -> {
            // TODO: Wire to real EventCloud client
            return false; // stub — not found until wired
        });
    }
}
