/**
 * @fileoverview ReviewRequiredBanner - Action requires human review with approve/reject controls.
 *
 * @doc.type component
 * @doc.purpose Banner for AI suggestions requiring human approval before application.
 * @doc.category molecule
 * @doc.tags ai, review, approval, visibility
 */

import * as React from 'react';
import type { AIVisibilityContract, AIChangeDescriptor } from '@ghatana/platform-events';
import { AILabel } from '../atoms/AILabel';
import { ConfidenceBadge, ConfidenceRange } from '../atoms/ConfidenceBadge';

export interface ReviewRequiredBannerProps {
  /** The AI visibility contract for the suggestion */
  readonly visibilityContract: AIVisibilityContract;
  /** Callback when user approves the suggestion */
  readonly onApprove: () => void;
  /** Callback when user rejects the suggestion */
  readonly onReject: () => void;
  /** Callback when user wants to modify before applying */
  readonly onModify?: () => void;
  /** Additional CSS classes */
  readonly className?: string;
  /** Whether the review is pending (default) or already decided */
  readonly status?: 'pending' | 'approved' | 'rejected';
}

/**
 * ReviewRequiredBanner component - displays AI suggestion requiring review.
 */
export const ReviewRequiredBanner: React.FC<ReviewRequiredBannerProps> = React.memo(({
  visibilityContract,
  onApprove,
  onReject,
  onModify,
  className = '',
  status = 'pending',
}) => {
  const { operationLabel, suggestedChanges, confidenceBand, rationale, triggeredBy } = visibilityContract;
  const changeCount = suggestedChanges.length;

  return (
    <div
      className={`rounded-lg border-2 ${status === 'pending' ? 'border-purple-300 bg-purple-50' : status === 'approved' ? 'border-green-300 bg-green-50' : 'border-red-300 bg-red-50'} p-4 ${className}`}
      role="alert"
      aria-live="polite"
    >
      {/* Header */}
      <div className="flex items-start gap-3">
        <div className="flex-shrink-0 mt-0.5">
          {status === 'pending' ? (
            <div className="h-6 w-6 rounded-full bg-purple-100 flex items-center justify-center">
              <svg className="h-4 w-4 text-purple-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
          ) : status === 'approved' ? (
            <div className="h-6 w-6 rounded-full bg-green-100 flex items-center justify-center">
              <svg className="h-4 w-4 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
          ) : (
            <div className="h-6 w-6 rounded-full bg-red-100 flex items-center justify-center">
              <svg className="h-4 w-4 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </div>
          )}
        </div>

        <div className="flex-1 min-w-0">
          {/* Title row */}
          <div className="flex items-center gap-2 flex-wrap">
            <h3 className="text-sm font-semibold text-gray-900">
              {status === 'pending' ? 'Review Required' : status === 'approved' ? 'Approved' : 'Rejected'}
            </h3>
            <AILabel isSuggestion={status === 'pending'} size="sm" />
            {triggeredBy === 'implicit' && (
              <span className="text-xs text-gray-500 bg-gray-100 px-1.5 py-0.5 rounded">Auto-suggested</span>
            )}
          </div>

          {/* Operation label */}
          <p className="text-sm text-gray-700 mt-1">{operationLabel}</p>

          {/* Rationale */}
          {rationale && (
            <p className="text-xs text-gray-500 mt-1 italic">{rationale}</p>
          )}

          {/* Confidence */}
          <div className="flex items-center gap-2 mt-2">
            <span className="text-xs text-gray-500">Confidence:</span>
            <ConfidenceRange low={confidenceBand.low} high={confidenceBand.high} size="sm" />
          </div>

          {/* Changes summary */}
          {changeCount > 0 && (
            <p className="text-xs text-gray-600 mt-2">
              {changeCount} change{changeCount !== 1 ? 's' : ''} proposed
            </p>
          )}
        </div>
      </div>

      {/* Action buttons */}
      {status === 'pending' && (
        <div className="flex items-center gap-2 mt-4">
          <button
            onClick={onApprove}
            className="inline-flex items-center px-3 py-1.5 text-sm font-medium text-white bg-green-600 hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-green-500 rounded"
            type="button"
          >
            <svg className="h-4 w-4 mr-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
            Approve
          </button>
          <button
            onClick={onReject}
            className="inline-flex items-center px-3 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-300 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-gray-500 rounded"
            type="button"
          >
            <svg className="h-4 w-4 mr-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
            Reject
          </button>
          {onModify && (
            <button
              onClick={onModify}
              className="inline-flex items-center px-3 py-1.5 text-sm font-medium text-purple-700 bg-purple-50 hover:bg-purple-100 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-purple-500 rounded"
              type="button"
            >
              Modify
            </button>
          )}
        </div>
      )}
    </div>
  );
});

ReviewRequiredBanner.displayName = 'ReviewRequiredBanner';
