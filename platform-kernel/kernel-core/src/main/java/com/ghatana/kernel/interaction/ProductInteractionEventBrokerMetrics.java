package com.ghatana.kernel.interaction;

/**
 * Point-in-time counters for product interaction event broker execution.
 *
 * @doc.type record
 * @doc.purpose Expose product interaction event broker diagnostics for tests and observability adapters
 * @doc.layer kernel
 * @doc.pattern MetricsSnapshot
 */
public record ProductInteractionEventBrokerMetrics(
        long published,
        long delivered,
        long blocked,
        long evidenceFailures,
        long totalLatencyMs,
        long maxLatencyMs
) {
    public double averageLatencyMs() {
        return published == 0L ? 0.0D : (double) totalLatencyMs / (double) published;
    }
}
