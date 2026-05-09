package com.ghatana.digitalmarketing.application.analytics;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;

/**
 * P0-003: Canonical analytics/reporting runtime for dashboard summary.
 *
 * <p>Backend computes dashboard summary with freshness and confidence indicators.
 * This ensures dashboard/report/export parity and eliminates UI-side aggregation.</p>
 *
 * <p>Dashboard summary includes:</p>
 * <ul>
 *   <li>Campaign metrics: count, active, paused, completed</li>
 *   <li>Approval queue: pending count, overdue count</li>
 *   <li>Budget pacing: total spend, remaining, pacing percentage</li>
 *   <li>Lead metrics: total captured, qualified, conversion rate</li>
 *   <li>Freshness indicator: data timestamp, staleness status</li>
 *   <li>Confidence label: high/medium/low based on data quality</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Backend dashboard summary service (P0-003)
 * @doc.layer product
 * @doc.pattern ApplicationService, Analytics
 */
public interface DashboardSummaryService {

    /**
     * Computes the dashboard summary for a workspace.
     *
     * @param ctx the operation context
     * @return the dashboard summary
     */
    Promise<DashboardSummary> computeSummary(DmOperationContext ctx);

    /**
     * Dashboard summary with freshness and confidence indicators.
     */
    record DashboardSummary(
        String workspaceId,
        CampaignMetrics campaignMetrics,
        ApprovalMetrics approvalMetrics,
        BudgetMetrics budgetMetrics,
        LeadMetrics leadMetrics,
        FreshnessInfo freshness,
        ConfidenceLevel confidence
    ) {}

    record CampaignMetrics(
        int totalCampaigns,
        int activeCampaigns,
        int pausedCampaigns,
        int completedCampaigns,
        int archivedCampaigns
    ) {}

    record ApprovalMetrics(
        int pendingApprovals,
        int overdueApprovals,
        int approvalsToday,
        int approvalsThisWeek
    ) {}

    record BudgetMetrics(
        long totalBudget,
        long spentBudget,
        long remainingBudget,
        double pacingPercentage,
        boolean onTrack
    ) {}

    record LeadMetrics(
        long totalLeads,
        long qualifiedLeads,
        double conversionRate,
        long leadsToday,
        long leadsThisWeek
    ) {}

    record FreshnessInfo(
        java.time.Instant lastUpdated,
        java.time.Duration staleness,
        StalenessStatus status
    ) {}

    enum StalenessStatus {
        FRESH,    // Data updated within last 5 minutes
        STALE,    // Data updated 5-30 minutes ago
        VERY_STALE, // Data updated 30-60 minutes ago
        CRITICAL  // Data older than 60 minutes
    }

    enum ConfidenceLevel {
        HIGH,   // All data sources fresh and complete
        MEDIUM, // Some data sources stale or partial
        LOW     // Critical data sources stale or missing
    }
}
