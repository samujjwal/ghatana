package com.ghatana.kernel.interaction;

/**
 * Point-in-time counters for product interaction broker execution.
 *
 * @doc.type record
 * @doc.purpose Expose product interaction broker diagnostics for tests and observability adapters
 * @doc.layer kernel
 * @doc.pattern MetricsSnapshot
 */
public record ProductInteractionBrokerMetrics(
        long requested,
        long succeeded,
        long blocked,
        long timedOut,
        long evidenceFailures,
        long totalLatencyMs,
        long maxLatencyMs
) {
    public double averageLatencyMs() {
        long completed = succeeded + blocked;
        if (completed == 0L) {
            return 0.0D;
        }
        return (double) totalLatencyMs / (double) completed;
    }
}
