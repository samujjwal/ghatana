/**
 * Approval Detail Page (F1-023).
 *
 * Shows the full approval request: status, target snapshot, and
 * approve/reject actions. Requires a comment when the risk is high.
 *
 * @doc.type page
 * @doc.purpose Reviewer detail — inspect and decide on a single approval request
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { Link, useParams, Navigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useApprovalDetail } from '@/hooks/useApprovalDetail';
import { ApiError } from '@/lib/http-client';
import { ApprovalSnapshotPanel } from '@/components/approval/ApprovalSnapshotPanel';
import { DecideDialog } from '@/components/approval/DecideDialog';
import { PageStateNotice } from '@/components/PageStateNotice';
import { canPerformAction } from '@/lib/action-permissions';
import { formatDateTime } from '@/lib/i18n/format';
import { hasMinimumRole, validateRoles, type ValidRole } from '@/lib/role-utils';
import { Badge, Button } from '@ghatana/design-system';

function statusTone(status: string): 'warning' | 'success' | 'danger' | 'neutral' {
  if (status === 'APPROVED') return 'success';
  if (status === 'REJECTED') return 'danger';
  if (status === 'CANCELLED') return 'neutral';
  return 'warning';
}

function hasRequiredApprovalRole(roles: readonly string[], requiredApproverRole: string | null | undefined): boolean {
  if (!requiredApproverRole) {
    return false;
  }

  const normalizedRequiredRole = requiredApproverRole.toLowerCase().trim();
  if (!validateRoles([normalizedRequiredRole])) {
    return false;
  }

  return hasMinimumRole(roles, normalizedRequiredRole as ValidRole);
}

export function ApprovalDetailPage(): React.ReactElement {
  const { workspaceId, requestId } = useParams<{
    workspaceId: string;
    requestId: string;
  }>();
  const { isAuthenticated, roles } = useAuth();
  const [showDecide, setShowDecide] = useState(false);

  const { request, snapshot, isLoading, isError, error } = useApprovalDetail(
    workspaceId ?? null,
    requestId ?? null,
  );

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const canDecide = request !== null
    && canPerformAction(roles, 'approve')
    && canPerformAction(roles, 'reject')
    && hasRequiredApprovalRole(roles, request.requiredApproverRole);

  const isPending = request?.status === 'PENDING';
  const riskLevel = request?.riskLevel ?? snapshot?.riskLevel ?? 0;
  const requireComment = riskLevel >= 4;

  return (
    <section
      data-testid="approval-detail-page"
      className="max-w-4xl mx-auto px-4 py-8 space-y-6"
    >
      <nav className="text-sm text-gray-500 flex items-center gap-4">
        <Link
          to={`/workspaces/${workspaceId}/approvals`}
          className="hover:underline"
        >
          ← Back to queue
        </Link>
        <Link
          to={`/workspaces/${workspaceId}/ai-actions`}
          data-testid="approval-detail-ai-log-link"
          className="hover:underline text-blue-600"
        >
          View AI action log
        </Link>
      </nav>

      {isLoading && (
        <PageStateNotice
          testId="approval-detail-loading"
          tone="loading"
          message="Loading…"
        />
      )}

      {isError && (
        <PageStateNotice
          testId="approval-detail-error"
          tone="error"
          message={error instanceof ApiError ? error.getUserMessage() : 'Failed to load approval.'}
        />
      )}

      {request && (
        <section aria-labelledby="request-heading" className="border rounded-lg p-6 space-y-4">
          <div className="flex items-start justify-between">
            <div>
              <h1
                id="request-heading"
                className="text-xl font-bold text-gray-900"
              >
                {request.targetType ?? 'Unknown'} Approval
              </h1>
              <p className="text-sm text-gray-500 mt-1">ID: {request.requestId}</p>
            </div>
            <Badge
              data-testid="approval-status-badge"
              tone={statusTone(request.status)}
              variant="soft"
            >
              {request.status}
            </Badge>
          </div>

          <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
            <div>
              <dt className="font-medium text-gray-600">Target Type</dt>
              <dd>{request.targetType ?? 'Unknown'}</dd>
            </div>
            <div>
              <dt className="font-medium text-gray-600">Target ID</dt>
              <dd>{request.targetId ?? 'N/A'}</dd>
            </div>
            {request.description && (
              <div className="col-span-2">
                <dt className="font-medium text-gray-600">Description</dt>
                <dd>{request.description}</dd>
              </div>
            )}
            <div>
              <dt className="font-medium text-gray-600">Risk Level</dt>
              <dd data-testid="approval-risk-level">{riskLevel}</dd>
            </div>
            <div>
              <dt className="font-medium text-gray-600">Required Role</dt>
              <dd>{request.requiredApproverRole ?? 'brand-manager'}</dd>
            </div>
            <div>
              <dt className="font-medium text-gray-600">Submitted By</dt>
              <dd>{request.submittedBy}</dd>
            </div>
            <div>
              <dt className="font-medium text-gray-600">Submitted At</dt>
              <dd>{formatDateTime(request.submittedAt)}</dd>
            </div>
            {(request.decidedAt || request.decidedBy) && (
              <>
                <div>
                  <dt className="font-medium text-gray-600">Decided At</dt>
                  <dd>{formatDateTime(request.decidedAt, { fallback: '—' })}</dd>
                </div>
                <div>
                  <dt className="font-medium text-gray-600">Decided By</dt>
                  <dd>{request.decidedBy ?? '—'}</dd>
                </div>
              </>
            )}
            {request.comment && (
              <div className="col-span-2">
                <dt className="font-medium text-gray-600">Comment</dt>
                <dd>{request.comment}</dd>
              </div>
            )}
          </dl>

          {!canDecide && (
            <PageStateNotice
              testId="approval-permission-denied"
              tone="warning"
              message="You do not have permission to approve or reject this request."
            />
          )}

          {canDecide && isPending && (
            <div className="flex gap-3 pt-2">
              <Button
                data-testid="open-decide-dialog"
                onClick={() => setShowDecide(true)}
                tone="primary"
                size="sm"
              >
                Approve / Reject
              </Button>
            </div>
          )}
        </section>
      )}

      {snapshot && <ApprovalSnapshotPanel snapshot={snapshot} />}

      {showDecide && workspaceId && requestId && (
        <DecideDialog
          workspaceId={workspaceId}
          requestId={requestId}
          requireComment={requireComment}
          onClose={() => setShowDecide(false)}
          onSuccess={() => setShowDecide(false)}
        />
      )}
    </section>
  );
}
