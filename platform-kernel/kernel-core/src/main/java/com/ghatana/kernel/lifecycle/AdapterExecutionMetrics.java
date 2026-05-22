package com.ghatana.kernel.lifecycle;

/**
 * Point-in-time counters for toolchain adapter execution.
 *
 * <p>Tracks execution statistics for each toolchain adapter (cargo-rust, python-pyproject, pnpm-node-api, gradle-java)
 * across all lifecycle phases. Provides visibility into adapter performance, failures, and toolchain health.</p>
 *
 * @doc.type record
 * @doc.purpose Expose adapter execution diagnostics for observability adapters and dashboards
 * @doc.layer kernel
 * @doc.pattern MetricsSnapshot
 */
public record AdapterExecutionMetrics(
    String adapterId,
    long executionsStarted,
    long executionsSucceeded,
    long executionsFailed,
    long executionsTimedOut,
    long totalDurationMs,
    long maxDurationMs,
    long preflightFailures,
    long planFailures,
    long executeFailures
) {
    public double averageDurationMs() {
        long completed = executionsSucceeded + executionsFailed;
        if (completed == 0L) {
            return 0.0D;
        }
        return (double) totalDurationMs / (double) completed;
    }

    public double successRate() {
        long total = executionsSucceeded + executionsFailed;
        if (total == 0L) {
            return 0.0D;
        }
        return (double) executionsSucceeded / (double) total;
    }
}
