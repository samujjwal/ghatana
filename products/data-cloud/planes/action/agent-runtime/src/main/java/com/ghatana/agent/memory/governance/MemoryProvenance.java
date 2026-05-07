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
 * Provenance record for a memory item, tracking its origin, authorship,
 * and derivation chain.
 *
 * @doc.type record
 * @doc.purpose Tracks origin and derivation chain for memory items
 * @doc.layer platform
 * @doc.pattern ValueObject
 * @doc.gaa.memory semantic
 */
public record MemoryProvenance(
        /** Agent that created or modified the memory item. */
        @NotNull String agentId,

        /** Tenant context. */
        @NotNull String tenantId,

        /** Source of the knowledge (e.g., "user-input", "llm-inference", "observation", "import"). */
        @NotNull String source,

        /** Confidence in the correctness of this memory item [0.0, 1.0]. */
        double confidence,

        /** ID of the episode or interaction that produced this item, if applicable. */
        @Nullable String derivedFromEpisodeId,

        /** ID of the previous item this one supersedes, if applicable. */
        @Nullable String supersedes,

        /** When this provenance was recorded. */
        @NotNull Instant recordedAt
) {
    public MemoryProvenance {
        Objects.requireNonNull(agentId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(source);
        Objects.requireNonNull(recordedAt);
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0.0, 1.0]");
        }
    }
}
