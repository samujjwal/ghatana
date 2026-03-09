package com.ghatana.platform.observability.trace;

import io.activej.promise.Promise;

import java.util.List;

/**
 * Interface for pluggable trace storage backends.
 * <p>
 * TraceStorage provides a vendor-neutral abstraction for storing and querying distributed
 * traces. Implementations can use different backends (ClickHouse, Elasticsearch, Cassandra,
 * in-memory) without affecting consumers.
 * </p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Async Operations</b>: All methods return ActiveJ Promise for non-blocking execution</li>
 *   <li><b>Batch Support</b>: storeSpans() for efficient bulk writes</li>
 *   <li><b>Flexible Queries</b>: Rich filtering via TraceQuery</li>
 *   <li><b>Aggregated Statistics</b>: Server-side aggregation for efficiency</li>
 *   <li><b>Health Checks</b>: isHealthy() for monitoring</li>
 *   <li><b>Resource Cleanup</b>: close() for graceful shutdown</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Store spans asynchronously
 * storage.storeSpan(span)
 *     .whenComplete(() -> System.out.println("Span stored"));
 *
 * // Batch storage (more efficient)
 * List<SpanData> batch = List.of(span1, span2, span3);
 * storage.storeSpans(batch)
 *     .whenComplete(() -> System.out.println("Batch stored"));
 *
 * // Query traces
 * TraceQuery query = TraceQuery.builder()
 *     .withServiceName("api-gateway")
 *     .withStatus("ERROR")
 *     .withMinDurationMs(100)
 *     .withLimit(50)
 *     .build();
 *
 * storage.queryTraces(query)
 *     .whenResult(traces -> {
 *         System.out.println("Found " + traces.size() + " traces");
 *         traces.forEach(trace -> 
 *             System.out.println("Trace: " + trace.traceId() + 
 *                              ", Duration: " + trace.durationMs() + "ms")
 *         );
 *     });
 *
 * // Get statistics (server-side aggregation)
 * storage.getStatistics(query)
 *     .whenResult(stats -> {
 *         System.out.println("Error rate: " + stats.errorRate() + "%");
 *         System.out.println("P99 latency: " + stats.p99DurationMs() + "ms");
 *     });
 *
 * // Health check
 * storage.isHealthy()
 *     .whenResult(healthy -> {
 *         if (!healthy) {
 *             System.err.println("Storage unhealthy!");
 *         }
 *     });
 * }</pre>
 * 
 * <h2>Implementation Guidelines</h2>
 * 
 * <h2>Non-Blocking Operations</h2>
 * All operations must be non-blocking and return Promises. Use Promise.ofBlocking()
 * for blocking I/O:
 * <pre>{@code
 * @Override
 * public Promise<Void> storeSpan(SpanData span) {
 *     return Promise.ofBlocking(executor, () -> {
 *         // Blocking database write
 *         jdbc.execute("INSERT INTO spans ...", span);
 *         return null;
 *     });
 * }
 * }</pre>
 * 
 * <h2>Error Handling</h2>
 * Use Promise.ofException() for errors, not throwing:
 * <pre>{@code
 * if (span == null) {
 *     return Promise.ofException(new IllegalArgumentException("span must not be null"));
 * }
 * }</pre>
 * 
 * <h2>Batching Efficiency</h2>
 * storeSpans() should be more efficient than multiple storeSpan() calls:
 * <ul>
 *   <li>Use batch inserts/bulk APIs</li>
 *   <li>Minimize round trips</li>
 *   <li>All-or-nothing semantics (transactional if possible)</li>
 * </ul>
 * 
 * <h2>Query Optimization</h2>
 * Optimize queries based on available filters:
 * <ul>
 *   <li>Use indexes for service, operation, status, time range</li>
 *   <li>Push-down filters to database</li>
 *   <li>Leverage columnar storage for analytics (ClickHouse)</li>
 *   <li>Use full-text search for tag queries (Elasticsearch)</li>
 * </ul>
 * 
 * <h2>Resource Cleanup</h2>
 * Properly close connections/resources in close():
 * <pre>{@code
 * @Override
 * public Promise<Void> close() {
 *     return Promise.ofBlocking(executor, () -> {
 *         connectionPool.close();
 *         executor.shutdown();
 *         return null;
 *     });
 * }
 * }</pre>
 * 
 * <h2>Performance Targets (Production Implementations)</h2>
 * <ul>
 *   <li><b>storeSpan()</b>: < 10ms p99 (async buffering)</li>
 *   <li><b>storeSpans(batch)</b>: < 50ms p99 for 100 spans</li>
 *   <li><b>queryTraces()</b>: < 500ms p99 (indexed queries)</li>
 *   <li><b>getStatistics()</b>: < 200ms p99 (pre-aggregated or indexed)</li>
 *   <li><b>isHealthy()</b>: < 100ms p99 (lightweight check)</li>
 * </ul>
 * 
 * <h2>Storage Backend Examples</h2>
 * <ul>
 *   <li><b>ClickHouse</b>: High-performance columnar storage, excellent for analytics</li>
 *   <li><b>Elasticsearch</b>: Full-text search, flexible queries, visualization</li>
 *   <li><b>Cassandra</b>: Distributed, high write throughput, eventual consistency</li>
 *   <li><b>PostgreSQL</b>: JSONB support, ACID, good for moderate scale</li>
 *   <li><b>In-Memory (Mock)</b>: Testing only, no persistence</li>
 * </ul>
 *
 * @author Ghatana Team
 * @version 1.0
 * @since 1.0
 * @stability Stable
 * @completeness 95
 * @testing Unit, Integration
 * @thread_safety Thread-safe (async, Promise-based)
 * @performance Backend-dependent (see targets above)
 * @since 1.0.0
 * @see SpanData
 * @see TraceInfo
 * @see TraceQuery
 * @see TraceStatistics
 * @see MockTraceStorage
 * @doc.type interface
 * @doc.purpose Vendor-neutral abstraction for distributed trace storage and querying
 * @doc.layer observability
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface TraceStorage {

    /**
     * Stores a single span asynchronously.
     * <p>
     * The returned Promise completes when the span has been persisted to storage.
     * For high-throughput scenarios, prefer {@link #storeSpans(List)} for batching.
     * </p>
     *
     * @param span the span to store (must not be null)
     * @return a Promise that completes when storage succeeds, or fails with exception
     * @throws IllegalArgumentException if span is null (Promise.ofException)
     */
    Promise<Void> storeSpan(SpanData span);

    /**
     * Stores multiple spans asynchronously in a batch.
     * <p>
     * This method should be more efficient than calling {@link #storeSpan(SpanData)}
     * multiple times, as implementations can batch the writes.
     * </p>
     * <p>
     * The returned Promise completes when ALL spans have been persisted. If any
     * span fails, the entire batch should fail (all-or-nothing semantics).
     * </p>
     *
     * @param spans the spans to store (must not be null or empty)
     * @return a Promise that completes when all spans are stored, or fails with exception
     * @throws IllegalArgumentException if spans is null or empty (Promise.ofException)
     */
    Promise<Void> storeSpans(List<SpanData> spans);

    /**
     * Queries traces matching the given criteria.
     * <p>
     * Returns a list of {@link TraceInfo} objects representing complete traces.
     * Each TraceInfo contains all spans for that trace.
     * </p>
     * <p>
     * The query respects limit and offset for pagination. Results should be
     * ordered by trace start time (most recent first) unless otherwise specified.
     * </p>
     *
     * @param query the query criteria (must not be null)
     * @return a Promise with list of matching traces (empty list if no matches)
     * @throws IllegalArgumentException if query is null (Promise.ofException)
     */
    Promise<List<TraceInfo>> queryTraces(TraceQuery query);

    /**
     * Gets aggregated statistics for traces matching the query.
     * <p>
     * This is more efficient than fetching all matching traces and calculating
     * statistics locally, as the storage backend can compute aggregates.
     * </p>
     * <p>
     * Statistics include:
     * <ul>
     *   <li>Total trace count</li>
     *   <li>Total span count</li>
     *   <li>Error count</li>
     *   <li>Duration statistics (min, max, avg)</li>
     *   <li>Most common services/operations</li>
     * </ul>
     * </p>
     *
     * @param query the query criteria (must not be null)
     * @return a Promise with aggregated statistics
     * @throws IllegalArgumentException if query is null (Promise.ofException)
     */
    Promise<TraceStatistics> getStatistics(TraceQuery query);

    /**
     * Checks if the storage backend is healthy and accessible.
     * <p>
     * This method should perform a lightweight check (e.g., ping database,
     * check connection pool) without expensive operations.
     * </p>
     *
     * @return a Promise with true if healthy, false otherwise
     */
    default Promise<Boolean> isHealthy() {
        // Default implementation: try a simple query
        return queryTraces(TraceQuery.builder().withLimit(1).build())
            .map(traces -> Boolean.TRUE)
            .whenException(exception -> {})
            .map(result -> result != null ? result : Boolean.FALSE);
    }

    /**
     * Closes the storage backend and releases resources.
     * <p>
     * After calling close(), no further operations should be performed.
     * Implementations should close connections, pools, and other resources.
     * </p>
     *
     * @return a Promise that completes when cleanup is done
     */
    default Promise<Void> close() {
        // Default no-op implementation
        return Promise.complete();
    }
}
