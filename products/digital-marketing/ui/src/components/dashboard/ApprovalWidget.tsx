/**
 * Approval widget for the DMOS Dashboard.
 *
 * @doc.type component
 * @doc.purpose Summary card showing pending approval count
 * @doc.layer frontend
 */
import React from 'react';
import { Link } from 'react-router';
import type { ApprovalRecordResponse } from '@/types/approval';

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

  return (
    <article
      aria-labelledby="approval-widget-title"
      data-testid="approval-widget"
      className="border rounded-lg p-4 space-y-2"
    >
      <h2 id="approval-widget-title" className="text-sm font-semibold text-gray-700">
        Pending Approvals
      </h2>

      {isLoading && (
        <p data-testid="approval-widget-loading" className="text-xs text-gray-400">
          Loading…
        </p>
      )}

      {isError && (
        <p
          data-testid="approval-widget-error"
          role="alert"
          className="text-xs text-red-500"
        >
          Could not load approvals.
        </p>
      )}

      {!isLoading && !isError && (
        <>
          <p className="text-3xl font-bold text-gray-900">
            {pendingCount}
          </p>
          {approvals.length === 0 && (
            <p
              data-testid="approval-widget-empty"
              className="text-xs text-green-600"
            >
              All clear
            </p>
          )}
          <Link
            to={`/workspaces/${workspaceId}/approvals`}
            data-testid="approval-widget-link"
            className="block text-xs text-blue-600 hover:underline mt-1"
          >
            View queue →
          </Link>
        </>
      )}
    </article>
  );
};
