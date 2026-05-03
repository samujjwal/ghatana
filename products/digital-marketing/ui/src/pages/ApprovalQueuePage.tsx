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
import type { ApprovalTargetType } from '@/types/approval';

const TARGET_TYPES: Array<ApprovalTargetType | 'ALL'> = [
  'ALL',
  'STRATEGY',
  'CAMPAIGN',
  'CONTENT',
  'AUDIENCE_SEGMENT',
  'BUDGET_PLAN',
  'ANALYTICS_REPORT',
  'INTEGRATION_CONFIG',
];

const RISK_FILTERS = [
  { label: 'All risks', value: 0 },
  { label: 'Medium+', value: 2 },
  { label: 'High only', value: 4 },
];

export function ApprovalQueuePage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated, tenantId, roles } = useAuth();

  const [typeFilter, setTypeFilter] = useState<ApprovalTargetType | 'ALL'>('ALL');
  const [minRisk, setMinRisk] = useState(0);

  const subjectId = tenantId ?? 'unknown';
  const { approvals, isLoading, isError, error } = useApprovalQueue(
    workspaceId ?? null,
    subjectId,
  );

  const filtered = useMemo(
    () =>
      approvals.filter(
        (a) =>
          (typeFilter === 'ALL' || a.targetType === typeFilter) &&
          a.riskLevel >= minRisk,
      ),
    [approvals, typeFilter, minRisk],
  );

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // Check if user has an approver role
  const hasApproverRole = roles.length === 0 || roles.some((r) => r.includes('approver') || r.includes('admin'));

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

      {!hasApproverRole && (
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
            value={typeFilter}
            onChange={(e) =>
              setTypeFilter(e.target.value as ApprovalTargetType | 'ALL')
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

        <label className="flex items-center gap-1 text-sm">
          Risk:
          <select
            data-testid="filter-risk"
            value={minRisk}
            onChange={(e) => setMinRisk(Number(e.target.value))}
            className="border rounded px-2 py-1 text-sm"
          >
            {RISK_FILTERS.map((f) => (
              <option key={f.value} value={f.value}>
                {f.label}
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
