package com.ghatana.aep.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Business KPI tracker for AEP advanced analytics (AEP-011.1).
 *
 * <p>Provides a lightweight, in-process business metric tracker that records
 * per-tenant KPI counters and gauges, enabling real-time KPI dashboards and
 * alerting without a full time-series database during development or testing.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * AepBusinessKpiTracker tracker = AepBusinessKpiTracker.create();
 *
 * // Record events
 * tracker.increment("tenant-alpha", "events.processed");
 * tracker.increment("tenant-alpha", "events.processed", 5);
 * tracker.gauge("tenant-alpha", "pipeline.lag.ms", 123.5);
 *
 * // Query
 * long processed = tracker.counter("tenant-alpha", "events.processed");
 * Optional<Double> lag = tracker.gauge("tenant-alpha", "pipeline.lag.ms");
 *
 * // Print a summary report
 * tracker.printReport("tenant-alpha");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Business KPI tracker for AEP real-time analytics
 * @doc.layer product
 * @doc.pattern Observer
 */
public final class AepBusinessKpiTracker {

    private static final Logger LOG = LoggerFactory.getLogger(AepBusinessKpiTracker.class);

    /** Counter values: tenantId → kpiName → count */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, LongAdder>> counters;

    /** Gauge values: tenantId → kpiName → (latestValue, timestamp) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, GaugeSample>> gauges;

    private AepBusinessKpiTracker() {
        this.counters = new ConcurrentHashMap<>();
        this.gauges = new ConcurrentHashMap<>();
    }

    /**
     * @return a new, empty tracker
     */
    public static AepBusinessKpiTracker create() {
        return new AepBusinessKpiTracker();
    }

    // ─── counter API ──────────────────────────────────────────────────────────

    /**
     * Increments a named KPI counter by 1 for the given tenant.
     *
     * @param tenantId the owning tenant
     * @param kpiName  the KPI name (e.g. {@code "events.processed"})
     * @throws NullPointerException if tenantId or kpiName is null
     */
    public void increment(String tenantId, String kpiName) {
        increment(tenantId, kpiName, 1L);
    }

    /**
     * Increments a named KPI counter by {@code delta} for the given tenant.
     *
     * @param tenantId the owning tenant
     * @param kpiName  the KPI name
     * @param delta    the amount to increment by (must be positive)
     * @throws NullPointerException     if tenantId or kpiName is null
     * @throws IllegalArgumentException if delta is negative
     */
    public void increment(String tenantId, String kpiName, long delta) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(kpiName, "kpiName must not be null");
        if (delta < 0) {
            throw new IllegalArgumentException("delta must not be negative: " + delta);
        }
        counters
                .computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(kpiName, k -> new LongAdder())
                .add(delta);
        LOG.debug("[kpi] tenant={} kpi={} delta=+{}", tenantId, kpiName, delta);
    }

    /**
     * Returns the current counter value for a given tenant and KPI.
     *
     * @param tenantId the owning tenant
     * @param kpiName  the KPI name
     * @return the counter value, or 0 if never recorded
     * @throws NullPointerException if tenantId or kpiName is null
     */
    public long counter(String tenantId, String kpiName) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(kpiName, "kpiName must not be null");
        var tenantCounters = counters.get(tenantId);
        if (tenantCounters == null) return 0L;
        LongAdder adder = tenantCounters.get(kpiName);
        return adder == null ? 0L : adder.sum();
    }

    // ─── gauge API ────────────────────────────────────────────────────────────

    /**
     * Sets a gauge KPI to the given value.
     *
     * @param tenantId the owning tenant
     * @param kpiName  the KPI name
     * @param value    the gauge value (e.g. latency in ms)
     * @throws NullPointerException if tenantId or kpiName is null
     */
    public void gauge(String tenantId, String kpiName, double value) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(kpiName, "kpiName must not be null");
        gauges
                .computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(kpiName, new GaugeSample(value, Instant.now()));
        LOG.debug("[kpi-gauge] tenant={} kpi={} value={}", tenantId, kpiName, value);
    }

    /**
     * Returns the latest gauge value for a given tenant and KPI.
     *
     * @param tenantId the owning tenant
     * @param kpiName  the KPI name
     * @return the latest gauge sample, or empty if never recorded
     * @throws NullPointerException if tenantId or kpiName is null
     */
    public Optional<GaugeSample> gaugeValue(String tenantId, String kpiName) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(kpiName, "kpiName must not be null");
        var tenantGauges = gauges.get(tenantId);
        return tenantGauges != null
                ? Optional.ofNullable(tenantGauges.get(kpiName))
                : Optional.empty();
    }

    // ─── query API ────────────────────────────────────────────────────────────

    /**
     * @return an unmodifiable set of all tenant IDs with at least one recorded KPI
     */
    public Set<String> trackedTenants() {
        var all = new java.util.HashSet<String>(counters.keySet());
        all.addAll(gauges.keySet());
        return Collections.unmodifiableSet(all);
    }

    /**
     * Returns all counter KPI names for a given tenant.
     *
     * @param tenantId the tenant to query
     * @return an unmodifiable set of KPI names, or empty if tenant is unknown
     * @throws NullPointerException if tenantId is null
     */
    public Set<String> counterKpisForTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        var tenantCounters = counters.get(tenantId);
        return tenantCounters != null
                ? Collections.unmodifiableSet(tenantCounters.keySet())
                : Collections.emptySet();
    }

    /**
     * Resets all counters and gauges for a specific tenant.
     *
     * @param tenantId the tenant to reset
     * @throws NullPointerException if tenantId is null
     */
    public void resetTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        counters.remove(tenantId);
        gauges.remove(tenantId);
    }

    /**
     * Prints a formatted KPI report for the given tenant to STDOUT.
     *
     * @param tenantId the tenant to report on
     * @throws NullPointerException if tenantId is null
     */
    public void printReport(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        System.out.printf("══ AEP Business KPI Report — tenant: %s ══%n", tenantId);
        System.out.println("  Counters:");
        counterKpisForTenant(tenantId).forEach(kpi ->
                System.out.printf("    %-40s = %d%n", kpi, counter(tenantId, kpi)));
        System.out.println("  Gauges:");
        var tg = gauges.get(tenantId);
        if (tg != null) {
            tg.forEach((kpi, sample) ->
                    System.out.printf("    %-40s = %.3f (at %s)%n", kpi, sample.value(), sample.timestamp()));
        }
    }

    // ─── GaugeSample ─────────────────────────────────────────────────────────

    /**
     * An immutable gauge reading.
     *
     * @param value     the gauge value at the time of recording
     * @param timestamp the wall-clock instant of this reading
     */
    public record GaugeSample(double value, Instant timestamp) {}
}
