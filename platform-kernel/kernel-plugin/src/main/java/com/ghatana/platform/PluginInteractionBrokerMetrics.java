package com.ghatana.platform.plugin;

/**
 * Snapshot of broker-local plugin interaction metrics.
 *
 * @doc.type record
 * @doc.purpose Expose observable counters for brokered plugin interactions
 * @doc.layer core
 * @doc.pattern Metrics
 */
public record PluginInteractionBrokerMetrics(
        long requests,
        long dispatched,
        long succeeded,
        long blocked,
        long denied,
        long failed,
        long published,
        long delivered,
        long evidenceFailures,
        long totalLatencyMs,
        long maxLatencyMs
) {
    public double averageLatencyMs() {
        long completed = succeeded + blocked + denied + failed;
        return completed == 0 ? 0.0d : (double) totalLatencyMs / completed;
    }
}
