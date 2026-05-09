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
import React, { useMemo, useState } from 'react';
import { useParams, Navigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useApprovalDetail } from '@/hooks/useApprovalDetail';
import { hasApproverRole } from '@/lib/role-utils';
import { ApiError } from '@/lib/http-client';
import { ApprovalSnapshotPanel } from '@/components/approval/ApprovalSnapshotPanel';
import { DecideDialog } from '@/components/approval/DecideDialog';
import { canApprove } from '@/lib/role-utils';

const STATUS_CLASSES: Record<string, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  APPROVED: 'bg-green-100 text-green-800',
  REJECTED: 'bg-red-100 text-red-800',
  CANCELLED: 'bg-gray-100 text-gray-700',
};

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

  const canDecide = canApprove(roles, request);

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
        <p
          data-testid="approval-detail-loading"
          className="text-sm text-gray-400"
        >
          Loading…
        </p>
      )}

      {isError && (
        <p
          data-testid="approval-detail-error"
          role="alert"
          className="text-sm text-red-600"
        >
          {error instanceof ApiError ? error.getUserMessage() : 'Failed to load approval.'}
        </p>
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
            <span
              data-testid="approval-status-badge"
              className={`px-2 py-0.5 rounded text-xs font-semibold ${STATUS_CLASSES[request.status] ?? 'bg-gray-100 text-gray-700'}`}
            >
              {request.status}
            </span>
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
              <dd>{new Date(request.submittedAt).toLocaleString()}</dd>
            </div>
            {(request.decidedAt || request.decidedBy) && (
              <>
                <div>
                  <dt className="font-medium text-gray-600">Decided At</dt>
                  <dd>{request.decidedAt ? new Date(request.decidedAt).toLocaleString() : '—'}</dd>
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
            <p
              data-testid="approval-permission-denied"
              role="alert"
              className="text-sm text-yellow-700 bg-yellow-50 px-3 py-2 rounded"
            >
              You do not have permission to approve or reject this request.
            </p>
          )}

          {canDecide && isPending && (
            <div className="flex gap-3 pt-2">
              <button
                type="button"
                data-testid="open-decide-dialog"
                onClick={() => setShowDecide(true)}
                className="px-4 py-2 text-sm bg-blue-600 text-white rounded hover:bg-blue-700"
              >
                Approve / Reject
              </button>
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
