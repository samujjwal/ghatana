/**
 * Offline Handling & Conflicts - Offline edit queueing and conflict resolution
 * 
 * Implements offline edit persistence, sync retry with exponential backoff,
 * conflict detection, and resolution workflows.
 */

/**
 * Edit operation types
 */
export type EditOperationType = 
  | 'insert' | 'delete' | 'update' 
  | 'move' | 'style' | 'property';

/**
 * Edit conflict resolution strategy
 */
export type ConflictResolution =
  | 'local' | 'remote' | 'manual' | 'merge';

/**
 * Edit operation
 */
export interface EditOperation {
  /** Operation ID */
  id: string;
  /** Operation type */
  type: EditOperationType;
  /** Target element ID */
  elementId: string;
  /** Operation data */
  data: Record<string, unknown>;
  /** Timestamp */
  timestamp: number;
  /** User ID */
  userId: string;
  /** Version/revision number */
  version: number;
}

/**
 * Queued edit with retry info
 */
export interface QueuedEdit {
  /** Edit operation */
  operation: EditOperation;
  /** Queued timestamp */
  queuedAt: number;
  /** Retry attempts */
  retryAttempts: number;
  /** Next retry time */
  nextRetryAt?: number;
  /** Last error */
  lastError?: string;
}

/**
 * Edit conflict
 */
export interface EditConflict {
  /** Conflict ID */
  id: string;
  /** Local operation */
  local: EditOperation;
  /** Remote operation */
  remote: EditOperation;
  /** Conflict type */
  type: 'concurrent' | 'version-mismatch' | 'deleted';
  /** Detected at */
  detectedAt: number;
  /** Resolution strategy */
  resolution?: ConflictResolution;
  /** Resolved at */
  resolvedAt?: number;
  /** Resolved by user */
  resolvedBy?: string;
}

/**
 * Sync status
 */
export type SyncStatus = 'idle' | 'syncing' | 'offline' | 'error';

/**
 * Offline queue configuration
 */
export interface OfflineQueueConfig {
  /** Max queue size */
  maxQueueSize: number;
  /** Max retry attempts */
  maxRetryAttempts: number;
  /** Initial retry delay in ms */
  initialRetryDelay: number;
  /** Max retry delay in ms */
  maxRetryDelay: number;
  /** Enable auto-sync when online */
  autoSync: boolean;
  /** Sync callback */
  syncOperation?: (operation: EditOperation) => Promise<void>;
  /** Conflict handler */
  onConflict?: (conflict: EditConflict) => Promise<ConflictResolution>;
}

/**
 * Offline queue statistics
 */
export interface OfflineQueueStats {
  /** Total queued edits */
  totalQueued: number;
  /** Successfully synced */
  successfullySynced: number;
  /** Failed syncs */
  failedSyncs: number;
  /** Conflicts detected */
  conflictsDetected: number;
  /** Conflicts resolved */
  conflictsResolved: number;
  /** Current queue size */
  currentQueueSize: number;
  /** Pending conflicts */
  pendingConflicts: number;
}

/**
 * Default configuration
 */
const DEFAULT_CONFIG: OfflineQueueConfig = {
  maxQueueSize: 1000,
  maxRetryAttempts: 5,
  initialRetryDelay: 1000,
  maxRetryDelay: 60000,
  autoSync: true,
};

/**
 * Offline Queue Manager
 */
export class OfflineQueueManager {
  private config: OfflineQueueConfig;
  private queue: Map<string, QueuedEdit> = new Map();
  private conflicts: Map<string, EditConflict> = new Map();
  private syncStatus: SyncStatus = 'idle';
  private retryTimer?: NodeJS.Timeout;
  private statistics: OfflineQueueStats = {
    totalQueued: 0,
    successfullySynced: 0,
    failedSyncs: 0,
    conflictsDetected: 0,
    conflictsResolved: 0,
    currentQueueSize: 0,
    pendingConflicts: 0,
  };

  /**
   *
   */
  constructor(config: Partial<OfflineQueueConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
  }

  /**
   * Queue an edit operation
   */
  async queueEdit(operation: EditOperation): Promise<boolean> {
    // Check queue size limit
    if (this.queue.size >= this.config.maxQueueSize) {
      throw new Error('Queue size limit reached');
    }

    const queuedEdit: QueuedEdit = {
      operation,
      queuedAt: Date.now(),
      retryAttempts: 0,
    };

    this.queue.set(operation.id, queuedEdit);
    this.statistics.totalQueued++;
    this.statistics.currentQueueSize = this.queue.size;

    // Persist to local storage
    this.persistQueue();

    // Try to sync immediately if online
    if (this.config.autoSync && this.syncStatus === 'idle') {
      await this.syncQueue();
    }

    return true;
  }

  /**
   * Sync queued edits
   */
  async syncQueue(): Promise<void> {
    if (this.syncStatus === 'syncing') {
      return;
    }

    if (this.queue.size === 0) {
      this.syncStatus = 'idle';
      return;
    }

    this.syncStatus = 'syncing';

    const editsToSync = Array.from(this.queue.values());

    for (const queued of editsToSync) {
      // Check if edit is ready to retry
      if (queued.nextRetryAt && Date.now() < queued.nextRetryAt) {
        continue;
      }

      try {
        // Attempt to sync
        if (this.config.syncOperation) {
          await this.config.syncOperation(queued.operation);
        }

        // Success - remove from queue
        this.queue.delete(queued.operation.id);
        this.statistics.successfullySynced++;
        this.statistics.currentQueueSize = this.queue.size;
        this.persistQueue();
      } catch (error) {
        // Sync failed
        await this.handleSyncFailure(queued, error);
      }
    }

    this.syncStatus = this.queue.size > 0 ? 'offline' : 'idle';

    // Schedule next retry if there are queued edits
    if (this.queue.size > 0) {
      this.scheduleRetry();
    }
  }

  /**
   * Handle sync failure
   */
  private async handleSyncFailure(
    queued: QueuedEdit,
    error: unknown
  ): Promise<void> {
    queued.retryAttempts++;
    queued.lastError = error instanceof Error ? error.message : 'Sync failed';

    this.statistics.failedSyncs++;

    // Check if max retries reached
    if (queued.retryAttempts >= this.config.maxRetryAttempts) {
      // Remove from queue after max retries
      this.queue.delete(queued.operation.id);
      this.statistics.currentQueueSize = this.queue.size;
      this.persistQueue();
      return;
    }

    // Calculate next retry time with exponential backoff
    const delay = Math.min(
      this.config.initialRetryDelay * Math.pow(2, queued.retryAttempts - 1),
      this.config.maxRetryDelay
    );

    queued.nextRetryAt = Date.now() + delay;
    this.persistQueue();
  }

  /**
   * Schedule next retry
   */
  private scheduleRetry(): void {
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
    }

    // Find earliest retry time
    let earliestRetry = Infinity;
    for (const queued of this.queue.values()) {
      if (queued.nextRetryAt && queued.nextRetryAt < earliestRetry) {
        earliestRetry = queued.nextRetryAt;
      }
    }

    if (earliestRetry !== Infinity) {
      const delay = Math.max(0, earliestRetry - Date.now());
      this.retryTimer = setTimeout(() => {
        this.syncQueue();
      }, delay);
    }
  }

  /**
   * Detect conflicts with remote operation
   */
  detectConflict(
    localOp: EditOperation,
    remoteOp: EditOperation
  ): EditConflict | null {
    // Same element?
    if (localOp.elementId !== remoteOp.elementId) {
      return null;
    }

    // Concurrent edits?
    if (Math.abs(localOp.timestamp - remoteOp.timestamp) < 1000) {
      const conflict: EditConflict = {
        id: `conflict-${Date.now()}-${Math.random()}`,
        local: localOp,
        remote: remoteOp,
        type: 'concurrent',
        detectedAt: Date.now(),
      };

      this.conflicts.set(conflict.id, conflict);
      this.statistics.conflictsDetected++;
      this.statistics.pendingConflicts = this.conflicts.size;

      return conflict;
    }

    // Version mismatch?
    if (localOp.version !== remoteOp.version) {
      const conflict: EditConflict = {
        id: `conflict-${Date.now()}-${Math.random()}`,
        local: localOp,
        remote: remoteOp,
        type: 'version-mismatch',
        detectedAt: Date.now(),
      };

      this.conflicts.set(conflict.id, conflict);
      this.statistics.conflictsDetected++;
      this.statistics.pendingConflicts = this.conflicts.size;

      return conflict;
    }

    return null;
  }

  /**
   * Resolve conflict
   */
  async resolveConflict(
    conflictId: string,
    resolution: ConflictResolution,
    userId: string
  ): Promise<boolean> {
    const conflict = this.conflicts.get(conflictId);
    if (!conflict) {
      return false;
    }

    conflict.resolution = resolution;
    conflict.resolvedAt = Date.now();
    conflict.resolvedBy = userId;

    this.statistics.conflictsResolved++;

    // Apply resolution
    switch (resolution) {
      case 'local':
        // Keep local operation, discard remote
        await this.queueEdit(conflict.local);
        break;

      case 'remote':
        // Accept remote, discard local
        this.queue.delete(conflict.local.id);
        this.statistics.currentQueueSize = this.queue.size;
        break;

      case 'merge':
        // Create merged operation
        const merged: EditOperation = {
          ...conflict.local,
          id: `merged-${conflict.local.id}-${conflict.remote.id}`,
          data: {
            ...conflict.remote.data,
            ...conflict.local.data,
          },
          timestamp: Date.now(),
        };
        await this.queueEdit(merged);
        break;

      case 'manual':
        // Manual resolution - handled externally
        break;
    }

    // Remove conflict
    this.conflicts.delete(conflictId);
    this.statistics.pendingConflicts = this.conflicts.size;

    this.persistQueue();
    return true;
  }

  /**
   * Get queued edits
   */
  getQueue(): QueuedEdit[] {
    return Array.from(this.queue.values());
  }

  /**
   * Get pending conflicts
   */
  getConflicts(): EditConflict[] {
    return Array.from(this.conflicts.values());
  }

  /**
   * Get sync status
   */
  getSyncStatus(): SyncStatus {
    return this.syncStatus;
  }

  /**
   * Get statistics
   */
  getStatistics(): OfflineQueueStats {
    return { ...this.statistics };
  }

  /**
   * Clear queue
   */
  clearQueue(): void {
    this.queue.clear();
    this.statistics.currentQueueSize = 0;
    this.persistQueue();
  }

  /**
   * Clear conflicts
   */
  clearConflicts(): void {
    this.conflicts.clear();
    this.statistics.pendingConflicts = 0;
  }

  /**
   * Persist queue to local storage
   */
  private persistQueue(): void {
    // In a real implementation, this would save to IndexedDB or localStorage
    // For testing, we just keep in memory
  }

  /**
   * Load queue from local storage
   */
  loadQueue(): void {
    // In a real implementation, this would load from IndexedDB or localStorage
    // For testing, we start with empty queue
  }

  /**
   * Update configuration
   */
  updateConfig(updates: Partial<OfflineQueueConfig>): void {
    this.config = { ...this.config, ...updates };
  }

  /**
   * Get configuration
   */
  getConfig(): OfflineQueueConfig {
    return { ...this.config };
  }

  /**
   * Clean up
   */
  destroy(): void {
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
    }
    this.queue.clear();
    this.conflicts.clear();
  }
}

/**
 * Create offline queue manager
 */
export function createOfflineQueue(
  config?: Partial<OfflineQueueConfig>
): OfflineQueueManager {
  return new OfflineQueueManager(config);
}
