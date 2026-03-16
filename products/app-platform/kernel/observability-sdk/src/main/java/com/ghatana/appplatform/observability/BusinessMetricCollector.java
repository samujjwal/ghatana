/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Collects business-level metrics that reflect domain outcomes rather than technical behaviour
 * (STORY-K06-008).
 *
 * <p>Examples of business metrics:
 * <ul>
 *   <li>Trades validated per minute (units: trade, tagged by instrument/jurisdiction)</li>
 *   <li>AML screening outcomes (cleared vs flagged)</li>
 *   <li>Settlement failures</li>
 *   <li>Processing latency for domain workflows</li>
 * </ul>
 *
 * <p>Business metrics feed the SLO/error-budget dashboards via {@link SloTracker}.
 *
 * @doc.type  class
 * @doc.purpose Emits outcome-oriented business metrics from domain services (K06-008)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class BusinessMetricCollector {

    private static final Logger log = LoggerFactory.getLogger(BusinessMetricCollector.class);

    private final MeterRegistry registry;

    // Pre-registered high-frequency business metrics
    private final Counter tradesValidated;
    private final Counter tradesRejected;
    private final Counter amlScreeningsCleared;
    private final Counter amlScreeningsFlagged;
    private final Counter settlementFailures;

    public BusinessMetricCollector(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");

        this.tradesValidated = Counter.builder("finance.business.trades.validated")
                .description("Number of trade validation requests that passed all checks")
                .tag("result", "pass")
                .register(registry);

        this.tradesRejected = Counter.builder("finance.business.trades.rejected")
                .description("Number of trade validation requests that failed pre-trade checks")
                .tag("result", "fail")
                .register(registry);

        this.amlScreeningsCleared = Counter.builder("finance.business.aml.screenings")
                .description("AML screening results")
                .tag("outcome", "cleared")
                .register(registry);

        this.amlScreeningsFlagged = Counter.builder("finance.business.aml.screenings")
                .description("AML screening results")
                .tag("outcome", "flagged")
                .register(registry);

        this.settlementFailures = Counter.builder("finance.business.settlements.failed")
                .description("Settlement cycles that ended in failure")
                .register(registry);
    }

    // ── Pre-built domain event recorders ─────────────────────────────────────

    public void recordTradeValidated(String instrument, String jurisdiction) {
        registry.counter("finance.business.trades.validated",
                "instrument", instrument, "jurisdiction", jurisdiction).increment();
    }

    public void recordTradeRejected(String instrument, String jurisdiction, String reason) {
        registry.counter("finance.business.trades.rejected",
                "instrument", instrument, "jurisdiction", jurisdiction, "reason", reason).increment();
    }

    public void recordAmlCleared(String entityType) {
        registry.counter("finance.business.aml.screenings",
                "outcome", "cleared", "entity_type", entityType).increment();
    }

    public void recordAmlFlagged(String entityType, String alertType) {
        registry.counter("finance.business.aml.screenings",
                "outcome", "flagged", "entity_type", entityType, "alert_type", alertType).increment();
    }

    public void recordSettlementFailure(String jurisdiction, String reason) {
        registry.counter("finance.business.settlements.failed",
                "jurisdiction", jurisdiction, "reason", reason).increment();
    }

    /**
     * Records the processing time for a domain workflow.
     *
     * @param workflowName unique workflow identifier (e.g. {@code "dtc_settlement"})
     * @param jurisdiction jurisdiction tag
     * @param sample       duration to record
     */
    public void recordWorkflowDuration(String workflowName, String jurisdiction,
                                        java.time.Duration sample) {
        Timer.builder("finance.business.workflow.duration")
                .description("End-to-end processing time for a domain workflow")
                .tag("workflow", workflowName)
                .tag("jurisdiction", jurisdiction)
                .register(registry)
                .record(sample);
    }

    /**
     * Registers a gauge for a business-level count tracked by the caller.
     * The gauge will read from {@code valueSupplier} on each scrape.
     *
     * @param metricName     full metric name
     * @param description    human-readable description
     * @param valueSupplier  provides the current gauge value
     */
    public void registerGauge(String metricName, String description, Supplier<Number> valueSupplier) {
        Gauge.builder(metricName, valueSupplier::get)
                .description(description)
                .register(registry);
    }
}
