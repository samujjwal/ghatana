/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.resilience;

import com.ghatana.platform.resilience.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link ResilienceProfileMonitor} (K18-011).
 *
 * <p>Uses {@link SimpleMeterRegistry} (in-memory) and injectable state suppliers so that
 * no real circuit breakers or server infrastructure are needed.
 */
@DisplayName("ResilienceProfileMonitor (K18-011)")
class ResilienceProfileMonitorTest {

    private MeterRegistry registry;

    private final AtomicReference<CircuitBreaker.State> ledgerState   = new AtomicReference<>(CircuitBreaker.State.CLOSED);
    private final AtomicReference<CircuitBreaker.State> iamState       = new AtomicReference<>(CircuitBreaker.State.CLOSED);
    private final AtomicReference<CircuitBreaker.State> calendarState  = new AtomicReference<>(CircuitBreaker.State.CLOSED);
    private final AtomicReference<Double> ledgerUtil   = new AtomicReference<>(0.0);
    private final AtomicReference<Double> iamUtil       = new AtomicReference<>(0.0);
    private final AtomicReference<Double> calendarUtil  = new AtomicReference<>(0.0);

    private ResilienceProfileMonitor monitor;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        monitor = new ResilienceProfileMonitor(
                registry,
                ledgerState::get,
                iamState::get,
                calendarState::get,
                ledgerUtil::get,
                iamUtil::get,
                calendarUtil::get,
                0.7   // alert threshold
        );
    }

    // ── dashboard_circuitStates ───────────────────────────────────────────────

    @Test
    @DisplayName("dashboard_circuitStates: gauges registered for all three services")
    void dashboard_circuitStates_gaugesRegistered() {
        Gauge ledger   = registry.find(ResilienceProfileMonitor.METRIC_CIRCUIT_STATE).tag("service", "ledger").gauge();
        Gauge iam      = registry.find(ResilienceProfileMonitor.METRIC_CIRCUIT_STATE).tag("service", "iam").gauge();
        Gauge calendar = registry.find(ResilienceProfileMonitor.METRIC_CIRCUIT_STATE).tag("service", "calendar").gauge();

        assertThat(ledger).isNotNull();
        assertThat(iam).isNotNull();
        assertThat(calendar).isNotNull();
    }

    @Test
    @DisplayName("dashboard_circuitStates: CLOSED reports 0.0, OPEN reports 1.0, HALF_OPEN reports 2.0")
    void dashboard_circuitStates_numericValues() {
        // All CLOSED initially → 0.0
        assertThat(registry.find(ResilienceProfileMonitor.METRIC_CIRCUIT_STATE)
                .tag("service", "ledger").gauge().value()).isEqualTo(0.0);

        // Set OPEN → 1.0
        ledgerState.set(CircuitBreaker.State.OPEN);
        assertThat(registry.find(ResilienceProfileMonitor.METRIC_CIRCUIT_STATE)
                .tag("service", "ledger").gauge().value()).isEqualTo(1.0);

        // Set HALF_OPEN → 2.0
        ledgerState.set(CircuitBreaker.State.HALF_OPEN);
        assertThat(registry.find(ResilienceProfileMonitor.METRIC_CIRCUIT_STATE)
                .tag("service", "ledger").gauge().value()).isEqualTo(2.0);

        // Back to CLOSED
        ledgerState.set(CircuitBreaker.State.CLOSED);
        assertThat(registry.find(ResilienceProfileMonitor.METRIC_CIRCUIT_STATE)
                .tag("service", "ledger").gauge().value()).isEqualTo(0.0);
    }

    // ── dashboard_bulkheadUtil ────────────────────────────────────────────────

    @Test
    @DisplayName("dashboard_bulkheadUtil: gauges registered for all three services")
    void dashboard_bulkheadUtil_gaugesRegistered() {
        Gauge ledger   = registry.find(ResilienceProfileMonitor.METRIC_BULKHEAD_UTIL).tag("service", "ledger").gauge();
        Gauge iam      = registry.find(ResilienceProfileMonitor.METRIC_BULKHEAD_UTIL).tag("service", "iam").gauge();
        Gauge calendar = registry.find(ResilienceProfileMonitor.METRIC_BULKHEAD_UTIL).tag("service", "calendar").gauge();

        assertThat(ledger).isNotNull();
        assertThat(iam).isNotNull();
        assertThat(calendar).isNotNull();
    }

    @Test
    @DisplayName("dashboard_bulkheadUtil: reflects supplier value accurately")
    void dashboard_bulkheadUtil_values() {
        // Initially 0.0
        assertThat(registry.find(ResilienceProfileMonitor.METRIC_BULKHEAD_UTIL)
                .tag("service", "ledger").gauge().value()).isEqualTo(0.0);

        // Simulate 50% utilization
        ledgerUtil.set(0.5);
        assertThat(registry.find(ResilienceProfileMonitor.METRIC_BULKHEAD_UTIL)
                .tag("service", "ledger").gauge().value()).isEqualTo(0.5);

        // Simulate 100% utilization
        iamUtil.set(1.0);
        assertThat(registry.find(ResilienceProfileMonitor.METRIC_BULKHEAD_UTIL)
                .tag("service", "iam").gauge().value()).isEqualTo(1.0);
    }

    // ── dashboard_healthScore ─────────────────────────────────────────────────

    @Test
    @DisplayName("dashboard_healthScore: 1.0 when all circuits CLOSED")
    void dashboard_healthScore_allClosed() {
        double score = monitor.healthScore();
        assertThat(score).isEqualTo(1.0, within(0.001));
    }

    @Test
    @DisplayName("dashboard_healthScore: 0.0 when all circuits OPEN")
    void dashboard_healthScore_allOpen() {
        ledgerState.set(CircuitBreaker.State.OPEN);
        iamState.set(CircuitBreaker.State.OPEN);
        calendarState.set(CircuitBreaker.State.OPEN);

        double score = monitor.healthScore();
        assertThat(score).isEqualTo(0.0, within(0.001));
    }

    @Test
    @DisplayName("dashboard_healthScore: partial score when some circuits degraded")
    void dashboard_healthScore_partial() {
        // ledger CLOSED=1.0, iam HALF_OPEN=0.5, calendar OPEN=0.0 → avg = 0.5
        ledgerState.set(CircuitBreaker.State.CLOSED);
        iamState.set(CircuitBreaker.State.HALF_OPEN);
        calendarState.set(CircuitBreaker.State.OPEN);

        double score = monitor.healthScore();
        // (1.0 + 0.5 + 0.0) / 3 ≈ 0.5
        assertThat(score).isEqualTo(0.5, within(0.001));
    }

    @Test
    @DisplayName("dashboard_healthScore: gauge registered with correct initial value")
    void dashboard_healthScore_gaugeRegistered() {
        Gauge healthGauge = registry.find(ResilienceProfileMonitor.METRIC_HEALTH_SCORE).gauge();
        assertThat(healthGauge).isNotNull();
        assertThat(healthGauge.value()).isEqualTo(1.0, within(0.001));
    }

    // ── alerting_threshold ────────────────────────────────────────────────────

    @Test
    @DisplayName("alerting_threshold: listener NOT called when health above threshold")
    void alerting_threshold_notFiredWhenHealthy() {
        List<Double> alerts = new ArrayList<>();
        monitor.addAlertListener((score, threshold) -> alerts.add(score));

        // All CLOSED → score=1.0, threshold=0.7 → no alert
        monitor.healthScore();
        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("alerting_threshold: listener called when health drops below threshold")
    void alerting_threshold_firedWhenDegraded() {
        List<Double> alertedScores = new ArrayList<>();
        monitor.addAlertListener((score, threshold) -> alertedScores.add(score));

        // Open all circuits → score=0.0 < 0.7 → alert fires
        ledgerState.set(CircuitBreaker.State.OPEN);
        iamState.set(CircuitBreaker.State.OPEN);
        calendarState.set(CircuitBreaker.State.OPEN);

        monitor.healthScore();
        assertThat(alertedScores).hasSize(1);
        assertThat(alertedScores.get(0)).isEqualTo(0.0, within(0.001));
    }

    @Test
    @DisplayName("alerting_threshold: multiple listeners all receive the alert")
    void alerting_threshold_multipleListeners() {
        List<Double> first  = new ArrayList<>();
        List<Double> second = new ArrayList<>();
        monitor.addAlertListener((s, t) -> first.add(s));
        monitor.addAlertListener((s, t) -> second.add(s));

        // score=0.0 → both listeners notified
        ledgerState.set(CircuitBreaker.State.OPEN);
        iamState.set(CircuitBreaker.State.OPEN);
        calendarState.set(CircuitBreaker.State.OPEN);

        monitor.healthScore();
        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
    }

    @Test
    @DisplayName("alerting_threshold: invalid threshold rejected at construction")
    void alerting_threshold_invalidThresholdRejected() {
        assertThatThrownBy(() ->
            new ResilienceProfileMonitor(
                registry,
                ledgerState::get, iamState::get, calendarState::get,
                ledgerUtil::get, iamUtil::get, calendarUtil::get,
                1.5
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("alertThreshold");
    }
}
