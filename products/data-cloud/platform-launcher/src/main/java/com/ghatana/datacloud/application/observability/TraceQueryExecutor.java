package com.ghatana.datacloud.application.observability;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.entity.observability.*;
import io.activej.promise.Promise;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Application service orchestrating trace query execution.
 *
 * <p><b>Purpose</b><br>
 * Coordinates query parsing, validation, execution via adapters, and result
 * pagination. Manages metrics collection and error handling for query operations.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * TraceQueryExecutor executor = new TraceQueryExecutor(adapter, metrics);
 *
 * TraceQuery query = TraceQueryBuilder.create()
 *     .withOperationName("user.login")
 *     .withErrorsOnly(true)
 *     .withDurationRange(100, 5000)
 *     .build();
 *
 * Promise<TraceQuery.QueryResult> result = executor.executeQuery(
 *     tenantId,
 *     (TraceQueryBuilder.AbstractTraceQuery) query,
 *     10, // pageSize
 *     0   // pageNumber
 * );
 *
 * result.whenResult(queryResult -> {
 *     List<String> traceIds = queryResult.getMatchingTraceIds();
 *     long total = queryResult.getTotalMatchCount();
 * });
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe for concurrent query execution from multiple threads.
 *
 * <p><b>Error Handling</b><br>
 * Query execution errors are propagated via Promise exception handlers.
 * Timeout errors are reported as QueryTimeoutException.
 * Validation errors throw IllegalArgumentException before execution starts.
 *
 * @see com.ghatana.datacloud.entity.observability.TraceQuery
 * @see com.ghatana.datacloud.entity.observability.TraceQueryBuilder
 * @doc.type class
 * @doc.purpose Orchestration service for trace query execution
 * @doc.layer product
 * @doc.pattern Service
 */
public class TraceQueryExecutor {
    private final TraceQueryAdapter adapter;
    private final MetricsCollector metrics;

    /**
     * Create trace query executor service.
     *
     * @param adapter query execution adapter
     * @param metrics metrics collector
     */
    public TraceQueryExecutor(TraceQueryAdapter adapter, MetricsCollector metrics) {
        Objects.requireNonNull(adapter, "adapter required");
        Objects.requireNonNull(metrics, "metrics required");
        this.adapter = adapter;
        this.metrics = metrics;
    }

    /**
     * Execute trace query with pagination.
     *
     * @param tenantId tenant whose traces to query
     * @param query query criteria
     * @param pageSize traces per page (1-10000)
     * @param pageNumber page to retrieve (0-indexed)
     * @return Promise resolving to query results
     */
    public Promise<TraceQuery.QueryResult> executeQuery(
            String tenantId,
            TraceQueryBuilder.AbstractTraceQuery query,
            int pageSize,
            int pageNumber) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(query, "query required");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must be non-blank");
        }
        if (pageSize < 1 || pageSize > 10000) {
            throw new IllegalArgumentException("pageSize must be in [1, 10000]");
        }
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must be ≥ 0");
        }

        long startTimeMs = System.currentTimeMillis();

        return adapter.executeQuery(tenantId, query, pageSize, pageNumber)
                .whenResult(result -> {
                    long durationMs = System.currentTimeMillis() - startTimeMs;
                    metrics.recordTimer(
                            "tracing.query.duration",
                            durationMs,
                            "tenant", tenantId,
                            "has_filters", String.valueOf(query.hasFilters()),
                            "result_count", String.valueOf(result.getMatchingTraceIds().size())
                    );
                    metrics.incrementCounter(
                            "tracing.query.successful",
                            "tenant", tenantId,
                            "total_matches", String.valueOf(result.getTotalMatchCount())
                    );
                })
                .whenException(ex -> {
                    long durationMs = System.currentTimeMillis() - startTimeMs;
                    metrics.recordTimer(
                            "tracing.query.duration",
                            durationMs,
                            "tenant", tenantId,
                            "status", "failed"
                    );
                    metrics.incrementCounter(
                            "tracing.query.failed",
                            "tenant", tenantId,
                            "error_type", ex.getClass().getSimpleName()
                    );
                });
    }

    /**
     * Execute count-only query (returns total matches without pagination).
     *
     * @param tenantId tenant whose traces to query
     * @param query query criteria
     * @return Promise resolving to match count
     */
    public Promise<Long> executeCountQuery(
            String tenantId,
            TraceQueryBuilder.AbstractTraceQuery query) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(query, "query required");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must be non-blank");
        }

        return executeQuery(tenantId, query, 1, 0)
                .map(result -> {
                    metrics.incrementCounter(
                            "tracing.query.count",
                            "tenant", tenantId,
                            "total_matches", String.valueOf(result.getTotalMatchCount())
                    );
                    return result.getTotalMatchCount();
                });
    }

    /**
     * Validate query criteria before execution.
     *
     * @param query query to validate
     * @return list of validation errors (empty if valid)
     */
    public List<String> validateQuery(TraceQueryBuilder.AbstractTraceQuery query) {
        Objects.requireNonNull(query, "query required");

        List<String> errors = new ArrayList<>();

        if (query.getTimeRangeStart() != null && query.getTimeRangeEnd() != null) {
            if (query.getTimeRangeEnd().isBefore(query.getTimeRangeStart())) {
                errors.add("timeRange: endTime before startTime");
            }
        }

        if (query.getSpanCountMin() != null && query.getSpanCountMax() != null) {
            if (query.getSpanCountMax() < query.getSpanCountMin()) {
                errors.add("spanCount: maxSpans less than minSpans");
            }
        }

        if (query.getDurationMinMs() != null && query.getDurationMaxMs() != null) {
            if (query.getDurationMaxMs() < query.getDurationMinMs()) {
                errors.add("duration: maxMs less than minMs");
            }
        }

        if (!query.hasFilters()) {
            errors.add("query: no filters specified");
        }

        return errors;
    }

    /**
     * Get query execution statistics.
     *
     * @param tenantId tenant to get stats for
     * @return statistics snapshot
     */
    public QueryExecutorStatistics getStatistics(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        return new QueryExecutorStatistics(
                adapter.getClass().getSimpleName(),
                tenantId,
                System.currentTimeMillis()
        );
    }

    /**
     * Port interface for query execution adapters.
     *
     * <p>Implementations execute queries against stored span data.
     */
    public interface TraceQueryAdapter {
        /**
         * Execute query and return paginated results.
         *
         * @param tenantId tenant whose traces to query
         * @param query query criteria
         * @param pageSize traces per page
         * @param pageNumber page index
         * @return Promise resolving to query results
         */
        Promise<TraceQuery.QueryResult> executeQuery(
                String tenantId,
                TraceQueryBuilder.AbstractTraceQuery query,
                int pageSize,
                int pageNumber);
    }

    /**
     * Statistics for query executor state.
     */
    public static final class QueryExecutorStatistics {
        private final String adapterType;
        private final String tenantId;
        private final long timestampMs;

        public QueryExecutorStatistics(String adapterType, String tenantId, long timestampMs) {
            this.adapterType = Objects.requireNonNull(adapterType);
            this.tenantId = Objects.requireNonNull(tenantId);
            this.timestampMs = timestampMs;
        }

        public String getAdapterType() {
            return adapterType;
        }

        public String getTenantId() {
            return tenantId;
        }

        public long getTimestampMs() {
            return timestampMs;
        }

        @Override
        public String toString() {
            return "QueryExecutorStatistics{" +
                    "adapterType='" + adapterType + '\'' +
                    ", tenantId='" + tenantId + '\'' +
                    ", timestampMs=" + timestampMs +
                    '}';
        }
    }
}
