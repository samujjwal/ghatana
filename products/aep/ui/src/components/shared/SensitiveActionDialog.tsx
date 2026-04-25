/**
 * SensitiveActionDialog — governed confirmation for destructive or
 * high-impact operations.
 *
 * Features:
 *   - Typed confirmation (must type a keyword to proceed)
 *   - Impact preview listing affected resources
 *   - Reason / justification input (optional but recommended)
 *   - Audit preview (what will be recorded)
 *   - Tenant confirmation check
 *
 * @doc.type component
 * @doc.purpose Governed confirmation for sensitive actions
 * @doc.layer frontend
 * @doc.pattern Dialog
 */
import React, { useRef, useState, useEffect } from 'react';
import { AlertTriangle } from 'lucide-react';
import { Button } from '@ghatana/design-system';

export interface ImpactItem {
  label: string;
  value: string;
  severity?: 'low' | 'medium' | 'high';
}

export interface SensitiveActionDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmKeyword?: string;
  impactItems?: ImpactItem[];
  auditMessage?: string;
  tenantName?: string;
  reasonRequired?: boolean;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

export function SensitiveActionDialog({
  open,
  title,
  description,
  confirmKeyword,
  impactItems = [],
  auditMessage,
  tenantName,
  reasonRequired = false,
  onConfirm,
  onCancel,
}: SensitiveActionDialogProps): React.ReactElement | null {
  const dialogRef = useRef<HTMLDivElement>(null);
  const lastFocusRef = useRef<HTMLElement | null>(null);
  const [confirmText, setConfirmText] = useState('');
  const [reason, setReason] = useState('');

  useEffect(() => {
    if (!open) return;
    // Save currently focused element to restore on close
    lastFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;

    // Focus first focusable element
    const timer = setTimeout(() => {
      const focusables = dialogRef.current?.querySelectorAll<HTMLElement>(
        'button, textarea, [href], input, select, [tabindex]:not([tabindex="-1"])',
      );
      focusables?.[0]?.focus();
    }, 0);

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onCancel();
      }
      if (e.key === 'Tab') {
        const focusables = dialogRef.current?.querySelectorAll<HTMLElement>(
          'button, textarea, [href], input, select, [tabindex]:not([tabindex="-1"])',
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

  const keywordMatch = !confirmKeyword || confirmText.trim() === confirmKeyword;
  const reasonValid = !reasonRequired || reason.trim().length > 3;
  const canConfirm = keywordMatch && reasonValid;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div
        ref={dialogRef}
        className="bg-white dark:bg-gray-900 rounded-xl shadow-xl max-w-md w-full mx-4 overflow-hidden"
        role="dialog"
        aria-modal="true"
        aria-labelledby="sensitive-action-title"
      >
        {/* Header */}
        <div className="flex items-start gap-3 px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <AlertTriangle className="h-5 w-5 text-amber-500 flex-shrink-0 mt-0.5" />
          <div>
            <h2
              id="sensitive-action-title"
              className="text-sm font-semibold text-gray-900 dark:text-gray-100"
            >
              {title}
            </h2>
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">{description}</p>
          </div>
        </div>

        {/* Body */}
        <div className="px-6 py-4 space-y-4 max-h-[60vh] overflow-y-auto">
          {/* Impact preview */}
          {impactItems.length > 0 && (
            <div>
              <p className="text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-2">
                Impact preview
              </p>
              <ul className="space-y-1.5">
                {impactItems.map((item, i) => (
                  <li
                    key={i}
                    className={[
                      'text-xs flex justify-between rounded px-2 py-1',
                      item.severity === 'high'
                        ? 'bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300'
                        : item.severity === 'medium'
                          ? 'bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-300'
                          : 'bg-gray-50 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
                    ].join(' ')}
                  >
                    <span>{item.label}</span>
                    <span className="font-medium">{item.value}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Tenant confirmation */}
          {tenantName && (
            <div className="rounded bg-indigo-50 dark:bg-indigo-950 px-3 py-2 text-xs text-indigo-700 dark:text-indigo-300">
              Tenant scope: <span className="font-semibold">{tenantName}</span>
            </div>
          )}

          {/* Reason input */}
          <div>
            <label
              htmlFor="sensitive-reason"
              className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1"
            >
              Reason {reasonRequired && <span className="text-red-500">*</span>}
            </label>
            <textarea
              id="sensitive-reason"
              rows={2}
              className="w-full rounded-md border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              placeholder="Why are you performing this action?"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>

          {/* Typed confirmation */}
          {confirmKeyword && (
            <div>
              <label
                htmlFor="sensitive-confirm"
                className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1"
              >
                Type <code className="font-mono bg-gray-100 dark:bg-gray-800 px-1 rounded">{confirmKeyword}</code> to confirm
              </label>
              <input
                id="sensitive-confirm"
                type="text"
                className="w-full rounded-md border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                value={confirmText}
                onChange={(e) => setConfirmText(e.target.value)}
              />
            </div>
          )}

          {/* Audit preview */}
          {auditMessage && (
            <div className="text-[11px] text-gray-400 dark:text-gray-500 border-t border-gray-100 dark:border-gray-800 pt-2">
              Audit trail: {auditMessage}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex justify-end gap-2 px-6 py-3 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-950">
          <Button onClick={onCancel} variant="secondary" type="button">
            Cancel
          </Button>
          <Button
            onClick={() => onConfirm(reason.trim())}
            disabled={!canConfirm}
            variant="primary"
            type="button"
            className="bg-red-600 hover:bg-red-700 text-white disabled:opacity-50"
          >
            Confirm
          </Button>
        </div>
      </div>
    </div>
  );
}
