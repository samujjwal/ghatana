package com.ghatana.datacloud.infrastructure.query;

import com.ghatana.platform.observability.MetricsCollector;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for capturing query execution telemetry and performance metrics.
 *
 * <p><b>Purpose</b><br>
 * Monitors database query performance by tracking execution times, result counts,
 * table scans, index usage, and slow query patterns. Provides data for query
 * optimization and performance tuning.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * QueryTelemetryService telemetry = new QueryTelemetryService(metrics);
 *
 * // Record query execution
 * QueryContext ctx = telemetry.startQuery("findByName", "SELECT * FROM collections WHERE name = ?");
 * try {
 *     // Execute query
 *     List<Result> results = executeQuery();
 *     ctx.finish(results.size(), true);
 * } catch (Exception e) {
 *     ctx.finish(0, false);
 *     throw e;
 * }
 *
 * // Get slow queries
 * List<SlowQuery> slowQueries = telemetry.getSlowQueries(Duration.ofMillis(500));
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Infrastructure layer service for query monitoring
 * - Uses MetricsCollector for metric emission
 * - Tracks query execution statistics
 * - Provides data for QueryAdvisorService
 * - Thread-safe for concurrent query tracking
 *
 * <p><b>Tracked Metrics</b><br>
 * - Query execution duration (p50, p95, p99)
 * - Query result count distribution
 * - Slow query frequency (>500ms threshold)
 * - Table scan detection
 * - Index usage statistics
 * - Query error rate
 *
 * <p><b>Performance Impact</b><br>
 * Minimal overhead (<1ms per query) using ConcurrentHashMap for tracking.
 * Automatic cleanup of old query records (retain last 1000).
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - can track concurrent queries from multiple threads.
 *
 * @see QueryAdvisorService
 * @see MetricsCollector
 * @doc.type class
 * @doc.purpose Query execution telemetry and performance tracking
 * @doc.layer product
 * @doc.pattern Service (Infrastructure Layer)
 */
public class QueryTelemetryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryTelemetryService.class);

    private static final Duration DEFAULT_SLOW_QUERY_THRESHOLD = Duration.ofMillis(500);
    private static final int MAX_QUERY_HISTORY = 1000;

    private final MetricsCollector metrics;
    private final Duration slowQueryThreshold;
    private final Map<String, List<QueryExecution>> queryHistory;
    private final AtomicLong totalQueries;
    private final AtomicLong slowQueries;

    /**
     * Creates a new query telemetry service.
     *
     * @param metrics the metrics collector (required)
     * @throws NullPointerException if metrics is null
     */
    public QueryTelemetryService(MetricsCollector metrics) {
        this(metrics, DEFAULT_SLOW_QUERY_THRESHOLD);
    }

    /**
     * Creates a new query telemetry service with custom slow query threshold.
     *
     * @param metrics the metrics collector (required)
     * @param slowQueryThreshold the threshold for slow query detection (required)
     * @throws NullPointerException if any parameter is null
     */
    public QueryTelemetryService(MetricsCollector metrics, Duration slowQueryThreshold) {
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
        this.slowQueryThreshold = Objects.requireNonNull(slowQueryThreshold, "SlowQueryThreshold must not be null");
        this.queryHistory = new ConcurrentHashMap<>();
        this.totalQueries = new AtomicLong(0);
        this.slowQueries = new AtomicLong(0);
    }

    /**
     * Starts tracking a query execution.
     *
     * <p><b>Usage Pattern</b><br>
     * Call this before executing a query, then call {@link QueryContext#finish(int, boolean)}
     * after query completes.
     *
     * @param queryName the logical query name (e.g., "findByName", "countAll")
     * @param sql the SQL statement being executed (required)
     * @return QueryContext for tracking this execution
     * @throws NullPointerException if any parameter is null
     */
    public QueryContext startQuery(String queryName, String sql) {
        Objects.requireNonNull(queryName, "QueryName must not be null");
        Objects.requireNonNull(sql, "SQL must not be null");

        return new QueryContext(queryName, sql, Instant.now());
    }

    /**
     * Records a completed query execution.
     *
     * @param execution the query execution record (required)
     */
    private void recordExecution(QueryExecution execution) {
        Objects.requireNonNull(execution, "Execution must not be null");

        // Update counters
        totalQueries.incrementAndGet();
        if (execution.duration().compareTo(slowQueryThreshold) > 0) {
            slowQueries.incrementAndGet();
        }

        // Add to history (bounded)
        queryHistory.compute(execution.queryName(), (key, list) -> {
            List<QueryExecution> executions = (list != null) ? list : new ArrayList<>();
            executions.add(execution);

            // Keep only recent executions
            if (executions.size() > MAX_QUERY_HISTORY) {
                executions = new ArrayList<>(executions.subList(
                    executions.size() - MAX_QUERY_HISTORY,
                    executions.size()
                ));
            }
            return executions;
        });

        // Emit metrics
        metrics.getMeterRegistry()
            .timer("query.execution.duration",
                "query", execution.queryName(),
                "success", String.valueOf(execution.success()))
            .record(Duration.ofMillis(execution.duration().toMillis()));

        metrics.incrementCounter("query.execution.count",
            "query", execution.queryName(),
            "success", String.valueOf(execution.success()));

        metrics.getMeterRegistry()
            .gauge("query.result.count", Tags.of("query", execution.queryName()), 
                execution.resultCount());

        if (execution.tableScan()) {
            metrics.incrementCounter("query.table.scan",
                "query", execution.queryName());
        }

        if (execution.indexUsed() != null && !execution.indexUsed().isEmpty()) {
            metrics.incrementCounter("query.index.usage",
                "query", execution.queryName(),
                "index", execution.indexUsed());
        }

        if (!execution.success()) {
            metrics.incrementCounter("query.execution.error",
                "query", execution.queryName());
        }

        // Log slow queries
        if (execution.duration().compareTo(slowQueryThreshold) > 0) {
            LOGGER.warn("Slow query detected: query={}, duration={}ms, sql={}",
                execution.queryName(), execution.duration().toMillis(), execution.sql());

            metrics.incrementCounter("query.slow",
                "query", execution.queryName(),
                "threshold", String.valueOf(slowQueryThreshold.toMillis()));
        }
    }

    /**
     * Gets slow queries exceeding the threshold.
     *
     * @param threshold the minimum duration to consider slow (required)
     * @return list of slow queries sorted by duration (descending)
     */
    public List<SlowQuery> getSlowQueries(Duration threshold) {
        Objects.requireNonNull(threshold, "Threshold must not be null");

        Map<String, SlowQuery> slowQueriesMap = new HashMap<>();

        queryHistory.forEach((queryName, executions) -> {
            List<QueryExecution> slowExecutions = executions.stream()
                .filter(e -> e.duration().compareTo(threshold) > 0)
                .sorted(Comparator.comparing(QueryExecution::duration).reversed())
                .toList();

            if (!slowExecutions.isEmpty()) {
                Duration avgDuration = Duration.ofMillis(
                    (long) slowExecutions.stream()
                        .mapToLong(e -> e.duration().toMillis())
                        .average()
                        .orElse(0)
                );

                Duration maxDuration = slowExecutions.get(0).duration();
                String exampleSql = slowExecutions.get(0).sql();

                slowQueriesMap.put(queryName, new SlowQuery(
                    queryName,
                    slowExecutions.size(),
                    avgDuration,
                    maxDuration,
                    exampleSql
                ));
            }
        });

        return slowQueriesMap.values().stream()
            .sorted(Comparator.comparing(SlowQuery::averageDuration).reversed())
            .toList();
    }

    /**
     * Gets query statistics for a specific query.
     *
     * @param queryName the query name (required)
     * @return QueryStatistics or null if query not found
     */
    public QueryStatistics getQueryStatistics(String queryName) {
        Objects.requireNonNull(queryName, "QueryName must not be null");

        List<QueryExecution> executions = queryHistory.get(queryName);
        if (executions == null || executions.isEmpty()) {
            return null;
        }

        long totalExecutions = executions.size();
        long successfulExecutions = executions.stream().filter(QueryExecution::success).count();
        long failedExecutions = totalExecutions - successfulExecutions;

        Duration avgDuration = Duration.ofMillis(
            (long) executions.stream()
                .mapToLong(e -> e.duration().toMillis())
                .average()
                .orElse(0)
        );

        Duration maxDuration = executions.stream()
            .map(QueryExecution::duration)
            .max(Comparator.naturalOrder())
            .orElse(Duration.ZERO);

        Duration minDuration = executions.stream()
            .map(QueryExecution::duration)
            .min(Comparator.naturalOrder())
            .orElse(Duration.ZERO);

        long tableScans = executions.stream().filter(QueryExecution::tableScan).count();

        Set<String> indexesUsed = executions.stream()
            .map(QueryExecution::indexUsed)
            .filter(Objects::nonNull)
            .filter(s -> !s.isEmpty())
            .collect(java.util.stream.Collectors.toSet());

        return new QueryStatistics(
            queryName,
            totalExecutions,
            successfulExecutions,
            failedExecutions,
            avgDuration,
            minDuration,
            maxDuration,
            tableScans,
            indexesUsed
        );
    }

    /**
     * Gets overall telemetry summary.
     *
     * @return TelemetrySummary with aggregate statistics
     */
    public TelemetrySummary getSummary() {
        return new TelemetrySummary(
            totalQueries.get(),
            slowQueries.get(),
            queryHistory.size(),
            slowQueryThreshold
        );
    }

    /**
     * Clears all query history (useful for testing).
     */
    public void clear() {
        queryHistory.clear();
        totalQueries.set(0);
        slowQueries.set(0);
        LOGGER.debug("Query telemetry history cleared");
    }

    /**
     * Context for tracking a single query execution.
     *
     * <p>Must call {@link #finish(int, boolean)} or {@link #finish(int, boolean, boolean, String)}
     * when query completes.
     *
     * @doc.type class
     * @doc.purpose Query execution tracking context
     * @doc.layer product
     * @doc.pattern Context Object
     */
    public class QueryContext {
        private final String queryName;
        private final String sql;
        private final Instant startTime;

        private QueryContext(String queryName, String sql, Instant startTime) {
            this.queryName = queryName;
            this.sql = sql;
            this.startTime = startTime;
        }

        /**
         * Finishes tracking this query execution.
         *
         * @param resultCount the number of results returned
         * @param success true if query succeeded, false if error
         */
        public void finish(int resultCount, boolean success) {
            finish(resultCount, success, false, null);
        }

        /**
         * Finishes tracking this query execution with detailed info.
         *
         * @param resultCount the number of results returned
         * @param success true if query succeeded, false if error
         * @param tableScan true if query performed full table scan
         * @param indexUsed the index used by query (null if none)
         */
        public void finish(int resultCount, boolean success, boolean tableScan, String indexUsed) {
            Duration duration = Duration.between(startTime, Instant.now());

            QueryExecution execution = new QueryExecution(
                queryName,
                sql,
                startTime,
                duration,
                resultCount,
                success,
                tableScan,
                indexUsed
            );

            recordExecution(execution);
        }
    }

    /**
     * Record of a single query execution.
     *
     * @param queryName logical query name
     * @param sql SQL statement executed
     * @param timestamp when query started
     * @param duration how long query took
     * @param resultCount number of results returned
     * @param success true if query succeeded
     * @param tableScan true if full table scan performed
     * @param indexUsed index used (null if none)
     *
     * @doc.type record
     * @doc.purpose Query execution record
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record QueryExecution(
            String queryName,
            String sql,
            Instant timestamp,
            Duration duration,
            int resultCount,
            boolean success,
            boolean tableScan,
            String indexUsed) {
    }

    /**
     * Slow query summary.
     *
     * @param queryName logical query name
     * @param occurrences number of slow executions
     * @param averageDuration average duration of slow executions
     * @param maxDuration maximum duration observed
     * @param exampleSql example SQL statement
     *
     * @doc.type record
     * @doc.purpose Slow query summary
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record SlowQuery(
            String queryName,
            int occurrences,
            Duration averageDuration,
            Duration maxDuration,
            String exampleSql) {
    }

    /**
     * Query statistics.
     *
     * @param queryName logical query name
     * @param totalExecutions total number of executions
     * @param successfulExecutions number of successful executions
     * @param failedExecutions number of failed executions
     * @param averageDuration average execution duration
     * @param minDuration minimum duration
     * @param maxDuration maximum duration
     * @param tableScans number of table scans
     * @param indexesUsed set of indexes used
     *
     * @doc.type record
     * @doc.purpose Query statistics summary
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record QueryStatistics(
            String queryName,
            long totalExecutions,
            long successfulExecutions,
            long failedExecutions,
            Duration averageDuration,
            Duration minDuration,
            Duration maxDuration,
            long tableScans,
            Set<String> indexesUsed) {
    }

    /**
     * Overall telemetry summary.
     *
     * @param totalQueries total queries tracked
     * @param slowQueries number of slow queries
     * @param uniqueQueries number of unique query names
     * @param slowQueryThreshold threshold for slow query
     *
     * @doc.type record
     * @doc.purpose Telemetry summary
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record TelemetrySummary(
            long totalQueries,
            long slowQueries,
            int uniqueQueries,
            Duration slowQueryThreshold) {
    }
}
