/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Calculates the current error budget remaining for a given SLO (STORY-K06-014).
 *
 * <p>Error budget = (1 - SLO target) × window duration. This class computes how much
 * budget has been consumed by observed failures and how much remains, enabling
 * "error budget burn rate" alerting (Google SRE model).
 *
 * <p>Example: 99.9% SLO over 30 days = 43.2 minutes of allowable downtime.
 * If 30 minutes have been consumed, 13.2 minutes remain.
 *
 * @doc.type  class
 * @doc.purpose Calculates remaining and consumed error budget for SLO compliance (K06-014)
 * @doc.layer kernel
 * @doc.pattern Service
 */
public final class ErrorBudgetCalculator {

    private static final Logger log = LoggerFactory.getLogger(ErrorBudgetCalculator.class);

    private final SloTracker sloTracker;

    public ErrorBudgetCalculator(SloTracker sloTracker) {
        this.sloTracker = Objects.requireNonNull(sloTracker, "sloTracker");
    }

    /**
     * Calculates the current error budget status for the given SLO and tenant.
     *
     * @param sloName    SLO identifier (must match an observed SLO in {@link SloTracker})
     * @param tenantId   tenant context
     * @param sloTarget  target success rate (e.g. 0.999 for 99.9%)
     * @param window     evaluation window (e.g. 30 days for monthly SLA)
     * @return a {@link BudgetStatus} describing current burn
     */
    public BudgetStatus calculate(String sloName, String tenantId,
                                   double sloTarget, Duration window) {
        Objects.requireNonNull(sloName,  "sloName");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(window,   "window");

        double currentSuccessRate = sloTracker.successRate(sloName, tenantId);
        double errorBudgetFraction = 1.0 - sloTarget;          // e.g. 0.001 for 99.9%
        double totalBudgetSeconds  = window.toSeconds() * errorBudgetFraction;
        double consumedFraction    = Math.max(0.0, sloTarget - currentSuccessRate);
        double consumedSeconds     = window.toSeconds() * consumedFraction;
        double remainingSeconds    = Math.max(0.0, totalBudgetSeconds - consumedSeconds);
        double burnRate            = errorBudgetFraction > 0
                ? consumedFraction / errorBudgetFraction
                : 0.0;

        boolean exhausted = remainingSeconds <= 0.0;
        if (exhausted) {
            log.warn("Error budget EXHAUSTED: slo={} tenant={} successRate={}", sloName, tenantId, currentSuccessRate);
        }

        return new BudgetStatus(
                sloName,
                tenantId,
                sloTarget,
                currentSuccessRate,
                Duration.ofSeconds((long) totalBudgetSeconds),
                Duration.ofSeconds((long) consumedSeconds),
                Duration.ofSeconds((long) remainingSeconds),
                burnRate,
                exhausted,
                Instant.now()
        );
    }

    // ── Domain types ──────────────────────────────────────────────────────────

    /**
     * Snapshot of the error budget for a single SLO at a point in time.
     *
     * @param sloName             SLO identifier
     * @param tenantId            tenant context
     * @param sloTarget           configured target success rate
     * @param currentSuccessRate  observed success rate in the window
     * @param totalBudget         total allowable error budget for the window
     * @param consumedBudget      error budget consumed so far
     * @param remainingBudget     error budget remaining (may be zero when exhausted)
     * @param burnRate            ratio of consumed / total budget (1.0 = fully consumed)
     * @param budgetExhausted     true when the error budget is at or below zero
     * @param calculatedAt        when this calculation was made
     */
    public record BudgetStatus(
            String sloName,
            String tenantId,
            double sloTarget,
            double currentSuccessRate,
            Duration totalBudget,
            Duration consumedBudget,
            Duration remainingBudget,
            double burnRate,
            boolean budgetExhausted,
            Instant calculatedAt
    ) {}
}
