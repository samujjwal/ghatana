package com.ghatana.platform.plugin;

/**
 * Point-in-time counters for plugin interaction broker execution.
 *
 * <p>Tracks execution statistics for plugin interactions including success rates,
 * failures by reason, and performance metrics across all plugin contracts.</p>
 *
 * @doc.type record
 * @doc.purpose Expose plugin interaction diagnostics for observability adapters and dashboards
 * @doc.layer kernel-plugin
 * @doc.pattern MetricsSnapshot
 */
public record PluginInteractionMetrics(
    String contractId,
    long interactionsRequested,
    long interactionsSucceeded,
    long interactionsDenied,
    long interactionsFailed,
    long interactionsTimedOut,
    long totalDurationMs,
    long maxDurationMs,
    long policyDenials,
    long tenantScopeDenials,
    long workspaceScopeDenials,
    long lifecyclePhaseDenials
) {
    public double averageDurationMs() {
        long completed = interactionsSucceeded + interactionsDenied + interactionsFailed;
        if (completed == 0L) {
            return 0.0D;
        }
        return (double) totalDurationMs / (double) completed;
    }

    public double successRate() {
        long total = interactionsSucceeded + interactionsDenied + interactionsFailed;
        if (total == 0L) {
            return 0.0D;
        }
        return (double) interactionsSucceeded / (double) total;
    }
}
