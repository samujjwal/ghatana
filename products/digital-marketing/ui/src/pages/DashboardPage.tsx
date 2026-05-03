/**
 * Dashboard Page (F1-024).
 *
 * Overview of the workspace: pending approvals, workflow status,
 * growth goals, and risk/compliance.
 *
 * @doc.type page
 * @doc.purpose Main workspace dashboard for authenticated DMOS users
 * @doc.layer frontend
 */
import React from 'react';
import { useParams, Navigate, Link } from 'react-router';
import { useAuth } from '@/context/AuthContext';
import { useApprovalQueue } from '@/hooks/useApprovalQueue';
import { ApprovalWidget } from '@/components/dashboard/ApprovalWidget';
import { GrowthGoalWidget } from '@/components/dashboard/GrowthGoalWidget';
import { RiskComplianceWidget } from '@/components/dashboard/RiskComplianceWidget';
import { WorkflowStatusWidget } from '@/components/dashboard/WorkflowStatusWidget';
import { AiActionLogWidget } from '@/components/dashboard/AiActionLogWidget';
import { useAiActionLog } from '@/hooks/useAiActionLog';

export function DashboardPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated, tenantId } = useAuth();

  const { approvals, isLoading, isError } = useApprovalQueue(
    workspaceId ?? null,
    tenantId ?? 'unknown',
  );

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const pendingCount = approvals.filter((a) => a.status === 'PENDING').length;
  const setupComplete = approvals.length > 0 || !isLoading;
  const aiActions = useAiActionLog(workspaceId ?? null);

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
        <WorkflowStatusWidget />
        <GrowthGoalWidget />
        <RiskComplianceWidget />
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
