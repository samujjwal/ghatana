/**
 * Cross-Tab State Synchronization
 *
 * Synchronizes Jotai state across browser tabs using localStorage events.
 * Implements conflict resolution with last-write-wins strategy.
 *
 * @module state/cross-tab-sync
 * @doc.type module
 * @doc.purpose Cross-tab state synchronization for multi-tab support
 * @doc.layer platform
 * @doc.pattern Observer/PubSub
 */

// ============================================================================
// Types
// ============================================================================

/**
 * Storage event data structure
 */
export interface StorageEvent {
  /** Atom key identifier */
  key: string;
  /** New value (serialized) */
  value: unknown;
  /** Timestamp of change */
  timestamp: number;
  /** Tab ID that initiated the change */
  tabId: string;
}

/**
 * Sync configuration options
 */
export interface SyncConfig {
  /** Storage key prefix */
  storagePrefix?: string;
  /** Enable debug logging */
  debug?: boolean;
  /** Debounce delay (ms) */
  debounceDelay?: number;
  /** Keys to exclude from sync */
  excludeKeys?: string[];
}

/**
 * Sync event listener callback
 */
export type SyncListener = (event: StorageEvent) => void;

// ============================================================================
// Constants
// ============================================================================

const DEFAULT_CONFIG: Required<SyncConfig> = {
  storagePrefix: 'jotai-state:',
  debug: false,
  debounceDelay: 50,
  excludeKeys: [],
};

const TAB_ID_KEY = 'tab-id';

// ============================================================================
// Tab Management
// ============================================================================

/**
 * Get unique tab ID
 *
 * Generates or retrieves unique identifier for current tab.
 * Persists in sessionStorage (unique per tab).
 */
function getTabId(): string {
  let tabId = sessionStorage.getItem(TAB_ID_KEY);
  if (!tabId) {
    tabId = `tab-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
    sessionStorage.setItem(TAB_ID_KEY, tabId);
  }
  return tabId;
}

// ============================================================================
// Storage Operations
// ============================================================================

/**
 * Write state to localStorage with metadata
 *
 * @param key - Atom key
 * @param value - Value to store
 * @param config - Sync configuration
 */
function writeToStorage(
  key: string,
  value: unknown,
  config: Required<SyncConfig>
): void {
  if (config.excludeKeys.includes(key)) {
    return;
  }

  const event: StorageEvent = {
    key,
    value,
    timestamp: Date.now(),
    tabId: getTabId(),
  };

  const storageKey = `${config.storagePrefix}${key}`;

  try {
    localStorage.setItem(storageKey, JSON.stringify(event));

    if (config.debug) {
      console.log('[CrossTabSync] Write:', {
        key,
        value,
        timestamp: event.timestamp,
      });
    }
  } catch (error) {
    console.error('[CrossTabSync] Failed to write to storage:', error);
  }
}

/**
 * Read state from localStorage
 *
 * @param key - Atom key
 * @param config - Sync configuration
 * @returns Parsed storage event or null
 */
function readFromStorage(
  key: string,
  config: Required<SyncConfig>
): StorageEvent | null {
  const storageKey = `${config.storagePrefix}${key}`;

  try {
    const item = localStorage.getItem(storageKey);
    if (!item) {
      return null;
    }

    const event = JSON.parse(item) as StorageEvent;

    if (config.debug) {
      console.log('[CrossTabSync] Read:', { key, value: event.value });
    }

    return event;
  } catch (error) {
    console.error('[CrossTabSync] Failed to read from storage:', error);
    return null;
  }
}

// ============================================================================
// Synchronization Manager
// ============================================================================

/**
 * Global synchronization state
 */
class SyncManager {
  private config: Required<SyncConfig>;
  private listeners = new Set<SyncListener>();
  private debounceTimers = new Map<string, NodeJS.Timeout>();
  private pendingWrites = new Map<string, unknown>();
  private isInitialized = false;
  private storageListener: ((event: Event) => void) | null = null;

  constructor(config: SyncConfig = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
  }

  /**
   * Initialize cross-tab synchronization
   *
   * Sets up storage event listener for incoming changes.
   */
  initialize(): void {
    if (this.isInitialized) {
      return;
    }

    this.storageListener = (event: Event) => {
      const storageEvent = event as globalThis.StorageEvent;

      // Only handle our prefixed keys
      if (!storageEvent.key?.startsWith(this.config.storagePrefix)) {
        return;
      }

      // Ignore changes from same tab
      if (storageEvent.newValue) {
        try {
          const parsed = JSON.parse(storageEvent.newValue) as StorageEvent;
          if (parsed.tabId === getTabId()) {
            return;
          }

          // Extract atom key
          const atomKey = storageEvent.key.slice(
            this.config.storagePrefix.length
          );

          // Notify listeners
          this.notifyListeners({
            ...parsed,
            key: atomKey,
          });

          if (this.config.debug) {
            console.log('[CrossTabSync] Received:', {
              atomKey,
              value: parsed.value,
            });
          }
        } catch (error) {
          console.error('[CrossTabSync] Failed to parse storage event:', error);
        }
      }
    };

    window.addEventListener('storage', this.storageListener);
    this.isInitialized = true;

    if (this.config.debug) {
      console.log('[CrossTabSync] Initialized');
    }
  }

  /**
   * Clean up resources
   */
  destroy(): void {
    if (this.storageListener) {
      window.removeEventListener('storage', this.storageListener);
      this.storageListener = null;
    }

    this.debounceTimers.forEach((timer) => clearTimeout(timer));
    this.debounceTimers.clear();
    this.pendingWrites.clear();
    this.listeners.clear();
    this.isInitialized = false;

    if (this.config.debug) {
      console.log('[CrossTabSync] Destroyed');
    }
  }

  /**
   * Write atom value with debouncing
   *
   * @param key - Atom key
   * @param value - Value to write
   */
  write(key: string, value: unknown): void {
    if (this.config.excludeKeys.includes(key)) {
      return;
    }

    // Store pending write
    this.pendingWrites.set(key, value);

    // Clear existing timer
    const existingTimer = this.debounceTimers.get(key);
    if (existingTimer) {
      clearTimeout(existingTimer);
    }

    // Debounce write
    const timer = setTimeout(() => {
      const pendingValue = this.pendingWrites.get(key);
      if (pendingValue !== undefined) {
        writeToStorage(key, pendingValue, this.config);
        this.pendingWrites.delete(key);
      }
      this.debounceTimers.delete(key);
    }, this.config.debounceDelay);

    this.debounceTimers.set(key, timer);
  }

  /**
   * Read atom value from storage
   *
   * @param key - Atom key
   * @returns Stored value or undefined
   */
  read(key: string): unknown {
    if (this.config.excludeKeys.includes(key)) {
      return undefined;
    }

    const event = readFromStorage(key, this.config);
    return event?.value;
  }

  /**
   * Subscribe to sync events
   *
   * @param listener - Callback for sync events
   * @returns Unsubscribe function
   */
  subscribe(listener: SyncListener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  /**
   * Notify all listeners of sync event
   */
  private notifyListeners(event: StorageEvent): void {
    this.listeners.forEach((listener) => {
      try {
        listener(event);
      } catch (error) {
        console.error('[CrossTabSync] Listener error:', error);
      }
    });
  }

  /**
   * Get sync statistics
   */
  getStatistics(): {
    isInitialized: boolean;
    listenerCount: number;
    pendingWrites: number;
    activeDebounceTimers: number;
  } {
    return {
      isInitialized: this.isInitialized,
      listenerCount: this.listeners.size,
      pendingWrites: this.pendingWrites.size,
      activeDebounceTimers: this.debounceTimers.size,
    };
  }
}

// ============================================================================
// Global Instance
// ============================================================================

let globalSyncManager: SyncManager | null = null;

/**
 * Get or create global sync manager
 */
function getSyncManager(config?: SyncConfig): SyncManager {
  if (!globalSyncManager) {
    globalSyncManager = new SyncManager(config);
  }
  return globalSyncManager;
}

// ============================================================================
// Public API
// ============================================================================

/**
 * Initialize and configure cross-tab state synchronization
 *
 * @param config - Synchronization configuration
 *
 * @example
 * ```tsx
 * // In app root
 * useEffect(() => {
 *   const cleanup = syncStateAcrossTabs({
 *     debug: true,
 *     excludeKeys: ['temp-state', 'ui-flags'],
 *   });
 *   return cleanup;
 * }, []);
 * ```
 *
 * @doc.type function
 * @doc.purpose Initialize cross-tab synchronization
 * @doc.layer platform
 * @doc.pattern Singleton
 */
export function syncStateAcrossTabs(config?: SyncConfig): () => void {
  const manager = getSyncManager(config);
  manager.initialize();
  return () => manager.destroy();
}

/**
 * Write atom value to shared storage
 *
 * @param key - Atom key
 * @param value - Value to sync
 *
 * @doc.type function
 * @doc.purpose Write atom value to cross-tab storage
 * @doc.layer platform
 */
export function writeAtomToStorage(key: string, value: unknown): void {
  const manager = getSyncManager();
  manager.write(key, value);
}

/**
 * Read atom value from shared storage
 *
 * @param key - Atom key
 * @returns Stored value or undefined
 *
 * @doc.type function
 * @doc.purpose Read atom value from cross-tab storage
 * @doc.layer platform
 */
export function readAtomFromStorage(key: string): unknown {
  const manager = getSyncManager();
  return manager.read(key);
}

/**
 * Subscribe to cross-tab sync events
 *
 * @param listener - Callback for sync events
 * @returns Unsubscribe function
 *
 * @doc.type function
 * @doc.purpose Subscribe to cross-tab state changes
 * @doc.layer platform
 */
export function subscribeToSync(listener: SyncListener): () => void {
  const manager = getSyncManager();
  return manager.subscribe(listener);
}

/**
 * Get synchronization statistics
 *
 * @returns Current sync manager statistics
 *
 * @doc.type function
 * @doc.purpose Get sync manager metrics
 * @doc.layer platform
 */
export function getSyncStatistics() {
  const manager = getSyncManager();
  return manager.getStatistics();
}
