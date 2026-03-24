/**
 * Offline Queue
 * 
 * Queues canvas changes when offline and replays them when connection is restored.
 * Implements conflict resolution and retry logic.
 */

import type {
  QueuedOperation,
  CanvasChange,
  SyncStatus,
  ConflictStrategy,
  SyncConfig,
} from './types';

/**
 * Offline Queue Configuration
 */
export interface OfflineQueueConfig extends Pick<SyncConfig, 'maxQueueSize' | 'conflictStrategy' | 'retry'> {
  /** LocalStorage key for persisting queue */
  storageKey?: string;
  
  /** Enable queue persistence */
  enablePersistence?: boolean;
}

/**
 * Offline Queue Manager
 * 
 * Features:
 * - Automatic queuing when offline
 * - Replay on reconnection
 * - Conflict detection and resolution
 * - LocalStorage persistence
 * - Retry with exponential backoff
 * 
 * @example
 * ```ts
 * const queue = new OfflineQueue({
 *   maxQueueSize: 1000,
 *   conflictStrategy: 'last-write-wins',
 * });
 * 
 * // Queue operation when offline
 * queue.enqueue({
 *   documentId: 'doc-123',
 *   operation: 'update',
 *   data: { ... },
 * });
 * 
 * // Replay when back online
 * await queue.replay(syncAdapter);
 * ```
 */
export class OfflineQueue {
  private config: Required<OfflineQueueConfig>;
  private queue: QueuedOperation[] = [];
  private processing = false;
  
  /**
   *
   */
  constructor(config: OfflineQueueConfig) {
    this.config = {
      maxQueueSize: config.maxQueueSize || 1000,
      conflictStrategy: config.conflictStrategy || 'last-write-wins',
      storageKey: config.storageKey || 'canvas-offline-queue',
      enablePersistence: config.enablePersistence ?? true,
      retry: config.retry || {
        maxRetries: 3,
        backoffMultiplier: 2,
        initialDelay: 1000,
      },
    };
    
    // Load persisted queue
    if (this.config.enablePersistence) {
      this.loadQueue();
    }
  }
  
  /**
   * Add operation to queue
   */
  enqueue(operation: Omit<QueuedOperation, 'id' | 'timestamp' | 'retryCount' | 'status'>): void {
    if (this.queue.length >= this.config.maxQueueSize) {
      throw new Error(`Queue full (max: ${this.config.maxQueueSize})`);
    }
    
    const queued: QueuedOperation = {
      ...operation,
      id: `queue-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      timestamp: Date.now(),
      retryCount: 0,
      status: 'pending',
      maxRetries: operation.maxRetries ?? this.config.retry.maxRetries,
    };
    
    this.queue.push(queued);
    this.persistQueue();
  }
  
  /**
   * Get all queued operations
   */
  getAll(): QueuedOperation[] {
    return [...this.queue];
  }
  
  /**
   * Get queue for specific document
   */
  getByDocument(documentId: string): QueuedOperation[] {
    return this.queue.filter((op) => op.documentId === documentId);
  }
  
  /**
   * Get queue size
   */
  size(): number {
    return this.queue.length;
  }
  
  /**
   * Clear queue
   */
  clear(documentId?: string): void {
    if (documentId) {
      this.queue = this.queue.filter((op) => op.documentId !== documentId);
    } else {
      this.queue = [];
    }
    this.persistQueue();
  }
  
  /**
   * Replay queued operations
   * Returns results for each operation
   */
  async replay(
    pushFn: (documentId: string, changes: CanvasChange[]) => Promise<{ success: boolean; error?: unknown }>
  ): Promise<Array<{ operation: QueuedOperation; success: boolean; error?: unknown }>> {
    if (this.processing) {
      throw new Error('Queue replay already in progress');
    }
    
    this.processing = true;
    const results: Array<{ operation: QueuedOperation; success: boolean; error?: unknown }> = [];
    
    try {
      // Group operations by document for batching
      const byDocument = new Map<string, QueuedOperation[]>();
      
      for (const op of this.queue) {
        if (op.status === 'pending' || op.status === 'error') {
          if (!byDocument.has(op.documentId)) {
            byDocument.set(op.documentId, []);
          }
          byDocument.get(op.documentId)!.push(op);
        }
      }
      
      // Process each document's operations
      for (const [documentId, operations] of byDocument) {
        const changes = this.operationsToChanges(operations);
        
        try {
          const result = await this.retryOperation(
            () => pushFn(documentId, changes),
            operations[0].retryCount
          );
          
          if (result.success) {
            // Mark all operations as synced
            operations.forEach((op) => {
              op.status = 'synced';
              results.push({ operation: op, success: true });
            });
            
            // Remove from queue
            this.queue = this.queue.filter((op) => !operations.includes(op));
          } else {
            // Mark as error and increment retry count
            operations.forEach((op) => {
              op.status = 'error';
              op.retryCount++;
              op.error = JSON.stringify(result.error);
              results.push({ operation: op, success: false, error: result.error });
              
              // Remove if max retries exceeded
              if (op.retryCount >= op.maxRetries) {
                this.queue = this.queue.filter((q) => q.id !== op.id);
              }
            });
          }
        } catch (error) {
          operations.forEach((op) => {
            op.status = 'error';
            op.retryCount++;
            op.error = error instanceof Error ? error.message : 'Unknown error';
            results.push({ operation: op, success: false, error });
          });
        }
      }
      
      this.persistQueue();
      return results;
      
    } finally {
      this.processing = false;
    }
  }
  
  /**
   * Detect conflicts between queued operations and server state
   */
  detectConflicts(serverChanges: CanvasChange[]): Array<{
    queuedOp: QueuedOperation;
    serverChange: CanvasChange;
  }> {
    const conflicts: Array<{ queuedOp: QueuedOperation; serverChange: CanvasChange }> = [];
    
    for (const op of this.queue) {
      for (const serverChange of serverChanges) {
        // Check if operation conflicts with server change
        if (
          op.documentId === serverChange.documentId &&
          op.timestamp < serverChange.timestamp
        ) {
          conflicts.push({ queuedOp: op, serverChange });
        }
      }
    }
    
    return conflicts;
  }
  
  /**
   * Resolve conflict using configured strategy
   */
  resolveConflict(
    queuedOp: QueuedOperation,
    serverChange: CanvasChange,
    strategy?: ConflictStrategy
  ): CanvasChange {
    const resolveStrategy = strategy || this.config.conflictStrategy;
    
    switch (resolveStrategy) {
      case 'server-wins':
        return serverChange;
        
      case 'client-wins':
        return this.operationToChange(queuedOp);
        
      case 'last-write-wins':
        return queuedOp.timestamp > serverChange.timestamp
          ? this.operationToChange(queuedOp)
          : serverChange;
        
      case 'merge':
        // Simple merge strategy - combine data from both
        return {
          ...serverChange,
          data: {
            ...serverChange.data,
            ...queuedOp.data,
          },
        };
        
      case 'manual':
        throw new Error('Manual conflict resolution required');
        
      default:
        return serverChange;
    }
  }
  
  /**
   * Retry operation with exponential backoff
   */
  private async retryOperation<T>(
    fn: () => Promise<T>,
    attemptCount: number
  ): Promise<T> {
    try {
      return await fn();
    } catch (error) {
      if (attemptCount < this.config.retry.maxRetries) {
        const delay = this.config.retry.initialDelay * 
          Math.pow(this.config.retry.backoffMultiplier, attemptCount);
        
        await new Promise((resolve) => setTimeout(resolve, delay));
        return this.retryOperation(fn, attemptCount + 1);
      }
      
      throw error;
    }
  }
  
  /**
   * Convert queued operations to canvas changes
   */
  private operationsToChanges(operations: QueuedOperation[]): CanvasChange[] {
    return operations.map((op) => this.operationToChange(op));
  }
  
  /**
   * Convert queued operation to canvas change
   */
  private operationToChange(operation: QueuedOperation): CanvasChange {
    return {
      id: operation.id,
      documentId: operation.documentId,
      operation: operation.operation,
      timestamp: operation.timestamp,
      userId: 'offline-user', // Should be replaced with actual user ID
      data: operation.data,
      version: 0, // Will be set by server
    };
  }
  
  /**
   * Persist queue to localStorage
   */
  private persistQueue(): void {
    if (!this.config.enablePersistence) return;
    
    try {
      localStorage.setItem(this.config.storageKey, JSON.stringify(this.queue));
    } catch (error) {
      console.error('Failed to persist queue:', error);
    }
  }
  
  /**
   * Load queue from localStorage
   */
  private loadQueue(): void {
    try {
      const stored = localStorage.getItem(this.config.storageKey);
      if (stored) {
        this.queue = JSON.parse(stored);
      }
    } catch (error) {
      console.error('Failed to load queue:', error);
      this.queue = [];
    }
  }
}

/**
 * Create offline queue
 */
export function createOfflineQueue(config: OfflineQueueConfig): OfflineQueue {
  return new OfflineQueue(config);
}
