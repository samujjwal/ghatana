/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * SPI for tenant-scoped context storage.
 *
 * <p>The context layer (P3.1) requires persistent storage for runtime metadata
 * in production environments. This SPI allows pluggable implementations.
 *
 * <p>Fixes DC-P1-01: Replaces in-memory {@code ConcurrentHashMap} with durable storage.
 *
 * @doc.type interface
 * @doc.purpose Storage abstraction for tenant-scoped runtime context
 * @doc.layer product
 * @doc.pattern SPI
 */
public interface ContextStore {

    /**
     * Get all context entries for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return promise of all entries (immutable map)
     */
    Promise<Map<String, Object>> getAllEntries(String tenantId);

    /**
     * Get a single context entry by key.
     *
     * @param tenantId the tenant identifier
     * @param key the entry key
     * @return promise of optional value
     */
    Promise<Optional<Object>> getEntry(String tenantId, String key);

    /**
     * Put or upsert context entries.
     *
     * @param tenantId the tenant identifier
     * @param entries the entries to put/upsert
     * @return promise of update result (new version number if available)
     */
    Promise<Long> putEntries(String tenantId, Map<String, Object> entries);

    /**
     * Delete a single context entry by key.
     *
     * @param tenantId the tenant identifier
     * @param key the entry key to delete
     * @return promise of deletion success
     */
    Promise<Boolean> deleteEntry(String tenantId, String key);

    /**
     * Delete all entries for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return promise of count of deleted entries
     */
    Promise<Integer> deleteAllEntries(String tenantId);

    /**
     * Get a versioned snapshot of all context entries.
     *
     * <p>Used for optimistic concurrency control on context updates.
     *
     * @param tenantId the tenant identifier
     * @return promise of snapshot with version number
     */
    Promise<ContextSnapshot> getSnapshot(String tenantId);

    /**
     * Represents a versioned snapshot of context entries.
     */
    record ContextSnapshot(
        String tenantId,
        Map<String, Object> entries,
        long version,
        Instant createdAt,
        Instant lastModifiedAt
    ) {}
}
