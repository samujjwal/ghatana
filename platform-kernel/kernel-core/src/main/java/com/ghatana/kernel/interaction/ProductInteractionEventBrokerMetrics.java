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
        long maxLatencyMs,
        long dlqCount,
        long idempotencySkips,
        long subscriberTimeouts,
        long subscriberRetries
) {
    public double averageLatencyMs() {
        return published == 0L ? 0.0D : (double) totalLatencyMs / (double) published;
    }

    public double dlqRate() {
        return published == 0L ? 0.0D : (double) dlqCount / (double) published;
    }

    public double idempotencySkipRate() {
        return published == 0L ? 0.0D : (double) idempotencySkips / (double) published;
    }

    public double subscriberTimeoutRate() {
        return delivered == 0L ? 0.0D : (double) subscriberTimeouts / (double) delivered;
    }

    public double subscriberRetryRate() {
        return delivered == 0L ? 0.0D : (double) subscriberRetries / (double) delivered;
    }
}
