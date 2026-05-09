/**
 * Approval status widget (P1-5: Fixed misleading name).
 *
 * Note: This widget shows approval-based status, not general workflow status.
 * A true workflow status widget would require a workflow execution API.
 *
 * @doc.type component
 * @doc.purpose Dashboard card for pending approval status
 * @doc.layer frontend
 */
import React from 'react';
import type { ApprovalRecordResponse, ApprovalStatus } from '@/types/approval';
import { DashboardWidgetCard } from './DashboardWidgetCard';

interface WorkflowStatusWidgetProps {
  approvals: ApprovalRecordResponse[];
  isLoading?: boolean;
  isError?: boolean;
}

const STATUS_LABEL: Record<ApprovalStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
};

const STATUS_CLASS: Record<ApprovalStatus, string> = {
  PENDING: 'text-yellow-700 bg-yellow-50 border-yellow-200',
  APPROVED: 'text-green-700 bg-green-50 border-green-200',
  REJECTED: 'text-red-700 bg-red-50 border-red-200',
  CANCELLED: 'text-gray-500 bg-gray-50 border-gray-200',
};

export const WorkflowStatusWidget: React.FC<WorkflowStatusWidgetProps> = ({
  approvals,
  isLoading = false,
  isError = false,
}) => {
  const activeApprovals = approvals.filter(
    (a) => a.status === 'PENDING',
  );
  const state = isLoading ? 'loading' : isError ? 'error' : 'ready';

  return (
    <DashboardWidgetCard
      testId="workflow-status-widget"
      title="Pending Approvals"
      state={state}
      message="Failed to load workflows."
      stateMessageTestId={state === 'loading' ? 'workflow-status-loading' : 'workflow-status-error'}
    >
      {activeApprovals.length === 0 && (
        <p
          data-testid="workflow-status-empty"
          className="text-xs text-gray-400 mt-2"
        >
          No active workflows
        </p>
      )}

      {activeApprovals.length > 0 && (
        <ul
          data-testid="workflow-status-list"
          className="mt-2 space-y-1"
          aria-label="Active workflows"
        >
          {activeApprovals.slice(0, 5).map((approval) => (
            <li
              key={approval.requestId}
              className={`text-xs border rounded px-2 py-1 ${STATUS_CLASS[approval.status]}`}
            >
              <span className="font-medium">{STATUS_LABEL[approval.status]}</span>
              {' — '}
              <span className="font-mono">{approval.action}</span>
              <span className="ml-1 text-gray-500 truncate block max-w-xs">
                {approval.subjectId}
              </span>
            </li>
          ))}
          {activeApprovals.length > 5 && (
            <li
              data-testid="workflow-status-overflow"
              className="text-xs text-gray-400"
            >
              +{activeApprovals.length - 5} more
            </li>
          )}
        </ul>
      )}
    </DashboardWidgetCard>
  );
};

