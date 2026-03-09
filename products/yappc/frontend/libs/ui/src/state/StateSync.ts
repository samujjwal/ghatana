/**
 * State Sync
 *
 * Synchronizes state across browser tabs using BroadcastChannel API.
 *
 * @module state/StateSync
 */

import type { AtomKey } from './StateManager';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface SyncMessage {
  type: 'state-update' | 'state-request' | 'state-response';
  atomKey: AtomKey;
  value?: unknown;
  timestamp: number;
  tabId: string;
}

/**
 *
 */
export interface SyncOptions {
  /**
   * Channel name for BroadcastChannel
   */
  channelName?: string;

  /**
   * Enable debug logging
   */
  debug?: boolean;

  /**
   * Conflict resolution strategy
   */
  conflictResolution?: 'last-write-wins' | 'first-write-wins' | 'manual';

  /**
   * Custom conflict resolver
   */
  onConflict?: (local: unknown, remote: unknown, atomKey: AtomKey) => any;
}

// ============================================================================
// State Sync Manager
// ============================================================================

/**
 *
 */
export class StateSync {
  private channel: BroadcastChannel | null = null;
  private listeners = new Map<AtomKey, Set<(value: unknown) => void>>();
  private tabId: string;
  private options: Required<Omit<SyncOptions, 'onConflict'>> & Pick<SyncOptions, 'onConflict'>;
  private lastUpdates = new Map<AtomKey, number>();

  /**
   *
   */
  constructor(options: SyncOptions = {}) {
    this.tabId = this.generateTabId();
    this.options = {
      channelName: options.channelName || 'ui-state-sync',
      debug: options.debug ?? false,
      conflictResolution: options.conflictResolution || 'last-write-wins',
      onConflict: options.onConflict,
    };

    this.initialize();
  }

  /**
   * Initialize BroadcastChannel
   */
  private initialize(): void {
    if (typeof BroadcastChannel === 'undefined') {
      console.warn('[StateSync] BroadcastChannel not supported, sync disabled');
      return;
    }

    try {
      this.channel = new BroadcastChannel(this.options.channelName);
      this.channel.onmessage = this.handleMessage.bind(this);

      if (this.options.debug) {
        console.log(`[StateSync] Initialized (tab: ${this.tabId})`);
      }

      // Also listen to storage events for fallback
      window.addEventListener('storage', this.handleStorageEvent.bind(this));
    } catch (error) {
      console.error('[StateSync] Initialization error:', error);
    }
  }

  /**
   * Generate unique tab ID
   */
  private generateTabId(): string {
    return `tab-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
  }

  /**
   * Handle incoming sync messages
   */
  private handleMessage(event: MessageEvent<SyncMessage>): void {
    const message = event.data;

    // Ignore messages from self
    if (message.tabId === this.tabId) {
      return;
    }

    if (this.options.debug) {
      console.log('[StateSync] Received message:', message);
    }

    if (message.type === 'state-update') {
      this.handleStateUpdate(message);
    } else if (message.type === 'state-request') {
      this.handleStateRequest(message);
    } else if (message.type === 'state-response') {
      this.handleStateResponse(message);
    }
  }

  /**
   * Handle state update message
   */
  private handleStateUpdate(message: SyncMessage): void {
    const { atomKey, value, timestamp } = message;

    // Check for conflicts
    const lastUpdate = this.lastUpdates.get(atomKey);
    if (lastUpdate !== undefined && lastUpdate > timestamp) {
      if (this.options.conflictResolution === 'first-write-wins') {
        // Ignore remote update
        return;
      }
    }

    // Update local state
    this.lastUpdates.set(atomKey, timestamp);

    // Notify listeners
    const listeners = this.listeners.get(atomKey);
    if (listeners) {
      listeners.forEach((listener) => listener(value));
    }
  }

  /**
   * Handle state request message
   */
  private handleStateRequest(message: SyncMessage): void {
    // Respond with current state (would need to be implemented with actual state access)
    // This is a placeholder for the architecture
  }

  /**
   * Handle state response message
   */
  private handleStateResponse(message: SyncMessage): void {
    // Handle response to state request
  }

  /**
   * Handle storage events (fallback for browsers without BroadcastChannel)
   */
  private handleStorageEvent(event: StorageEvent): void {
    if (!event.key || !event.newValue) return;

    // Parse key to extract atom key
    const prefix = 'ui-state:';
    if (event.key.startsWith(prefix)) {
      const atomKey = event.key.substring(prefix.length);

      try {
        const value = JSON.parse(event.newValue);
        const listeners = this.listeners.get(atomKey);
        if (listeners) {
          listeners.forEach((listener) => listener(value));
        }
      } catch (error) {
        console.error('[StateSync] Error parsing storage event:', error);
      }
    }
  }

  /**
   * Broadcast state update
   */
  broadcastUpdate(atomKey: AtomKey, value: unknown): void {
    if (!this.channel) return;

    const timestamp = Date.now();
    this.lastUpdates.set(atomKey, timestamp);

    const message: SyncMessage = {
      type: 'state-update',
      atomKey,
      value,
      timestamp,
      tabId: this.tabId,
    };

    try {
      this.channel.postMessage(message);

      if (this.options.debug) {
        console.log('[StateSync] Broadcasted update:', message);
      }
    } catch (error) {
      console.error('[StateSync] Error broadcasting:', error);
    }
  }

  /**
   * Subscribe to state changes for an atom
   */
  subscribe(atomKey: AtomKey, listener: (value: unknown) => void): () => void {
    if (!this.listeners.has(atomKey)) {
      this.listeners.set(atomKey, new Set());
    }

    const listeners = this.listeners.get(atomKey)!;
    listeners.add(listener);

    // Return unsubscribe function
    return () => {
      listeners.delete(listener);
      if (listeners.size === 0) {
        this.listeners.delete(atomKey);
      }
    };
  }

  /**
   * Request current state from other tabs
   */
  requestState(atomKey: AtomKey): void {
    if (!this.channel) return;

    const message: SyncMessage = {
      type: 'state-request',
      atomKey,
      timestamp: Date.now(),
      tabId: this.tabId,
    };

    this.channel.postMessage(message);
  }

  /**
   * Close sync connection
   */
  close(): void {
    if (this.channel) {
      this.channel.close();
      this.channel = null;
    }

    window.removeEventListener('storage', this.handleStorageEvent.bind(this));
    this.listeners.clear();
    this.lastUpdates.clear();

    if (this.options.debug) {
      console.log('[StateSync] Closed');
    }
  }

  /**
   * Check if sync is available
   */
  isAvailable(): boolean {
    return typeof BroadcastChannel !== 'undefined';
  }

  /**
   * Get tab ID
   */
  getTabId(): string {
    return this.tabId;
  }

  /**
   * Get active listeners count
   */
  getListenerCount(): number {
    let count = 0;
    for (const listeners of this.listeners.values()) {
      count += listeners.size;
    }
    return count;
  }
}

// ============================================================================
// Global Sync Instance
// ============================================================================

let globalSyncInstance: StateSync | null = null;

/**
 * Get global sync instance
 */
export function getStateSync(options?: SyncOptions): StateSync {
  if (!globalSyncInstance) {
    globalSyncInstance = new StateSync(options);
  }
  return globalSyncInstance;
}

/**
 * Close global sync instance
 */
export function closeStateSync(): void {
  if (globalSyncInstance) {
    globalSyncInstance.close();
    globalSyncInstance = null;
  }
}
