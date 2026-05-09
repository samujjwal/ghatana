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
import { ApiError } from '@/lib/http-client';
import type { ApprovalDecision } from '@/types/approval';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Select,
  TextArea,
} from '@ghatana/design-system';

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
    mutationFn: () => {
      // P1-022: Generate idempotency key at mutation start
      const idempotencyKey = crypto.randomUUID();
      return decideApproval(workspaceId, requestId, {
        decision,
        notes: comment.trim() || undefined,
      }, idempotencyKey);
    },
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
    <Dialog
      open
      onClose={onClose}
      data-testid="decide-dialog"
      size="sm"
    >
      <DialogTitle id="decide-dialog-title" className="text-lg font-semibold">
        Submit Decision
      </DialogTitle>

      <DialogContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <Select
            data-testid="decision-select"
            label="Decision"
            value={decision}
            onChange={(e) => setDecision(e.target.value as ApprovalDecision)}
            options={[
              { value: 'APPROVE', label: 'Approve' },
              { value: 'REJECT', label: 'Reject' },
            ]}
            fullWidth
          />

          <TextArea
            id="comment"
            data-testid="decide-comment"
            label={`Comment${requireComment ? ' (required)' : ' (optional)'}`}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            rows={3}
            aria-required={requireComment}
            errorMessage={requireComment && !canSubmit ? 'Comment is required for high-risk decisions.' : undefined}
          />

          {mutation.isError && (
            <p
              data-testid="decide-error"
              role="alert"
              className="text-red-600 text-sm"
            >
              {mutation.error instanceof ApiError
                ? mutation.error.getUserMessage()
                : 'Failed to submit decision.'}
            </p>
          )}

          <DialogActions>
            <Button
              type="button"
              data-testid="decide-cancel"
              onClick={onClose}
              tone="neutral"
              variant="outline"
              size="sm"
            >
              Cancel
            </Button>
            <Button
              type="submit"
              data-testid="decide-submit"
              disabled={!canSubmit || mutation.isPending}
              tone="primary"
              size="sm"
              loading={mutation.isPending}
              loadingText="Submitting..."
            >
              Submit
            </Button>
          </DialogActions>
        </form>
      </DialogContent>
    </Dialog>
  );
};
