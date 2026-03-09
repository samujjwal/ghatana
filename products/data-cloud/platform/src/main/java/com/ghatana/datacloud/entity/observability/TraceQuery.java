package com.ghatana.datacloud.entity.observability;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;

/**
 * Port interface for querying traces with complex filtering and pattern matching.
 *
 * <p><b>Purpose</b><br>
 * Defines contract for trace selection and filtering across stored spans using
 * attribute-based DSL, time range constraints, and span count criteria.
 *
 * <p><b>Query Types</b><br>
 * - Time range: spans within [startTime, endTime]
 * - Attribute matching: attribute name/value predicates (exact, contains, regex)
 * - Span count: traces with span count in range [min, max]
 * - Error filtering: traces containing any ERROR spans
 * - Duration filtering: traces with duration in [minDurationMs, maxDurationMs]
 * - Combined: boolean AND/OR of multiple criteria
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * TraceQuery query = TraceQueryBuilder.create()
 *     .withOperationName("user.login")
 *     .withErrorsOnly(true)
 *     .withDurationRange(100, 5000)
 *     .build();
 *
 * Promise<QueryResult> result = executor.execute(tenantId, query);
 * result.whenResult(queryResult -> {
 *     List<String> traceIds = queryResult.getMatchingTraceIds();
 *     long totalMatches = queryResult.getTotalMatchCount();
 * });
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Implementations should be thread-safe for concurrent query execution.
 *
 * <p><b>Performance Characteristics</b><br>
 * - Time-based queries: O(log n) with indexing
 * - Attribute queries: O(n) without indexing
 * - Combined queries: O(m * n) where m = criteria count, n = span count
 *
 * @see com.ghatana.datacloud.entity.observability.Span
 * @see com.ghatana.datacloud.entity.observability.TraceContext
 * @doc.type interface
 * @doc.purpose Query contract for trace selection and filtering
 * @doc.layer product
 * @doc.pattern Port
 */
public interface TraceQuery {

    /**
     * Immutable query result with matching trace IDs and metadata.
     *
     * <p>Represents outcome of executing a trace query against stored spans.
     * Includes matched trace IDs, pagination info, and query execution stats.
     */
    final class QueryResult {
        private final List<String> matchingTraceIds;
        private final long totalMatchCount;
        private final int pageSize;
        private final int pageNumber;
        private final long executionTimeMs;

        /**
         * Create query result with matching traces.
         *
         * @param matchingTraceIds trace IDs matching query criteria
         * @param totalMatchCount total matches (may exceed page size)
         * @param pageSize traces returned per page
         * @param pageNumber current page (0-indexed)
         * @param executionTimeMs time taken to execute query
         */
        public QueryResult(
                List<String> matchingTraceIds,
                long totalMatchCount,
                int pageSize,
                int pageNumber,
                long executionTimeMs) {
            Objects.requireNonNull(matchingTraceIds, "matchingTraceIds required");
            if (totalMatchCount < 0) throw new IllegalArgumentException("totalMatchCount must be ≥ 0");
            if (pageSize <= 0) throw new IllegalArgumentException("pageSize must be > 0");
            if (pageNumber < 0) throw new IllegalArgumentException("pageNumber must be ≥ 0");
            if (executionTimeMs < 0) throw new IllegalArgumentException("executionTimeMs must be ≥ 0");

            this.matchingTraceIds = List.copyOf(matchingTraceIds);
            this.totalMatchCount = totalMatchCount;
            this.pageSize = pageSize;
            this.pageNumber = pageNumber;
            this.executionTimeMs = executionTimeMs;
        }

        /** Get trace IDs matching query on current page. */
        public List<String> getMatchingTraceIds() {
            return matchingTraceIds;
        }

        /** Get total matching traces (across all pages). */
        public long getTotalMatchCount() {
            return totalMatchCount;
        }

        /** Get traces returned per page. */
        public int getPageSize() {
            return pageSize;
        }

        /** Get current page number (0-indexed). */
        public int getPageNumber() {
            return pageNumber;
        }

        /** Get query execution time in milliseconds. */
        public long getExecutionTimeMs() {
            return executionTimeMs;
        }

        /** Get total pages needed to display all matches. */
        public int getTotalPages() {
            return (int) Math.ceil((double) totalMatchCount / pageSize);
        }

        /** Check if more pages available after current page. */
        public boolean hasNextPage() {
            return pageNumber < getTotalPages() - 1;
        }

        @Override
        public String toString() {
            return "QueryResult{" +
                    "matchingTraceIds=" + matchingTraceIds.size() +
                    ", totalMatchCount=" + totalMatchCount +
                    ", pageSize=" + pageSize +
                    ", pageNumber=" + pageNumber +
                    ", executionTimeMs=" + executionTimeMs +
                    '}';
        }
    }

    /**
     * Configuration for query execution including timeouts and resource limits.
     *
     * <p>Controls query performance and resource usage constraints.
     */
    final class QueryConfig {
        private final long timeoutMs;
        private final int maxResultSize;
        private final boolean enableIndexing;

        /**
         * Create query configuration.
         *
         * @param timeoutMs maximum query execution time
         * @param maxResultSize maximum traces to return
         * @param enableIndexing whether to use indexes (if available)
         */
        public QueryConfig(long timeoutMs, int maxResultSize, boolean enableIndexing) {
            if (timeoutMs <= 0) throw new IllegalArgumentException("timeoutMs must be > 0");
            if (maxResultSize <= 0) throw new IllegalArgumentException("maxResultSize must be > 0");

            this.timeoutMs = timeoutMs;
            this.maxResultSize = maxResultSize;
            this.enableIndexing = enableIndexing;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public int getMaxResultSize() {
            return maxResultSize;
        }

        public boolean isIndexingEnabled() {
            return enableIndexing;
        }

        @Override
        public String toString() {
            return "QueryConfig{" +
                    "timeoutMs=" + timeoutMs +
                    ", maxResultSize=" + maxResultSize +
                    ", enableIndexing=" + enableIndexing +
                    '}';
        }
    }

    /**
     * Execute query against tenant's traces.
     *
     * @param tenantId tenant whose traces to query
     * @param pageSize traces per page
     * @param pageNumber page to retrieve (0-indexed)
     * @return Promise resolving to query results
     */
    Promise<QueryResult> execute(String tenantId, int pageSize, int pageNumber);

    /**
     * Get description of query criteria for logging/display.
     *
     * @return human-readable query description
     */
    String getDescription();

    /**
     * Check if query has any filtering criteria.
     *
     * @return true if query applies at least one filter
     */
    boolean hasFilters();
}
