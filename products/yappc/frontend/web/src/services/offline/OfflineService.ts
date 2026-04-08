/**
 * Offline Service
 *
 * Manages offline detection, operation queueing, and synchronization.
 * Provides storage for pending operations when the user is offline.
 *
 * @doc.type service
 * @doc.purpose Offline mode support and operation queueing
 * @doc.layer product
 * @doc.pattern Service Layer
 */

// ============================================================================
// Types
// ============================================================================

export interface QueuedOperation {
  id: string;
  type: 'create' | 'update' | 'delete' | 'custom';
  endpoint: string;
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  headers?: Record<string, string>;
  timestamp: number;
  retries: number;
}

export interface OfflineState {
  isOnline: boolean;
  queuedOperations: QueuedOperation[];
  lastSyncTime: number | null;
}

export interface OfflineServiceOptions {
  storageKey?: string;
  maxQueueSize?: number;
  syncOnOnline?: boolean;
}

// ============================================================================
// Storage Helpers
// ============================================================================

class StorageManager {
  constructor(private storageKey: string) {}

  save(data: unknown): void {
    try {
      localStorage.setItem(this.storageKey, JSON.stringify(data));
    } catch (error) {
      console.error('Failed to save to localStorage:', error);
    }
  }

  load<T>(): T | null {
    try {
      const data = localStorage.getItem(this.storageKey);
      return data ? JSON.parse(data) : null;
    } catch (error) {
      console.error('Failed to load from localStorage:', error);
      return null;
    }
  }

  clear(): void {
    try {
      localStorage.removeItem(this.storageKey);
    } catch (error) {
      console.error('Failed to clear localStorage:', error);
    }
  }
}

// ============================================================================
// Offline Service Implementation
// ============================================================================

export class OfflineService {
  private storage: StorageManager;
  private state: OfflineState;
  private listeners: Set<(state: OfflineState) => void> = new Set();
  private syncInProgress = false;
  private options: Required<OfflineServiceOptions>;

  constructor(options: OfflineServiceOptions = {}) {
    this.options = {
      storageKey: options.storageKey || 'offline-state',
      maxQueueSize: options.maxQueueSize || 100,
      syncOnOnline: options.syncOnOnline !== false,
    };

    this.storage = new StorageManager(this.options.storageKey);
    this.state = this.loadState();

    // Initialize online status
    this.state.isOnline = navigator.onLine;
    this.setupEventListeners();
  }

  // ========================================================================
  // State Management
  // ========================================================================

  private loadState(): OfflineState {
    const saved = this.storage.load<OfflineState>();
    return {
      isOnline: navigator.onLine,
      queuedOperations: saved?.queuedOperations || [],
      lastSyncTime: saved?.lastSyncTime || null,
    };
  }

  private saveState(): void {
    this.storage.save(this.state);
    this.notifyListeners();
  }

  private notifyListeners(): void {
    this.listeners.forEach(listener => listener(this.state));
  }

  // ========================================================================
  // Event Listeners
  // ========================================================================

  private setupEventListeners(): void {
    const handleOnline = () => {
      this.state.isOnline = true;
      this.saveState();

      if (this.options.syncOnOnline) {
        this.sync().catch(console.error);
      }
    };

    const handleOffline = () => {
      this.state.isOnline = false;
      this.saveState();
    };

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
  }

  // ========================================================================
  // Public API
  // ========================================================================

  /**
   * Get current offline state
   */
  getState(): OfflineState {
    return { ...this.state };
  }

  /**
   * Subscribe to state changes
   */
  subscribe(listener: (state: OfflineState) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * Check if currently online
   */
  isOnline(): boolean {
    return this.state.isOnline;
  }

  /**
   * Queue an operation for offline execution
   */
  queueOperation(operation: Omit<QueuedOperation, 'id' | 'timestamp' | 'retries'>): string {
    if (!this.state.isOnline) {
      const queued: QueuedOperation = {
        ...operation,
        id: `op-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        timestamp: Date.now(),
        retries: 0,
      };

      // Check queue size limit
      if (this.state.queuedOperations.length >= this.options.maxQueueSize) {
        // Remove oldest operation
        this.state.queuedOperations.shift();
      }

      this.state.queuedOperations.push(queued);
      this.saveState();

      return queued.id;
    }

    // If online, execute immediately
    throw new Error('Cannot queue operation while online');
  }

  /**
   * Remove a queued operation
   */
  removeOperation(operationId: string): void {
    this.state.queuedOperations = this.state.queuedOperations.filter(
      op => op.id !== operationId
    );
    this.saveState();
  }

  /**
   * Clear all queued operations
   */
  clearQueue(): void {
    this.state.queuedOperations = [];
    this.saveState();
  }

  /**
   * Sync queued operations with the server
   */
  async sync(): Promise<{ success: number; failed: number }> {
    if (!this.state.isOnline || this.syncInProgress) {
      return { success: 0, failed: 0 };
    }

    this.syncInProgress = true;
    let success = 0;
    let failed = 0;

    const operationsToRetry: QueuedOperation[] = [];

    for (const operation of this.state.queuedOperations) {
      try {
        await this.executeOperation(operation);
        success++;
      } catch (error) {
        operation.retries++;

        // Retry up to 3 times
        if (operation.retries < 3) {
          operationsToRetry.push(operation);
        } else {
          failed++;
        }
      }
    }

    // Update queue with operations to retry
    this.state.queuedOperations = operationsToRetry;
    this.state.lastSyncTime = Date.now();
    this.saveState();

    this.syncInProgress = false;

    return { success, failed };
  }

  /**
   * Execute a queued operation
   */
  private async executeOperation(operation: QueuedOperation): Promise<unknown> {
    const url = operation.endpoint;
    const options: RequestInit = {
      method: operation.method,
      headers: {
        'Content-Type': 'application/json',
        ...operation.headers,
      },
    };

    if (operation.body && ['POST', 'PUT', 'PATCH'].includes(operation.method)) {
      options.body = JSON.stringify(operation.body);
    }

    const response = await fetch(url, options);

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    return response.json();
  }

  /**
   * Get statistics about the offline queue
   */
  getQueueStats(): {
    total: number;
    byType: Record<string, number>;
    oldestTimestamp: number | null;
  } {
    const byType: Record<string, number> = {};
    let oldestTimestamp: number | null = null;

    for (const op of this.state.queuedOperations) {
      byType[op.type] = (byType[op.type] || 0) + 1;
      if (!oldestTimestamp || op.timestamp < oldestTimestamp) {
        oldestTimestamp = op.timestamp;
      }
    }

    return {
      total: this.state.queuedOperations.length,
      byType,
      oldestTimestamp,
    };
  }

  /**
   * Cleanup old operations (older than 24 hours)
   */
  cleanupOldOperations(maxAge: number = 24 * 60 * 60 * 1000): number {
    const now = Date.now();
    const before = this.state.queuedOperations.length;

    this.state.queuedOperations = this.state.queuedOperations.filter(
      op => now - op.timestamp < maxAge
    );

    const removed = before - this.state.queuedOperations.length;
    if (removed > 0) {
      this.saveState();
    }

    return removed;
  }

  /**
   * Destroy the service and clean up
   */
  destroy(): void {
    this.listeners.clear();
    this.storage.clear();
  }
}

// ============================================================================
// Singleton Instance
// ============================================================================

let offlineServiceInstance: OfflineService | null = null;

export function getOfflineService(options?: OfflineServiceOptions): OfflineService {
  if (!offlineServiceInstance) {
    offlineServiceInstance = new OfflineService(options);
  }
  return offlineServiceInstance;
}

export function destroyOfflineService(): void {
  if (offlineServiceInstance) {
    offlineServiceInstance.destroy();
    offlineServiceInstance = null;
  }
}
