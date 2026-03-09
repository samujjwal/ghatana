package com.ghatana.yappc.ai.vector;

import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.vectorstore.VectorStoreClient;
import com.ghatana.yappc.ai.vector.SearchResult;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Vector search service using shared AI libraries.
 * 
 * <p>Replaces custom SemanticSearchService with implementation
 * backed by libs/ai-integration vector store.
 * 
 * @doc.type class
 * @doc.purpose Vector search using shared AI libs
 * @doc.layer product
 * @doc.pattern Service
 */
public class VectorSearchService {
    
    private static final Logger LOG = LoggerFactory.getLogger(VectorSearchService.class);
    
    private final VectorStoreClient vectorStore;
    private final EmbeddingService embeddingService;
    
    public VectorSearchService(
        @NotNull VectorStoreClient vectorStore,
        @NotNull EmbeddingService embeddingService
    ) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        LOG.info("Initialized VectorSearchService");
    }
    
    /**
     * Searches for similar documents using vector similarity.
     */
    @NotNull
    public Promise<List<SearchResult>> search(
        @NotNull String query,
        int topK,
        @NotNull Map<String, Object> filters
    ) {
        LOG.debug("Vector search for query: {}", query);
        
        return embeddingService.embed(query)
            .then(embedding -> vectorStore.search(embedding, topK, filters))
            .map(results -> results.stream()
                .map(r -> new SearchResult(r.id(), r.content(), r.score(), new Vector(r.vector())))
                .toList());
    }
    
    /**
     * Adds a document to the vector store.
     */
    @NotNull
    public Promise<String> addDocument(
        @NotNull String id,
        @NotNull String content,
        @NotNull Map<String, Object> metadata
    ) {
        LOG.debug("Adding document to vector store: {}", id);
        
        return embeddingService.embed(content)
            .then(embedding -> vectorStore.upsert(id, embedding, metadata))
            .map(v -> id);
    }
    
    /**
     * Deletes a document from the vector store.
     */
    @NotNull
    public Promise<Boolean> deleteDocument(@NotNull String id) {
        LOG.debug("Deleting document from vector store: {}", id);
        return vectorStore.delete(id);
    }
    
    /**
     * Updates a document in the vector store.
     */
    @NotNull
    public Promise<String> updateDocument(
        @NotNull String id,
        @NotNull String content,
        @NotNull Map<String, Object> metadata
    ) {
        LOG.debug("Updating document in vector store: {}", id);
        
        return embeddingService.embed(content)
            .then(embedding -> vectorStore.upsert(id, embedding, metadata))
            .map(v -> id);
    }
}
