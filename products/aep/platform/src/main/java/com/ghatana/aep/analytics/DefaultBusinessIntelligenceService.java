/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * In-process Business Intelligence service that accumulates aggregate statistics
 * per tenant and derives BI summaries on demand.
 *
 * <h3>Tracked signals</h3>
 * <ul>
 *   <li>Total events processed</li>
 *   <li>Distinct event-type count</li>
 *   <li>Total error events</li>
 *   <li>Peak concurrency / max payload-field count observed</li>
 * </ul>
 *
 * <p>Call {@link #observe(String, String, int, boolean)} from the event-processing
 * hot path to keep statistics current.
 *
 * @doc.type class
 * @doc.purpose In-process BI summary aggregation for tenant analytics
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class DefaultBusinessIntelligenceService implements BusinessIntelligenceService {

    private final ConcurrentHashMap<String, LongAdder>  totalEvents   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder>  errorEvents   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>> eventTypes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder>  maxPayload    = new ConcurrentHashMap<>();

    /**
     * Records a single event observation.
     *
     * @param tenantId        tenant identifier
     * @param eventTypeName   event type name
     * @param payloadFields   number of payload fields in this event
     * @param isError         whether this event represents an error
     */
    public void observe(String tenantId, String eventTypeName, int payloadFields, boolean isError) {
        if (tenantId == null) return;
        totalEvents.computeIfAbsent(tenantId, k -> new LongAdder()).increment();
        if (isError) errorEvents.computeIfAbsent(tenantId, k -> new LongAdder()).increment();
        eventTypes.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                  .put(eventTypeName != null ? eventTypeName : "unknown", Boolean.TRUE);
        maxPayload.merge(tenantId, new LongAdder(), (existing, __) -> {
            if (payloadFields > existing.intValue()) existing.reset(); // use as max tracker
            return existing;
        });
        // Simpler max tracking:
        maxPayload.compute(tenantId, (k, adder) -> {
            if (adder == null) { adder = new LongAdder(); adder.add(payloadFields); return adder; }
            if (payloadFields > adder.longValue()) { adder.reset(); adder.add(payloadFields); }
            return adder;
        });
    }

    @Override
    public BISummary generateSummary(String tenantId, AnalyticsEngine.TimeRange timeRange) {
        if (tenantId == null) {
            return new BISummary(null, "No tenant specified", Map.of());
        }

        long total   = longValue(totalEvents, tenantId);
        long errors  = longValue(errorEvents, tenantId);
        int  types   = eventTypeCount(tenantId);
        long maxPay  = longValue(maxPayload, tenantId);
        double errorPct = total > 0 ? (double) errors * 100.0 / total : 0.0;

        double windowHours = timeRange != null
                ? Math.max(0.01, Duration.between(timeRange.getStart(), timeRange.getEnd()).toMinutes() / 60.0)
                : 1.0;
        double eventsPerHour = total / windowHours;

        String summary = String.format(
                "Tenant '%s': %d events (%.1f/hr), %d error(s) (%.1f%%), "
                + "%d distinct type(s), max payload %d field(s)",
                tenantId, total, eventsPerHour, errors, errorPct, types, maxPay);

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("total_events",    (double) total);
        metrics.put("error_count",     (double) errors);
        metrics.put("error_rate_pct",  errorPct);
        metrics.put("distinct_types",  (double) types);
        metrics.put("events_per_hour", eventsPerHour);
        metrics.put("max_payload_fields", (double) maxPay);

        return new BISummary(tenantId, summary, metrics);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static long longValue(ConcurrentHashMap<String, LongAdder> map, String key) {
        LongAdder a = map.get(key);
        return a != null ? a.longValue() : 0L;
    }

    private int eventTypeCount(String tenantId) {
        ConcurrentHashMap<String, Boolean> types = eventTypes.get(tenantId);
        return types != null ? types.size() : 0;
    }
}
