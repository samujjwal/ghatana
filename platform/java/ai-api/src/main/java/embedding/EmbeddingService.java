package com.ghatana.ai.embedding;

import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Service for generating and managing text embeddings.
 * 
 * @doc.type interface
 * @doc.purpose Defines the contract for embedding services that convert text to vector representations.
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public interface EmbeddingService {
    
    /**
     * Generates an embedding for the given text.
     *
     * @param text The input text to generate embedding for
     * @return A promise that completes with the embedding result
     */
    Promise<EmbeddingResult> createEmbedding(String text);
    
    /**
     * Generates embeddings for multiple texts in a batch.
     *
     * @param texts List of input texts to generate embeddings for
     * @return A promise that completes with a list of embedding results
     */
    Promise<List<EmbeddingResult>> createEmbeddings(List<String> texts);
    
    /**
     * Gets the configuration used by this embedding service.
     *
     * @return The LLM configuration
     */
    LLMConfiguration getConfig();
    
    /**
     * Gets the metrics collector for this service.
     *
     * @return The metrics collector instance
     */
    MetricsCollector getMetricsCollector();
    
    /**
     * Generates an embedding for the given text (convenience method).
     *
     * @param text The input text
     * @return A promise that completes with the embedding vector
     */
    default Promise<float[]> embed(String text) {
        return createEmbedding(text)
            .map(result -> result.embedding());
    }
}
