package com.ghatana.kernel.lifecycle;

/**
 * Point-in-time counters for lifecycle phase execution.
 *
 * <p>Tracks execution statistics for each lifecycle phase (validate, test, build, package, deploy)
 * across all product units. Provides visibility into phase success rates, failures, and performance.</p>
 *
 * @doc.type record
 * @doc.purpose Expose lifecycle phase diagnostics for observability adapters and dashboards
 * @doc.layer kernel
 * @doc.pattern MetricsSnapshot
 */
public record LifecyclePhaseMetrics(
    long phaseStarted,
    long phaseSucceeded,
    long phaseFailed,
    long phaseSkipped,
    long totalDurationMs,
    long maxDurationMs,
    long surfaceCount
) {
    public double averageDurationMs() {
        long completed = phaseSucceeded + phaseFailed;
        if (completed == 0L) {
            return 0.0D;
        }
        return (double) totalDurationMs / (double) completed;
    }

    public double successRate() {
        long total = phaseSucceeded + phaseFailed;
        if (total == 0L) {
            return 0.0D;
        }
        return (double) phaseSucceeded / (double) total;
    }
}
