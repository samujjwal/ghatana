/**
 * LiveStatusBanner — YAPPC Web.
 *
 * Displays the live status of a YAPPC workflow, run, or background operation
 * as a top-of-page banner. Updates reactively via polling or WebSocket events.
 *
 * The banner is:
 * - Invisible when status is IDLE / COMPLETED with no error.
 * - Blue when status is RUNNING.
 * - Yellow when status is PAUSED.
 * - Green when status is COMPLETED (auto-dismisses after 3 s).
 * - Red when status is FAILED or CANCELLED.
 *
 * @doc.type component
 * @doc.purpose Contextual live-status banner for async operations
 * @doc.layer product
 * @doc.pattern Molecule
 */

import React, { useEffect, useState } from 'react';

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

export type LiveStatus =
  | 'IDLE'
  | 'RUNNING'
  | 'PAUSED'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export interface LiveStatusBannerProps {
  /** Current status of the operation. */
  status: LiveStatus;
  /** Human-readable description of the operation in progress. */
  label?: string;
  /** Error message shown when status is FAILED. */
  errorMessage?: string;
  /** Dismiss callback — called when the user clicks the dismiss button. */
  onDismiss?: () => void;
  /** Auto-dismiss delay in ms after COMPLETED. Set to 0 to disable. Defaults to 3000. */
  autoDismissMs?: number;
  /** Additional CSS class names. */
  className?: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

const STATUS_CONFIG = {
  IDLE: null, // not rendered
  RUNNING: {
    bgClass: 'bg-blue-50 border-blue-200 text-blue-800',
    icon: '○',
    ariaLabel: 'Operation running',
    animate: true,
  },
  PAUSED: {
    bgClass: 'bg-yellow-50 border-yellow-200 text-yellow-800',
    icon: '⏸',
    ariaLabel: 'Operation paused',
    animate: false,
  },
  COMPLETED: {
    bgClass: 'bg-green-50 border-green-200 text-green-800',
    icon: '✓',
    ariaLabel: 'Operation completed',
    animate: false,
  },
  FAILED: {
    bgClass: 'bg-red-50 border-red-200 text-red-800',
    icon: '✕',
    ariaLabel: 'Operation failed',
    animate: false,
  },
  CANCELLED: {
    bgClass: 'bg-slate-50 border-slate-200 text-slate-700',
    icon: '○',
    ariaLabel: 'Operation cancelled',
    animate: false,
  },
} as const satisfies Record<LiveStatus, { bgClass: string; icon: string; ariaLabel: string; animate: boolean } | null>;

// ─────────────────────────────────────────────────────────────────────────────
// Component
// ─────────────────────────────────────────────────────────────────────────────

export const LiveStatusBanner = React.memo<LiveStatusBannerProps>(function LiveStatusBanner({
  status,
  label,
  errorMessage,
  onDismiss,
  autoDismissMs = 3000,
  className,
}) {
  const [dismissed, setDismissed] = useState(false);

  // Reset dismissed state when status changes back to an active state.
  useEffect(() => {
    if (status === 'RUNNING' || status === 'PAUSED') {
      setDismissed(false);
    }
  }, [status]);

  // Auto-dismiss on COMPLETED.
  useEffect(() => {
    if (status !== 'COMPLETED' || autoDismissMs === 0) return;

    const timer = setTimeout(() => {
      setDismissed(true);
      onDismiss?.();
    }, autoDismissMs);

    return () => clearTimeout(timer);
  }, [status, autoDismissMs, onDismiss]);

  const config = STATUS_CONFIG[status];

  if (!config || dismissed) return null;

  const handleDismiss = (): void => {
    setDismissed(true);
    onDismiss?.();
  };

  return (
    <div
      role="status"
      aria-live="polite"
      aria-label={config.ariaLabel}
      className={[
        'flex items-center gap-2 px-4 py-2 border rounded-md text-sm font-medium',
        config.bgClass,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <span
        aria-hidden="true"
        className={config.animate ? 'animate-pulse' : undefined}
      >
        {config.icon}
      </span>

      <span className="flex-1">
        {label ?? statusDefaultLabel(status)}
        {status === 'FAILED' && errorMessage ? `: ${errorMessage}` : null}
      </span>

      {onDismiss && (
        <button
          type="button"
          onClick={handleDismiss}
          aria-label="Dismiss status banner"
          className="ml-auto p-0.5 opacity-60 hover:opacity-100 transition-opacity"
        >
          ✕
        </button>
      )}
    </div>
  );
});

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

function statusDefaultLabel(status: LiveStatus): string {
  switch (status) {
    case 'RUNNING':
      return 'Operation in progress…';
    case 'PAUSED':
      return 'Operation paused';
    case 'COMPLETED':
      return 'Operation completed';
    case 'FAILED':
      return 'Operation failed';
    case 'CANCELLED':
      return 'Operation cancelled';
    case 'IDLE':
      return '';
  }
}
