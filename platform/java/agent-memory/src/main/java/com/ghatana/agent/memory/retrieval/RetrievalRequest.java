package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.memory.model.MemoryItemType;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Request parameters for memory retrieval.
 *
 * @doc.type value-object
 * @doc.purpose Retrieval request specification
 * @doc.layer agent-memory
 */
@Value
@Builder
public class RetrievalRequest {

    /** Natural language query. */
    @NotNull String query;

    /** Filter by memory item types (null = all). */
    @Nullable List<MemoryItemType> itemTypes;

    /** Maximum results to return. */
    @Builder.Default int k = 10;

    /** Optional time range start. */
    @Nullable Instant startTime;

    /** Optional time range end. */
    @Nullable Instant endTime;

    /** Additional filters. */
    @Builder.Default @NotNull Map<String, String> filters = Map.of();

    /** Reranker configuration: hybrid weight alpha (0.0 = pure sparse, 1.0 = pure dense). */
    @Builder.Default double hybridAlpha = 0.7;

    /** Whether to include similarity scores. */
    @Builder.Default boolean includeSimilarityScores = true;

    /** Whether to include retrieval explanation. */
    @Builder.Default boolean includeExplanation = false;

    /** Tenant ID for isolation. */
    @Nullable String tenantId;

    /** Agent ID for scoping. */
    @Nullable String agentId;
}
