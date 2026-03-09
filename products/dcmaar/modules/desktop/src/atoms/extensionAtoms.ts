/**
 * Extension Store Atoms - Jotai migration from Zustand
 * Manages extension WebSocket connection state with fine-grained reactivity
 */

import { atom } from 'jotai';

// Types (preserved from original store)
export interface ExtensionEvent {
  type: 'page_view' | 'click' | 'form_submit' | 'custom' | 'handshake' | 'pong';
  url: string;
  title?: string;
  element?: string;
  form_id?: string;
  data?: Record<string, unknown>;
  timestamp: number;
  id?: string;
}

export interface ExtensionConfig {
  capture_enabled: boolean;
  capture_domains: string[];
  metrics_interval: number;
  privacy_mode: boolean;
}

export interface ConnectionState {
  id: string;
  extension_id?: string;
  authenticated: boolean;
  connected_at: number;
  last_ping?: number;
  latency?: number;
}

// Core state atoms - split into fine-grained pieces
export const extensionConnectedAtom = atom<boolean>(false);
export const extensionIsConnectingAtom = atom<boolean>(false);
export const extensionConnectionAttemptsAtom = atom<number>(0);
export const extensionLastErrorAtom = atom<string | null>(null);
export const extensionConnectionsAtom = atom<ConnectionState[]>([]);
export const extensionErrorAtom = atom<string | null>(null);
export const extensionReconnectAttemptsAtom = atom<number>(0);
export const extensionLatencyAtom = atom<number | null>(null);
export const extensionLastPingTimeAtom = atom<number | null>(null);

// Events backing store - keep full array here
const extensionEventsBackingAtom = atom<ExtensionEvent[]>([]);

// Public read-only events atom
export const extensionEventsAtom = atom(get => get(extensionEventsBackingAtom));

// Derived atom for recent events preview (optimized for rendering)
export const extensionRecentEventsAtom = atom(get => get(extensionEventsBackingAtom).slice(0, 12));

// Events count (optimized for components that only need count)
export const extensionEventsCountAtom = atom(get => get(extensionEventsBackingAtom).length);

// Config atom with default values
export const extensionConfigAtom = atom<ExtensionConfig>({
  capture_enabled: true,
  capture_domains: ['*'],
  metrics_interval: 60000,
  privacy_mode: false,
});

// Write-only action atoms
export const addExtensionEventsAtom = atom(null, (get, set, newEvents: ExtensionEvent[]) => {
  set(extensionEventsBackingAtom, prev => [...newEvents, ...prev].slice(0, 1000));
});

export const clearExtensionEventsAtom = atom(null, (get, set) => {
  set(extensionEventsBackingAtom, []);
});

export const setExtensionConnectedAtom = atom(null, (get, set, connected: boolean) => {
  set(extensionConnectedAtom, connected);
});

export const setExtensionLatencyAtom = atom(null, (get, set, latency: number | null) => {
  set(extensionLatencyAtom, latency);
  set(extensionLastPingTimeAtom, null);
});

export const updateExtensionConfigAtom = atom(
  null,
  (get, set, configUpdate: Partial<ExtensionConfig>) => {
    set(extensionConfigAtom, prev => ({
      ...prev,
      ...configUpdate,
    }));
  }
);

// Derived connection status
export const extensionConnectionStatusAtom = atom(get => {
  const connected = get(extensionConnectedAtom);
  const isConnecting = get(extensionIsConnectingAtom);
  const lastError = get(extensionLastErrorAtom);

  return {
    connected,
    isConnecting,
    lastError,
    status: connected ? 'connected' : isConnecting ? 'connecting' : 'disconnected',
  };
});
