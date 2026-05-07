/**
 * ReviewDecisionDialog — approve / reject HITL review with reason and
 * policy diff preview.
 *
 * @doc.type component
 * @doc.purpose Governed review decision dialog
 * @doc.layer frontend
 * @doc.pattern Dialog
 */
import React, { useRef, useEffect, useState } from 'react';
import { Button, TextArea } from '@ghatana/design-system';

export interface ReviewDecisionDialogProps {
  open: boolean;
  mode: 'approve' | 'reject';
  runId: string;
  policyDiff?: { before: string; after: string } | null;
  onConfirm: (note: string) => void;
  onCancel: () => void;
}

export function ReviewDecisionDialog({
  open,
  mode,
  runId,
  policyDiff,
  onConfirm,
  onCancel,
}: ReviewDecisionDialogProps): React.ReactElement | null {
  const dialogRef = useRef<HTMLDivElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const lastFocusRef = useRef<HTMLElement | null>(null);
  const [note, setNote] = useState('');

  useEffect(() => {
    if (!open) return;
    // Save currently focused element to restore on close
    lastFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;

    // Focus textarea when opened
    const timer = setTimeout(() => textareaRef.current?.focus(), 0);

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onCancel();
      }
      if (e.key === 'Tab') {
        const focusables = dialogRef.current?.querySelectorAll<HTMLElement>(
          'button, textarea, [href], input, select, [tabindex]:not([tabindex="-1"])'
        );
        if (!focusables || focusables.length === 0) return;
        const first = focusables[0];
        const last = focusables[focusables.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      clearTimeout(timer);
      document.removeEventListener('keydown', handleKeyDown);
      // Restore focus
      lastFocusRef.current?.focus();
    };
  }, [open, onCancel]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div
        ref={dialogRef}
        className="bg-white dark:bg-gray-900 rounded-xl shadow-xl max-w-lg w-full mx-4 overflow-hidden"
        role="dialog"
        aria-modal="true"
        aria-labelledby="review-decision-title"
      >
        <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <h2
            id="review-decision-title"
            className="text-sm font-semibold text-gray-900 dark:text-gray-100"
          >
            {mode === 'approve' ? 'Approve' : 'Reject'} review
          </h2>
          <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
            Run ID: <code className="font-mono">{runId}</code>
          </p>
        </div>

        <div className="px-6 py-4 space-y-4 max-h-[60vh] overflow-y-auto">
          {policyDiff && (
            <div className="rounded border border-gray-200 dark:border-gray-700 overflow-hidden">
              <div className="grid grid-cols-2 text-xs font-medium bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
                <div className="px-3 py-1.5">Before</div>
                <div className="px-3 py-1.5 border-l border-gray-200 dark:border-gray-700">After</div>
              </div>
              <div className="grid grid-cols-2 text-xs">
                <div className="px-3 py-2 text-gray-600 dark:text-gray-400 whitespace-pre-wrap">{policyDiff.before}</div>
                <div className="px-3 py-2 text-gray-900 dark:text-gray-100 whitespace-pre-wrap border-l border-gray-200 dark:border-gray-700">{policyDiff.after}</div>
              </div>
            </div>
          )}

          <div>
            <label
              htmlFor="review-note"
              className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1"
            >
              Note / reason
            </label>
            <TextArea
              id="review-note"
              ref={textareaRef}
              rows={3}
              className="w-full"
              placeholder={mode === 'approve' ? 'Why is this approved?' : 'Why is this rejected?'}
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
          </div>
        </div>

        <div className="flex justify-end gap-2 px-6 py-3 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-950">
          <Button
            ref={cancelRef}
            onClick={onCancel}
            variant="secondary"
            type="button"
          >
            Cancel
          </Button>
          <Button
            onClick={() => onConfirm(note.trim())}
            variant="primary"
            type="button"
            className={mode === 'reject' ? 'bg-red-600 hover:bg-red-700 text-white' : ''}
          >
            {mode === 'approve' ? 'Approve' : 'Reject'}
          </Button>
        </div>
      </div>
    </div>
  );
}
