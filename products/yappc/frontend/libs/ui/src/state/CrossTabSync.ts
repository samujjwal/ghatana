/**
 * Cross-Tab State Synchronization
 *
 * Synchronizes Jotai atom state across browser tabs using BroadcastChannel API.
 * Enables real-time state updates across multiple tabs/windows.
 *
 * @module CrossTabSync
 */

import { atom } from 'jotai';

import type { WritableAtom } from 'jotai';

/**
 * Message types for cross-tab communication
 */
export enum SyncMessageType {
  STATE_UPDATE = 'state_update',
  STATE_REQUEST = 'state_request',
  STATE_RESPONSE = 'state_response',
  PING = 'ping',
  PONG = 'pong',
}

/**
 * Message structure for cross-tab communication
 */
export interface SyncMessage<T = unknown> {
  type: SyncMessageType;
  atomKey: string;
  value?: T;
  timestamp: number;
  tabId: string;
}

/**
 * Configuration options for cross-tab sync
 */
export interface CrossTabSyncOptions {
  /**
   * BroadcastChannel name (should be unique per app)
   * @default 'yappc-state-sync'
   */
  channelName?: string;

  /**
   * Whether to enable debug logging
   * @default false
   */
  debug?: boolean;

  /**
   * Debounce delay for state updates (ms)
   * @default 50
   */
  debounceDelay?: number;

  /**
   * Whether to sync initial state on mount
   * @default true
   */
  syncOnMount?: boolean;

  /**
   * Maximum message age to accept (ms)
   * @default 5000
   */
  maxMessageAge?: number;
}

/**
 * CrossTabSync manager class
 */
export class CrossTabSync {
  private channel: BroadcastChannel | null = null;
  private tabId: string;
  private listeners = new Map<string, Set<(value: unknown) => void>>();
  private debounceTimers = new Map<string, NodeJS.Timeout>();
  private atomValues = new Map<string, unknown>();
  private options: Required<CrossTabSyncOptions>;

  /**
   *
   */
  constructor(options: CrossTabSyncOptions = {}) {
    this.tabId = this.generateTabId();
    this.options = {
      channelName: options.channelName || 'yappc-state-sync',
      debug: options.debug || false,
      debounceDelay: options.debounceDelay || 50,
      syncOnMount: options.syncOnMount !== false,
      maxMessageAge: options.maxMessageAge || 5000,
    };

    this.initialize();
  }

  /**
   * Initialize BroadcastChannel (if supported)
   */
  private initialize(): void {
    if (typeof window === 'undefined' || !window.BroadcastChannel) {
      this.log('BroadcastChannel not supported');
      return;
    }

    try {
      this.channel = new BroadcastChannel(this.options.channelName);
      this.channel.addEventListener('message', this.handleMessage.bind(this));
      this.log('CrossTabSync initialized', { tabId: this.tabId });

      // Request initial state from other tabs
      if (this.options.syncOnMount) {
        this.requestStateFromOtherTabs();
      }
    } catch (error) {
      console.error('Failed to initialize CrossTabSync:', error);
    }
  }

  /**
   * Generate unique tab ID
   */
  private generateTabId(): string {
    return `tab_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * Debug logging
   */
  private log(message: string, data?: unknown): void {
    if (this.options.debug) {
      console.log(`[CrossTabSync:${this.tabId}]`, message, data || '');
    }
  }

  /**
   * Handle incoming messages from other tabs
   */
  private handleMessage(event: MessageEvent<SyncMessage>): void {
    const message = event.data;

    // Ignore messages from this tab
    if (message.tabId === this.tabId) {
      return;
    }

    // Ignore old messages
    const age = Date.now() - message.timestamp;
    if (age > this.options.maxMessageAge) {
      this.log('Ignoring old message', { age, message });
      return;
    }

    this.log('Received message', message);

    switch (message.type) {
      case SyncMessageType.STATE_UPDATE:
        this.handleStateUpdate(message);
        break;
      case SyncMessageType.STATE_REQUEST:
        this.handleStateRequest(message);
        break;
      case SyncMessageType.STATE_RESPONSE:
        this.handleStateResponse(message);
        break;
      case SyncMessageType.PING:
        this.handlePing(message);
        break;
      case SyncMessageType.PONG:
        this.handlePong(message);
        break;
    }
  }

  /**
   * Handle state update from another tab
   */
  private handleStateUpdate(message: SyncMessage): void {
    const { atomKey, value } = message;

    // Update local cache
    this.atomValues.set(atomKey, value);

    // Notify listeners
    const listeners = this.listeners.get(atomKey);
    if (listeners) {
      listeners.forEach((listener) => {
        try {
          listener(value);
        } catch (error) {
          console.error('Error in state update listener:', error);
        }
      });
    }
  }

  /**
   * Handle state request from another tab
   */
  private handleStateRequest(message: SyncMessage): void {
    const { atomKey } = message;

    // Send response with current state
    if (this.atomValues.has(atomKey)) {
      this.sendMessage({
        type: SyncMessageType.STATE_RESPONSE,
        atomKey,
        value: this.atomValues.get(atomKey),
        timestamp: Date.now(),
        tabId: this.tabId,
      });
    }
  }

  /**
   * Handle state response from another tab
   */
  private handleStateResponse(message: SyncMessage): void {
    this.handleStateUpdate(message);
  }

  /**
   * Handle ping message
   */
  private handlePing(_message: SyncMessage): void {
    this.sendMessage({
      type: SyncMessageType.PONG,
      atomKey: '',
      timestamp: Date.now(),
      tabId: this.tabId,
    });
  }

  /**
   * Handle pong message
   */
  private handlePong(message: SyncMessage): void {
    this.log('Received pong from', message.tabId);
  }

  /**
   * Send message to other tabs
   */
  private sendMessage(message: SyncMessage): void {
    if (!this.channel) {
      return;
    }

    try {
      this.channel.postMessage(message);
      this.log('Sent message', message);
    } catch (error) {
      console.error('Failed to send message:', error);
    }
  }

  /**
   * Broadcast state update to other tabs (debounced)
   */
  public broadcastStateUpdate(atomKey: string, value: unknown): void {
    // Update local cache
    this.atomValues.set(atomKey, value);

    // Clear existing debounce timer
    const existingTimer = this.debounceTimers.get(atomKey);
    if (existingTimer) {
      clearTimeout(existingTimer);
    }

    // Set new debounce timer
    const timer = setTimeout(() => {
      this.sendMessage({
        type: SyncMessageType.STATE_UPDATE,
        atomKey,
        value,
        timestamp: Date.now(),
        tabId: this.tabId,
      });
      this.debounceTimers.delete(atomKey);
    }, this.options.debounceDelay);

    this.debounceTimers.set(atomKey, timer);
  }

  /**
   * Request state from other tabs
   */
  public requestState(atomKey: string): void {
    this.sendMessage({
      type: SyncMessageType.STATE_REQUEST,
      atomKey,
      timestamp: Date.now(),
      tabId: this.tabId,
    });
  }

  /**
   * Request all state from other tabs
   */
  private requestStateFromOtherTabs(): void {
    // Send ping to discover other tabs
    this.sendMessage({
      type: SyncMessageType.PING,
      atomKey: '',
      timestamp: Date.now(),
      tabId: this.tabId,
    });
  }

  /**
   * Subscribe to state updates for an atom
   */
  public subscribe(
    atomKey: string,
    listener: (value: unknown) => void
  ): () => void {
    if (!this.listeners.has(atomKey)) {
      this.listeners.set(atomKey, new Set());
    }

    this.listeners.get(atomKey)!.add(listener);

    // Request initial state from other tabs
    this.requestState(atomKey);

    // Return unsubscribe function
    return () => {
      const listeners = this.listeners.get(atomKey);
      if (listeners) {
        listeners.delete(listener);
        if (listeners.size === 0) {
          this.listeners.delete(atomKey);
        }
      }
    };
  }

  /**
   * Check if BroadcastChannel is supported
   */
  public isSupported(): boolean {
    return this.channel !== null;
  }

  /**
   * Get current tab ID
   */
  public getTabId(): string {
    return this.tabId;
  }

  /**
   * Cleanup resources
   */
  public destroy(): void {
    // Clear all debounce timers
    this.debounceTimers.forEach((timer) => clearTimeout(timer));
    this.debounceTimers.clear();

    // Close BroadcastChannel
    if (this.channel) {
      this.channel.close();
      this.channel = null;
    }

    // Clear listeners
    this.listeners.clear();
    this.atomValues.clear();

    this.log('CrossTabSync destroyed');
  }
}

/**
 * Global singleton instance
 */
let globalSyncInstance: CrossTabSync | null = null;

/**
 * Get or create the global CrossTabSync instance
 */
export function getCrossTabSync(options?: CrossTabSyncOptions): CrossTabSync {
  if (!globalSyncInstance) {
    globalSyncInstance = new CrossTabSync(options);
  }
  return globalSyncInstance;
}

/**
 * Create a synced atom that automatically syncs across tabs
 *
 * @param key - Unique key for the atom
 * @param initialValue - Initial value
 * @param syncOptions - Sync configuration options
 * @returns Writable atom that syncs across tabs
 *
 * @example
 * ```tsx
 * const countAtom = createSyncedAtom('count', 0);
 *
 * function Counter() {
 *   const [count, setCount] = useAtom(countAtom);
 *   return <button onClick={() => setCount(c => c + 1)}>{count}</button>;
 * }
 * ```
 */
export function createSyncedAtom<T>(
  key: string,
  initialValue: T,
  syncOptions?: CrossTabSyncOptions
): WritableAtom<T, [T | ((prev: T) => T)], void> {
  const sync = getCrossTabSync(syncOptions);

  // Base atom to store the value
  const baseAtom = atom<T>(initialValue);

  // Writable atom that handles syncing
  const syncedAtom = atom<T, [T | ((prev: T) => T)], void>(
    (get) => get(baseAtom),
    (get, set, update) => {
      const prevValue = get(baseAtom);
      const nextValue =
        typeof update === 'function'
          ? (update as (prev: T) => T)(prevValue)
          : update;

      // Update local state
      set(baseAtom, nextValue);

      // Broadcast to other tabs
      if (sync.isSupported()) {
        sync.broadcastStateUpdate(key, nextValue);
      }
    }
  );

  // Subscribe to updates from other tabs
  if (sync.isSupported()) {
    sync.subscribe(key, (_value: T) => {
      // This will be handled by Jotai store subscription
      // The actual update happens in useSyncedAtom hook
    });
  }

  return syncedAtom;
}

/**
 * React hook to use a synced atom
 *
 * @param syncedAtom - The synced atom created with createSyncedAtom
 * @param atomKey - The unique key for the atom
 * @returns [value, setValue] tuple
 *
 * @example
 * ```tsx
 * const countAtom = createSyncedAtom('count', 0);
 *
 * function Counter() {
 *   const [count, setCount] = useSyncedAtom(countAtom, 'count');
 *   return <button onClick={() => setCount(c => c + 1)}>{count}</button>;
 * }
 * ```
 */
export function useSyncedAtom<T>(
  syncedAtom: WritableAtom<T, [T | ((prev: T) => T)], void>,
  atomKey: string
): [T, (update: T | ((prev: T) => T)) => void] {
  const { useAtom } = require('jotai');
  const { useEffect } = require('react');

  const [value, setValue] = useAtom(syncedAtom);
  const sync = getCrossTabSync();

  useEffect(() => {
    if (!sync.isSupported()) {
      return;
    }

    // Subscribe to updates from other tabs
    const unsubscribe = sync.subscribe(atomKey, (newValue: T) => {
      setValue(newValue);
    });

    return unsubscribe;
  }, [atomKey, setValue, sync]);

  return [value, setValue];
}

/**
 * Cleanup function for tests or manual cleanup
 */
export function destroyCrossTabSync(): void {
  if (globalSyncInstance) {
    globalSyncInstance.destroy();
    globalSyncInstance = null;
  }
}
