package com.ghatana.digitalmarketing.application.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Log-based implementation of {@link DmosMetricsCollector}.
 *
 * <p>Emits structured log entries for each business KPI counter increment. Log lines
 * follow a {@code [DMOS-KPI]} prefix and include {@code counter} and {@code labels}
 * fields, making them scrapeable by log-based alerting stacks (Loki, CloudWatch Insights,
 * Datadog Log Management) until the platform MetricCollectorPort (KE-03) is available.</p>
 *
 * <p>Example log line:
 * <pre>{@code
 * [DMOS-KPI] counter=dmos.campaign.launched labels={tenantId=acme, workspaceId=ws-1}
 * }</pre>
 * </p>
 *
 * @doc.type class
 * @doc.purpose Log-based DMOS business KPI metrics collector (KE-03 forward compatibility)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class LoggingDmosMetricsCollector implements DmosMetricsCollector {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingDmosMetricsCollector.class);

    @Override
    public void increment(String counterName, Map<String, String> labels) {
        Objects.requireNonNull(counterName, "counterName must not be null");
        Objects.requireNonNull(labels,      "labels must not be null");
        LOG.info("[DMOS-KPI] counter={} labels={}", counterName, labels);
    }
}
