package com.ghatana.aep.analytics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default in-process KPI aggregator.
 *
 * @doc.type class
 * @doc.purpose Aggregate tenant-scoped KPI counters for event count, error rate, and payload size
 * @doc.layer product
 * @doc.pattern Aggregator
 */
public final class DefaultKPIAggregator {

    private final Map<String, TenantStats> byTenant = new LinkedHashMap<>();

    public void recordEvent(String tenantId, boolean error, int payloadFieldCount) {
        TenantStats stats = byTenant.computeIfAbsent(tenantId, ignored -> new TenantStats());
        stats.eventCount += 1;
        if (error) {
            stats.errorCount += 1;
        }
        stats.payloadFieldCountTotal += payloadFieldCount;
    }

    public KPIReport calculateKPIs(String tenantId, AnalyticsEngine.TimeRange range, List<String> requestedKpis) {
        TenantStats stats = byTenant.getOrDefault(tenantId, new TenantStats());
        List<String> kpiNames = requestedKpis == null || requestedKpis.isEmpty()
            ? List.of("event.count", "error.rate", "payload.size.avg")
            : requestedKpis;

        List<KPIReport.KPIEntry> entries = new ArrayList<>();
        for (String kpiName : kpiNames) {
            switch (kpiName) {
                case "event.count" -> entries.add(new KPIReport.KPIEntry(kpiName, stats.eventCount, "count"));
                case "error.rate" -> entries.add(new KPIReport.KPIEntry(
                    kpiName,
                    stats.eventCount == 0 ? 0.0 : (double) stats.errorCount / stats.eventCount,
                    "ratio"));
                case "payload.size.avg" -> entries.add(new KPIReport.KPIEntry(
                    kpiName,
                    stats.eventCount == 0 ? 0.0 : (double) stats.payloadFieldCountTotal / stats.eventCount,
                    "fields"));
                default -> {
                }
            }
        }
        return new KPIReport(tenantId, Instant.now(), true, entries);
    }

    private static final class TenantStats {
        private long eventCount;
        private long errorCount;
        private long payloadFieldCountTotal;
    }
}