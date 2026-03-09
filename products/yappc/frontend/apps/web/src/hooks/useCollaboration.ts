/**
 * useCollaboration Hook
 *
 * Wires the real ProviderManager (WebSocket/WebRTC + JWT auth) to the canvas
 * collaboration layer. Replaces the dev-only MockCollaborationProvider in the
 * production path.
 *
 * Usage:
 *   const collab = useCollaboration({ projectId, getToken: auth.getToken });
 *   // collab.status — 'disconnected' | 'connecting' | 'connected' | ...
 *   // collab.presence — Map<userId, Presence>
 *
 * @doc.type hook
 * @doc.purpose Real-time collaboration via ProviderManager + JWT auth
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useEffect, useCallback, useRef } from 'react';

import {
  ProviderManager,
  type ConnectionState,
  type ProviderConfig,
  type ProviderEvent,
} from '@ghatana/yappc-canvas/collab/providerManager';
import type { Presence, User } from '../services/collaboration/types';

export interface UseCollaborationOptions {
  /** Project/room ID to connect to */
  projectId: string;
  /** Returns the current JWT Bearer token, or null */
  getToken: () => string | null;
  /** Current authenticated user */
  currentUser?: User | null;
  /** Set to false to disable real-time collaboration */
  enabled?: boolean;
  /** Override WebSocket URL (defaults to env var) */
  websocketUrl?: string;
}

export interface UseCollaborationReturn {
  /** Current connection state */
  connectionState: ConnectionState;
  /** Short status string */
  status: ConnectionState['status'];
  /** Whether connected */
  isConnected: boolean;
  /** Presence map — keyed by userId */
  presence: Map<string, Presence>;
  /** Connect (idempotent) */
  connect: () => Promise<void>;
  /** Disconnect */
  disconnect: () => void;
}

/**
 * Derives the WebSocket URL from environment variables.
 * Falls back to the current origin with /collab path.
 */
function resolveWsUrl(): string {
  const viteEnv = (import.meta as unknown as { env?: Record<string, string> }).env;
  if (viteEnv?.VITE_WS_URL) return viteEnv.VITE_WS_URL;
  if (viteEnv?.VITE_API_ORIGIN) return viteEnv.VITE_API_ORIGIN.replace(/^http/, 'ws') + '/collab';
  return `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/collab`;
}

/**
 * Provides real-time collaboration state backed by ProviderManager.
 */
export function useCollaboration({
  projectId,
  getToken,
  currentUser,
  enabled = true,
  websocketUrl,
}: UseCollaborationOptions): UseCollaborationReturn {
  const managerRef = useRef<ProviderManager | null>(null);
  const [connectionState, setConnectionState] = useState<ConnectionState>({
    provider: 'none',
    status: 'disconnected',
    reconnectAttempts: 0,
    failoverActive: false,
  });
  const [presence] = useState<Map<string, Presence>>(new Map());

  // Build or re-use the ProviderManager
  useEffect(() => {
    if (!enabled) return;

    const config: Partial<ProviderConfig> = {
      preferredProvider: 'websocket',
      enableWebSocket: true,
      enableWebRTC: false,
      enableFailover: true,
      maxReconnectAttempts: 5,
      reconnectDelay: 1500,
      enableAuth: true,
      websocketUrl: websocketUrl ?? resolveWsUrl(),
      getToken: () => getToken() ?? '',
      tokenRefreshInterval: 15 * 60 * 1000,
    };

    const manager = new ProviderManager(config);
    managerRef.current = manager;

    const unsubscribe = manager.onEvent((_event: ProviderEvent) => {
      setConnectionState(manager.getState());
    });

    return () => {
      unsubscribe();
      manager.disconnect();
      managerRef.current = null;
    };
    // Re-create when projectId or enabled changes; getToken is stable ref
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId, enabled, websocketUrl]);

  const connect = useCallback(async () => {
    if (!managerRef.current || !enabled) return;
    const token = getToken();
    if (!token) {
      console.warn('[useCollaboration] No auth token — skipping collab connect');
      return;
    }
    await managerRef.current.connect(projectId);
    setConnectionState(managerRef.current.getState());
  }, [projectId, enabled, getToken]);

  const disconnect = useCallback(() => {
    managerRef.current?.disconnect();
    if (managerRef.current) setConnectionState(managerRef.current.getState());
  }, []);

  // Auto-connect when currentUser is available and we have a token
  useEffect(() => {
    if (!enabled || !currentUser) return;
    const token = getToken();
    if (!token) return;
    connect().catch((err: unknown) =>
      console.error('[useCollaboration] auto-connect failed:', err)
    );
  }, [enabled, currentUser, connect, getToken]);

  const status = connectionState.status;
  const isConnected = status === 'connected';

  return {
    connectionState,
    status,
    isConnected,
    presence,
    connect,
    disconnect,
  };
}
