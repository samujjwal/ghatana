package com.ghatana.digitalmarketing.application.metrics;

import java.util.Map;

/**
 * Product-local metrics collector for DMOS business KPIs.
 *
 * <p>Encapsulates structured counter recording for key DMOS domain events.
 * Implementations forward metrics to the platform MetricCollectorPort once the
 * kernel team delivers that API (KE-03). Until then, the canonical implementation
 * ({@link LoggingDmosMetricsCollector}) emits structured log entries consumable by
 * log-based alerting (e.g., Loki, CloudWatch Insights).</p>
 *
 * <h3>Business KPI counters</h3>
 * <ul>
 *   <li>{@link #CAMPAIGN_CREATED} — campaign creation events</li>
 *   <li>{@link #CAMPAIGN_LAUNCHED} — successful campaign launches</li>
 *   <li>{@link #CAMPAIGN_PAUSED} — campaign pause events</li>
 *   <li>{@link #CAMPAIGN_COMPLETED} — campaign completion events</li>
 *   <li>{@link #CAMPAIGN_ARCHIVED} — campaign archive events</li>
 *   <li>{@link #CAMPAIGN_ROLLED_BACK} — campaign rollback events</li>
 *   <li>{@link #APPROVAL_REQUESTED} — budget / campaign approval requests</li>
 *   <li>{@link #PERFORMANCE_FETCHED} — connector performance data fetch events</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose DMOS business KPI metrics collection port (KE-03 forward compatibility)
 * @doc.layer product
 * @doc.pattern Port
 */
public interface DmosMetricsCollector {

    /** Counter: a campaign was created. Labels: {@code tenantId}, {@code workspaceId}, {@code campaignType}. */
    String CAMPAIGN_CREATED = "dmos.campaign.created";

    /** Counter: a campaign was successfully launched. Labels: {@code tenantId}, {@code workspaceId}. */
    String CAMPAIGN_LAUNCHED = "dmos.campaign.launched";

    /** Counter: a campaign was paused. Labels: {@code tenantId}, {@code workspaceId}. */
    String CAMPAIGN_PAUSED = "dmos.campaign.paused";

    /** Counter: a campaign was completed. Labels: {@code tenantId}, {@code workspaceId}. */
    String CAMPAIGN_COMPLETED = "dmos.campaign.completed";

    /** Counter: a campaign was archived. Labels: {@code tenantId}, {@code workspaceId}. */
    String CAMPAIGN_ARCHIVED = "dmos.campaign.archived";

    /** Counter: a campaign was rolled back. Labels: {@code tenantId}, {@code workspaceId}. */
    String CAMPAIGN_ROLLED_BACK = "dmos.campaign.rolled_back";

    /** Counter: a human-approval was requested (budget or campaign). Labels: {@code tenantId}, {@code operationType}. */
    String APPROVAL_REQUESTED = "dmos.approval.requested";

    /** Counter: connector performance data was fetched. Labels: {@code tenantId}, {@code connector}. */
    String PERFORMANCE_FETCHED = "dmos.performance.fetched";

    /**
     * Gauge signal: count of pending approvals recorded after list or submit operation.
     * Labels: {@code tenantId}, {@code workspaceId}.
     */
    String APPROVAL_PENDING_GAUGE = "dmos.approval.pending";

    /**
     * Counter: a compliance rule violation was detected (preflight or risk threshold).
     * Labels: {@code tenantId}, {@code workspaceId}, {@code ruleSet}.
     */
    String COMPLIANCE_VIOLATION = "dmos.compliance.violation";

    /**
     * Histogram signal: API request duration observation.
     * Because the underlying log-based adapter records all values as counter increments,
     * callers pass the measured duration in milliseconds as a label so log-based alerting
     * can extract it.  Labels: {@code servlet}, {@code method}, {@code status}.
     */
    String API_REQUEST_DURATION = "dmos.api.request_duration_ms";

    /**
     * Increments (or observes) the named metric by one observation.
     *
     * @param counterName canonical metric name (use the constants on this interface)
     * @param labels      label key-value pairs for this observation
     */
    void increment(String counterName, Map<String, String> labels);

    /**
     * Records a duration observation (histogram / gauge) for the given metric.
     *
     * <p>The default implementation falls back to {@link #increment} with a {@code durationMs}
     * label so log-based alerting stacks can still extract the value from structured logs
     * until the platform MetricCollectorPort (KE-03) adds native histogram support.</p>
     *
     * @param metricName  canonical metric name (use the constants on this interface)
     * @param durationMs  observed duration in milliseconds
     * @param labels      additional label key-value pairs
     */
    default void observe(String metricName, long durationMs, Map<String, String> labels) {
        Map<String, String> combined = new java.util.LinkedHashMap<>(labels);
        combined.put("durationMs", Long.toString(durationMs));
        increment(metricName, combined);
    }

    /**
     * Returns a no-op collector suitable for unit tests and integration test fixtures
     * that do not exercise observability paths.
     *
     * @return no-op implementation
     */
    static DmosMetricsCollector noop() {
        return (counterName, labels) -> { /* intentionally empty */ };
    }
}
