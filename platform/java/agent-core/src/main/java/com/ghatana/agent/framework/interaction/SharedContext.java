/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.interaction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An entry in the shared context store, keyed by namespace and key within a tenant scope.
 *
 * <p>Entries can carry any serializable value via {@link Object}; callers are responsible for
 * type safety. A {@code null} {@link #value()} represents a deletion (tombstone).
 *
 * @param entryId    unique identifier for this entry
 * @param namespace  logical grouping for related entries (e.g. "conversation:thread-1")
 * @param key        key within the namespace
 * @param value      the value; {@code null} means the entry is deleted
 * @param tenantId   tenant boundary
 * @param scope      visibility scope for this entry
 * @param createdAt  timestamp when the entry was created
 *
 * @doc.type record
 * @doc.purpose Shared state entry exchanged between agents in the same context boundary
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record SharedContext(
        @NotNull String entryId,
        @NotNull String namespace,
        @NotNull String key,
        @Nullable Object value,
        @NotNull String tenantId,
        @NotNull ContextSharingScope scope,
        @NotNull Instant createdAt) {

    public SharedContext {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Creates a new entry with a generated ID and current timestamp. */
    @NotNull
    public static SharedContext create(
            @NotNull String namespace,
            @NotNull String key,
            @Nullable Object value,
            @NotNull String tenantId,
            @NotNull ContextSharingScope scope) {
        return new SharedContext(UUID.randomUUID().toString(), namespace, key, value, tenantId, scope, Instant.now());
    }

    /** Returns {@code true} if this entry is a deletion tombstone. */
    public boolean isDeleted() {
        return value == null;
    }
}
