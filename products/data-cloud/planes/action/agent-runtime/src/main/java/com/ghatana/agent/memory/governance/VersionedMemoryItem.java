/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.governance;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * A versioned memory item wrapper that tracks version history, provenance,
 * and modification metadata for governed memory storage.
 *
 * @doc.type record
 * @doc.purpose Versioned memory item with provenance tracking
 * @doc.layer platform
 * @doc.pattern ValueObject
 * @doc.gaa.memory semantic
 */
public record VersionedMemoryItem(
        /** Unique item identifier. */
        @NotNull String itemId,

        /** Memory namespace this item belongs to. */
        @NotNull String namespaceId,

        /** Version number (monotonically increasing). */
        long version,

        /** The content type (e.g., "fact", "episode", "policy", "preference"). */
        @NotNull String contentType,

        /** Serialized content payload. */
        @NotNull String content,

        /** Provenance information for this version. */
        @NotNull MemoryProvenance provenance,

        /** Whether this item has been marked as deleted (soft-delete). */
        boolean deleted,

        /** When this version was created. */
        @NotNull Instant createdAt,

        /** Hash of the previous version for chain verification. */
        @Nullable String previousVersionHash
) {
    public VersionedMemoryItem {
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(namespaceId);
        Objects.requireNonNull(contentType);
        Objects.requireNonNull(content);
        Objects.requireNonNull(provenance);
        Objects.requireNonNull(createdAt);
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
    }

    /** Returns {@code true} if this is the first version of the item. */
    public boolean isInitialVersion() {
        return version == 1;
    }
}
