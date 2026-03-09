package com.ghatana.platform.observability.trace;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builder for creating {@link TraceQuery} instances using a fluent API.
 * <p>
 * This builder provides a convenient way to construct immutable TraceQuery objects
 * with optional filtering criteria. All filters are optional and combine with AND
 * logic (all specified filters must match).
 * </p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Fluent API</b>: Chain method calls for readable query construction</li>
 *   <li><b>Optional Filters</b>: All filters optional, defaults to "match all"</li>
 *   <li><b>Incremental Tags</b>: Add tags one at a time or in bulk</li>
 *   <li><b>Smart Defaults</b>: limit=100, offset=0</li>
 *   <li><b>Validation</b>: Ensures limit >= 1, offset >= 0</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Simple query with one filter
 * TraceQuery query1 = TraceQuery.builder()
 *     .withServiceName("api-gateway")
 *     .build();
 *
 * // Complex query with multiple filters
 * TraceQuery query2 = TraceQuery.builder()
 *     .withServiceName("api-gateway")
 *     .withOperationName("GET /api/users")
 *     .withMinDurationMs(100)  // Slow traces (>100ms)
 *     .withMaxDurationMs(5000)  // Not too slow (<5s)
 *     .withStatus("ERROR")  // Errors only
 *     .withTag("http.method", "GET")
 *     .withTag("http.status_code", "500")
 *     .withStartTime(Instant.now().minus(Duration.ofHours(1)))
 *     .withEndTime(Instant.now())
 *     .withMinSpanCount(3)  // At least 3 spans
 *     .withLimit(50)
 *     .withOffset(0)
 *     .build();
 *
 * // Time range query (last hour)
 * TraceQuery query3 = TraceQuery.builder()
 *     .withStartTime(Instant.now().minus(Duration.ofHours(1)))
 *     .withEndTime(Instant.now())
 *     .build();
 *
 * // Pagination example (page 3, 20 per page)
 * TraceQuery query4 = TraceQuery.builder()
 *     .withServiceName("user-service")
 *     .withLimit(20)
 *     .withOffset(40)  // Skip first 2 pages (0-19, 20-39)
 *     .build();
 * }</pre>
 * 
 * <h2>Default Values</h2>
 * <ul>
 *   <li><b>limit</b>: 100 (maximum results per query)</li>
 *   <li><b>offset</b>: 0 (start from first result)</li>
 *   <li><b>All other filters</b>: null (no filtering)</li>
 * </ul>
 * 
 * <h2>Filter Semantics</h2>
 * <ul>
 *   <li><b>serviceName</b>: ANY span must have this service (OR across spans)</li>
 *   <li><b>operationName</b>: ANY span must have this operation (OR across spans)</li>
 *   <li><b>tags</b>: ALL tags must match at least one span (AND across tags)</li>
 *   <li><b>duration/time/spanCount</b>: Trace-level filters (exact match)</li>
 * </ul>
 * 
 * <h2>Pagination</h2>
 * <pre>{@code
 * // Page 1 (first 100 results)
 * TraceQuery page1 = TraceQuery.builder().withLimit(100).withOffset(0).build();
 * 
 * // Page 2 (next 100 results)
 * TraceQuery page2 = TraceQuery.builder().withLimit(100).withOffset(100).build();
 * 
 * // Page 3 (next 100 results)
 * TraceQuery page3 = TraceQuery.builder().withLimit(100).withOffset(200).build();
 * }</pre>
 * 
 * <h2>Validation</h2>
 * Build-time validation ensures:
 * <ul>
 *   <li>limit >= 1 (must request at least one result)</li>
 *   <li>offset >= 0 (cannot skip negative results)</li>
 * </ul>
 *
 * @author Ghatana Team
 * @version 1.0
 * @since 1.0
 * @stability Stable
 * @completeness 95
 * @testing Unit
 * @thread_safety Not thread-safe (single-threaded builder)
 * @performance O(1) build, O(n) for n tags
 * @since 1.0.0
 * @see TraceQuery
 * @doc.type class
 * @doc.purpose Fluent builder for constructing immutable TraceQuery objects with optional filtering
 * @doc.layer observability
 * @doc.pattern Builder, Fluent API
 */
public class TraceQueryBuilder {

    private static final int DEFAULT_LIMIT = 100;
    private static final int DEFAULT_OFFSET = 0;

    private String serviceName;
    private String operationName;
    private String status;
    private Long minDurationMs;
    private Long maxDurationMs;
    private Instant startTime;
    private Instant endTime;
    private final Map<String, String> tags = new HashMap<>();
    private Integer minSpanCount;
    private Integer maxSpanCount;
    private int limit = DEFAULT_LIMIT;
    private int offset = DEFAULT_OFFSET;

    /**
     * Sets the service name filter.
     *
     * @param serviceName service name to filter by
     * @return this builder for chaining
     */
    public TraceQueryBuilder withServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    /**
     * Sets the operation name filter.
     *
     * @param operationName operation name to filter by
     * @return this builder for chaining
     */
    public TraceQueryBuilder withOperationName(String operationName) {
        this.operationName = operationName;
        return this;
    }

    /**
     * Sets the status filter.
     *
     * @param status trace status: "OK", "ERROR", or "UNSET"
     * @return this builder for chaining
     */
    public TraceQueryBuilder withStatus(String status) {
        this.status = status;
        return this;
    }

    /**
     * Sets the minimum duration filter.
     *
     * @param minDurationMs minimum duration in milliseconds
     * @return this builder for chaining
     */
    public TraceQueryBuilder withMinDurationMs(long minDurationMs) {
        this.minDurationMs = minDurationMs;
        return this;
    }

    /**
     * Sets the maximum duration filter.
     *
     * @param maxDurationMs maximum duration in milliseconds
     * @return this builder for chaining
     */
    public TraceQueryBuilder withMaxDurationMs(long maxDurationMs) {
        this.maxDurationMs = maxDurationMs;
        return this;
    }

    /**
     * Sets the start time filter.
     *
     * @param startTime start of time range
     * @return this builder for chaining
     */
    public TraceQueryBuilder withStartTime(Instant startTime) {
        this.startTime = startTime;
        return this;
    }

    /**
     * Sets the end time filter.
     *
     * @param endTime end of time range
     * @return this builder for chaining
     */
    public TraceQueryBuilder withEndTime(Instant endTime) {
        this.endTime = endTime;
        return this;
    }

    /**
     * Adds a single tag filter.
     *
     * @param key   tag key
     * @param value tag value
     * @return this builder for chaining
     */
    public TraceQueryBuilder withTag(String key, String value) {
        this.tags.put(key, value);
        return this;
    }

    /**
     * Adds multiple tag filters at once.
     *
     * @param tags map of tags to filter by
     * @return this builder for chaining
     */
    public TraceQueryBuilder withTags(Map<String, String> tags) {
        this.tags.putAll(tags);
        return this;
    }

    /**
     * Sets the minimum span count filter.
     *
     * @param minSpanCount minimum number of spans
     * @return this builder for chaining
     */
    public TraceQueryBuilder withMinSpanCount(int minSpanCount) {
        this.minSpanCount = minSpanCount;
        return this;
    }

    /**
     * Sets the maximum span count filter.
     *
     * @param maxSpanCount maximum number of spans
     * @return this builder for chaining
     */
    public TraceQueryBuilder withMaxSpanCount(int maxSpanCount) {
        this.maxSpanCount = maxSpanCount;
        return this;
    }

    /**
     * Sets the maximum number of results to return.
     * <p>
     * Default: 100
     * </p>
     *
     * @param limit maximum results (must be >= 1)
     * @return this builder for chaining
     * @throws IllegalArgumentException if limit < 1
     */
    public TraceQueryBuilder withLimit(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1: " + limit);
        }
        this.limit = limit;
        return this;
    }

    /**
     * Sets the offset for pagination.
     * <p>
     * Default: 0
     * </p>
     *
     * @param offset offset for pagination (must be >= 0)
     * @return this builder for chaining
     * @throws IllegalArgumentException if offset < 0
     */
    public TraceQueryBuilder withOffset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0: " + offset);
        }
        this.offset = offset;
        return this;
    }

    /**
     * Builds the TraceQuery instance.
     * <p>
     * All filters are optional. The resulting query will match traces
     * that satisfy ALL specified filters (AND logic).
     * </p>
     *
     * @return a new immutable TraceQuery instance
     */
    public TraceQuery build() {
        return new TraceQueryImpl(
            serviceName,
            operationName,
            status,
            minDurationMs,
            maxDurationMs,
            startTime,
            endTime,
            tags.isEmpty() ? null : Map.copyOf(tags),
            minSpanCount,
            maxSpanCount,
            limit,
            offset
        );
    }

    /**
     * Immutable implementation of TraceQuery.
     */
    private record TraceQueryImpl(
        String serviceName,
        String operationName,
        String status,
        Long minDurationMs,
        Long maxDurationMs,
        Instant startTime,
        Instant endTime,
        Map<String, String> tags,
        Integer minSpanCount,
        Integer maxSpanCount,
        int limit,
        int offset
    ) implements TraceQuery {

        @Override
        public Optional<String> getServiceName() {
            return Optional.ofNullable(serviceName);
        }

        @Override
        public Optional<String> getOperationName() {
            return Optional.ofNullable(operationName);
        }

        @Override
        public Optional<String> getStatus() {
            return Optional.ofNullable(status);
        }

        @Override
        public Optional<Long> getMinDurationMs() {
            return Optional.ofNullable(minDurationMs);
        }

        @Override
        public Optional<Long> getMaxDurationMs() {
            return Optional.ofNullable(maxDurationMs);
        }

        @Override
        public Optional<Instant> getStartTime() {
            return Optional.ofNullable(startTime);
        }

        @Override
        public Optional<Instant> getEndTime() {
            return Optional.ofNullable(endTime);
        }

        @Override
        public Optional<Map<String, String>> getTags() {
            return Optional.ofNullable(tags);
        }

        @Override
        public Optional<Integer> getMinSpanCount() {
            return Optional.ofNullable(minSpanCount);
        }

        @Override
        public Optional<Integer> getMaxSpanCount() {
            return Optional.ofNullable(maxSpanCount);
        }

        @Override
        public int getLimit() {
            return limit;
        }

        @Override
        public int getOffset() {
            return offset;
        }
    }
}
