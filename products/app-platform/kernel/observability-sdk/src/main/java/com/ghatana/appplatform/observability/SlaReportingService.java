/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.observability;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Generates periodic SLA compliance reports for each registered SLO (STORY-K06-015).
 *
 * <p>Reports are typically produced on a monthly or quarterly cadence and include:
 * <ul>
 *   <li>Achieved success rate vs. contracted SLA</li>
 *   <li>Total downtime / error budget consumed</li>
 *   <li>Incident summary counts</li>
 *   <li>Per-tenant breakdown</li>
 * </ul>
 *
 * <p>Consumers can persist the {@link SlaReport} to PostgreSQL, export to PDF via
 * {@code AuditEvidencePdfGenerator}, or deliver to customer-facing dashboards.
 *
 * @doc.type  class
 * @doc.purpose Generates SLA compliance reports from SLO tracker data (K06-015)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class SlaReportingService {

    private static final Logger log = LoggerFactory.getLogger(SlaReportingService.class);

    private final SloTracker sloTracker;
    private final ErrorBudgetCalculator errorBudgetCalculator;
    private final Executor executor;

    public SlaReportingService(SloTracker sloTracker,
                                ErrorBudgetCalculator errorBudgetCalculator,
                                Executor executor) {
        this.sloTracker             = Objects.requireNonNull(sloTracker, "sloTracker");
        this.errorBudgetCalculator  = Objects.requireNonNull(errorBudgetCalculator, "errorBudgetCalculator");
        this.executor               = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Generates an SLA report for the specified SLOs over the given period.
     *
     * @param sloDefinitions  list of SLO definitions to include in the report
     * @param reportStart     start of the reporting period
     * @param reportEnd       end of the reporting period (typically now)
     * @return promise resolving to a list of per-SLO compliance results
     */
    public Promise<SlaReport> generate(List<SloDefinition> sloDefinitions,
                                        Instant reportStart, Instant reportEnd) {
        Objects.requireNonNull(sloDefinitions, "sloDefinitions");
        Objects.requireNonNull(reportStart,    "reportStart");
        Objects.requireNonNull(reportEnd,      "reportEnd");

        return Promise.ofBlocking(executor, () -> {
            Duration period = Duration.between(reportStart, reportEnd);
            List<SloResult> results = sloDefinitions.stream().map(def -> {
                double rate = sloTracker.successRate(def.sloName(), def.tenantId());
                ErrorBudgetCalculator.BudgetStatus budget = errorBudgetCalculator.calculate(
                        def.sloName(), def.tenantId(), def.targetSuccessRate(), period);
                boolean compliant = rate >= def.targetSuccessRate();
                return new SloResult(def.sloName(), def.tenantId(), def.targetSuccessRate(),
                        rate, compliant, budget.consumedBudget(), budget.remainingBudget());
            }).toList();

            long compliantCount    = results.stream().filter(SloResult::compliant).count();
            long nonCompliantCount = results.size() - compliantCount;

            log.info("SLA report generated: period={} slos={} compliant={} non-compliant={}",
                    period, results.size(), compliantCount, nonCompliantCount);

            return new SlaReport(reportStart, reportEnd, period, results,
                    compliantCount, nonCompliantCount, Instant.now());
        });
    }

    // ── Domain types ──────────────────────────────────────────────────────────

    public record SloDefinition(
            String sloName,
            String tenantId,
            double targetSuccessRate
    ) {}

    public record SloResult(
            String sloName,
            String tenantId,
            double targetSuccessRate,
            double achievedSuccessRate,
            boolean compliant,
            Duration errorBudgetConsumed,
            Duration errorBudgetRemaining
    ) {}

    public record SlaReport(
            Instant periodStart,
            Instant periodEnd,
            Duration periodDuration,
            List<SloResult> sloResults,
            long compliantSloCount,
            long nonCompliantSloCount,
            Instant generatedAt
    ) {}
}
