/**
 * Governance Policy Recommendations Component
 *
 * Displays AI-generated governance policy recommendations with rationale,
 * impacted collections, confidence indicators, and human approval gates.
 * AI/ML #8 frontend scaffolding.
 *
 * @doc.type component
 * @doc.purpose Governance policy recommendations with approval gates
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { Shield, CheckCircle2, XCircle, Database } from 'lucide-react';
import { cn, cardStyles, textStyles } from '../../lib/theme';
import { AIConfidenceIndicator } from './AIConfidenceIndicator';
import type { AIConfidence } from './AIConfidenceIndicator';

interface PolicyRecommendation {
  id: string;
  policyName: string;
  rationale: string;
  impactedCollections: string[];
  confidence: AIConfidence;
  severity: 'critical' | 'high' | 'medium';
}

interface GovernancePolicyRecommendationsProps {
  recommendations: PolicyRecommendation[];
  onApprove: (id: string) => void;
  onDismiss: (id: string) => void;
  className?: string;
}

/**
 * Governance Policy Recommendations Panel
 */
export function GovernancePolicyRecommendations({
  recommendations,
  onApprove,
  onDismiss,
  className,
}: GovernancePolicyRecommendationsProps): React.ReactElement {
  const [confirmingId, setConfirmingId] = useState<string | null>(null);

  if (recommendations.length === 0) {
    return (
      <div className={cn(cardStyles.base, cardStyles.padded, className)} data-testid="policy-recommendations-empty">
        <div className="flex items-center gap-2 text-gray-500">
          <Shield className="h-4 w-4" />
          <p className="text-sm">No pending governance policy recommendations.</p>
        </div>
      </div>
    );
  }

  return (
    <div className={cn(cardStyles.base, className)} data-testid="policy-recommendations-panel">
      <div className={cn(cardStyles.header, 'flex items-center gap-2')}>
        <Shield className="h-5 w-5 text-amber-500" />
        <h3 className={textStyles.h4}>Policy Recommendations</h3>
      </div>

      <div className="p-4 space-y-4">
        {recommendations.map((rec) => (
          <div
            key={rec.id}
            className={cn(
              'rounded-lg border p-4 space-y-3',
              rec.severity === 'critical'
                ? 'border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/10'
                : rec.severity === 'high'
                  ? 'border-amber-200 dark:border-amber-800 bg-amber-50 dark:bg-amber-900/10'
                  : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800'
            )}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex-1">
                <h4 className="text-sm font-semibold text-gray-900 dark:text-gray-100">{rec.policyName}</h4>
                <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">{rec.rationale}</p>
              </div>
              <AIConfidenceIndicator confidence={rec.confidence} />
            </div>

            {rec.impactedCollections.length > 0 && (
              <div className="flex items-start gap-2">
                <Database className="h-4 w-4 text-gray-400 mt-0.5" />
                <div className="flex-1">
                  <p className="text-xs text-gray-500 mb-1">Impacted collections:</p>
                  <div className="flex flex-wrap gap-1.5">
                    {rec.impactedCollections.map((col) => (
                      <span
                        key={col}
                        className="px-2 py-0.5 text-xs bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded"
                      >
                        {col}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            )}

            <div className="flex items-center gap-2 pt-2">
              {confirmingId === rec.id ? (
                <>
                  <span className="text-xs text-gray-500 mr-1">Confirm approval?</span>
                  <button
                    onClick={() => {
                      onApprove(rec.id);
                      setConfirmingId(null);
                    }}
                    className="flex items-center gap-1 px-3 py-1.5 text-xs bg-green-600 hover:bg-green-700 text-white rounded-lg transition-colors"
                    data-testid={`policy-approve-${rec.id}`}
                  >
                    <CheckCircle2 className="h-3.5 w-3.5" />
                    Approve
                  </button>
                  <button
                    onClick={() => setConfirmingId(null)}
                    className="px-3 py-1.5 text-xs text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700 rounded-lg transition-colors"
                  >
                    Cancel
                  </button>
                </>
              ) : (
                <>
                  <button
                    onClick={() => setConfirmingId(rec.id)}
                    className="flex items-center gap-1 px-3 py-1.5 text-xs bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors"
                  >
                    <CheckCircle2 className="h-3.5 w-3.5" />
                    Approve
                  </button>
                  <button
                    onClick={() => onDismiss(rec.id)}
                    className="flex items-center gap-1 px-3 py-1.5 text-xs text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors"
                    data-testid={`policy-dismiss-${rec.id}`}
                  >
                    <XCircle className="h-3.5 w-3.5" />
                    Dismiss
                  </button>
                </>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default GovernancePolicyRecommendations;
