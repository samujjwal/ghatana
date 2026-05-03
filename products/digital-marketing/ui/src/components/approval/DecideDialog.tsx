/**
 * Decide dialog — approve or reject an approval request.
 *
 * @doc.type component
 * @doc.purpose Modal for submitting an approval decision with optional comment
 * @doc.layer frontend
 */
import React, { useCallback, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { decideApproval } from '@/api/approvals';
import type { ApprovalDecision } from '@/types/approval';

interface DecideDialogProps {
  workspaceId: string;
  requestId: string;
  requireComment: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export const DecideDialog: React.FC<DecideDialogProps> = ({
  workspaceId,
  requestId,
  requireComment,
  onClose,
  onSuccess,
}) => {
  const [decision, setDecision] = useState<ApprovalDecision>('APPROVE');
  const [comment, setComment] = useState('');
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () =>
      decideApproval(workspaceId, requestId, {
        decision,
        notes: comment.trim() || undefined,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['approvals'],
      });
      onSuccess();
    },
  });

  const canSubmit = !requireComment || comment.trim().length > 0;

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();
      if (canSubmit) {
        mutation.mutate();
      }
    },
    [canSubmit, mutation],
  );

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="decide-dialog-title"
      data-testid="decide-dialog"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
    >
      <div className="bg-white rounded-lg shadow-xl p-6 w-full max-w-md">
        <h2 id="decide-dialog-title" className="text-lg font-semibold mb-4">
          Submit Decision
        </h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <fieldset>
            <legend className="text-sm font-medium text-gray-700 mb-1">
              Decision
            </legend>
            <div className="flex gap-4">
              <label className="flex items-center gap-1 text-sm">
                <input
                  type="radio"
                  name="decision"
                  value="APPROVE"
                  checked={decision === 'APPROVE'}
                  onChange={() => setDecision('APPROVE')}
                  data-testid="decision-approve"
                />
                Approve
              </label>
              <label className="flex items-center gap-1 text-sm">
                <input
                  type="radio"
                  name="decision"
                  value="REJECT"
                  checked={decision === 'REJECT'}
                  onChange={() => setDecision('REJECT')}
                  data-testid="decision-reject"
                />
                Reject
              </label>
            </div>
          </fieldset>

          <div>
            <label
              htmlFor="comment"
              className="block text-sm font-medium text-gray-700 mb-1"
            >
              Comment{requireComment ? ' (required)' : ' (optional)'}
            </label>
            <textarea
              id="comment"
              data-testid="decide-comment"
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              rows={3}
              className="w-full border rounded px-2 py-1 text-sm"
              aria-required={requireComment}
            />
          </div>

          {mutation.isError && (
            <p
              data-testid="decide-error"
              role="alert"
              className="text-red-600 text-sm"
            >
              {mutation.error instanceof Error
                ? mutation.error.message
                : 'Failed to submit decision.'}
            </p>
          )}

          <div className="flex justify-end gap-2">
            <button
              type="button"
              data-testid="decide-cancel"
              onClick={onClose}
              className="px-4 py-2 text-sm border rounded hover:bg-gray-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              data-testid="decide-submit"
              disabled={!canSubmit || mutation.isPending}
              className="px-4 py-2 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
            >
              {mutation.isPending ? 'Submitting…' : 'Submit'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
