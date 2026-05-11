/**
 * AILineagePanel Module
 *
 * @doc.type component
 * @doc.purpose Display AI action lineage and pending review decisions
 * @doc.layer product
 * @doc.pattern Widget
 */

import React from 'react';
import { Bot, Check, X, Undo2 } from 'lucide-react';
import { Button } from '@ghatana/design-system';
import { Typography } from '@ghatana/design-system';
import type { AIActionLineage } from '../pageArtifactDocument';

export interface AILineagePanelProps {
  readonly pendingActions: readonly AIActionLineage[];
  readonly onReviewDecision: (actionId: string, decision: 'accepted' | 'rejected') => void;
  readonly canEdit: boolean;
}

export function AILineagePanel({
  pendingActions,
  onReviewDecision,
  canEdit,
}: AILineagePanelProps): React.JSX.Element | null {
  if (pendingActions.length === 0) {
    return null;
  }

  return (
    <div className="rounded-lg border border-blue-200 bg-blue-50 p-4 dark:border-blue-900/50 dark:bg-blue-950/20">
      <div className="mb-3 flex items-center gap-2">
        <Bot className="h-5 w-5 text-blue-600 dark:text-blue-400" />
        <Typography variant="h3" className="text-base font-semibold text-blue-800 dark:text-blue-200">
          AI Actions Pending Review
        </Typography>
      </div>

      <p className="mb-4 text-sm text-blue-700 dark:text-blue-300">
        {pendingActions.length} AI-generated action{pendingActions.length !== 1 ? 's' : ''} require review before trust promotion.
      </p>

      <div className="space-y-3">
        {pendingActions.map((action) => (
          <div
            key={action.actionId}
            className="rounded border border-blue-200 bg-white p-3 dark:border-blue-900/50 dark:bg-gray-900"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex-1">
                <Typography variant="body1" className="font-medium">
                  {action.hookKind}
                </Typography>

                <div className="mt-1 text-xs text-gray-600 dark:text-gray-400">
                  <div>Action ID: {action.actionId}</div>
                  <div>Confidence: {(action.confidence * 100).toFixed(1)}%</div>
                  <div>{action.reason}</div>
                  {action.evidence && action.evidence.length > 0 && (
                    <div>
                      Evidence: {action.evidence.join(', ')}
                    </div>
                  )}
                </div>
              </div>

              <div className="flex gap-2">
                {canEdit && (
                  <>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => onReviewDecision(action.actionId, 'accepted')}
                      className="flex items-center gap-1"
                    >
                      <Check className="h-3 w-3" />
                      Accept
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => onReviewDecision(action.actionId, 'rejected')}
                      className="flex items-center gap-1"
                    >
                      <X className="h-3 w-3" />
                      Reject
                    </Button>
                  </>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
