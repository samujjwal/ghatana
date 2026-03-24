/**
 * Bidirectional Sync Coordinator
 * 
 * Manages synchronization between visual UI builder and Monaco code editor,
 * ensuring changes in one view are reflected in the other in real-time.
 * 
 * Features:
 * - 🔄 Bidirectional synchronization
 * - 🎯 Conflict resolution
 * - 📊 Change tracking and history
 * - ⚡ Debounced updates for performance
 * - 👥 Collaborative sync support
 * 
 * @doc.type class
 * @doc.purpose Bidirectional sync between visual and code editing
 * @doc.layer product
 * @doc.pattern Coordinator
 */

/**
 * Sync event types
 */
export type SyncEventType = 'visual-to-code' | 'code-to-visual' | 'conflict' | 'resolved';

/**
 * Sync event
 */
export interface SyncEvent {
  type: SyncEventType;
  timestamp: number;
  source: 'visual' | 'code';
  data: unknown;
  conflictId?: string;
}

/**
 * Sync configuration
 */
export interface SyncConfig {
  debounceMs: number;
  enableConflictDetection: boolean;
  enableHistory: boolean;
  maxHistorySize: number;
  autoResolveConflicts: boolean;
}

/**
 * Bidirectional Sync Coordinator
 */
export class BidirectionalSyncCoordinator {
  private config: SyncConfig;
  private syncHistory: SyncEvent[] = [];
  private pendingUpdates: Map<string, unknown> = new Map();
  private debounceTimers: Map<string, NodeJS.Timeout> = new Map();
  private listeners: Map<SyncEventType, Set<(event: SyncEvent) => void>> = new Map();
  private lastSyncTimestamp: number = 0;
  private isSyncing: boolean = false;

  constructor(config: Partial<SyncConfig> = {}) {
    this.config = {
      debounceMs: 300,
      enableConflictDetection: true,
      enableHistory: true,
      maxHistorySize: 100,
      autoResolveConflicts: true,
      ...config,
    };

    // Initialize event listeners
    this.listeners.set('visual-to-code', new Set());
    this.listeners.set('code-to-visual', new Set());
    this.listeners.set('conflict', new Set());
    this.listeners.set('resolved', new Set());
  }

  /**
   * Register event listener
   */
  on(eventType: SyncEventType, callback: (event: SyncEvent) => void): () => void {
    const listeners = this.listeners.get(eventType);
    if (listeners) {
      listeners.add(callback);
      return () => listeners.delete(callback);
    }
    return () => {};
  }

  /**
   * Emit sync event
   */
  private emitEvent(event: SyncEvent): void {
    const listeners = this.listeners.get(event.type);
    if (listeners) {
      listeners.forEach(callback => callback(event));
    }

    // Add to history
    if (this.config.enableHistory) {
      this.syncHistory.push(event);
      if (this.syncHistory.length > this.config.maxHistorySize) {
        this.syncHistory.shift();
      }
    }
  }

  /**
   * Sync visual changes to code
   */
  syncVisualToCode(visualData: unknown, debounce = true): void {
    if (debounce) {
      this.debouncedSync('visual-to-code', visualData);
    } else {
      this.performSync('visual-to-code', visualData);
    }
  }

  /**
   * Sync code changes to visual
   */
  syncCodeToVisual(codeData: unknown, debounce = true): void {
    if (debounce) {
      this.debouncedSync('code-to-visual', codeData);
    } else {
      this.performSync('code-to-visual', codeData);
    }
  }

  /**
   * Debounced sync
   */
  private debouncedSync(source: 'visual-to-code' | 'code-to-visual', data: unknown): void {
    // Clear existing timer
    const existingTimer = this.debounceTimers.get(source);
    if (existingTimer) {
      clearTimeout(existingTimer);
    }

    // Set new timer
    const timer = setTimeout(() => {
      this.performSync(source, data);
      this.debounceTimers.delete(source);
    }, this.config.debounceMs);

    this.debounceTimers.set(source, timer);
  }

  /**
   * Perform sync
   */
  private performSync(source: 'visual-to-code' | 'code-to-visual', data: unknown): void {
    if (this.isSyncing) {
      this.pendingUpdates.set(source, data);
      return;
    }

    this.isSyncing = true;

    try {
      const eventType: SyncEventType = source === 'visual-to-code' ? 'visual-to-code' : 'code-to-visual';
      const sourceType = source === 'visual-to-code' ? 'visual' : 'code';

      // Check for conflicts
      if (this.config.enableConflictDetection) {
        const conflict = this.detectConflict(source, data);
        if (conflict) {
          this.handleConflict(conflict);
          this.isSyncing = false;
          return;
        }
      }

      // Emit sync event
      const event: SyncEvent = {
        type: eventType,
        timestamp: Date.now(),
        source: sourceType,
        data,
      };

      this.emitEvent(event);
      this.lastSyncTimestamp = Date.now();
    } finally {
      this.isSyncing = false;

      // Process pending updates
      if (this.pendingUpdates.size > 0) {
        const pending = Array.from(this.pendingUpdates.entries());
        this.pendingUpdates.clear();
        pending.forEach(([source, data]) => {
          this.performSync(source as 'visual-to-code' | 'code-to-visual', data);
        });
      }
    }
  }

  /**
   * Detect conflicts
   */
  private detectConflict(source: string, data: unknown): { id: string; source: string; data: unknown } | null {
    // Check if there are pending updates from the other source
    const otherSource = source === 'visual-to-code' ? 'code-to-visual' : 'visual-to-code';
    const otherData = this.pendingUpdates.get(otherSource);

    if (otherData) {
      return {
        id: `conflict-${Date.now()}`,
        source,
        data,
      };
    }

    return null;
  }

  /**
   * Handle conflict
   */
  private handleConflict(conflict: { id: string; source: string; data: unknown }): void {
    const event: SyncEvent = {
      type: 'conflict',
      timestamp: Date.now(),
      source: conflict.source === 'visual-to-code' ? 'visual' : 'code',
      data: conflict.data,
      conflictId: conflict.id,
    };

    this.emitEvent(event);

    // Auto-resolve if enabled
    if (this.config.autoResolveConflicts) {
      this.resolveConflict(conflict.id, conflict.source === 'visual-to-code' ? 'visual' : 'code');
    }
  }

  /**
   * Resolve conflict
   */
  resolveConflict(conflictId: string, preferredSource: 'visual' | 'code'): void {
    const event: SyncEvent = {
      type: 'resolved',
      timestamp: Date.now(),
      source: preferredSource,
      data: null,
      conflictId,
    };

    this.emitEvent(event);
  }

  /**
   * Get sync history
   */
  getHistory(): SyncEvent[] {
    return [...this.syncHistory];
  }

  /**
   * Clear sync history
   */
  clearHistory(): void {
    this.syncHistory = [];
  }

  /**
   * Get last sync timestamp
   */
  getLastSyncTimestamp(): number {
    return this.lastSyncTimestamp;
  }

  /**
   * Check if currently syncing
   */
  isSyncInProgress(): boolean {
    return this.isSyncing;
  }

  /**
   * Dispose coordinator
   */
  dispose(): void {
    // Clear all timers
    this.debounceTimers.forEach(timer => clearTimeout(timer));
    this.debounceTimers.clear();

    // Clear listeners
    this.listeners.forEach(set => set.clear());
    this.listeners.clear();

    // Clear history
    this.syncHistory = [];
    this.pendingUpdates.clear();
  }
}

/**
 * Create bidirectional sync coordinator
 */
export function createBidirectionalSyncCoordinator(
  config?: Partial<SyncConfig>
): BidirectionalSyncCoordinator {
  return new BidirectionalSyncCoordinator(config);
}
