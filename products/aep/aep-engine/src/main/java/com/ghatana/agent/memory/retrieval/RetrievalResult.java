package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.memory.store.ScoredMemoryItem;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Result of a memory retrieval operation.
 *
 * @doc.type value-object
 * @doc.purpose Retrieval result container
 * @doc.layer agent-memory
 */
@Value
@Builder
public class RetrievalResult {

    @NotNull List<ScoredMemoryItem> items;
    int totalCandidates;
    long retrievalTimeMs;
    @NotNull String strategyUsed;
    @Nullable String explanation;
}
