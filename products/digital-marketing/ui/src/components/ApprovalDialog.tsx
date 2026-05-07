/**
 * ApprovalDialog - Governance-grade approval confirmation modal.
 *
 * P1-006: All approval actions (strategy + budget) must route through this
 * dialog to enforce snapshot review, role awareness, audit comment capture,
 * and explicit confirmation before submitting.
 *
 * @doc.type component
 * @doc.purpose Governance approval dialog with snapshot review and audit comment
 * @doc.layer frontend
 */
import React, { useState, useCallback, useId } from 'react';
import { useAuth } from '@/context/AuthContext';

export interface ApprovalDialogProps {
  /** What is being approved */
  entityLabel: string;
  /** Unique ID of the entity being approved */
  entityId: string;
  /** Snapshot summary lines shown for review before approval */
  snapshotLines: string[];
  /** Whether a confirm action is in flight */
  isPending: boolean;
  /** Called when user confirms approval with an audit comment */
  onConfirm: (comment: string) => void;
  /** Called when user cancels */
  onCancel: () => void;
}

/**
 * Renders a blocking modal that forces the approver to:
 * 1. Review the entity snapshot
 * 2. See their role / identity
 * 3. Write an audit comment (required)
 * 4. Click an explicit "Confirm Approval" button
 */
export function ApprovalDialog({
  entityLabel,
  entityId,
  snapshotLines,
  isPending,
  onConfirm,
  onCancel,
}: ApprovalDialogProps): React.ReactElement {
  const { principalId, roles } = useAuth();
  const [comment, setComment] = useState('');
  const [confirmChecked, setConfirmChecked] = useState(false);
  const commentId = useId();
  const checkId = useId();

  const handleConfirm = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();
      if (!comment.trim() || !confirmChecked) return;
      onConfirm(comment.trim());
    },
    [comment, confirmChecked, onConfirm],
  );

  return (
    /* Overlay */
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="approval-dialog-title"
      data-testid="approval-dialog"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
    >
      <div className="bg-white rounded-lg shadow-xl w-full max-w-lg mx-4 p-6">
        <h2 id="approval-dialog-title" className="text-lg font-bold mb-1">
          Approve {entityLabel}
        </h2>
        <p className="text-xs text-gray-500 mb-4">
          ID: <code>{entityId}</code>
        </p>

        {/* Snapshot review */}
        <section className="mb-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-2">Snapshot Review</h3>
          <ul
            data-testid="approval-snapshot"
            className="text-sm bg-gray-50 border rounded p-3 space-y-1 text-gray-700"
          >
            {snapshotLines.map((line, idx) => (
              <li key={idx}>{line}</li>
            ))}
          </ul>
        </section>

        {/* Approver identity */}
        <section className="mb-4 text-sm text-gray-600">
          <span className="font-medium">Approving as: </span>
          <span data-testid="approval-approver-identity">
            {principalId ?? 'Unknown user'}
          </span>
          {roles && roles.length > 0 && (
            <span className="ml-2 text-xs text-gray-400">
              ({roles.join(', ')})
            </span>
          )}
        </section>

        <form onSubmit={handleConfirm}>
          {/* Audit comment — required */}
          <label htmlFor={commentId} className="block text-sm font-medium mb-1">
            Audit comment <span className="text-red-500">*</span>
          </label>
          <textarea
            id={commentId}
            data-testid="approval-comment-input"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            rows={3}
            required
            placeholder="Describe why you are approving this…"
            className="w-full border rounded px-2 py-1 text-sm mb-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />

          {/* Explicit acknowledgement */}
          <label htmlFor={checkId} className="flex items-start gap-2 text-sm mb-4 cursor-pointer">
            <input
              id={checkId}
              data-testid="approval-confirm-checkbox"
              type="checkbox"
              checked={confirmChecked}
              onChange={(e) => setConfirmChecked(e.target.checked)}
              className="mt-0.5"
            />
            <span>
              I confirm I have reviewed the snapshot above and accept responsibility for this approval.
            </span>
          </label>

          <div className="flex justify-end gap-2">
            <button
              type="button"
              data-testid="approval-cancel-btn"
              onClick={onCancel}
              disabled={isPending}
              className="px-4 py-1.5 border rounded text-sm text-gray-700 disabled:opacity-50 hover:bg-gray-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              data-testid="approval-confirm-btn"
              disabled={isPending || !comment.trim() || !confirmChecked}
              className="px-4 py-1.5 bg-green-600 text-white rounded text-sm disabled:opacity-50"
            >
              {isPending ? 'Approving…' : 'Confirm Approval'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
