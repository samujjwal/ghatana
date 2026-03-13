/**
 * ApproveRejectPanel — action panel for approving or rejecting an HITL item.
 *
 * Approve → optional note. Reject → mandatory reason.
 *
 * @doc.type component
 * @doc.purpose Human review action controls for HITL queue items
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import type { ReviewItem } from '@/types/hitl.types';

interface ApproveRejectPanelProps {
  item: ReviewItem;
  onApprove: (reviewId: string, note: string) => void;
  onReject: (reviewId: string, reason: string) => void;
  isSubmitting?: boolean;
}

export function ApproveRejectPanel({
  item,
  onApprove,
  onReject,
  isSubmitting = false,
}: ApproveRejectPanelProps) {
  const [note, setNote] = useState('');
  const [reason, setReason] = useState('');
  const [mode, setMode] = useState<'idle' | 'approving' | 'rejecting'>('idle');

  if (item.status !== 'PENDING') {
    return (
      <p className="text-sm text-gray-500 dark:text-gray-400 italic">
        This item has already been {item.status.toLowerCase()}.
        {item.reviewerNote && (
          <span className="block mt-1 not-italic text-gray-600 dark:text-gray-300">
            Note: {item.reviewerNote}
          </span>
        )}
      </p>
    );
  }

  function handleApprove() {
    if (mode === 'approving') {
      onApprove(item.reviewId, note);
    } else {
      setMode('approving');
    }
  }

  function handleReject() {
    if (mode === 'rejecting') {
      if (!reason.trim()) return;
      onReject(item.reviewId, reason);
    } else {
      setMode('rejecting');
    }
  }

  return (
    <div className="space-y-3">
      {mode === 'approving' && (
        <div>
          <label className="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">
            Review note (optional)
          </label>
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            rows={2}
            placeholder="Add an optional note…"
            className="w-full text-sm rounded border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-1.5 outline-none focus:ring-1 focus:ring-green-400 resize-none"
          />
        </div>
      )}

      {mode === 'rejecting' && (
        <div>
          <label className="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">
            Rejection reason <span className="text-red-500">*</span>
          </label>
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={2}
            placeholder="Describe why this is being rejected…"
            className="w-full text-sm rounded border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-1.5 outline-none focus:ring-1 focus:ring-red-400 resize-none"
          />
        </div>
      )}

      <div className="flex gap-2">
        <button
          onClick={handleApprove}
          disabled={isSubmitting}
          className="flex-1 rounded px-3 py-1.5 text-sm font-medium bg-green-600 text-white hover:bg-green-700 disabled:opacity-50 transition-colors"
        >
          {mode === 'approving' ? '✓ Confirm Approve' : 'Approve'}
        </button>
        <button
          onClick={handleReject}
          disabled={isSubmitting || (mode === 'rejecting' && !reason.trim())}
          className="flex-1 rounded px-3 py-1.5 text-sm font-medium bg-red-600 text-white hover:bg-red-700 disabled:opacity-50 transition-colors"
        >
          {mode === 'rejecting' ? '✗ Confirm Reject' : 'Reject'}
        </button>
        {mode !== 'idle' && (
          <button
            onClick={() => setMode('idle')}
            className="rounded px-3 py-1.5 text-sm text-gray-500 hover:text-gray-700 border border-gray-300 dark:border-gray-600"
          >
            Cancel
          </button>
        )}
      </div>
    </div>
  );
}
