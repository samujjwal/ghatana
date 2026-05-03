/**
 * Risk and compliance widget.
 *
 * @doc.type component
 * @doc.purpose Dashboard card for risk and compliance status
 * @doc.layer frontend
 */
import React from 'react';
import type { ApprovalRequest } from '@/types/approval';

const HIGH_RISK_THRESHOLD = 7;

interface RiskComplianceWidgetProps {
  approvals?: ApprovalRequest[];
  isLoading?: boolean;
  isError?: boolean;
}

export const RiskComplianceWidget: React.FC<RiskComplianceWidgetProps> = ({
  approvals = [],
  isLoading = false,
  isError = false,
}) => {
  const highRiskItems = approvals.filter(
    (a) => a.status === 'PENDING' && a.riskLevel >= HIGH_RISK_THRESHOLD,
  );

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

      {!isLoading && !isError && highRiskItems.length === 0 && (
        <p
          data-testid="risk-compliance-ok"
          className="text-xs text-green-600 mt-2"
        >
          No active violations
        </p>
      )}

      {!isLoading && !isError && highRiskItems.length > 0 && (
        <>
          <p
            data-testid="risk-compliance-alert"
            className="text-xs text-red-600 font-medium mt-2"
          >
            {highRiskItems.length} high-risk item
            {highRiskItems.length !== 1 ? 's' : ''} pending review
          </p>
          <ul
            data-testid="risk-compliance-list"
            className="mt-1 space-y-1"
            aria-label="High-risk compliance items"
          >
            {highRiskItems.slice(0, 3).map((item) => (
              <li
                key={item.requestId}
                className="text-xs border border-red-200 rounded px-2 py-1 bg-red-50 text-red-700"
              >
                <span className="font-medium">Risk {item.riskLevel}/10</span>
                {' — '}
                <span>{item.targetType}</span>
                {item.description && (
                  <span className="ml-1 text-gray-600 truncate block max-w-xs">
                    {item.description}
                  </span>
                )}
              </li>
            ))}
            {highRiskItems.length > 3 && (
              <li className="text-xs text-gray-400">
                +{highRiskItems.length - 3} more
              </li>
            )}
          </ul>
        </>
      )}
    </article>
  );
};

