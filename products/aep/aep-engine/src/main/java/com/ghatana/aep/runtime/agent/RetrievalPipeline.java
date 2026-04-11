package com.ghatana.agent.memory.retrieval;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Pipeline for retrieving relevant memory items given a query.
 * Implementations may combine dense vector search, sparse lexical search,
 * time-aware reranking, and caching.
 *
 * @doc.type interface
 * @doc.purpose Memory retrieval pipeline SPI
 * @doc.layer agent-memory
 */
public interface RetrievalPipeline {

    /**
     * Retrieves memory items matching the request.
     *
     * @param request Retrieval parameters
     * @return Scored results with explanation
     */
    @NotNull Promise<RetrievalResult> retrieve(@NotNull RetrievalRequest request);
}
