/**
 * Risk and compliance widget.
 *
 * @doc.type component
 * @doc.purpose Dashboard card for risk and compliance status
 * @doc.layer frontend
 */
import React from 'react';

interface RiskComplianceWidgetProps {
  isLoading?: boolean;
}

export const RiskComplianceWidget: React.FC<RiskComplianceWidgetProps> = ({
  isLoading = false,
}) => (
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

    {isLoading ? (
      <p
        data-testid="risk-compliance-loading"
        className="text-xs text-gray-400 mt-2"
      >
        Loading…
      </p>
    ) : (
      <p
        data-testid="risk-compliance-ok"
        className="text-xs text-green-600 mt-2"
      >
        No active violations
      </p>
    )}
  </article>
);
