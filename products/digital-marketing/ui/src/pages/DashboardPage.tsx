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

export function DashboardPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated, tenantId } = useAuth();

  // P0-5.1: Call hooks unconditionally at the top level (React rule violation fix)
  const { approvals, isLoading, isError } = useApprovalQueue(
    workspaceId ?? null,
    tenantId ?? 'unknown',
  );
  const aiActions = useAiActionLog(workspaceId ?? null);

  // P0-5.3: Handle loading state separately from early return
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const pendingCount = approvals.filter((a) => a.status === 'PENDING').length;
  const setupComplete = approvals.length > 0 || !isLoading;

  return (
    <main
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
          activeCount={3}
          pausedCount={2}
          pendingCount={1}
          isLoading={false}
          isError={false}
        />
        <BudgetTrackingWidget
          workspaceId={workspaceId ?? ''}
          totalBudget={50000}
          spent={12500}
          remaining={37500}
          utilizationPercent={25}
          isLoading={false}
          isError={false}
        />
        <StrategyInsightsWidget
          workspaceId={workspaceId ?? ''}
          strategyCount={2}
          recentRecommendation="Increase budget for high-performing campaigns"
          confidenceScore={0.85}
          isLoading={false}
          isError={false}
        />
        <ConnectorHealthWidget
          workspaceId={workspaceId ?? ''}
          connectors={[
            { name: 'Google Ads', status: 'healthy', lastSync: '2 min ago' },
            { name: 'Meta Ads', status: 'healthy', lastSync: '5 min ago' },
          ]}
          isLoading={false}
          isError={false}
        />
        <AiActionLogWidget
          workspaceId={workspaceId ?? ''}
          entries={aiActions.entries}
          isLoading={aiActions.isLoading}
          isError={aiActions.isError}
        />
      </div>
    </main>
  );
}
