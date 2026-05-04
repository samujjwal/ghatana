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
import { useParams, Navigate } from 'react-router';
import { useAuth } from '@/context/AuthContext';
import { useApprovalQueue } from '@/hooks/useApprovalQueue';
import { ApprovalQueueTable } from '@/components/approval/ApprovalQueueTable';
import { hasApproverRole } from '@/lib/role-utils';
import type { ApprovalTargetType } from '@/types/approval';

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

  const userHasApproverRole = hasApproverRole(roles);

  return (
    <main
      data-testid="approval-queue-page"
      className="max-w-6xl mx-auto px-4 py-8"
    >
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Pending Approvals</h1>
        <span className="text-sm text-gray-500">
          Workspace: <code>{workspaceId}</code>
        </span>
      </div>

      {!userHasApproverRole && (
        <div
          data-testid="permission-denied-banner"
          role="alert"
          className="mb-4 p-3 bg-yellow-50 border border-yellow-300 rounded text-sm text-yellow-800"
        >
          You do not have an approver role. You can view but not act on these
          requests.
        </div>
      )}

      <div
        data-testid="approval-queue-filters"
        className="flex flex-wrap gap-3 mb-6"
      >
        <label className="flex items-center gap-1 text-sm">
          Type:
          <select
            data-testid="filter-type"
            value={actionFilter}
            onChange={(e) =>
              setActionFilter(e.target.value as ApprovalTargetType | 'ALL')
            }
            className="border rounded px-2 py-1 text-sm"
          >
            {TARGET_TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </label>

      </div>

      {isLoading && (
        <p data-testid="approval-queue-loading" className="text-sm text-gray-400">
          Loading approvals…
        </p>
      )}

      {isError && (
        <p
          data-testid="approval-queue-error"
          role="alert"
          className="text-sm text-red-600"
        >
          {error instanceof Error ? error.message : 'Failed to load approvals.'}
        </p>
      )}

      {!isLoading && !isError && workspaceId !== undefined && (
        <ApprovalQueueTable
          workspaceId={workspaceId}
          approvals={filtered}
        />
      )}
    </main>
  );
}
