/**
 * SseStatus — displays the current SSE connection state in the sidebar.
 *
 * Shows a coloured dot + label:
 *  - Green "Live"         → connected
 *  - Yellow "Connecting"  → establishing connection (with pulse animation)
 *  - Grey "Offline"       → disconnected / error
 *
 * Re-subscribes whenever the active `tenantId` changes.
 *
 * @doc.type component
 * @doc.purpose Display real-time SSE connection health indicator
 * @doc.layer frontend
 */
import React, { useEffect, useState } from 'react';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import { subscribeToAepStream } from '@/api/sse';

type SseState = 'connecting' | 'connected' | 'disconnected';

export function SseStatus() {
  const tenantId = useAtomValue(tenantIdAtom);
  const [state, setState] = useState<SseState>('connecting');

  useEffect(() => {
    setState('connecting');
    const sub = subscribeToAepStream(
      tenantId,
      (msg) => {
        if (msg.type === 'connected' || msg.type === 'heartbeat') {
          setState('connected');
        }
      },
      () => setState('disconnected'),
    );
    return () => sub.close();
  }, [tenantId]);

  const dot =
    state === 'connected'
      ? 'bg-green-500'
      : state === 'disconnected'
        ? 'bg-gray-400'
        : 'bg-yellow-400 animate-pulse';

  const label =
    state === 'connected' ? 'Live' : state === 'disconnected' ? 'Offline' : 'Connecting';

  return (
    <div className="mx-3 mt-1 flex items-center gap-2 px-2 py-1 text-xs text-gray-400">
      <span className={['h-2 w-2 rounded-full flex-shrink-0', dot].join(' ')} />
      <span>{label}</span>
    </div>
  );
}
