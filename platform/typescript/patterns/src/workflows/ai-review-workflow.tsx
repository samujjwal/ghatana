/**
 * @fileoverview AI Review Workflow - Standardized AI suggestion review flow.
 *
 * @doc.type composition
 * @doc.purpose Reusable AI suggestion review workflow with approve/reject/modify.
 * @doc.category pattern
 */

import * as React from 'react';
import type { AIVisibilityContract } from '@ghatana/platform-events';

export interface AIReviewWorkflowProps {
  readonly visibilityContract: AIVisibilityContract;
  readonly onApprove: () => void;
  readonly onReject: () => void;
  readonly onModify: () => void;
  readonly children: React.ReactNode;
}

export const AIReviewWorkflow: React.FC<AIReviewWorkflowProps> = ({
  visibilityContract,
  onApprove,
  onReject,
  onModify,
  children,
}) => {
  return (
    <div className="ai-review-workflow">
      <div className="ai-review-header">
        <span>AI Suggestion: {visibilityContract.operationLabel}</span>
        <span>Confidence: {visibilityContract.confidenceBand.low}-{visibilityContract.confidenceBand.high}</span>
      </div>
      <div className="ai-review-content">{children}</div>
      <div className="ai-review-actions">
        <button onClick={onApprove}>Approve</button>
        <button onClick={onReject}>Reject</button>
        <button onClick={onModify}>Modify</button>
      </div>
    </div>
  );
};
