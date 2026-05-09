/**
 * Risk and compliance widget.
 *
 * @doc.type component
 * @doc.purpose Dashboard card for risk and compliance status
 * @doc.layer frontend
 */
import React from 'react';
import type { ApprovalRecordResponse } from '@/types/approval';
import { DashboardWidgetCard } from './DashboardWidgetCard';

const MAX_PENDING_THRESHOLD = 5;

interface RiskComplianceWidgetProps {
  approvals?: ApprovalRecordResponse[];
  isLoading?: boolean;
  isError?: boolean;
}

export const RiskComplianceWidget: React.FC<RiskComplianceWidgetProps> = ({
  approvals = [],
  isLoading = false,
  isError = false,
}) => {
  const pendingItems = approvals.filter((a) => a.status === 'PENDING');
  const hasComplianceAlert = pendingItems.length >= MAX_PENDING_THRESHOLD;
  const state = isLoading ? 'loading' : isError ? 'error' : 'ready';

  return (
    <DashboardWidgetCard
      testId="risk-compliance-widget"
      title="Risk & Compliance"
      state={state}
      message="Failed to load compliance data."
      stateMessageTestId={state === 'loading' ? 'risk-compliance-loading' : 'risk-compliance-error'}
    >
      {!hasComplianceAlert && (
        <p
          data-testid="risk-compliance-ok"
          className="text-xs text-green-800 mt-2"
        >
          No active violations
        </p>
      )}

      {hasComplianceAlert && (
        <>
          <p
            data-testid="risk-compliance-alert"
            className="text-xs text-red-600 font-medium mt-2"
          >
            {pendingItems.length} approval{pendingItems.length !== 1 ? 's' : ''} pending review
          </p>
          <ul
            data-testid="risk-compliance-list"
            className="mt-1 space-y-1"
            aria-label="Pending compliance items"
          >
            {pendingItems.slice(0, 3).map((item) => (
              <li
                key={item.requestId}
                className="text-xs border border-red-200 rounded px-2 py-1 bg-red-50 text-red-700"
              >
                <span className="font-mono">{item.action}</span>
                <span className="ml-1 text-gray-800 truncate block max-w-xs">
                  {item.subjectId}
                </span>
              </li>
            ))}
            {pendingItems.length > 3 && (
              <li className="text-xs text-gray-700">
                +{pendingItems.length - 3} more
              </li>
            )}
          </ul>
        </>
      )}
    </DashboardWidgetCard>
  );
};
