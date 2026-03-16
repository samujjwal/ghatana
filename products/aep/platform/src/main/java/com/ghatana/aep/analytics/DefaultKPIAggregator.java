/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * In-process KPI aggregator that calculates operational Key Performance Indicators
 * from a sliding window of tenant event observations.
 *
 * <h3>Tracked KPIs</h3>
 * <ul>
 *   <li>{@code event.count} — total events observed in the window</li>
 *   <li>{@code event.rate} — events per second over the window duration</li>
 *   <li>{@code error.rate} — fraction of events with a truthy {@code error} payload field</li>
 *   <li>{@code payload.size.avg} — average number of payload fields per event</li>
 * </ul>
 *
 * <p>All counters are maintained in {@link LongAdder} / {@link ConcurrentHashMap}
 * structures — fully thread-safe with no synchronization bottlenecks.
 *
 * @doc.type class
 * @doc.purpose Operational KPI calculation from in-process event observations
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class DefaultKPIAggregator implements KPIAggregator {

    private final ConcurrentHashMap<String, LongAdder> eventCounts  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> errorCounts  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> payloadSizes = new ConcurrentHashMap<>();

    /**
     * Records a single event observation — call this each time an event is processed
     * so that KPIs remain accurate.
     *
     * @param tenantId tenant identifier
     * @param hasError whether this event represents an error condition
     * @param payloadFieldCount number of fields in the event payload
     */
    public void recordEvent(String tenantId, boolean hasError, int payloadFieldCount) {
        if (tenantId == null) return;
        eventCounts .computeIfAbsent(tenantId, k -> new LongAdder()).increment();
        payloadSizes.computeIfAbsent(tenantId, k -> new LongAdder()).add(payloadFieldCount);
        if (hasError) {
            errorCounts.computeIfAbsent(tenantId, k -> new LongAdder()).increment();
        }
    }

    @Override
    public KPIReport calculateKPIs(String tenantId, AnalyticsEngine.TimeRange timeRange, List<String> kpiTypes) {
        if (tenantId == null) return new KPIReport(tenantId);

        long totalEvents = eventCount(tenantId);
        long totalErrors = errorCount(tenantId);
        long totalPayload = payloadSize(tenantId);

        double windowSeconds = timeRange != null
                ? Math.max(1.0, Duration.between(timeRange.getStart(), timeRange.getEnd()).getSeconds())
                : 60.0;

        double eventRate  = totalEvents / windowSeconds;
        double errorRate  = totalEvents > 0 ? (double) totalErrors / totalEvents : 0.0;
        double avgPayload = totalEvents > 0 ? (double) totalPayload / totalEvents : 0.0;

        List<KPIReport.KPIEntry> entries = new ArrayList<>();

        if (kpiTypes == null || kpiTypes.isEmpty() || kpiTypes.contains("event.count")) {
            entries.add(new KPIReport.KPIEntry("event.count", totalEvents, "events",
                    Map.of("window_seconds", windowSeconds)));
        }
        if (kpiTypes == null || kpiTypes.isEmpty() || kpiTypes.contains("event.rate")) {
            entries.add(new KPIReport.KPIEntry("event.rate", eventRate, "events/s",
                    Map.of("window_seconds", windowSeconds)));
        }
        if (kpiTypes == null || kpiTypes.isEmpty() || kpiTypes.contains("error.rate")) {
            entries.add(new KPIReport.KPIEntry("error.rate", errorRate, "ratio",
                    Map.of("error_count", (double) totalErrors)));
        }
        if (kpiTypes == null || kpiTypes.isEmpty() || kpiTypes.contains("payload.size.avg")) {
            entries.add(new KPIReport.KPIEntry("payload.size.avg", avgPayload, "fields",
                    Map.of()));
        }

        return new KPIReport(tenantId, entries, true);
    }

    private long eventCount(String tenantId) {
        LongAdder a = eventCounts.get(tenantId);
        return a != null ? a.longValue() : 0L;
    }

    private long errorCount(String tenantId) {
        LongAdder a = errorCounts.get(tenantId);
        return a != null ? a.longValue() : 0L;
    }

    private long payloadSize(String tenantId) {
        LongAdder a = payloadSizes.get(tenantId);
        return a != null ? a.longValue() : 0L;
    }
}
