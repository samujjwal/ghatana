package com.ghatana.aep.analytics;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Default BI summary generator.
 *
 * @doc.type class
 * @doc.purpose Generate tenant-scoped BI summaries from observed event metadata
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DefaultBusinessIntelligenceService implements BusinessIntelligenceService {

    private final Map<String, TenantStats> byTenant = new LinkedHashMap<>();

    public void observe(String tenantId, String eventType, int payloadFieldCount, boolean error) {
        TenantStats stats = byTenant.computeIfAbsent(tenantId, ignored -> new TenantStats());
        stats.totalEvents += 1;
        if (error) {
            stats.errorEvents += 1;
        }
        if (eventType != null) {
            stats.eventTypes.add(eventType);
        }
        stats.payloadFieldCountTotal += payloadFieldCount;
    }

    public BISummary generateSummary(String tenantId, AnalyticsEngine.TimeRange range) {
        TenantStats stats = byTenant.getOrDefault(tenantId, new TenantStats());
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("total_events", (double) stats.totalEvents);
        metrics.put("error_rate_pct", stats.totalEvents == 0 ? 0.0 : (100.0 * stats.errorEvents) / stats.totalEvents);
        metrics.put("distinct_types", (double) stats.eventTypes.size());
        metrics.put("payload_size_avg", stats.totalEvents == 0 ? 0.0 : (double) stats.payloadFieldCountTotal / stats.totalEvents);
        return new BISummary(tenantId, Instant.now(), metrics);
    }

    private static final class TenantStats {
        private long totalEvents;
        private long errorEvents;
        private long payloadFieldCountTotal;
        private final Set<String> eventTypes = new LinkedHashSet<>();
    }
}