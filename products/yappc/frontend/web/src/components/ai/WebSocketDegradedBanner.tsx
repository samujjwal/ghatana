/**
 * WebSocketDegradedBanner — honest degraded-state disclosure (C-Y5)
 *
 * Shows a non-intrusive banner when the WebSocket connection is degraded
 * (reconnecting) or down (disconnected/failed). When the connection is healthy
 * or the user explicitly dismisses the banner, it is hidden.
 *
 * The banner includes:
 *  - A colour-coded severity indicator (yellow = degraded, red = down)
 *  - The current reconnect attempt count
 *  - A dismiss button (reappears if health worsens again)
 *
 * @doc.type component
 * @doc.purpose Transparent WebSocket degradation disclosure
 * @doc.layer product
 * @doc.pattern Molecule
 */

import React, { useEffect, useState } from 'react';
import { WifiOff, Wifi } from 'lucide-react';
import { useWebSocketHealth } from '@/hooks/useWebSocketHealth';
import type { WebSocketHealth } from '@/hooks/useWebSocketHealth';
import { Button } from '../ui/Button';
import { useI18n } from '../../i18n/I18nProvider';

// ── Props ─────────────────────────────────────────────────────────────────────

export interface WebSocketDegradedBannerProps {
  /** Override hook result for testing */
  healthOverride?: WebSocketHealth;
  reconnectAttemptOverride?: number;
  /** Extra class names */
  className?: string;
}

// ── Config ────────────────────────────────────────────────────────────────────

const HEALTH_CONFIG: Record<
  'degraded' | 'down',
  { bg: string; icon: React.ReactNode; label: string }
> = {
  degraded: {
    bg: 'border-warning-border bg-warning-bg text-warning-color',
    icon: <Wifi className="h-4 w-4 text-warning-color" aria-hidden="true" />,
    label: 'Live updates degraded — reconnecting',
  },
  down: {
    bg: 'border-destructive-border bg-destructive-bg text-destructive',
    icon: <WifiOff className="h-4 w-4 text-destructive" aria-hidden="true" />,
    label: 'Live updates unavailable',
  },
};

// ── Component ─────────────────────────────────────────────────────────────────

export function WebSocketDegradedBanner({
  healthOverride,
  reconnectAttemptOverride,
  className,
}: WebSocketDegradedBannerProps) {
  const { t } = useI18n();
  const { health: rawHealth, reconnectAttempt: rawAttempt } = useWebSocketHealth();
  const health = healthOverride ?? rawHealth;
  const reconnectAttempt = reconnectAttemptOverride ?? rawAttempt;

  const [dismissed, setDismissed] = useState(false);

  // Reset dismissed state when health goes from ok → degraded/down
  useEffect(() => {
    if (health === 'degraded' || health === 'down') {
      setDismissed(false);
    }
  }, [health]);

  if (health === 'ok' || health === 'idle' || dismissed) {
    return null;
  }

  const config = HEALTH_CONFIG[health];

  return (
    <div
      role="alert"
      aria-live="assertive"
      className={[
        'flex items-center gap-2 rounded-md border px-4 py-2 text-sm font-medium',
        config.bg,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      data-testid="ws-degraded-banner"
      data-health={health}
    >
      {config.icon}
      <span className="flex-1">
        {config.label}
        {health === 'degraded' && reconnectAttempt > 0
          ? ` (attempt ${reconnectAttempt})`
          : null}
      </span>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={() => setDismissed(true)}
        aria-label={t('ai.websocket.dismissWarning')}
        className="ml-auto p-0.5 opacity-60 transition-opacity hover:opacity-100"
        data-testid="btn-dismiss-ws-banner"
      >
        ✕
      </Button>
    </div>
  );
}
