/**
 * GuardedAction Component
 *
 * Wraps a sensitive operation (acknowledge, resolve, migrate, delete) behind a
 * confirmation gate that optionally captures an audit reason from the user.
 * Renders the caller-supplied trigger element; when the trigger fires it opens
 * an inline confirmation panel or overlay instead of immediately executing the
 * destructive call.
 *
 * This component is purely presentational — callers own the mutation, loading
 * state, and error handling. The component only controls whether the confirm
 * dialog is open and collects an optional reason string before handing back to
 * the caller.
 *
 * @doc.type component
 * @doc.purpose Sensitive operation wrapper with optional reason capture and confirmation gate
 * @doc.layer shared
 * @doc.pattern Guard / Confirmation Dialog
 *
 * @example
 * ```tsx
 * <GuardedAction
 *   label="Acknowledge alert"
 *   impact="Alert will be marked as acknowledged and suppressed for 24 hours."
 *   requiresReason
 *   reasonPrompt="Why are you acknowledging this alert?"
 *   onConfirm={(reason) => acknowledgeMutation.mutate({ id, reason })}
 *   isExecuting={acknowledgeMutation.isPending}
 * >
 *   {({ open }) => (
 *     <Button variant="outline" onClick={open}>Acknowledge</Button>
 *   )}
 * </GuardedAction>
 * ```
 */

import { AlertTriangle, X } from "lucide-react";
import React, { useCallback, useId, useState } from "react";
import { cn } from "../../lib/theme";
import { Button } from "./Button";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface GuardedActionTriggerProps {
  /** Call this to open the confirmation dialog. */
  open: () => void;
}

export interface GuardedActionProps {
  /**
   * Human-readable label for the action — used as the dialog heading and the
   * default confirm button label (can be overridden with `confirmLabel`).
   */
  label: string;
  /**
   * A sentence explaining what will happen after the user confirms. Shown
   * prominently in the dialog body. Required to ensure users understand the
   * impact.
   */
  impact: string;
  /**
   * When `true`, renders a `<textarea>` for the user to type an audit reason.
   * The reason string is passed as the first argument to `onConfirm`.
   * @default false
   */
  requiresReason?: boolean;
  /**
   * Prompt text shown above the reason textarea.
   * @default "Reason for this action (recorded for audit)"
   */
  reasonPrompt?: string;
  /**
   * Called when the user clicks the confirm button. Receives the typed reason
   * if `requiresReason` is `true`, otherwise receives `undefined`.
   */
  onConfirm: (reason: string | undefined) => void;
  /** Called when the user clicks Cancel or the close button. */
  onCancel?: () => void;
  /**
   * When `true`, the confirm button shows a loading spinner and is disabled.
   * Useful for disabling re-clicks while the parent mutation is in-flight.
   * @default false
   */
  isExecuting?: boolean;
  /**
   * Override the confirm button label.
   * @default Same as `label`
   */
  confirmLabel?: string;
  /**
   * Render-prop that receives `{ open }`. The caller renders their trigger
   * element and calls `open()` to show the confirmation dialog.
   */
  children: (props: GuardedActionTriggerProps) => React.ReactNode;
  /** Optional className for the dialog container. */
  className?: string;
  /** Optional test id applied to the dialog panel. */
  "data-testid"?: string;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * GuardedAction
 *
 * Inline confirmation gate for sensitive operations. Renders the trigger via
 * a render prop and opens a dialog when the trigger fires.
 */
export function GuardedAction({
  label,
  impact,
  requiresReason = false,
  reasonPrompt = "Reason for this action (recorded for audit)",
  onConfirm,
  onCancel,
  isExecuting = false,
  confirmLabel,
  children,
  className,
  "data-testid": testId,
}: GuardedActionProps): React.ReactElement {
  const [isOpen, setIsOpen] = useState(false);
  const [reason, setReason] = useState("");
  const reasonId = useId();
  const dialogId = useId();

  const open = useCallback(() => {
    setIsOpen(true);
    setReason("");
  }, []);

  const handleCancel = useCallback(() => {
    setIsOpen(false);
    setReason("");
    onCancel?.();
  }, [onCancel]);

  const handleConfirm = useCallback(() => {
    onConfirm(requiresReason ? reason.trim() || undefined : undefined);
  }, [onConfirm, requiresReason, reason]);

  const canConfirm =
    !isExecuting && (!requiresReason || reason.trim().length > 0);

  return (
    <>
      {children({ open })}

      {isOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby={dialogId}
          data-testid={testId}
          className={cn(
            "fixed inset-0 z-50 flex items-center justify-center bg-black/40",
            className,
          )}
        >
          <section className="relative w-full max-w-md rounded-xl bg-white p-6 shadow-xl dark:bg-neutral-900">
            {/* Header */}
            <div className="mb-4 flex items-start justify-between gap-3">
              <div className="flex items-center gap-2">
                <AlertTriangle
                  className="mt-px h-5 w-5 shrink-0 text-amber-500"
                  aria-hidden="true"
                />
                <h2
                  id={dialogId}
                  className="text-base font-semibold text-neutral-900 dark:text-neutral-100"
                >
                  {label}
                </h2>
              </div>
              <button
                type="button"
                onClick={handleCancel}
                aria-label="Cancel"
                className="rounded p-1 text-neutral-400 hover:text-neutral-700 dark:hover:text-neutral-200"
              >
                <X className="h-4 w-4" aria-hidden="true" />
              </button>
            </div>

            {/* Impact summary */}
            <p className="mb-4 text-sm text-neutral-700 dark:text-neutral-300">
              {impact}
            </p>

            {/* Reason input */}
            {requiresReason && (
              <div className="mb-4">
                <label
                  htmlFor={reasonId}
                  className="mb-1 block text-xs font-medium text-neutral-600 dark:text-neutral-400"
                >
                  {reasonPrompt}
                </label>
                <textarea
                  id={reasonId}
                  value={reason}
                  onChange={(e) => {
                    setReason(e.target.value);
                  }}
                  rows={3}
                  className="w-full resize-none rounded-lg border border-neutral-300 px-3 py-2 text-sm text-neutral-900 placeholder-neutral-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 dark:border-neutral-600 dark:bg-neutral-800 dark:text-neutral-100"
                  placeholder="Provide a brief reason…"
                  data-testid={testId ? `${testId}-reason` : undefined}
                />
              </div>
            )}

            {/* Actions */}
            <div className="flex justify-end gap-2">
              <Button
                variant="ghost"
                size="sm"
                onClick={handleCancel}
                disabled={isExecuting}
              >
                Cancel
              </Button>
              <Button
                variant="danger"
                size="sm"
                onClick={handleConfirm}
                disabled={!canConfirm}
                isLoading={isExecuting}
                data-testid={testId ? `${testId}-confirm` : undefined}
              >
                {confirmLabel ?? label}
              </Button>
            </div>
          </section>
        </div>
      )}
    </>
  );
}
