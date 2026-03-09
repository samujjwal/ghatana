package com.ghatana.requirements.ai.profiling;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * JFR event for embedding generation profiling.
 *
 * <p>
 * <b>Purpose</b><br>
 * Records performance metrics for text embedding generation including model,
 * text length, embedding dimensions, and latency. Critical for understanding
 * vector storage costs and embedding pipeline performance.
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Part of AI Requirements embedding pipeline: - Created by:
 * OpenAIEmbeddingService during embedding generation - Consumed by: JFR
 * profiling tools, cost analysis dashboards - Provides: Per-embedding latency,
 * token consumption, batch efficiency - Enables: Performance optimization, cost
 * projection, bottleneck identification
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * EmbeddingGenerationEvent event = new EmbeddingGenerationEvent();
 * event.begin();
 * try {
 *     event.model = "text-embedding-ada-002";
 *     event.textLength = text.length();
 *     event.dimensions = 1536;
 *     event.batchSize = texts.size();
 *
 *     EmbeddingResult result = generateEmbedding(text);
 *
 *     event.tokenCount = result.tokenCount();
 *     event.success = true;
 * } catch (Exception e) {
 *     event.success = false;
 *     event.errorMessage = e.getMessage();
 * } finally {
 *     event.commit();
 * }
 * }</pre>
 *
 * <p>
 * <b>Cost Analysis</b><br>
 * Use JFR data to calculate embedding costs:
 * <pre>{@code
 * // Extract token usage from JFR recording
 * long totalTokens = jfrEvents.stream()
 *     .filter(e -> e instanceof EmbeddingGenerationEvent)
 *     .mapToLong(e -> ((EmbeddingGenerationEvent) e).tokenCount)
 *     .sum();
 *
 * // OpenAI pricing: $0.0001 per 1K tokens
 * double cost = (totalTokens / 1000.0) * 0.0001;
 * }</pre>
 *
 * <p>
 * <b>Performance Characteristics</b><br>
 * - Event overhead: <100ns per embedding - Negligible impact on embedding
 * latency - Safe for production batch processing - Captures both sync and async
 * embedding calls
 *
 * @doc.type class
 * @doc.purpose JFR event for embedding generation profiling
 * @doc.layer product
 * @doc.pattern Profiling Event
 * @see Event
 * @see jdk.jfr
 * @since 1.0.0
 */
@Name("com.ghatana.requirements.EmbeddingGeneration")
@Label("Embedding Generation")
@Description("Text embedding generation with token usage and latency")
@Category({"AI Requirements", "Embeddings"})
@StackTrace(false)
public class EmbeddingGenerationEvent extends Event {

    /**
     * Embedding model name (e.g., "text-embedding-ada-002").
     */
    @Label("Model")
    @Description("Embedding model name")
    public String model;

    /**
     * Length of input text in characters.
     */
    @Label("Text Length")
    @Description("Input text length in characters")
    public int textLength;

    /**
     * Number of dimensions in embedding vector.
     */
    @Label("Dimensions")
    @Description("Embedding vector dimensions")
    public int dimensions;

    /**
     * Number of tokens consumed for embedding.
     */
    @Label("Token Count")
    @Description("Tokens consumed")
    public int tokenCount;

    /**
     * Number of texts in batch (1 for single embedding).
     */
    @Label("Batch Size")
    @Description("Number of texts in batch")
    public int batchSize;

    /**
     * Whether embedding generation succeeded.
     */
    @Label("Success")
    @Description("Whether embedding generation succeeded")
    public boolean success;

    /**
     * Error message if generation failed (null if successful).
     */
    @Label("Error Message")
    @Description("Error message if failed")
    public String errorMessage;
}
