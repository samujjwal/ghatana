package com.ghatana.requirements.ai.profiling;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * JFR event for vector similarity search profiling.
 *
 * <p>
 * <b>Purpose</b><br>
 * Records performance metrics for vector similarity searches including query
 * vector dimensions, result count, similarity threshold, and search latency.
 * Critical for optimizing vector database queries and understanding RAG
 * performance.
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Part of AI Requirements vector search pipeline: - Created by: PgVectorStore
 * during similarity search operations - Consumed by: JFR profiling tools,
 * performance dashboards - Provides: Per-query latency, result counts,
 * similarity distributions - Enables: Query optimization, index tuning,
 * performance regression detection
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * VectorSearchEvent event = new VectorSearchEvent();
 * event.begin();
 * try {
 *     event.dimensions = queryVector.length;
 *     event.limit = maxResults;
 *     event.similarityThreshold = 0.75f;
 *
 *     List<VectorSearchResult> results = searchVectors(queryVector, maxResults);
 *
 *     event.resultCount = results.size();
 *     event.topSimilarity = results.isEmpty() ? 0 : results.get(0).similarityScore();
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
 * <b>Performance Analysis</b><br>
 * Use JFR data to identify slow queries:
 * <pre>{@code
 * // Find queries slower than 100ms
 * jfrEvents.stream()
 *     .filter(e -> e instanceof VectorSearchEvent)
 *     .filter(e -> e.getDuration().toMillis() > 100)
 *     .forEach(e -> {
 *         System.out.println("Slow query: " + e.dimensions + " dims, " +
 *                            e.resultCount + " results");
 *     });
 * }</pre>
 *
 * <p>
 * <b>Optimization Insights</b><br>
 * - High latency with few results → Index not effective - Many zero-result
 * searches → Similarity threshold too high - Consistent latency → Well-indexed,
 * predictable performance
 *
 * @doc.type class
 * @doc.purpose JFR event for vector similarity search profiling
 * @doc.layer product
 * @doc.pattern Profiling Event
 * @see Event
 * @see jdk.jfr
 * @since 1.0.0
 */
@Name("com.ghatana.requirements.VectorSearch")
@Label("Vector Similarity Search")
@Description("Vector similarity search with latency and result metrics")
@Category({"AI Requirements", "Vector Store"})
@StackTrace(false)
public class VectorSearchEvent extends Event {

    /**
     * Number of dimensions in query vector.
     */
    @Label("Dimensions")
    @Description("Query vector dimensions")
    public int dimensions;

    /**
     * Maximum number of results requested.
     */
    @Label("Limit")
    @Description("Maximum results requested")
    public int limit;

    /**
     * Minimum similarity threshold for results.
     */
    @Label("Similarity Threshold")
    @Description("Minimum similarity threshold")
    public float similarityThreshold;

    /**
     * Number of results actually returned.
     */
    @Label("Result Count")
    @Description("Number of results returned")
    public int resultCount;

    /**
     * Highest similarity score in results.
     */
    @Label("Top Similarity")
    @Description("Highest similarity score")
    public float topSimilarity;

    /**
     * Whether search succeeded.
     */
    @Label("Success")
    @Description("Whether search succeeded")
    public boolean success;

    /**
     * Error message if search failed (null if successful).
     */
    @Label("Error Message")
    @Description("Error message if failed")
    public String errorMessage;

    /**
     * Vector store implementation (e.g., "pgvector", "pinecone").
     */
    @Label("Store Type")
    @Description("Vector store implementation")
    public String storeType;
}
