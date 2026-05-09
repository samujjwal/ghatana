/**
 * Approval widget for the DMOS Dashboard.
 *
 * @doc.type component
 * @doc.purpose Summary card showing pending approval count
 * @doc.layer frontend
 */
import React from 'react';
import { Link } from 'react-router-dom';
import type { ApprovalRecordResponse } from '@/types/approval';
import { DashboardWidgetCard } from './DashboardWidgetCard';

interface ApprovalWidgetProps {
  workspaceId: string;
  approvals: ApprovalRecordResponse[];
  isLoading: boolean;
  isError: boolean;
}

export const ApprovalWidget: React.FC<ApprovalWidgetProps> = ({
  workspaceId,
  approvals,
  isLoading,
  isError,
}) => {
  const pendingCount = approvals.filter((a) => a.status === 'PENDING').length;
  const state = isLoading ? 'loading' : isError ? 'error' : 'ready';

  return (
    <DashboardWidgetCard
      testId="approval-widget"
      title="Pending Approvals"
      state={state}
      message="Could not load approvals."
      stateMessageTestId={state === 'loading' ? 'approval-widget-loading' : 'approval-widget-error'}
      footer={(
        <Link
          to={`/workspaces/${workspaceId}/approvals`}
          data-testid="approval-widget-link"
          className="block text-xs text-blue-600 hover:underline mt-1"
        >
          View queue →
        </Link>
      )}
    >
      <p className="text-3xl font-bold text-gray-900">{pendingCount}</p>
      {approvals.length === 0 && (
        <p data-testid="approval-widget-empty" className="text-xs text-green-600">
          All clear
        </p>
      )}
    </DashboardWidgetCard>
  );
};
