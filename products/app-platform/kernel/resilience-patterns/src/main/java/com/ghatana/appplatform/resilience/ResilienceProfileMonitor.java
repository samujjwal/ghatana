/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.resilience;

import com.ghatana.platform.resilience.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Registers Micrometer gauges for resilience profile health and exposes a
 * composite health score suitable for Grafana dashboards (K18-011).
 *
 * <p>Three metric families are registered:
 * <ul>
 *   <li><b>{@value #METRIC_CIRCUIT_STATE}</b> — per-service circuit breaker state:
 *       {@code 0}=CLOSED, {@code 1}=OPEN, {@code 2}=HALF_OPEN; tagged by {@code service}.</li>
 *   <li><b>{@value #METRIC_BULKHEAD_UTIL}</b> — per-service bulkhead utilization ratio
 *       ({@code 0.0}=empty, {@code 1.0}=fully saturated); tagged by {@code service}.</li>
 *   <li><b>{@value #METRIC_HEALTH_SCORE}</b> — composite health score ({@code 1.0}=all
 *       circuits CLOSED, {@code 0.0}=all circuits OPEN).</li>
 * </ul>
 *
 * <p>Alerting: whenever {@link #healthScore()} is evaluated (which happens on each Prometheus
 * scrape), if the score falls below the configured {@code alertThreshold}, every registered
 * {@link AlertListener} is notified synchronously.
 *
 * <p>Usage:
 * <pre>{@code
 * KernelResilienceFactory factory = KernelResilienceFactory.create();
 * ResilienceProfileMonitor monitor = new ResilienceProfileMonitor(
 *     meterRegistry, factory, 0.7);
 * monitor.addAlertListener((score, threshold) ->
 *     log.warn("Resilience health degraded: score={} below threshold={}", score, threshold));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Micrometer gauge registration and composite health scoring for resilience profiles (K18-011)
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class ResilienceProfileMonitor {

    /** Gauge name for per-service circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN). */
    public static final String METRIC_CIRCUIT_STATE = "resilience.circuit.state";

    /** Gauge name for per-service bulkhead utilization (0.0–1.0). */
    public static final String METRIC_BULKHEAD_UTIL = "resilience.bulkhead.utilization";

    /** Gauge name for the composite health score (0.0–1.0). */
    public static final String METRIC_HEALTH_SCORE  = "resilience.health.score";

    private final Supplier<CircuitBreaker.State> ledgerState;
    private final Supplier<CircuitBreaker.State> iamState;
    private final Supplier<CircuitBreaker.State> calendarState;
    private final Supplier<Double> ledgerBulkheadUtil;
    private final Supplier<Double> iamBulkheadUtil;
    private final Supplier<Double> calendarBulkheadUtil;
    private final double alertThreshold;
    private final List<AlertListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Wires the monitor directly to an existing {@link KernelResilienceFactory}.
     *
     * @param registry       Micrometer registry to publish gauges into
     * @param factory        resilience factory to observe
     * @param alertThreshold health score below which alert listeners fire (0.0–1.0)
     */
    public ResilienceProfileMonitor(
            MeterRegistry registry,
            KernelResilienceFactory factory,
            double alertThreshold) {
        this(registry,
             factory::ledgerCircuitState,
             factory::iamCircuitState,
             factory::calendarCircuitState,
             factory::ledgerBulkheadUtilization,
             factory::iamBulkheadUtilization,
             factory::calendarBulkheadUtilization,
             alertThreshold);
    }

    /**
     * Primary constructor with fully injectable suppliers — ideal for unit tests.
     *
     * @param registry            Micrometer registry
     * @param ledgerState         supplier of the ledger circuit breaker state
     * @param iamState            supplier of the IAM circuit breaker state
     * @param calendarState       supplier of the calendar circuit breaker state
     * @param ledgerBulkheadUtil  supplier of ledger bulkhead utilization [0.0, 1.0]
     * @param iamBulkheadUtil     supplier of IAM bulkhead utilization [0.0, 1.0]
     * @param calendarBulkheadUtil supplier of calendar bulkhead utilization [0.0, 1.0]
     * @param alertThreshold      threshold below which alert listeners are notified [0.0, 1.0]
     */
    ResilienceProfileMonitor(
            MeterRegistry registry,
            Supplier<CircuitBreaker.State> ledgerState,
            Supplier<CircuitBreaker.State> iamState,
            Supplier<CircuitBreaker.State> calendarState,
            Supplier<Double> ledgerBulkheadUtil,
            Supplier<Double> iamBulkheadUtil,
            Supplier<Double> calendarBulkheadUtil,
            double alertThreshold) {
        this.ledgerState         = Objects.requireNonNull(ledgerState, "ledgerState");
        this.iamState            = Objects.requireNonNull(iamState, "iamState");
        this.calendarState       = Objects.requireNonNull(calendarState, "calendarState");
        this.ledgerBulkheadUtil  = Objects.requireNonNull(ledgerBulkheadUtil, "ledgerBulkheadUtil");
        this.iamBulkheadUtil     = Objects.requireNonNull(iamBulkheadUtil, "iamBulkheadUtil");
        this.calendarBulkheadUtil= Objects.requireNonNull(calendarBulkheadUtil, "calendarBulkheadUtil");
        if (alertThreshold < 0.0 || alertThreshold > 1.0) {
            throw new IllegalArgumentException("alertThreshold must be between 0.0 and 1.0, got: " + alertThreshold);
        }
        this.alertThreshold = alertThreshold;
        registerMetrics(registry);
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Computes the composite health score as the average of per-service circuit state scores:
     * {@code CLOSED=1.0, HALF_OPEN=0.5, OPEN=0.0}.
     *
     * <p>If the computed score is below {@code alertThreshold}, every registered
     * {@link AlertListener} is notified synchronously.
     *
     * @return composite health score in [0.0, 1.0]
     */
    public double healthScore() {
        double score = (stateScore(ledgerState.get())
                      + stateScore(iamState.get())
                      + stateScore(calendarState.get())) / 3.0;
        if (score < alertThreshold) {
            double s = score;
            listeners.forEach(l -> l.onHealthDegraded(s, alertThreshold));
        }
        return score;
    }

    /**
     * Registers an alert listener that is called whenever the health score drops below threshold.
     *
     * @param listener listener to notify; not null
     */
    public void addAlertListener(AlertListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private void registerMetrics(MeterRegistry registry) {
        // ── Circuit state gauges ──────────────────────────────────────────────
        Gauge.builder(METRIC_CIRCUIT_STATE, ledgerState,
                      s -> stateToNumeric(s.get()))
            .tag("service", "ledger")
            .description("Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
            .register(registry);

        Gauge.builder(METRIC_CIRCUIT_STATE, iamState,
                      s -> stateToNumeric(s.get()))
            .tag("service", "iam")
            .description("Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
            .register(registry);

        Gauge.builder(METRIC_CIRCUIT_STATE, calendarState,
                      s -> stateToNumeric(s.get()))
            .tag("service", "calendar")
            .description("Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
            .register(registry);

        // ── Bulkhead utilization gauges ───────────────────────────────────────
        Gauge.builder(METRIC_BULKHEAD_UTIL, ledgerBulkheadUtil, Supplier::get)
            .tag("service", "ledger")
            .description("Bulkhead utilization ratio (0.0=empty, 1.0=saturated)")
            .register(registry);

        Gauge.builder(METRIC_BULKHEAD_UTIL, iamBulkheadUtil, Supplier::get)
            .tag("service", "iam")
            .description("Bulkhead utilization ratio (0.0=empty, 1.0=saturated)")
            .register(registry);

        Gauge.builder(METRIC_BULKHEAD_UTIL, calendarBulkheadUtil, Supplier::get)
            .tag("service", "calendar")
            .description("Bulkhead utilization ratio (0.0=empty, 1.0=saturated)")
            .register(registry);

        // ── Composite health score gauge ──────────────────────────────────────
        Gauge.builder(METRIC_HEALTH_SCORE, this, ResilienceProfileMonitor::healthScore)
            .description("Composite resilience health score (1.0=all CLOSED, 0.0=all OPEN)")
            .register(registry);
    }

    private static double stateScore(CircuitBreaker.State state) {
        return switch (state) {
            case CLOSED    -> 1.0;
            case HALF_OPEN -> 0.5;
            case OPEN      -> 0.0;
        };
    }

    private static double stateToNumeric(CircuitBreaker.State state) {
        return switch (state) {
            case CLOSED    -> 0.0;
            case OPEN      -> 1.0;
            case HALF_OPEN -> 2.0;
        };
    }

    // ─── Alert listener contract ──────────────────────────────────────────────

    /**
     * Callback invoked when the composite health score falls below the alert threshold.
     */
    @FunctionalInterface
    public interface AlertListener {

        /**
         * Called when health has degraded below threshold.
         *
         * @param currentScore the current health score
         * @param threshold    the configured alert threshold
         */
        void onHealthDegraded(double currentScore, double threshold);
    }
}
