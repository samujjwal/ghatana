package com.ghatana.agent.memory.store;

import com.ghatana.agent.memory.model.MemoryItem;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * A memory item paired with a relevance score from retrieval.
 *
 * @doc.type value-object
 * @doc.purpose Scored retrieval result
 * @doc.layer agent-memory
 */
@Value
public class ScoredMemoryItem {

    /** The retrieved memory item. */
    @NotNull
    MemoryItem item;

    /** Relevance score in [0.0, 1.0]. */
    double score;

    /** Metadata explaining why this item was retrieved (factor → score). */
    @NotNull
    Map<String, String> retrievalMetadata;
}
