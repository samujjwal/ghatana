package com.ghatana.agent.memory.retrieval;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Detailed explanation of a retrieval operation for debugging/audit.
 *
 * @doc.type value-object
 * @doc.purpose Retrieval process explanation
 * @doc.layer agent-memory
 */
@Value
@Builder
public class RetrievalExplanation {

    @NotNull String retrievalStrategy;
    int candidatesScanned;
    @NotNull Map<String, Map<String, Double>> scores;
    @Builder.Default @NotNull List<String> filtersApplied = List.of();
    @Builder.Default @NotNull Map<String, String> rerankerInputs = Map.of();
}
