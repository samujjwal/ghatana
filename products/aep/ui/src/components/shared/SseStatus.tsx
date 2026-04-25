/**
 * SseStatus — displays the current SSE connection state in the sidebar.
 *
 * Shows a coloured dot + label:
 *  - Green  "Live"            → connected
 *  - Yellow "Connecting"       → establishing connection (with pulse animation)
 *  - Amber  "Stale"           → connected but no heartbeat for >30s
 *  - Red    "Unauthorized"     → connection rejected (401/403)
 *  - Red    "Tenant Mismatch"  → connected to wrong tenant stream
 *  - Grey   "Offline"          → disconnected / error
 *
 * Re-subscribes whenever the active `tenantId` changes.
 *
 * @doc.type component
 * @doc.purpose Display real-time SSE connection health indicator
 * @doc.layer frontend
 */
import React, { useEffect, useRef, useState } from 'react';
import { useAtomValue } from 'jotai';
import { useLocation } from 'react-router';
import { tenantIdAtom } from '@/stores/tenant.store';
import { subscribeToAepStream } from '@/api/sse';
import { isSseRelevantPath } from '@/lib/routes';

type SseState =
  | 'connecting'
  | 'connected'
  | 'stale'
  | 'unauthorized'
  | 'tenant_mismatch'
  | 'disconnected';

const STATE_META: Record<
  SseState,
  { dot: string; label: string; ariaLabel: string }
> = {
  connecting: {
    dot: 'bg-yellow-400 animate-pulse',
    label: 'Connecting',
    ariaLabel: 'SSE connection is being established',
  },
  connected: {
    dot: 'bg-green-500',
    label: 'Live',
    ariaLabel: 'Real-time updates are active',
  },
  stale: {
    dot: 'bg-amber-400 animate-pulse',
    label: 'Stale',
    ariaLabel: 'Real-time connection is slow; updates may be delayed',
  },
  unauthorized: {
    dot: 'bg-red-500',
    label: 'Unauthorized',
    ariaLabel: 'Real-time connection was rejected; re-authenticate',
  },
  tenant_mismatch: {
    dot: 'bg-red-500 animate-pulse',
    label: 'Tenant Mismatch',
    ariaLabel: 'Real-time stream belongs to a different tenant',
  },
  disconnected: {
    dot: 'bg-gray-400',
    label: 'Offline',
    ariaLabel: 'Real-time updates are unavailable',
  },
};

const STALE_THRESHOLD_MS = 30_000;

export function SseStatus() {
  const tenantId = useAtomValue(tenantIdAtom);
  const { pathname } = useLocation();
  const [state, setState] = useState<SseState>('connecting');
  const lastHeartbeatRef = useRef<number>(Date.now());
  const staleTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Only subscribe to SSE on pages that actually need real-time data
  const isRelevant = isSseRelevantPath(pathname);

  useEffect(() => {
    if (!isRelevant) {
      if (staleTimerRef.current) {
        clearInterval(staleTimerRef.current);
        staleTimerRef.current = null;
      }
      return;
    }
    setState('connecting');
    lastHeartbeatRef.current = Date.now();

    const sub = subscribeToAepStream(
      tenantId,
      (msg) => {
        if (msg.type === 'connected' || msg.type === 'heartbeat') {
          setState((prev) => (prev === 'tenant_mismatch' ? prev : 'connected'));
          lastHeartbeatRef.current = Date.now();
        }
        if (msg.type === 'error') {
          const err =
            msg.data && typeof msg.data === 'object' && 'error' in msg.data
              ? String((msg.data as Record<string, unknown>).error)
              : '';
          if (err.includes('401') || err.includes('403') || err.includes('unauthorized')) {
            setState('unauthorized');
          } else if (err.includes('tenant')) {
            setState('tenant_mismatch');
          } else {
            setState('disconnected');
          }
        }
      },
      () => setState('disconnected'),
    );

    staleTimerRef.current = setInterval(() => {
      const elapsed = Date.now() - lastHeartbeatRef.current;
      if (elapsed > STALE_THRESHOLD_MS) {
        setState((prev) => {
          if (prev === 'connected') return 'stale';
          return prev;
        });
      }
    }, 10_000);

    return () => {
      sub.close();
      if (staleTimerRef.current) {
        clearInterval(staleTimerRef.current);
        staleTimerRef.current = null;
      }
    };
  }, [tenantId, isRelevant]);

  if (!isRelevant) {
    return null;
  }

  const meta = STATE_META[state];

  return (
    <div
      className="mx-3 mt-1 flex items-center gap-2 px-2 py-1 text-xs text-gray-500 dark:text-gray-300"
      role="status"
      aria-live="polite"
      aria-label={meta.ariaLabel}
      title={meta.ariaLabel}
    >
      <span className={['h-2 w-2 rounded-full flex-shrink-0', meta.dot].join(' ')} />
      <span className="font-medium">{meta.label}</span>
    </div>
  );
}
