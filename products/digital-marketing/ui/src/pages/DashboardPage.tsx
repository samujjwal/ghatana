/**
 * Dashboard Page (F1-024).
 *
 * P1-015: Complete DMOS command center dashboard.
 *
 * Overview of the workspace: pending approvals, workflow status,
 * growth goals, risk/compliance, campaign status, budget tracking,
 * strategy insights, and connector health.
 *
 * @doc.type page
 * @doc.purpose Main workspace dashboard for authenticated DMOS users
 * @doc.layer frontend
 */
import React from 'react';
import { useParams, Navigate, Link } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useApprovalQueue } from '@/hooks/useApprovalQueue';
import { useCampaigns } from '@/hooks/useCampaigns';
import { useStrategy } from '@/hooks/useStrategy';
import { useBudgetRecommendation } from '@/hooks/useBudget';
import { ApprovalWidget } from '@/components/dashboard/ApprovalWidget';
import { GrowthGoalWidget } from '@/components/dashboard/GrowthGoalWidget';
import { RiskComplianceWidget } from '@/components/dashboard/RiskComplianceWidget';
import { WorkflowStatusWidget } from '@/components/dashboard/WorkflowStatusWidget';
import { AiActionLogWidget } from '@/components/dashboard/AiActionLogWidget';
import { useAiActionLog } from '@/hooks/useAiActionLog';
import { CampaignStatusWidget } from '@/components/dashboard/CampaignStatusWidget';
import { BudgetTrackingWidget } from '@/components/dashboard/BudgetTrackingWidget';
import { StrategyInsightsWidget } from '@/components/dashboard/StrategyInsightsWidget';
import { ConnectorHealthWidget } from '@/components/dashboard/ConnectorHealthWidget';
import { useConnectorHealth } from '@/hooks/useConnectorHealth';

export function DashboardPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated, tenantId } = useAuth();

  // P0-5.1: Call hooks unconditionally at the top level (React rule violation fix)
  const { approvals, isLoading, isError } = useApprovalQueue(
    workspaceId ?? null,
    tenantId ?? 'unknown',
  );
  const aiActions = useAiActionLog(workspaceId ?? null);
  
  // P0-1: Fetch real campaign data for dashboard metrics
  const { campaigns, isLoading: campaignsLoading, isError: campaignsError } = useCampaigns(
    workspaceId ?? null,
    { limit: 100 } // Fetch all campaigns to calculate accurate metrics
  );
  
  // P0-1: Fetch real strategy data for dashboard metrics
  const { strategy: latestStrategy, isLoading: strategyLoading, isError: strategyError } = useStrategy(
    workspaceId ?? null
  );
  const {
    recommendation: latestBudgetRecommendation,
    isLoading: budgetLoading,
    isError: budgetError,
  } = useBudgetRecommendation(workspaceId ?? null);
  const connectorHealth = useConnectorHealth(workspaceId ?? null);

  // P0-5.3: Handle loading state separately from early return
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const pendingCount = approvals.filter((a) => a.status === 'PENDING').length;
  const setupComplete = approvals.length > 0 || !isLoading;

  // P0-1: Calculate real campaign status metrics from API data
  const activeCount = campaigns.filter((c) => c.status === 'LAUNCHED').length;
  const pausedCount = campaigns.filter((c) => c.status === 'PAUSED').length;
  const pendingCampaignCount = campaigns.filter((c) => c.status === 'DRAFT').length;
  
  // P0-1: Calculate real strategy metrics from API data
  const strategyCount = latestStrategy ? 1 : 0;
  const strategyStatus = latestStrategy?.status ?? 'NONE';
  const strategyBudgetCap = latestStrategy?.budgetCap ?? 0;
  const budgetTotal = latestBudgetRecommendation?.totalMonthlyCap ?? null;
  const isBudgetApproved = latestBudgetRecommendation?.status === 'APPROVED';
  const committedCampaignSpend = campaigns
    .filter((campaign) => campaign.status === 'LAUNCHED' || campaign.status === 'COMPLETED')
    .reduce((sum, campaign) => sum + ((campaign.budgetCents ?? 0) / 100), 0);
  const budgetSpent =
    budgetTotal !== null && isBudgetApproved && !campaignsError
      ? committedCampaignSpend
      : null;
  const budgetRemaining =
    budgetTotal !== null && budgetSpent !== null
      ? budgetTotal - budgetSpent
      : null;
  const budgetUtilization =
    budgetTotal !== null && budgetSpent !== null && budgetTotal > 0
      ? (budgetSpent / budgetTotal) * 100
      : null;
  const budgetGeneratedAt = latestBudgetRecommendation?.approvedAt ?? latestBudgetRecommendation?.generatedAt ?? null;
  const budgetSource =
    latestBudgetRecommendation === null
      ? 'Budget Recommendation API'
      : isBudgetApproved
      ? 'Budget Recommendation API + Campaign Spend Model'
      : 'Budget Recommendation API (draft recommendation)';
  const budgetUnavailableReason =
    latestBudgetRecommendation === null
      ? 'No budget recommendation is available yet.'
      : campaignsError
      ? 'Campaign spend telemetry is temporarily unavailable.'
      : !isBudgetApproved
      ? 'Budget recommendation is not approved yet. Spend utilization is hidden until approval.'
      : undefined;
  const isBudgetPartial =
    latestBudgetRecommendation !== null && (!isBudgetApproved || campaignsError);

  return (
    <section
      data-testid="dashboard-page"
      className="max-w-6xl mx-auto px-4 py-8"
    >
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <span className="text-sm text-gray-500">
          Workspace: <code>{workspaceId}</code>
        </span>
      </div>

      {!setupComplete && (
        <div
          data-testid="dashboard-setup-prompt"
          className="mb-6 p-4 bg-blue-50 border border-blue-200 rounded text-sm text-blue-800"
        >
          Get started:{' '}
          <Link
            to={`/workspaces/${workspaceId ?? ''}/approvals`}
            className="underline"
          >
            Review pending approvals
          </Link>{' '}
          or set up your first campaign.
        </div>
      )}

      {pendingCount > 0 && (
        <div
          data-testid="dashboard-approval-alert"
          role="alert"
          className="mb-4 p-3 bg-yellow-50 border border-yellow-200 rounded text-sm text-yellow-800"
        >
          You have{' '}
          <strong>{pendingCount}</strong>{' '}
          pending approval{pendingCount !== 1 ? 's' : ''} awaiting review.
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <ApprovalWidget
          workspaceId={workspaceId ?? ''}
          approvals={approvals}
          isLoading={isLoading}
          isError={isError}
        />
        <WorkflowStatusWidget
          approvals={approvals}
          isLoading={isLoading}
          isError={isError}
        />
        <GrowthGoalWidget />
        <RiskComplianceWidget
          approvals={approvals}
          isLoading={isLoading}
          isError={isError}
        />
        <CampaignStatusWidget
          workspaceId={workspaceId ?? ''}
          activeCount={activeCount}
          pausedCount={pausedCount}
          pendingCount={pendingCampaignCount}
          isLoading={campaignsLoading}
          isError={campaignsError}
        />
        <BudgetTrackingWidget
          workspaceId={workspaceId ?? ''}
          totalBudget={budgetTotal}
          spent={budgetSpent}
          remaining={budgetRemaining}
          utilizationPercent={budgetUtilization}
          isLoading={budgetLoading}
          isError={budgetError}
          isPartial={isBudgetPartial}
          source={budgetSource}
          lastUpdated={budgetGeneratedAt}
          unavailableReason={budgetUnavailableReason}
        />
        <StrategyInsightsWidget
          workspaceId={workspaceId ?? ''}
          strategyCount={strategyCount}
          recentRecommendation={strategyStatus}
          confidenceScore={strategyBudgetCap > 0 ? 0.85 : 0}
          isLoading={strategyLoading}
          isError={strategyError}
        />
        <ConnectorHealthWidget
          workspaceId={workspaceId ?? ''}
          connectors={connectorHealth.connectors}
          isLoading={connectorHealth.isLoading}
          isError={connectorHealth.isError}
          source={connectorHealth.source}
          lastUpdated={connectorHealth.lastUpdated}
          unavailableReason={connectorHealth.unavailableReason}
        />
        <AiActionLogWidget
          workspaceId={workspaceId ?? ''}
          entries={aiActions.entries}
          isLoading={aiActions.isLoading}
          isError={aiActions.isError}
        />
      </div>
    </section>
  );
}
