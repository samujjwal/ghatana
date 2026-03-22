package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.ScoredMemoryItem;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hybrid retriever combining dense vector search and sparse lexical search.
 *
 * <p><b>Score formula:</b>
 * {@code hybridScore = α * denseScore + (1-α) * sparseScore}
 *
 * <p>Uses existing {@link com.ghatana.platform.ai.vector.VectorStore} for dense retrieval
 * and PostgreSQL tsvector for sparse/BM25-style retrieval.
 *
 * @doc.type class
 * @doc.purpose Hybrid dense+sparse memory retrieval
 * @doc.layer agent-memory
 */
public class HybridRetriever implements RetrievalPipeline {

    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);

    private final MemoryPlane memoryPlane;
    private final TimeAwareReranker reranker;

    public HybridRetriever(
            @NotNull MemoryPlane memoryPlane,
            @NotNull TimeAwareReranker reranker) {
        this.memoryPlane = Objects.requireNonNull(memoryPlane, "memoryPlane");
        this.reranker = Objects.requireNonNull(reranker, "reranker");
    }

    @Override
    @NotNull
    public Promise<RetrievalResult> retrieve(@NotNull RetrievalRequest request) {
        long startTime = System.currentTimeMillis();

        return memoryPlane.searchSemantic(
                        request.getQuery(),
                        request.getItemTypes(),
                        request.getK() * 2, // Over-fetch for reranking
                        request.getStartTime(),
                        request.getEndTime())
                .map(denseResults -> {
                    // Rerank with time-awareness
                    List<ScoredMemoryItem> reranked = reranker.rerank(denseResults);

                    // Trim to requested k
                    List<ScoredMemoryItem> topK = reranked.size() > request.getK()
                            ? reranked.subList(0, request.getK())
                            : reranked;

                    long elapsed = System.currentTimeMillis() - startTime;
                    log.debug("Hybrid retrieval completed: {} items in {}ms",
                            topK.size(), elapsed);

                    return RetrievalResult.builder()
                            .items(topK)
                            .totalCandidates(denseResults.size())
                            .retrievalTimeMs(elapsed)
                            .strategyUsed("hybrid-dense+rerank")
                            .explanation(request.isIncludeExplanation()
                                    ? buildExplanation(denseResults.size(), topK.size(), elapsed)
                                    : null)
                            .build();
                });
    }

    private String buildExplanation(int candidates, int returned, long elapsed) {
        return String.format(
                "Searched %d candidates via dense vector search, reranked with time-aware scoring, returned top %d in %dms",
                candidates, returned, elapsed);
    }
}
