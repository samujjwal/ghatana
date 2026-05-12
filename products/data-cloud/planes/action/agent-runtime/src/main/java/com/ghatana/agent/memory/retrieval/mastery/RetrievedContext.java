/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval.mastery;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Context retrieved from memory with mastery-aware filtering.
 *
 * <p>Contains procedures, semantic facts, negative knowledge, episodes,
 * and other memory items filtered by version compatibility and lifecycle state.
 *
 * @doc.type record
 * @doc.purpose Retrieved context with mastery-aware filtering
 * @doc.layer agent-runtime
 * @doc.pattern Record
 */
public record RetrievedContext(
        @NotNull List<MemoryItem> procedures,
        @NotNull List<MemoryItem> negativeKnowledge,
        @NotNull List<MemoryItem> knownFailureModes,
        @NotNull List<MemoryItem> semanticFacts,
        @NotNull List<MemoryItem> successfulEpisodes,
        @NotNull List<MemoryItem> failedEpisodes,
        @NotNull List<MemoryItem> activeTaskState,
        @NotNull Map<String, String> metadata
) {
    public RetrievedContext {
        Objects.requireNonNull(procedures, "procedures must not be null");
        Objects.requireNonNull(negativeKnowledge, "negativeKnowledge must not be null");
        Objects.requireNonNull(knownFailureModes, "knownFailureModes must not be null");
        Objects.requireNonNull(semanticFacts, "semanticFacts must not be null");
        Objects.requireNonNull(successfulEpisodes, "successfulEpisodes must not be null");
        Objects.requireNonNull(failedEpisodes, "failedEpisodes must not be null");
        Objects.requireNonNull(activeTaskState, "activeTaskState must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        procedures = List.copyOf(procedures);
        negativeKnowledge = List.copyOf(negativeKnowledge);
        knownFailureModes = List.copyOf(knownFailureModes);
        semanticFacts = List.copyOf(semanticFacts);
        successfulEpisodes = List.copyOf(successfulEpisodes);
        failedEpisodes = List.copyOf(failedEpisodes);
        activeTaskState = List.copyOf(activeTaskState);
        metadata = Map.copyOf(metadata);
    }

    /**
     * Returns an empty retrieved context.
     *
     * @return empty retrieved context
     */
    @NotNull
    public static RetrievedContext empty() {
        return new RetrievedContext(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()
        );
    }

    /**
     * A memory item retrieved from the memory plane.
     */
    public record MemoryItem(
            @NotNull String id,
            @NotNull String type,
            @NotNull String content,
            @NotNull Map<String, String> metadata,
            double confidence
    ) {
        public MemoryItem {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(type, "type must not be null");
            Objects.requireNonNull(content, "content must not be null");
            Objects.requireNonNull(metadata, "metadata must not be null");
            metadata = Map.copyOf(metadata);
        }
    }
}
