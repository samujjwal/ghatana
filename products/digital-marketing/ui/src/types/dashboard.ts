/**
 * Dashboard summary API contract.
 *
 * @doc.type types
 * @doc.purpose Typed frontend model for backend-computed dashboard facts
 * @doc.layer frontend
 */

export type DashboardFreshnessStatus = 'FRESH' | 'STALE' | 'VERY_STALE' | 'CRITICAL';
export type DashboardConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW';

export interface DashboardSummary {
  workspaceId: string;
  campaignMetrics: DashboardCampaignMetrics;
  approvalMetrics: DashboardApprovalMetrics;
  budgetMetrics: DashboardBudgetMetrics;
  leadMetrics: DashboardLeadMetrics;
  freshness: DashboardFreshness;
  confidence: DashboardConfidenceLevel;
  metricSource: string;
  formulaVersion: string;
  authorizationScope: string;
  partialData: boolean;
}

export interface DashboardCampaignMetrics {
  totalCampaigns: number;
  activeCampaigns: number;
  pausedCampaigns: number;
  completedCampaigns: number;
  archivedCampaigns: number;
}

export interface DashboardApprovalMetrics {
  pendingApprovals: number;
  overdueApprovals: number;
  approvalsToday: number;
  approvalsThisWeek: number;
}

export interface DashboardBudgetMetrics {
  totalBudget: number;
  spentBudget: number;
  remainingBudget: number;
  pacingPercentage: number;
  onTrack: boolean;
}

export interface DashboardLeadMetrics {
  totalLeads: number;
  qualifiedLeads: number;
  conversionRate: number;
  leadsToday: number;
  leadsThisWeek: number;
}

export interface DashboardFreshness {
  lastUpdated: string;
  stalenessSeconds: number;
  status: DashboardFreshnessStatus;
}
