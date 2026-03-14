package com.ghatana.ai.vectorstore;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Interface for vector store operations.
 * 
 * <p>Provides persistence and search capabilities for vector embeddings.</p>
 *
 * @doc.type interface
 * @doc.purpose Client for vector similarity search operations
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface VectorStoreClient {
    
    /**
     * Store a vector with associated content.
     * 
     * @param id The unique identifier
     * @param vector The embedding vector
     * @param content The original content
     * @return Promise that completes when stored
     */
    @NotNull
    Promise<Void> store(@NotNull String id, @NotNull float[] vector, @NotNull String content);
    
    /**
     * Upsert (insert or update) a vector with metadata.
     * 
     * @param id The unique identifier
     * @param vector The embedding vector
     * @param metadata Additional metadata
     * @return Promise that completes when upserted
     */
    @NotNull
    Promise<Void> upsert(@NotNull String id, @NotNull float[] vector, @NotNull Map<String, Object> metadata);
    
    /**
     * Search for similar vectors.
     * 
     * @param query The query vector
     * @param limit Maximum number of results
     * @return Promise with search results
     */
    @NotNull
    Promise<List<VectorStoreResult>> search(@NotNull float[] query, int limit);
    
    /**
     * Search for similar vectors with filters.
     * 
     * @param query The query vector
     * @param limit Maximum number of results
     * @param filters Metadata filters
     * @return Promise with search results
     */
    @NotNull
    Promise<List<VectorStoreResult>> search(@NotNull float[] query, int limit, @NotNull Map<String, Object> filters);
    
    /**
     * Delete a vector by ID.
     * 
     * @param id The vector ID
     * @return Promise that completes when deleted (true if deleted, false if not found)
     */
    @NotNull
    Promise<Boolean> delete(@NotNull String id);
    
    /**
     * Result from vector store search.
     */
    record VectorStoreResult(
        @NotNull String id,
        @NotNull String content,
        @NotNull float[] vector,
        double score
    ) {}
}
