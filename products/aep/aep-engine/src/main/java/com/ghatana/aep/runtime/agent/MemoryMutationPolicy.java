/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.governance;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Policy governing mutations to a governed memory namespace.
 *
 * <p>Defines which agents may mutate memory, what governance checks apply,
 * and what audit requirements exist for memory modifications.
 *
 * @doc.type record
 * @doc.purpose Policy for governing memory mutations
 * @doc.layer platform
 * @doc.pattern ValueObject
 * @doc.gaa.memory episodic|semantic|procedural|preference
 */
public record MemoryMutationPolicy(
        /** Namespace this policy governs. */
        @NotNull String namespaceId,

        /** Agents allowed to write (empty = owner only). */
        @NotNull List<String> authorizedWriters,

        /** Whether a provenance record is required on every mutation. */
        boolean requireProvenance,

        /** Whether version conflict checks are enforced on writes. */
        boolean requireVersionCheck,

        /** Whether all mutations must be logged to the evidence ledger. */
        boolean auditAllMutations,

        /** Data classification of content in this namespace. */
        @NotNull String dataClassification,

        /** Maximum number of items in this namespace (0 = unlimited). */
        int maxItems,

        /** Whether redaction rules apply to content before storage. */
        boolean applyRedaction
) {
    public MemoryMutationPolicy {
        Objects.requireNonNull(namespaceId);
        authorizedWriters = List.copyOf(authorizedWriters);
        Objects.requireNonNull(dataClassification);
    }

    /**
     * Returns {@code true} if the given agent is authorized to mutate this namespace.
     */
    public boolean isAuthorizedWriter(@NotNull String agentId) {
        return authorizedWriters.isEmpty() || authorizedWriters.contains(agentId);
    }
}
