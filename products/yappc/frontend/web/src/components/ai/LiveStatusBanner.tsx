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
import { Button } from '../ui/Button';
import { useI18n } from '../../i18n/I18nProvider';

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
    bgClass: 'bg-info-bg border-info-border text-info-color',
    icon: '○',
    ariaLabel: 'Operation running',
    animate: true,
  },
  PAUSED: {
    bgClass: 'bg-warning-bg border-warning-border text-warning-color',
    icon: '⏸',
    ariaLabel: 'Operation paused',
    animate: false,
  },
  COMPLETED: {
    bgClass: 'bg-success-bg border-success-border text-success-color',
    icon: '✓',
    ariaLabel: 'Operation completed',
    animate: false,
  },
  FAILED: {
    bgClass: 'bg-destructive-bg border-destructive-border text-destructive',
    icon: '✕',
    ariaLabel: 'Operation failed',
    animate: false,
  },
  CANCELLED: {
    bgClass: 'bg-surface-muted border-border text-fg',
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
  const { t } = useI18n();
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
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={handleDismiss}
          aria-label={t('ai.liveStatus.dismiss')}
          className="ml-auto p-0.5 opacity-60 hover:opacity-100 transition-opacity"
        >
          ✕
        </Button>
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
