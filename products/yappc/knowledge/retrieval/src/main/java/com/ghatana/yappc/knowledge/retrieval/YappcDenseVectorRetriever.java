/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Knowledge Module
 */
package com.ghatana.yappc.knowledge.retrieval;

import com.ghatana.agent.memory.retrieval.RetrievalPipeline;
import com.ghatana.agent.memory.retrieval.RetrievalRequest;
import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.vectorstore.VectorSearchResult;
import com.ghatana.ai.vectorstore.VectorStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Dense vector retriever backed by platform VectorStore.
 *
 * <p><b>Purpose:</b> Semantic search using dense embeddings and vector similarity.
 * Complements BM25Retriever for hybrid search in HybridRetriever.
 *
 * <p><b>Integration:</b> Reuses platform's EmbeddingService and VectorStore for
 * similarity search on embeddings (e.g., OpenAI, sentence-transformer).
 *
 * <p><b>Algorithm:</b>
 * <ol>
 *   <li>Generate embedding for query using EmbeddingService
 *   <li>Query VectorStore for k-nearest neighbors
 *   <li>Return scored results with vector similarity scores
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Dense vector semantic retriever backed by platform VectorStore
 * @doc.layer product
 * @doc.pattern Strategy
 *
 * @since 2.4.0
 */
public class YappcDenseVectorRetriever implements RetrievalPipeline {

    private static final Logger logger = LoggerFactory.getLogger(YappcDenseVectorRetriever.class);
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final Executor executor;

    /**
     * Creates a new dense vector retriever.
     *
     * @param embeddingService Platform embedding service for generating query embeddings
     * @param vectorStore      Platform vector store for similarity search
     * @param executor         Blocking executor for async operations
     */
    public YappcDenseVectorRetriever(
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            Executor executor) {
        this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService");
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public Promise<RetrievalResult> retrieve(RetrievalRequest request) {
        String query = request.getQuery();
        String tenantId = request.getTenantId();
        int limit = request.getK();

        logger.info("[DENSE] Dense vector retrieval: tenant={} query='{}' limit={}", 
                tenantId, query, limit);

        // Step 1: Generate embedding for query
        return embeddingService.embed(query)
                .then(queryEmbedding -> {
                    // Step 2: Build metadata filter for tenant isolation
                    Map<String, String> filterMetadata = new HashMap<>();
                    if (tenantId != null) {
                        filterMetadata.put("tenantId", tenantId);
                    }
                    
                    // Add additional filters from request
                    if (request.getFilters() != null) {
                        filterMetadata.putAll(request.getFilters());
                    }

                    // Step 3: Query vector store for similar vectors
                    return vectorStore.search(
                                    queryEmbedding,
                                    limit,
                                    DEFAULT_SIMILARITY_THRESHOLD,
                                    filterMetadata
                            )
                            .then(vectorResults -> {
                                // Step 4: Convert VectorSearchResult to ScoredMemoryItem
                                List<ScoredMemoryItem> items = vectorResults.stream()
                                        .map(result -> new ScoredMemoryItem(
                                                result.id(),
                                                result.content(),
                                                result.score(),
                                                extractMemoryType(result.metadata()),
                                                System.currentTimeMillis()
                                        ))
                                        .collect(Collectors.toList());

                                logger.info("[DENSE] Retrieved {} items from vector store", items.size());
                                return new RetrievalResult(items, "DENSE_VECTOR", System.currentTimeMillis());
                            });
                })
                .whenException(e -> {
                    logger.error("[DENSE] Vector search failed: {}", e.getMessage(), e);
                    return Promise.ofException(new RuntimeException("Dense vector retrieval failed", e));
                });
    }

    /**
     * Extracts memory type from metadata.
     *
     * @param metadata Metadata map
     * @return Memory type or "UNKNOWN" if not found
     */
    private String extractMemoryType(Map<String, String> metadata) {
        if (metadata == null) {
            return "UNKNOWN";
        }
        return metadata.getOrDefault("memoryType", "UNKNOWN");
    }

    /**
     * Scored memory item result from vector store.
     */
    public record ScoredMemoryItem(
            String id,
            String content,
            double vectorSimilarityScore,
            String memoryType,
            long retrievedAt
    ) {}

    /**
     * Retrieval result from vector search.
     */
    public record RetrievalResult(
            List<ScoredMemoryItem> items,
            String retrievalMethod,
            long timestamp
    ) {
        public int getCount() {
            return items.size();
        }
    }
}
