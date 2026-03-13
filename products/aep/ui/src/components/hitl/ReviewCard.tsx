/**
 * ReviewCard — displays a single HITL review item card in the queue.
 *
 * @doc.type component
 * @doc.purpose Show a policy/pattern candidate that requires human review
 * @doc.layer frontend
 */
import React from 'react';
import type { ReviewItem } from '@/types/hitl.types';
import { ConfidenceBadge } from '@/components/shared/ConfidenceBadge';

interface ReviewCardProps {
  item: ReviewItem;
  isSelected: boolean;
  onClick: () => void;
}

const TYPE_LABELS: Record<ReviewItem['itemType'], string> = {
  POLICY: 'Policy Candidate',
  PATTERN: 'Pattern Candidate',
  AGENT_DECISION: 'Agent Decision',
};

const STATUS_STYLES: Record<ReviewItem['status'], string> = {
  PENDING: 'text-yellow-700 bg-yellow-50 border-yellow-200 dark:text-yellow-300 dark:bg-yellow-950',
  APPROVED: 'text-green-700 bg-green-50 border-green-200 dark:text-green-300 dark:bg-green-950',
  REJECTED: 'text-red-700 bg-red-50 border-red-200 dark:text-red-300 dark:bg-red-950',
};

function elapsed(iso: string) {
  const diffMs = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diffMs / 60_000);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

export function ReviewCard({ item, isSelected, onClick }: ReviewCardProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={[
        'w-full text-left rounded-lg border p-4 transition-colors',
        isSelected
          ? 'border-indigo-400 bg-indigo-50 dark:border-indigo-700 dark:bg-indigo-950'
          : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 hover:border-gray-300 dark:hover:border-gray-600',
      ].join(' ')}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-gray-800 dark:text-gray-200 truncate">
            {TYPE_LABELS[item.itemType]}{' '}
            <span className="font-mono text-xs text-gray-400">#{item.reviewId.slice(-6)}</span>
          </p>
          <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5 font-mono truncate">
            {item.skillId}
          </p>
        </div>
        <div className="flex flex-col items-end gap-1 flex-shrink-0">
          <span
            className={[
              'text-xs px-2 py-0.5 rounded-full border font-medium',
              STATUS_STYLES[item.status],
            ].join(' ')}
          >
            {item.status}
          </span>
          <span className="text-xs text-gray-400">{elapsed(item.createdAt)}</span>
        </div>
      </div>
      {item.confidenceScore !== undefined && (
        <div className="mt-2">
          <ConfidenceBadge value={item.confidenceScore} />
        </div>
      )}
    </button>
  );
}
