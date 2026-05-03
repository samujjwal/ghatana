/**
 * Risk and compliance widget.
 *
 * @doc.type component
 * @doc.purpose Dashboard card for risk and compliance status
 * @doc.layer frontend
 */
import React from 'react';
import type { ApprovalRecordResponse } from '@/types/approval';

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

  return (
    <article
      aria-labelledby="risk-compliance-title"
      data-testid="risk-compliance-widget"
      className="border rounded-lg p-4"
    >
      <h2
        id="risk-compliance-title"
        className="text-sm font-semibold text-gray-700"
      >
        Risk & Compliance
      </h2>

      {isLoading && (
        <p
          data-testid="risk-compliance-loading"
          className="text-xs text-gray-400 mt-2"
        >
          Loading…
        </p>
      )}

      {isError && !isLoading && (
        <p
          data-testid="risk-compliance-error"
          className="text-xs text-red-500 mt-2"
        >
          Failed to load compliance data.
        </p>
      )}

      {!isLoading && !isError && !hasComplianceAlert && (
        <p
          data-testid="risk-compliance-ok"
          className="text-xs text-green-600 mt-2"
        >
          No active violations
        </p>
      )}

      {!isLoading && !isError && hasComplianceAlert && (
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
                <span className="ml-1 text-gray-600 truncate block max-w-xs">
                  {item.subjectId}
                </span>
              </li>
            ))}
            {pendingItems.length > 3 && (
              <li className="text-xs text-gray-400">
                +{pendingItems.length - 3} more
              </li>
            )}
          </ul>
        </>
      )}
    </article>
  );
};

