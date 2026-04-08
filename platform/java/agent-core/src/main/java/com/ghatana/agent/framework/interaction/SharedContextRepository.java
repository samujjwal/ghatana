/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.interaction;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * SPI for reading and writing shared context entries across agents.
 *
 * <p>Implementations may be in-process for tests or backed by a durable store (e.g. Redis,
 * the platform database) for production multi-agent sessions.
 *
 * @doc.type interface
 * @doc.purpose Shared context store SPI for inter-agent state exchange
 * @doc.layer platform
 * @doc.pattern Repository
 */
public interface SharedContextRepository {

    /**
     * Writes or updates a shared context entry.
     *
     * @param entry the entry to persist
     * @return a {@link Promise} that completes when the write is durable
     */
    @NotNull
    Promise<Void> put(@NotNull SharedContext entry);

    /**
     * Reads a single entry by namespace, key, and tenant.
     *
     * @param namespace the namespace of the entry
     * @param key       the key within the namespace
     * @param tenantId  tenant boundary
     * @return the entry if present and not deleted, otherwise empty
     */
    @NotNull
    Promise<Optional<SharedContext>> get(@NotNull String namespace, @NotNull String key, @NotNull String tenantId);

    /**
     * Returns all live entries (excluding tombstones) for a given namespace and tenant.
     *
     * @param namespace the namespace to scan
     * @param tenantId  tenant boundary
     * @return all matching non-deleted entries
     */
    @NotNull
    Promise<List<SharedContext>> list(@NotNull String namespace, @NotNull String tenantId);

    /**
     * Deletes an entry by placing a tombstone.  Implementations may also physically remove it.
     *
     * @param namespace the namespace
     * @param key       the key
     * @param tenantId  tenant boundary
     * @return a {@link Promise} completing when the deletion is durable
     */
    @NotNull
    Promise<Void> delete(@NotNull String namespace, @NotNull String key, @NotNull String tenantId);
}
