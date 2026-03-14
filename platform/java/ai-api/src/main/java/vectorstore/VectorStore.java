package com.ghatana.ai.vectorstore;

import io.activej.promise.Promise;

import java.util.List;

/**
 * Core abstraction for vector storage and similarity search operations.
 * 
 * @doc.type interface
 * @doc.purpose Defines the contract for vector storage backends that support similarity search operations.
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public interface VectorStore {
    
    /**
     * Stores a vector with associated metadata.
     *
     * @param id Unique identifier for the vector
     * @param content The original text content
     * @param vector The vector representation
     * @param metadata Optional metadata as a key-value map
     * @return A promise that completes when the vector is stored
     */
    Promise<Void> store(
            String id,
            String content,
            float[] vector,
            java.util.Map<String, String> metadata
    );
    
    /**
     * Performs a similarity search for the given query vector.
     *
     * @param queryVector The query vector
     * @param limit Maximum number of results to return
     * @param threshold Minimum similarity threshold (0.0 to 1.0)
     * @return A promise that completes with a list of search results
     */
    Promise<List<VectorSearchResult>> search(
            float[] queryVector,
            int limit,
            double threshold
    );
    
    /**
     * Performs a similarity search with metadata filtering.
     *
     * @param queryVector The query vector
     * @param limit Maximum number of results to return
     * @param threshold Minimum similarity threshold (0.0 to 1.0)
     * @param filterMetadata Metadata key-value pairs to filter by (exact match)
     * @return A promise that completes with a list of search results
     */
    Promise<List<VectorSearchResult>> search(
            float[] queryVector,
            int limit,
            double threshold,
            java.util.Map<String, String> filterMetadata
    );

    /**
     * Performs a similarity search using a query embedding.
     *
     * @param queryId The ID of the query vector to search from
     * @param limit Maximum number of results to return
     * @param threshold Minimum similarity threshold (0.0 to 1.0)
     * @return A promise that completes with a list of search results
     */
    Promise<List<VectorSearchResult>> searchById(
            String queryId,
            int limit,
            double threshold
    );
    
    /**
     * Retrieves a vector by its ID.
     *
     * @param id The vector ID
     * @return A promise that completes with the vector
     */
    Promise<VectorSearchResult> getById(String id);
    
    /**
     * Deletes a vector by its ID.
     *
     * @param id The vector ID
     * @return A promise that completes when the vector is deleted
     */
    Promise<Void> delete(String id);
    
    /**
     * Clears all vectors from the store.
     *
     * @return A promise that completes when all vectors are cleared
     */
    Promise<Void> clear();
    
    /**
     * Gets the total number of vectors in the store.
     *
     * @return A promise that completes with the count
     */
    Promise<Long> count();
    
    /**
     * Checks if a vector with the given ID exists.
     *
     * @param id The vector ID
     * @return A promise that completes with true if the vector exists, false otherwise
     */
    Promise<Boolean> exists(String id);
}
