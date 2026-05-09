/**
 * Approval Queue Page (F1-023).
 *
 * Displays pending approvals for the authenticated workspace user.
 * Supports filtering by risk level and target type.
 *
 * @doc.type page
 * @doc.purpose Reviewer queue — list, filter, and navigate to pending approvals
 * @doc.layer frontend
 */
import React, { useMemo, useState } from 'react';
import { useParams, Navigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useApprovalQueue } from '@/hooks/useApprovalQueue';
import { ApprovalQueueTable } from '@/components/approval/ApprovalQueueTable';
import { PageStateNotice } from '@/components/PageStateNotice';
import { ApiError } from '@/lib/http-client';
import { canPerformAction } from '@/lib/action-permissions';
import type { ApprovalTargetType } from '@/types/approval';
import { Select } from '@ghatana/design-system';

const TARGET_TYPES: Array<ApprovalTargetType | 'ALL'> = [
  'ALL',
  'STRATEGY',
  'PROPOSAL',
  'SOW',
  'CONTENT_VERSION',
  'BUDGET',
  'CAMPAIGN_LAUNCH',
  'CONNECTOR_WRITE',
  'OVERRIDE',
];


export function ApprovalQueuePage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated, tenantId, principalId, roles } = useAuth();

  const [actionFilter, setActionFilter] = useState<ApprovalTargetType | 'ALL'>('ALL');

  // P1-2: Use principalId (reviewer) instead of tenantId for reviewer-scoped queue
  const reviewerId = principalId ?? 'unknown';
  const { approvals, isLoading, isError, error } = useApprovalQueue(
    workspaceId ?? null,
    reviewerId,
  );

  const filtered = useMemo(
    () =>
      approvals.filter(
        (a) =>
          actionFilter === 'ALL' ||
          (a.targetType && a.targetType.toUpperCase().includes(actionFilter)),
      ),
    [approvals, actionFilter],
  );

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const canDecideApprovals = canPerformAction(roles, 'approve') || canPerformAction(roles, 'reject');

  return (
    <section
      data-testid="approval-queue-page"
      className="max-w-6xl mx-auto px-4 py-8"
    >
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Pending Approvals</h1>
        <span className="text-sm text-gray-500">
          Workspace: <code>{workspaceId}</code>
        </span>
      </div>

      {!canDecideApprovals && (
        <div className="mb-4">
          <PageStateNotice
            testId="permission-denied-banner"
            tone="warning"
            message="You can review approval requests, but your role does not allow approve or reject actions."
          />
        </div>
      )}

      <div
        data-testid="approval-queue-filters"
        className="flex flex-wrap gap-3 mb-6"
      >
        <label className="flex items-center gap-1 text-sm">
          Type:
          <Select
            data-testid="filter-type"
            value={actionFilter}
            onChange={(e) =>
              setActionFilter(e.target.value as ApprovalTargetType | 'ALL')
            }
            options={TARGET_TYPES.map((t) => ({ value: t, label: t }))}
            size="sm"
          />
        </label>

      </div>

      {isLoading && (
        <PageStateNotice
          testId="approval-queue-loading"
          tone="loading"
          message="Loading approvals…"
        />
      )}

      {isError && (
        <PageStateNotice
          testId="approval-queue-error"
          tone="error"
          message={error instanceof ApiError ? error.getUserMessage() : 'Failed to load approvals.'}
        />
      )}

      {!isLoading && !isError && workspaceId !== undefined && (
        <ApprovalQueueTable
          workspaceId={workspaceId}
          approvals={filtered}
        />
      )}
    </section>
  );
}
