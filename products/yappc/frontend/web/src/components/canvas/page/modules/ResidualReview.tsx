/**
 * ResidualReview Module
 *
 * @doc.type component
 * @doc.purpose Review residual islands from decompiled imports
 * @doc.layer product
 * @doc.pattern Widget
 */

import React from 'react';
import { AlertTriangle, Check, X, ChevronRight } from 'lucide-react';
import { Button } from '@ghatana/design-system';
import { Typography } from '@ghatana/design-system';

export type ImportReviewDecision = 'applied' | 'skipped' | 'promoted';

export interface ImportReviewQueueItem {
  readonly id: string;
  readonly kind: 'loss-point' | 'residual-island';
  readonly label: string;
  readonly details: string;
  readonly sourceEvidence: string;
  readonly governedEvidence: string;
  readonly reviewImpact: string;
}

export interface ResidualReviewProps {
  readonly reviewQueue: readonly ImportReviewQueueItem[];
  readonly reviewDecisions: Readonly<Record<string, ImportReviewDecision>>;
  readonly reviewingId: string | null;
  readonly promotingId: string | null;
  readonly onAccept: (residualId: string) => Promise<void>;
  readonly onReject: (residualId: string) => Promise<void>;
  readonly onPromote: (residualId: string) => Promise<void>;
  readonly onDecision: (item: ImportReviewQueueItem, decision: ImportReviewDecision) => Promise<void>;
}

export function ResidualReview({
  reviewQueue,
  reviewDecisions,
  reviewingId,
  promotingId,
  onAccept,
  onReject,
  onPromote,
  onDecision,
}: ResidualReviewProps): React.JSX.Element | null {
  if (reviewQueue.length === 0) {
    return null;
  }

  return (
    <div className="rounded-lg border border-yellow-200 bg-yellow-50 p-4 dark:border-yellow-900/50 dark:bg-yellow-950/20">
      <div className="mb-3 flex items-center gap-2">
        <AlertTriangle className="h-5 w-5 text-yellow-600 dark:text-yellow-400" />
        <Typography variant="h3" className="text-base font-semibold text-yellow-800 dark:text-yellow-200">
          Import Review Required
        </Typography>
      </div>

      <p className="mb-4 text-sm text-yellow-700 dark:text-yellow-300">
        The decompiled import contains {reviewQueue.length} item{reviewQueue.length !== 1 ? 's' : ''} that require review before handoff.
      </p>

      <div className="space-y-3">
        {reviewQueue.map((item) => {
          const decision = reviewDecisions[item.id];
          const isReviewing = reviewingId === item.id;
          const isPromoting = promotingId === item.id;

          return (
            <div
              key={item.id}
              className={`rounded border p-3 ${
                decision === 'applied' || decision === 'promoted'
                  ? 'border-green-200 bg-green-50 dark:border-green-900/50 dark:bg-green-950/20'
                  : decision === 'skipped'
                    ? 'border-gray-200 bg-gray-50 dark:border-gray-900/50 dark:bg-gray-950/20'
                    : 'border-yellow-200 bg-white dark:border-yellow-900/50 dark:bg-gray-900'
              }`}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <Typography variant="body1" className="font-medium">
                      {item.label}
                    </Typography>
                    {item.kind === 'loss-point' && (
                      <span className="rounded-full bg-yellow-100 px-2 py-0.5 text-xs font-medium text-yellow-800 dark:bg-yellow-900/50 dark:text-yellow-200">
                        Loss Point
                      </span>
                    )}
                    {item.kind === 'residual-island' && (
                      <span className="rounded-full bg-orange-100 px-2 py-0.5 text-xs font-medium text-orange-800 dark:bg-orange-900/50 dark:text-orange-200">
                        Residual Island
                      </span>
                    )}
                  </div>

                  <p className="mt-1 text-sm text-gray-600 dark:text-gray-400">{item.details}</p>

                  <details className="mt-2">
                    <summary className="cursor-pointer text-xs font-medium text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300">
                      <span className="flex items-center gap-1">
                        View evidence
                        <ChevronRight className="h-3 w-3" />
                      </span>
                    </summary>
                    <div className="mt-2 space-y-1 text-xs text-gray-600 dark:text-gray-400">
                      <div>
                        <strong>Source:</strong> {item.sourceEvidence}
                      </div>
                      <div>
                        <strong>Builder Impact:</strong> {item.governedEvidence}
                      </div>
                      <div>
                        <strong>Review Impact:</strong> {item.reviewImpact}
                      </div>
                    </div>
                  </details>
                </div>

                <div className="flex gap-2">
                  {!decision && item.kind === 'residual-island' && (
                    <>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => onAccept(item.id)}
                        disabled={isReviewing || isPromoting}
                        className="flex items-center gap-1"
                      >
                        {isReviewing ? (
                          '...'
                        ) : (
                          <>
                            <Check className="h-3 w-3" />
                            Accept
                          </>
                        )}
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => onReject(item.id)}
                        disabled={isReviewing || isPromoting}
                        className="flex items-center gap-1"
                      >
                        {isReviewing ? (
                          '...'
                        ) : (
                          <>
                            <X className="h-3 w-3" />
                            Reject
                          </>
                        )}
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => onPromote(item.id)}
                        disabled={isPromoting}
                        className="flex items-center gap-1"
                      >
                        {isPromoting ? (
                          '...'
                        ) : (
                          'Promote'
                        )}
                      </Button>
                    </>
                  )}

                  {!decision && item.kind === 'loss-point' && (
                    <>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => onDecision(item, 'applied')}
                        disabled={isReviewing}
                        className="flex items-center gap-1"
                      >
                        Apply
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => onDecision(item, 'skipped')}
                        disabled={isReviewing}
                        className="flex items-center gap-1"
                      >
                        Skip
                      </Button>
                    </>
                  )}

                  {decision === 'applied' && (
                    <span className="flex items-center gap-1 text-sm font-medium text-green-700 dark:text-green-300">
                      <Check className="h-4 w-4" />
                      Applied
                    </span>
                  )}
                  {decision === 'skipped' && (
                    <span className="flex items-center gap-1 text-sm font-medium text-gray-700 dark:text-gray-300">
                      <X className="h-4 w-4" />
                      Skipped
                    </span>
                  )}
                  {decision === 'promoted' && (
                    <span className="flex items-center gap-1 text-sm font-medium text-purple-700 dark:text-purple-300">
                      <ChevronRight className="h-4 w-4" />
                      Promoted
                    </span>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
