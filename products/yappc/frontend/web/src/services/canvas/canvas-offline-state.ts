/**
 * Canvas Offline State Management
 *
 * Explicit pending/offline state for canvas saves.
 * Users are notified when saves fail and can retry/reconcile.
 *
 * @doc.type module
 * @doc.purpose Explicit offline/pending state for canvas saves
 * @doc.layer product
 * @doc.pattern State Management
 */

import type { CanvasState } from '../../../../apps/api/src/domain/canvas/canvas-schema';

// ============================================================================
// Types
// ============================================================================

/**
 * Save operation status
 */
export type SaveStatus =
  | 'idle'           // No pending operations
  | 'pending'        // Currently saving
  | 'offline'        // Queued for retry when online
  | 'failed'         // Failed with error, needs user action
  | 'conflict'       // Conflict with server version, needs reconciliation
  | 'synced';        // Successfully saved

/**
 * Pending save operation
 */
export interface PendingSave {
  id: string;
  projectId: string;
  canvasId: string;
  state: CanvasState;
  timestamp: number;
  attempts: number;
  maxAttempts: number;
  error?: string;
  lastErrorAt?: number;
}

/**
 * Save result
 */
export interface SaveResult {
  success: boolean;
  status: SaveStatus;
  pendingId?: string;
  error?: string;
  retryable?: boolean;
  serverVersion?: number;
  conflictResolution?: 'use_local' | 'use_server' | 'merge';
}

/**
 * Offline queue
 */
export interface OfflineQueue {
  operations: PendingSave[];
  lastSyncAttempt: number | null;
  isOnline: boolean;
}

/**
 * Save notification
 */
export interface SaveNotification {
  type: 'success' | 'warning' | 'error';
  message: string;
  action?: 'retry' | 'reconcile' | 'dismiss';
  pendingId?: string;
}

// ============================================================================
// Offline Queue Manager
// ============================================================================

const STORAGE_KEY = 'canvas-offline-queue';
const MAX_RETRY_ATTEMPTS = 5;
const RETRY_DELAY_BASE = 5000; // 5 seconds

/**
 * Load offline queue from storage
 */
export function loadOfflineQueue(): OfflineQueue {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return { operations: [], lastSyncAttempt: null, isOnline: navigator.onLine };
    }
    const parsed = JSON.parse(raw) as OfflineQueue;
    return { ...parsed, isOnline: navigator.onLine };
  } catch {
    return { operations: [], lastSyncAttempt: null, isOnline: navigator.onLine };
  }
}

/**
 * Save offline queue to storage
 */
export function persistOfflineQueue(queue: OfflineQueue): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(queue));
  } catch {
    // Silent fail - queue will be lost on page reload
  }
}

/**
 * Add operation to offline queue
 */
export function queueOfflineOperation(
  queue: OfflineQueue,
  projectId: string,
  canvasId: string,
  state: CanvasState,
  error?: string
): { queue: OfflineQueue; pendingId: string } {
  const pendingId = `pending-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

  const operation: PendingSave = {
    id: pendingId,
    projectId,
    canvasId,
    state,
    timestamp: Date.now(),
    attempts: 1,
    maxAttempts: MAX_RETRY_ATTEMPTS,
    error,
    lastErrorAt: Date.now(),
  };

  const newQueue: OfflineQueue = {
    ...queue,
    operations: [...queue.operations, operation],
    lastSyncAttempt: Date.now(),
  };

  persistOfflineQueue(newQueue);

  return { queue: newQueue, pendingId };
}

/**
 * Mark operation as completed
 */
export function completeOperation(queue: OfflineQueue, pendingId: string): OfflineQueue {
  const newQueue: OfflineQueue = {
    ...queue,
    operations: queue.operations.filter(op => op.id !== pendingId),
  };

  persistOfflineQueue(newQueue);
  return newQueue;
}

/**
 * Increment operation attempt count
 */
export function incrementAttempt(queue: OfflineQueue, pendingId: string): OfflineQueue {
  const newQueue: OfflineQueue = {
    ...queue,
    operations: queue.operations.map(op =>
      op.id === pendingId
        ? { ...op, attempts: op.attempts + 1, lastErrorAt: Date.now() }
        : op
    ),
  };

  persistOfflineQueue(newQueue);
  return newQueue;
}

/**
 * Get retry delay for operation (exponential backoff)
 */
export function getRetryDelay(attempts: number): number {
  return RETRY_DELAY_BASE * Math.pow(2, attempts - 1);
}

/**
 * Check if operation should be retried
 */
export function shouldRetry(operation: PendingSave): boolean {
  return operation.attempts < operation.maxAttempts;
}

/**
 * Get operations ready for retry
 */
export function getRetryableOperations(queue: OfflineQueue): PendingSave[] {
  const now = Date.now();
  return queue.operations.filter(op => {
    if (op.attempts >= op.maxAttempts) return false;
    const delay = getRetryDelay(op.attempts);
    return !op.lastErrorAt || now - op.lastErrorAt > delay;
  });
}

// ============================================================================
// Save State Manager
// ============================================================================

/**
 * Save state manager for tracking canvas save status
 */
export class CanvasSaveStateManager {
  private saveStatus: Map<string, SaveStatus> = new Map();
  private listeners: Set<(projectId: string, status: SaveStatus) => void> = new Set();
  private queue: OfflineQueue;

  constructor() {
    this.queue = loadOfflineQueue();
    this.setupOnlineListener();
  }

  /**
   * Subscribe to save status changes
   */
  public subscribe(listener: (projectId: string, status: SaveStatus) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notifyListeners(projectId: string, status: SaveStatus): void {
    this.listeners.forEach(listener => listener(projectId, status));
  }

  /**
   * Get current save status for project
   */
  public getStatus(projectId: string): SaveStatus {
    return this.saveStatus.get(projectId) || 'idle';
  }

  /**
   * Set save status
   */
  public setStatus(projectId: string, status: SaveStatus): void {
    this.saveStatus.set(projectId, status);
    this.notifyListeners(projectId, status);
  }

  /**
   * Start save operation
   */
  public startSave(projectId: string): void {
    this.setStatus(projectId, 'pending');
  }

  /**
   * Mark save as successful
   */
  public saveSuccess(projectId: string, pendingId?: string): void {
    if (pendingId) {
      this.queue = completeOperation(this.queue, pendingId);
    }
    this.setStatus(projectId, 'synced');
  }

  /**
   * Mark save as failed - queues for retry if appropriate
   */
  public saveFailed(
    projectId: string,
    canvasId: string,
    state: CanvasState,
    error: string,
    retryable: boolean
  ): SaveResult {
    if (!retryable) {
      this.setStatus(projectId, 'failed');
      return {
        success: false,
        status: 'failed',
        error,
        retryable: false,
      };
    }

    // Queue for offline retry
    const { queue, pendingId } = queueOfflineOperation(
      this.queue,
      projectId,
      canvasId,
      state,
      error
    );
    this.queue = queue;

    const status = navigator.onLine ? 'failed' : 'offline';
    this.setStatus(projectId, status);

    return {
      success: false,
      status,
      pendingId,
      error,
      retryable: true,
    };
  }

  /**
   * Detect conflict with server version
   */
  public detectConflict(
    projectId: string,
    serverVersion: number
  ): SaveResult {
    this.setStatus(projectId, 'conflict');
    return {
      success: false,
      status: 'conflict',
      error: 'Conflict detected: server has newer version',
      serverVersion,
      retryable: false,
    };
  }

  /**
   * Retry failed save
   */
  public retry(projectId: string, pendingId: string): void {
    this.queue = incrementAttempt(this.queue, pendingId);
    this.setStatus(projectId, 'pending');
  }

  /**
   * Get pending operations for project
   */
  public getPendingOperations(projectId: string): PendingSave[] {
    return this.queue.operations.filter(op => op.projectId === projectId);
  }

  /**
   * Check if any operations are pending
   */
  public hasPendingOperations(): boolean {
    return this.queue.operations.length > 0;
  }

  /**
   * Process retryable operations
   */
  public processRetries(): PendingSave[] {
    return getRetryableOperations(this.queue);
  }

  /**
   * Clear all pending operations
   */
  public clear(): void {
    this.queue = { operations: [], lastSyncAttempt: null, isOnline: navigator.onLine };
    persistOfflineQueue(this.queue);
    this.saveStatus.clear();
  }

  private setupOnlineListener(): void {
    window.addEventListener('online', () => {
      this.queue = { ...this.queue, isOnline: true };
      // Trigger retry of offline operations
      const retryable = this.processRetries();
      if (retryable.length > 0) {
        retryable.forEach(op => {
          this.setStatus(op.projectId, 'pending');
        });
      }
    });

    window.addEventListener('offline', () => {
      this.queue = { ...this.queue, isOnline: false };
      // Mark pending saves as offline
      this.saveStatus.forEach((status, projectId) => {
        if (status === 'pending') {
          this.setStatus(projectId, 'offline');
        }
      });
    });
  }
}

// ============================================================================
// Notification Generator
// ============================================================================

/**
 * Generate user notification for save status
 */
export function generateSaveNotification(
  status: SaveStatus,
  pendingId?: string,
  error?: string,
  attempts?: number
): SaveNotification | null {
  switch (status) {
    case 'pending':
      return {
        type: 'warning',
        message: 'Saving canvas...',
      };

    case 'offline':
      return {
        type: 'warning',
        message: 'Canvas saved locally. Will sync when online.',
        action: 'dismiss',
        pendingId,
      };

    case 'failed':
      return {
        type: 'error',
        message: error || `Save failed after ${attempts} attempts. Click to retry.`,
        action: 'retry',
        pendingId,
      };

    case 'conflict':
      return {
        type: 'error',
        message: 'Conflict detected: your changes conflict with the server version.',
        action: 'reconcile',
        pendingId,
      };

    case 'synced':
      return {
        type: 'success',
        message: 'Canvas saved successfully',
      };

    case 'idle':
    default:
      return null;
  }
}

// ============================================================================
// Reconciliation
// ============================================================================

/**
 * Reconciliation strategy
 */
export type ReconciliationStrategy = 'use_local' | 'use_server' | 'merge';

/**
 * Reconcile conflicting versions
 */
export function reconcileVersions(
  local: CanvasState,
  server: CanvasState,
  strategy: ReconciliationStrategy
): { result: CanvasState; appliedStrategy: ReconciliationStrategy } {
  switch (strategy) {
    case 'use_local':
      return { result: local, appliedStrategy: 'use_local' };

    case 'use_server':
      return { result: server, appliedStrategy: 'use_server' };

    case 'merge':
      // Simple merge: combine nodes and connections, use latest viewport
      const merged: CanvasState = {
        ...server,
        nodes: [...server.nodes, ...local.nodes.filter(n => !server.nodes.find(sn => sn.id === n.id))],
        connections: [...server.connections, ...local.connections.filter(c => !server.connections.find(sc => sc.id === c.id))],
        lastSaved: new Date().toISOString(),
      };
      return { result: merged, appliedStrategy: 'merge' };

    default:
      return { result: server, appliedStrategy: 'use_server' };
  }
}
