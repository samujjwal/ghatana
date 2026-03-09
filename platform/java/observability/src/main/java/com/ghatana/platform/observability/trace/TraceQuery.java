package com.ghatana.platform.observability.trace;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for building flexible trace queries with filtering criteria.
 * <p>
 * TraceQuery provides a fluent API for specifying search criteria when querying
 * distributed traces. All filter fields are optional, allowing queries ranging from
 * "get all traces" to highly specific searches combining multiple filters.
 * </p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Flexible Filtering</b>: Service, operation, status, duration, time range, tags, span count</li>
 *   <li><b>Pagination</b>: Limit and offset for result paging</li>
 *   <li><b>AND Logic</b>: All specified filters must match (combined with AND)</li>
 *   <li><b>Optional Filters</b>: Each filter is Optional, allowing incremental refinement</li>
 *   <li><b>Vendor-Neutral</b>: Can be implemented by any storage backend</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Simple service query
 * TraceQuery query1 = TraceQuery.forService("api-gateway");
 * 
 * // Error traces only
 * TraceQuery query2 = TraceQuery.errors();
 * 
 * // Slow traces (> 100ms)
 * TraceQuery query3 = TraceQuery.slow(100);
 * 
 * // Complex query with multiple filters
 * TraceQuery query4 = TraceQuery.builder()
 *     .withServiceName("api-gateway")
 *     .withOperationName("GET /api/users")
 *     .withMinDurationMs(100)
 *     .withMaxDurationMs(5000)
 *     .withStatus("ERROR")
 *     .withTag("http.status_code", "500")
 *     .withTag("environment", "production")
 *     .withStartTime(Instant.now().minus(Duration.ofHours(1)))
 *     .withEndTime(Instant.now())
 *     .withMinSpanCount(3)
 *     .withLimit(50)
 *     .withOffset(0)
 *     .build();
 *
 * // Use with TraceStorage
 * Promise<List<TraceInfo>> traces = storage.queryTraces(query4);
 * traces.whenResult(results -> {
 *     System.out.println("Found " + results.size() + " matching traces");
 * });
 * }</pre>
 * 
 * <h2>Filter Types</h2>
 * 
 * <h2>Attribute Filters</h2>
 * <ul>
 *   <li><b>serviceName</b>: Any span in trace must have this service name</li>
 *   <li><b>operationName</b>: Any span in trace must have this operation name</li>
 *   <li><b>status</b>: Overall trace status must match (OK, ERROR, UNSET)</li>
 * </ul>
 * 
 * <h2>Range Filters</h2>
 * <ul>
 *   <li><b>minDurationMs / maxDurationMs</b>: Trace duration must be within range</li>
 *   <li><b>startTime / endTime</b>: Trace start/end time must be within range</li>
 *   <li><b>minSpanCount / maxSpanCount</b>: Number of spans must be within range</li>
 * </ul>
 * 
 * <h2>Tag Filters</h2>
 * <ul>
 *   <li><b>tags</b>: Map of key-value pairs, ALL must match at least one span</li>
 *   <li>Example: {http.method=GET, http.status_code=500}</li>
 * </ul>
 * 
 * <h2>Pagination</h2>
 * <ul>
 *   <li><b>limit</b>: Maximum results to return (default: 100)</li>
 *   <li><b>offset</b>: Number of results to skip (default: 0)</li>
 * </ul>
 * 
 * <h2>Factory Methods</h2>
 * <ul>
 *   <li><b>all()</b>: Match all traces with default limits</li>
 *   <li><b>forService(name)</b>: Match traces containing service name</li>
 *   <li><b>errors()</b>: Match traces with ERROR status</li>
 *   <li><b>slow(minMs)</b>: Match traces above duration threshold</li>
 * </ul>
 * 
 * <h2>Implementation Notes</h2>
 * Storage backends should:
 * <ul>
 *   <li>Order results by trace start time (most recent first)</li>
 *   <li>Apply filters with AND logic (all must match)</li>
 *   <li>Optimize queries based on available filters (use indexes)</li>
 *   <li>Handle empty Optional fields as "no filter"</li>
 * </ul>
 *
 * @author Ghatana Team
 * @version 1.0
 * @since 1.0
 * @stability Stable
 * @completeness 95
 * @testing Unit
 * @thread_safety Immutable (thread-safe)
 * @performance Depends on storage backend implementation
 * @since 1.0.0
 * @see TraceQueryBuilder
 * @see TraceStorage
 * @doc.type interface
 * @doc.purpose Flexible distributed trace query specification with filtering, range, and pagination
 * @doc.layer observability
 * @doc.pattern Query Builder Pattern
 */
public interface TraceQuery {

    /**
     * Gets the service name filter.
     *
     * @return service name to filter by, or empty if not filtering by service
     */
    Optional<String> getServiceName();

    /**
     * Gets the operation name filter.
     *
     * @return operation name to filter by, or empty if not filtering by operation
     */
    Optional<String> getOperationName();

    /**
     * Gets the trace status filter.
     *
     * @return trace status ("OK", "ERROR", "UNSET"), or empty if not filtering by status
     */
    Optional<String> getStatus();

    /**
     * Gets the minimum duration filter in milliseconds.
     *
     * @return minimum duration, or empty if no minimum
     */
    Optional<Long> getMinDurationMs();

    /**
     * Gets the maximum duration filter in milliseconds.
     *
     * @return maximum duration, or empty if no maximum
     */
    Optional<Long> getMaxDurationMs();

    /**
     * Gets the start of the time range filter.
     *
     * @return start time, or empty if no start time filter
     */
    Optional<Instant> getStartTime();

    /**
     * Gets the end of the time range filter.
     *
     * @return end time, or empty if no end time filter
     */
    Optional<Instant> getEndTime();

    /**
     * Gets tag filters (key-value pairs that must all match).
     *
     * @return map of tags to filter by, or empty if not filtering by tags
     */
    Optional<Map<String, String>> getTags();

    /**
     * Gets the minimum span count filter.
     *
     * @return minimum number of spans, or empty if no minimum
     */
    Optional<Integer> getMinSpanCount();

    /**
     * Gets the maximum span count filter.
     *
     * @return maximum number of spans, or empty if no maximum
     */
    Optional<Integer> getMaxSpanCount();

    /**
     * Gets the maximum number of results to return.
     * <p>
     * Default: 100
     * </p>
     *
     * @return maximum results (always >= 1)
     */
    int getLimit();

    /**
     * Gets the offset for pagination.
     * <p>
     * Default: 0
     * </p>
     *
     * @return offset for pagination (always >= 0)
     */
    int getOffset();

    /**
     * Creates a new builder for constructing TraceQuery instances.
     *
     * @return a new TraceQueryBuilder
     */
    static TraceQueryBuilder builder() {
        return new TraceQueryBuilder();
    }

    /**
     * Creates a query that matches all traces with default limits.
     *
     * @return a query with no filters
     */
    static TraceQuery all() {
        return builder().build();
    }

    /**
     * Creates a query for a specific service.
     *
     * @param serviceName the service name to filter by
     * @return a query filtering by service name
     */
    static TraceQuery forService(String serviceName) {
        return builder().withServiceName(serviceName).build();
    }

    /**
     * Creates a query for error traces only.
     *
     * @return a query filtering by ERROR status
     */
    static TraceQuery errors() {
        return builder().withStatus("ERROR").build();
    }

    /**
     * Creates a query for slow traces (above threshold).
     *
     * @param minDurationMs minimum duration in milliseconds
     * @return a query filtering by minimum duration
     */
    static TraceQuery slow(long minDurationMs) {
        return builder().withMinDurationMs(minDurationMs).build();
    }
}
