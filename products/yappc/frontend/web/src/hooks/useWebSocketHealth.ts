/**
 * useWebSocketHealth — WebSocket connection health hook (C-Y5)
 *
 * Reads the WebSocket connection status from the `WebSocketContext` and
 * maps it to a simplified `WebSocketHealth` value:
 *  - `'ok'`       — connected
 *  - `'degraded'` — reconnecting (still trying, but live updates may be delayed)
 *  - `'down'`     — disconnected or failed
 *  - `'idle'`     — not yet attempted / connecting
 *
 * @doc.type hook
 * @doc.purpose Expose degraded WebSocket status for honest UI disclosure
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useContext } from 'react';
import { WebSocketContext } from '@/contexts/WebSocketContext';

// ── Types ──────────────────────────────────────────────────────────────────────

export type WebSocketHealth = 'ok' | 'degraded' | 'down' | 'idle';

export interface WebSocketHealthState {
  health: WebSocketHealth;
  /** Underlying connection status string for diagnostics */
  statusDetail: string;
  /** Number of reconnect attempts already made */
  reconnectAttempt: number;
  lastError?: Error;
}

// ── Hook ──────────────────────────────────────────────────────────────────────

/**
 * Returns the simplified WebSocket health state.
 * When used outside `<WebSocketProvider>` the context will be `undefined`;
 * in that case the hook returns `health: 'idle'` gracefully.
 *
 * @example
 * ```tsx
 * const { health } = useWebSocketHealth();
 * if (health === 'degraded') return <WebSocketDegradedBanner />;
 * ```
 */
export function useWebSocketHealth(): WebSocketHealthState {
  const ctx = useContext(WebSocketContext);

  // If no provider (e.g. in tests), return safe defaults.
  if (!ctx) {
    return { health: 'idle', statusDetail: 'no-provider', reconnectAttempt: 0 };
  }

  const { status, reconnectAttempt, lastError } = ctx;

  const health: WebSocketHealth = (() => {
    switch (status) {
      case 'connected':
        return 'ok';
      case 'reconnecting':
        return 'degraded';
      case 'disconnected':
      case 'failed':
        return 'down';
      default:
        return 'idle';
    }
  })();

  return { health, statusDetail: status, reconnectAttempt, lastError };
}
