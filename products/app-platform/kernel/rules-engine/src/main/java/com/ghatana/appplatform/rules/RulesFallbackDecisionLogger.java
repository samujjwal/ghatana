/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.rules;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Logs and counts every rule evaluation that degrades to the fail-safe fallback decision
 * (i.e. when the OPA circuit breaker is open or OPA is unavailable).
 *
 * <p>Every fallback log entry is emitted at {@code WARN} level with a structured MDC payload so
 * Kibana / ELK can alert on anomalous fallback rates. A Micrometer counter lets Prometheus
 * trigger SLO-burn alerts.
 *
 * @doc.type  class
 * @doc.purpose Audit every instance where the rules engine falls back to a default decision (K03-014)
 * @doc.layer kernel
 * @doc.pattern EventListener
 */
public final class RulesFallbackDecisionLogger {

    private static final Logger log = LoggerFactory.getLogger(RulesFallbackDecisionLogger.class);

    private final Counter fallbackCounter;

    public RulesFallbackDecisionLogger(MeterRegistry meterRegistry) {
        this.fallbackCounter = Counter.builder("rules.fallback.decisions.total")
                .description("Number of rule evaluations that fell back to a default decision")
                .register(Objects.requireNonNull(meterRegistry, "meterRegistry"));
    }

    /**
     * Records a fallback decision event.
     *
     * @param policyPath   the OPA policy path that was being evaluated
     * @param jurisdiction jurisdiction for which the policy was evaluated
     * @param tenantId     tenant making the request
     * @param cause        the exception that caused the fallback (may be null for timeout)
     * @param fallbackDecision the decision applied ({@code "DENY"} or {@code "ALLOW"})
     */
    public void record(String policyPath,
                       String jurisdiction,
                       String tenantId,
                       Throwable cause,
                       String fallbackDecision) {

        fallbackCounter.increment();

        log.warn("RULES_FALLBACK decision={} policy={} jurisdiction={} tenantId={} " +
                        "timestamp={} reason={}",
                fallbackDecision,
                policyPath,
                jurisdiction,
                tenantId,
                Instant.now(),
                cause != null ? cause.getMessage() : "circuit-breaker-open");
    }

    /**
     * Convenience method that logs a structured map of context keys alongside the standard fields.
     *
     * @param policyPath       the OPA policy path
     * @param context          arbitrary key-value pairs added to the log entry
     * @param cause            exception that triggered the fallback
     * @param fallbackDecision applied decision
     */
    public void recordWithContext(String policyPath,
                                  Map<String, String> context,
                                  Throwable cause,
                                  String fallbackDecision) {

        fallbackCounter.increment();

        log.warn("RULES_FALLBACK decision={} policy={} context={} timestamp={} reason={}",
                fallbackDecision,
                policyPath,
                context,
                Instant.now(),
                cause != null ? cause.getMessage() : "circuit-breaker-open");
    }

    /** Returns the total number of fallback decisions recorded since startup. */
    public long totalFallbacks() {
        return (long) fallbackCounter.count();
    }
}
