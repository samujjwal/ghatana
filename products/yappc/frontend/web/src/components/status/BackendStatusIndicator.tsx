/**
 * Backend Status Indicator
 *
 * Small, non-intrusive real-time indicator of backend/API health.
 * Polls the health endpoint periodically and surfaces status via color-coded dot + tooltip.
 *
 * @doc.type component
 * @doc.purpose Surface backend availability transparently in the UI
 * @doc.layer product
 * @doc.pattern Status Indicator
 */

import { useEffect, useState, useCallback, useRef } from 'react';
import { Server, WifiOff, AlertCircle, Loader2 } from 'lucide-react';
import { Tooltip } from '@ghatana/design-system';

export type BackendHealth = 'unknown' | 'checking' | 'healthy' | 'degraded' | 'unavailable';

interface BackendStatus {
  health: BackendHealth;
  lastChecked: number | null;
  message: string;
}

const POLL_INTERVAL_MS = 30000;
const INITIAL_DELAY_MS = 5000;

function getHealthConfig(health: BackendHealth): {
  color: string;
  bg: string;
  icon: React.ReactNode;
  label: string;
} {
  switch (health) {
    case 'healthy':
      return {
        color: 'text-emerald-500',
        bg: 'bg-emerald-500',
        icon: <Server className="w-3 h-3" />,
        label: 'Backend healthy',
      };
    case 'degraded':
      return {
        color: 'text-warning-color',
        bg: 'bg-warning-bg',
        icon: <AlertCircle className="w-3 h-3" />,
        label: 'Backend degraded',
      };
    case 'unavailable':
      return {
        color: 'text-destructive',
        bg: 'bg-destructive-bg',
        icon: <WifiOff className="w-3 h-3" />,
        label: 'Backend unavailable',
      };
    case 'checking':
      return {
        color: 'text-info-color',
        bg: 'bg-info-bg',
        icon: <Loader2 className="w-3 h-3 animate-spin" />,
        label: 'Checking backend...',
      };
    default:
      return {
        color: 'text-grey-400',
        bg: 'bg-grey-400',
        icon: <Server className="w-3 h-3" />,
        label: 'Backend status unknown',
      };
  }
}

function getApiBaseUrl(): string {
  return import.meta.env.VITE_API_BASE_URL ?? '/api';
}

export function useBackendStatus(): BackendStatus {
  const [status, setStatus] = useState<BackendStatus>({
    health: 'unknown',
    lastChecked: null,
    message: 'Not checked yet',
  });
  const abortRef = useRef<AbortController | null>(null);

  const check = useCallback(async () => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    setStatus((prev) =>
      prev.health === 'unknown'
        ? { ...prev, health: 'checking', message: 'Checking backend health...' }
        : prev
    );

    try {
      const base = getApiBaseUrl();
      const response = await fetch(`${base}/health`, {
        method: 'GET',
        signal: controller.signal,
        headers: { Accept: 'application/json' },
      });

      if (!response.ok) {
        setStatus({
          health: 'degraded',
          lastChecked: Date.now(),
          message: `Backend responded with status ${response.status}`,
        });
        return;
      }

      setStatus({
        health: 'healthy',
        lastChecked: Date.now(),
        message: 'All services operational',
      });
    } catch (error) {
      const message =
        error instanceof Error && error.name === 'AbortError'
          ? 'Health check cancelled'
          : 'Cannot reach backend server';
      setStatus({
        health: 'unavailable',
        lastChecked: Date.now(),
        message,
      });
    }
  }, []);

  useEffect(() => {
    const initialTimeout = setTimeout(() => {
      void check();
    }, INITIAL_DELAY_MS);

    const interval = setInterval(() => {
      void check();
    }, POLL_INTERVAL_MS);

    return () => {
      clearTimeout(initialTimeout);
      clearInterval(interval);
      abortRef.current?.abort();
    };
  }, [check]);

  return status;
}

export interface BackendStatusIndicatorProps {
  /** Optional additional CSS classes */
  className?: string;
}

export function BackendStatusIndicator({ className }: BackendStatusIndicatorProps): React.JSX.Element {
  const { health, lastChecked, message } = useBackendStatus();
  const config = getHealthConfig(health);

  const tooltip = `${config.label}${lastChecked ? ` — ${new Date(lastChecked).toLocaleTimeString()}` : ''}${message ? ` (${message})` : ''}`;

  return (
    <Tooltip title={tooltip} arrow placement="bottom">
      <span
        className={[
          'inline-flex items-center gap-1 rounded-full px-1.5 py-0.5 text-[10px] font-medium transition-colors',
          'border border-divider bg-bg-paper hover:bg-surface-tertiary cursor-default',
          config.color,
          className ?? '',
        ]
          .filter(Boolean)
          .join(' ')}
        aria-label={tooltip}
        role="status"
      >
        <span className={['w-1.5 h-1.5 rounded-full', config.bg].join(' ')} />
        {config.icon}
      </span>
    </Tooltip>
  );
}
