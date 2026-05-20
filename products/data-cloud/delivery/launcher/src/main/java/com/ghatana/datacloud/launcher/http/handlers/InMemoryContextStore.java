/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of {@link ContextStore}.
 *
 * <p>DC-P1-01: For LOCAL and TEST profiles only.
 * Production profiles MUST use a durable store such as {@code JdbcContextStore}.
 *
 * @doc.type class
 * @doc.purpose In-memory context storage (local/test only)
 * @doc.layer product
 * @doc.pattern Implementation
 */
public final class InMemoryContextStore implements ContextStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryContextStore.class);

    // Tenant-scoped context entries
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> tenantContexts =
            new ConcurrentHashMap<>();

    // Version counter per tenant
    private final ConcurrentHashMap<String, AtomicLong> tenantVersions =
            new ConcurrentHashMap<>();

    // Track when context was first created for a tenant
    private final ConcurrentHashMap<String, Instant> tenantCreatedAt =
            new ConcurrentHashMap<>();

    // Track last modification timestamp per tenant
    private final ConcurrentHashMap<String, Instant> tenantLastModifiedAt =
            new ConcurrentHashMap<>();

    @Override
    public Promise<Map<String, Object>> getAllEntries(String tenantId) {
        log.debug("[InMemoryContextStore] Getting all entries for tenant={}", tenantId);
        Map<String, Object> entries = tenantContexts
                .getOrDefault(tenantId, new ConcurrentHashMap<>());
        return Promise.of(Collections.unmodifiableMap(new HashMap<>(entries)));
    }

    @Override
    public Promise<Optional<Object>> getEntry(String tenantId, String key) {
        log.debug("[InMemoryContextStore] Getting entry tenant={}, key={}", tenantId, key);
        Map<String, Object> entries = tenantContexts.getOrDefault(tenantId, new ConcurrentHashMap<>());
        return Promise.of(Optional.ofNullable(entries.get(key)));
    }

    @Override
    public Promise<Long> putEntries(String tenantId, Map<String, Object> entries) {
        log.debug("[InMemoryContextStore] Putting entries tenant={}, count={}", tenantId, entries.size());

        ConcurrentHashMap<String, Object> tenantData = tenantContexts
                .computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());

        // Update entries
        tenantData.putAll(entries);

        // Increment version
        AtomicLong version = tenantVersions.computeIfAbsent(tenantId, k -> new AtomicLong(0));
        long newVersion = version.incrementAndGet();

        // Update timestamps
        Instant now = Instant.now();
        tenantCreatedAt.computeIfAbsent(tenantId, k -> now);
        tenantLastModifiedAt.put(tenantId, now);

        log.debug("[InMemoryContextStore] Entries updated for tenant={}, newVersion={}", tenantId, newVersion);
        return Promise.of(newVersion);
    }

    @Override
    public Promise<Boolean> deleteEntry(String tenantId, String key) {
        log.debug("[InMemoryContextStore] Deleting entry tenant={}, key={}", tenantId, key);

        Map<String, Object> entries = tenantContexts.getOrDefault(tenantId, new ConcurrentHashMap<>());
        Object removed = entries.remove(key);

        if (removed != null) {
            // Update version and timestamp on deletion
            AtomicLong version = tenantVersions.computeIfAbsent(tenantId, k -> new AtomicLong(0));
            version.incrementAndGet();
            tenantLastModifiedAt.put(tenantId, Instant.now());
            log.debug("[InMemoryContextStore] Entry deleted for tenant={}, key={}", tenantId, key);
            return Promise.of(true);
        }

        log.debug("[InMemoryContextStore] Entry not found for deletion tenant={}, key={}", tenantId, key);
        return Promise.of(false);
    }

    @Override
    public Promise<Integer> deleteAllEntries(String tenantId) {
        log.debug("[InMemoryContextStore] Deleting all entries for tenant={}", tenantId);

        Map<String, Object> entries = tenantContexts.getOrDefault(tenantId, new ConcurrentHashMap<>());
        int count = entries.size();
        entries.clear();

        // Reset version and timestamps
        tenantVersions.remove(tenantId);
        tenantCreatedAt.remove(tenantId);
        tenantLastModifiedAt.remove(tenantId);
        tenantContexts.remove(tenantId);

        log.debug("[InMemoryContextStore] All entries deleted for tenant={}, count={}", tenantId, count);
        return Promise.of(count);
    }

    @Override
    public Promise<ContextSnapshot> getSnapshot(String tenantId) {
        log.debug("[InMemoryContextStore] Getting snapshot for tenant={}", tenantId);

        Map<String, Object> entries = tenantContexts
                .getOrDefault(tenantId, new ConcurrentHashMap<>());
        long version = tenantVersions
                .getOrDefault(tenantId, new AtomicLong(0))
                .get();
        Instant createdAt = tenantCreatedAt.getOrDefault(tenantId, Instant.now());
        Instant lastModifiedAt = tenantLastModifiedAt.getOrDefault(tenantId, createdAt);

        ContextStore.ContextSnapshot snapshot = new ContextStore.ContextSnapshot(
                tenantId,
                Collections.unmodifiableMap(new HashMap<>(entries)),
                version,
                createdAt,
                lastModifiedAt);

        return Promise.of(snapshot);
    }
}
