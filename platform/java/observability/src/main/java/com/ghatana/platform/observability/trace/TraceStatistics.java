package com.ghatana.platform.observability.trace;

/**
 * Immutable record representing aggregated statistics for a set of traces.
 * <p>
 * TraceStatistics provides summary metrics calculated from traces matching a query.
 * This is useful for monitoring dashboards, alerting systems, and understanding system
 * behavior without fetching all trace details.
 * </p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Count Metrics</b>: Total traces, total spans, error count</li>
 *   <li><b>Duration Statistics</b>: Min, max, avg, P50, P95, P99 percentiles</li>
 *   <li><b>Top Services/Operations</b>: Most frequently appearing</li>
 *   <li><b>Calculated Metrics</b>: Error rate, avg spans per trace</li>
 *   <li><b>Empty State Support</b>: TraceStatistics.empty() for zero results</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Get statistics for last hour
 * TraceQuery query = TraceQuery.builder()
 *     .withServiceName("api-gateway")
 *     .withStartTime(Instant.now().minus(Duration.ofHours(1)))
 *     .build();
 *
 * storage.getStatistics(query)
 *     .whenResult(stats -> {
 *         System.out.println("Total traces: " + stats.totalTraces());
 *         System.out.println("Total spans: " + stats.totalSpans());
 *         System.out.println("Error count: " + stats.errorCount());
 *         System.out.println("Error rate: " + stats.errorRate() + "%");
 *         
 *         System.out.println("Duration stats:");
 *         System.out.println("  Min: " + stats.minDurationMs() + "ms");
 *         System.out.println("  Avg: " + stats.avgDurationMs() + "ms");
 *         System.out.println("  Max: " + stats.maxDurationMs() + "ms");
 *         System.out.println("  P50: " + stats.p50DurationMs() + "ms");
 *         System.out.println("  P95: " + stats.p95DurationMs() + "ms");
 *         System.out.println("  P99: " + stats.p99DurationMs() + "ms");
 *         
 *         System.out.println("Most common service: " + stats.mostCommonService());
 *         System.out.println("Most common operation: " + stats.mostCommonOperation());
 *         System.out.println("Avg spans/trace: " + stats.avgSpansPerTrace());
 *     });
 * }</pre>
 * 
 * <h2>Metrics Explained</h2>
 * 
 * <h2>Count Metrics</h2>
 * <ul>
 *   <li><b>totalTraces</b>: Number of traces matching query</li>
 *   <li><b>totalSpans</b>: Sum of spans across all matching traces</li>
 *   <li><b>errorCount</b>: Traces with at least one error span</li>
 * </ul>
 * 
 * <h2>Duration Statistics</h2>
 * <ul>
 *   <li><b>avgDurationMs</b>: Mean trace duration</li>
 *   <li><b>minDurationMs</b>: Shortest trace duration</li>
 *   <li><b>maxDurationMs</b>: Longest trace duration</li>
 *   <li><b>p50DurationMs</b>: 50th percentile (median) - half of traces faster</li>
 *   <li><b>p95DurationMs</b>: 95th percentile - 95% of traces faster</li>
 *   <li><b>p99DurationMs</b>: 99th percentile - 99% of traces faster</li>
 * </ul>
 * 
 * <h2>Top Entities</h2>
 * <ul>
 *   <li><b>mostCommonService</b>: Service appearing most frequently in traces</li>
 *   <li><b>mostCommonOperation</b>: Operation appearing most frequently in spans</li>
 * </ul>
 * 
 * <h2>Calculated Metrics</h2>
 * <ul>
 *   <li><b>errorRate()</b>: (errorCount / totalTraces) * 100 (percentage)</li>
 *   <li><b>avgSpansPerTrace()</b>: totalSpans / totalTraces</li>
 *   <li><b>hasTraces()</b>: totalTraces > 0</li>
 *   <li><b>hasErrors()</b>: errorCount > 0</li>
 * </ul>
 * 
 * <h2>Validation Rules</h2>
 * <ul>
 *   <li>totalTraces >= 0</li>
 *   <li>totalSpans >= 0</li>
 *   <li>errorCount: 0 <= errorCount <= totalTraces</li>
 *   <li>avgDurationMs >= 0</li>
 *   <li>minDurationMs >= 0</li>
 *   <li>maxDurationMs >= minDurationMs</li>
 *   <li>p50DurationMs: minDurationMs <= p50 <= maxDurationMs</li>
 *   <li>p95DurationMs: p50DurationMs <= p95 <= maxDurationMs</li>
 *   <li>p99DurationMs: p95DurationMs <= p99 <= maxDurationMs</li>
 * </ul>
 *
 * @param totalTraces         Total number of traces matching query
 * @param totalSpans          Total number of spans across all matching traces
 * @param errorCount          Number of traces with at least one error
 * @param avgDurationMs       Average trace duration in milliseconds
 * @param minDurationMs       Minimum trace duration in milliseconds
 * @param maxDurationMs       Maximum trace duration in milliseconds
 * @param p50DurationMs       50th percentile (median) duration in milliseconds
 * @param p95DurationMs       95th percentile duration in milliseconds
 * @param p99DurationMs       99th percentile duration in milliseconds
 * @param mostCommonService   Service name that appears most frequently
 * @param mostCommonOperation Operation name that appears most frequently
 * 
 * @author Ghatana Team
 * @version 1.0
 * @since 1.0
 * @stability Stable
 * @completeness 95
 * @testing Unit
 * @thread_safety Immutable (thread-safe)
 * @performance O(1) access, calculated metrics O(1)
 * @since 1.0.0
 * @see TraceQuery
 * @see TraceStorage
 * @doc.type record
 * @doc.purpose Aggregated trace statistics for monitoring and analytics (percentiles, error rates)
 * @doc.layer observability
 * @doc.pattern Value Object
 */
public record TraceStatistics(
    int totalTraces,
    int totalSpans,
    int errorCount,
    long avgDurationMs,
    long minDurationMs,
    long maxDurationMs,
    long p50DurationMs,
    long p95DurationMs,
    long p99DurationMs,
    String mostCommonService,
    String mostCommonOperation
) {

    /**
     * Validates statistics consistency.
     * <p>
     * Validation rules:
     * <ul>
     *   <li>totalTraces must be non-negative</li>
     *   <li>totalSpans must be non-negative</li>
     *   <li>errorCount must be non-negative and <= totalTraces</li>
     *   <li>avgDurationMs must be non-negative</li>
     *   <li>minDurationMs must be non-negative</li>
     *   <li>maxDurationMs must be >= minDurationMs</li>
     *   <li>p50DurationMs must be between minDurationMs and maxDurationMs</li>
     *   <li>p95DurationMs must be between p50DurationMs and maxDurationMs</li>
     *   <li>p99DurationMs must be between p95DurationMs and maxDurationMs</li>
     * </ul>
     * </p>
     *
     * @throws IllegalArgumentException if validation fails
     */
    public TraceStatistics {
        if (totalTraces < 0) {
            throw new IllegalArgumentException("totalTraces must be non-negative: " + totalTraces);
        }
        if (totalSpans < 0) {
            throw new IllegalArgumentException("totalSpans must be non-negative: " + totalSpans);
        }
        if (errorCount < 0 || errorCount > totalTraces) {
            throw new IllegalArgumentException(
                "errorCount must be between 0 and totalTraces: errorCount=" + errorCount + ", totalTraces=" + totalTraces
            );
        }
        if (avgDurationMs < 0) {
            throw new IllegalArgumentException("avgDurationMs must be non-negative: " + avgDurationMs);
        }
        if (minDurationMs < 0) {
            throw new IllegalArgumentException("minDurationMs must be non-negative: " + minDurationMs);
        }
        if (maxDurationMs < minDurationMs) {
            throw new IllegalArgumentException(
                "maxDurationMs must be >= minDurationMs: min=" + minDurationMs + ", max=" + maxDurationMs
            );
        }
        if (totalTraces > 0) {
            // Only validate percentiles if we have traces
            if (p50DurationMs < minDurationMs || p50DurationMs > maxDurationMs) {
                throw new IllegalArgumentException(
                    "p50DurationMs must be between min and max: p50=" + p50DurationMs + ", min=" + minDurationMs + ", max=" + maxDurationMs
                );
            }
            if (p95DurationMs < p50DurationMs || p95DurationMs > maxDurationMs) {
                throw new IllegalArgumentException(
                    "p95DurationMs must be between p50 and max: p95=" + p95DurationMs + ", p50=" + p50DurationMs + ", max=" + maxDurationMs
                );
            }
            if (p99DurationMs < p95DurationMs || p99DurationMs > maxDurationMs) {
                throw new IllegalArgumentException(
                    "p99DurationMs must be between p95 and max: p99=" + p99DurationMs + ", p95=" + p95DurationMs + ", max=" + maxDurationMs
                );
            }
        }
    }

    /**
     * Returns a builder for creating TraceStatistics instances.
     *
     * @return a new TraceStatisticsBuilder instance
     */
    public static TraceStatisticsBuilder builder() {
        return new TraceStatisticsBuilder();
    }

    /**
     * Returns an empty statistics instance (no traces).
     *
     * @return statistics with all counts zero
     */
    public static TraceStatistics empty() {
        return new TraceStatistics(0, 0, 0, 0, 0, 0, 0, 0, 0, null, null);
    }

    /**
     * Calculates error rate as a percentage.
     *
     * @return error rate (0-100), or 0 if no traces
     */
    public double errorRate() {
        if (totalTraces == 0) {
            return 0.0;
        }
        return (double) errorCount / totalTraces * 100.0;
    }

    /**
     * Calculates average spans per trace.
     *
     * @return average spans per trace, or 0 if no traces
     */
    public double avgSpansPerTrace() {
        if (totalTraces == 0) {
            return 0.0;
        }
        return (double) totalSpans / totalTraces;
    }

    /**
     * Checks if there are any traces in these statistics.
     *
     * @return true if totalTraces > 0, false otherwise
     */
    public boolean hasTraces() {
        return totalTraces > 0;
    }

    /**
     * Checks if there are any errors in these statistics.
     *
     * @return true if errorCount > 0, false otherwise
     */
    public boolean hasErrors() {
        return errorCount > 0;
    }

    /**
     * Builder for creating TraceStatistics instances.
     */
    public static class TraceStatisticsBuilder {
        private int totalTraces;
        private int totalSpans;
        private int errorCount;
        private long avgDurationMs;
        private long minDurationMs;
        private long maxDurationMs;
        private long p50DurationMs;
        private long p95DurationMs;
        private long p99DurationMs;
        private String mostCommonService;
        private String mostCommonOperation;

        public TraceStatisticsBuilder withTotalTraces(int totalTraces) {
            this.totalTraces = totalTraces;
            return this;
        }

        public TraceStatisticsBuilder withTotalSpans(int totalSpans) {
            this.totalSpans = totalSpans;
            return this;
        }

        public TraceStatisticsBuilder withErrorCount(int errorCount) {
            this.errorCount = errorCount;
            return this;
        }

        public TraceStatisticsBuilder withAvgDurationMs(long avgDurationMs) {
            this.avgDurationMs = avgDurationMs;
            return this;
        }

        public TraceStatisticsBuilder withMinDurationMs(long minDurationMs) {
            this.minDurationMs = minDurationMs;
            return this;
        }

        public TraceStatisticsBuilder withMaxDurationMs(long maxDurationMs) {
            this.maxDurationMs = maxDurationMs;
            return this;
        }

        public TraceStatisticsBuilder withP50DurationMs(long p50DurationMs) {
            this.p50DurationMs = p50DurationMs;
            return this;
        }

        public TraceStatisticsBuilder withP95DurationMs(long p95DurationMs) {
            this.p95DurationMs = p95DurationMs;
            return this;
        }

        public TraceStatisticsBuilder withP99DurationMs(long p99DurationMs) {
            this.p99DurationMs = p99DurationMs;
            return this;
        }

        public TraceStatisticsBuilder withMostCommonService(String mostCommonService) {
            this.mostCommonService = mostCommonService;
            return this;
        }

        public TraceStatisticsBuilder withMostCommonOperation(String mostCommonOperation) {
            this.mostCommonOperation = mostCommonOperation;
            return this;
        }

        public TraceStatistics build() {
            return new TraceStatistics(
                totalTraces,
                totalSpans,
                errorCount,
                avgDurationMs,
                minDurationMs,
                maxDurationMs,
                p50DurationMs,
                p95DurationMs,
                p99DurationMs,
                mostCommonService,
                mostCommonOperation
            );
        }
    }
}
