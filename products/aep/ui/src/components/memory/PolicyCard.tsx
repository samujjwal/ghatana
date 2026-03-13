/**
 * PolicyCard — displays a single learned procedural policy with confidence badge.
 *
 * Low-confidence or PENDING_REVIEW policies show a link to the HITL queue.
 *
 * @doc.type component
 * @doc.purpose Visualise a learned agent policy with status and confidence
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { Link } from 'react-router';
import type { LearnedPolicy } from '@/api/aep.api';
import { ConfidenceBadge } from '@/components/shared/ConfidenceBadge';

interface PolicyCardProps {
  policy: LearnedPolicy;
  /** Called when the user confirms approval. Only shown for PENDING_REVIEW policies. */
  onApprove?: (policyId: string) => void;
  /** Called when the user confirms rejection with a reason. Only shown for PENDING_REVIEW policies. */
  onReject?: (policyId: string, reason: string) => void;
  isSubmitting?: boolean;
  className?: string;
}

const STATUS_STYLES: Record<LearnedPolicy['status'], string> = {
  PENDING_REVIEW: 'text-yellow-700 bg-yellow-50 border-yellow-200',
  APPROVED: 'text-green-700 bg-green-50 border-green-200',
  REJECTED: 'text-red-700 bg-red-50 border-red-200',
  ACTIVE: 'text-blue-700 bg-blue-50 border-blue-200',
  DEPRECATED: 'text-gray-400 bg-gray-50 border-gray-200',
};

export function PolicyCard({ policy, onApprove, onReject, isSubmitting = false, className = '' }: PolicyCardProps) {
  const [rejectMode, setRejectMode] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const needsReview = policy.status === 'PENDING_REVIEW' || policy.confidenceScore < 0.7;
  const hasPendingActions = onApprove != null || onReject != null;

  return (
    <div
      className={[
        'rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-4 py-3 space-y-2',
        className,
      ].join(' ')}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-gray-800 dark:text-gray-200 truncate">
            {policy.name}
          </p>
          {policy.description && (
            <p className="text-xs text-gray-500 dark:text-gray-400 line-clamp-2 mt-0.5">
              {policy.description}
            </p>
          )}
        </div>
        <div className="flex flex-col items-end gap-1 flex-shrink-0">
          <span
            className={[
              'text-xs px-2 py-0.5 rounded-full border font-medium',
              STATUS_STYLES[policy.status],
            ].join(' ')}
          >
            {policy.status.replace('_', ' ')}
          </span>
          <ConfidenceBadge value={policy.confidenceScore} showValue />
        </div>
      </div>

      <div className="flex items-center gap-4 text-xs text-gray-400">
        <span>v{policy.version}</span>
        <span>{policy.episodeCount} episodes</span>
        <span>Updated {new Date(policy.updatedAt).toLocaleDateString()}</span>
      </div>

      {needsReview && !hasPendingActions && (
        <Link
          to="/hitl"
          className="inline-flex items-center gap-1 text-xs text-yellow-600 hover:text-yellow-700 font-medium"
        >
          ⚠ Needs human review →
        </Link>
      )}

      {policy.status === 'PENDING_REVIEW' && hasPendingActions && !rejectMode && (
        <div className="flex gap-2 pt-1 border-t border-gray-100 dark:border-gray-800">
          {onApprove && (
            <button
              type="button"
              onClick={() => onApprove(policy.id)}
              disabled={isSubmitting}
              className="flex-1 px-3 py-1 text-xs rounded bg-green-600 hover:bg-green-700 text-white disabled:opacity-50 transition-colors"
            >
              Approve
            </button>
          )}
          {onReject && (
            <button
              type="button"
              onClick={() => setRejectMode(true)}
              className="flex-1 px-3 py-1 text-xs rounded bg-red-600 hover:bg-red-700 text-white transition-colors"
            >
              Reject
            </button>
          )}
        </div>
      )}

      {rejectMode && onReject && (
        <div className="space-y-2 pt-1 border-t border-gray-100 dark:border-gray-800">
          <textarea
            className="block w-full rounded border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-xs"
            rows={2}
            placeholder="Rejection reason (required)…"
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
          />
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => { setRejectMode(false); setRejectReason(''); }}
              className="flex-1 px-3 py-1 text-xs rounded border border-gray-200 dark:border-gray-700"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={() => { onReject(policy.id, rejectReason); setRejectMode(false); setRejectReason(''); }}
              disabled={!rejectReason || isSubmitting}
              className="flex-1 px-3 py-1 text-xs rounded bg-red-600 hover:bg-red-700 text-white disabled:opacity-50"
            >
              {isSubmitting ? 'Rejecting…' : 'Confirm reject'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
