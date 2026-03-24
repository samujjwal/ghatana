/**
 * CRDT core service.
 *
 * <p><b>Purpose</b><br>
 * Provides conflict-free replicated data types with vector clocks,
 * operation logs, and deterministic merging for real-time collaboration.
 *
 * @doc.type module
 * @doc.purpose CRDT core service
 * @doc.layer product
 * @doc.pattern Service
 */

import type {
  VectorClock,
  CRDTOperation,
  OperationLogEntry,
  StateVector,
  CRDTValue,
  MergeResult,
  Conflict,
  ConflictMetadata,
  CRDTState,
  CRDTOperationResult,
  SyncMessage,
  MergeStrategy,
  CRDTConfig,
  Snapshot,
  CRDTStatistics,
} from './types';

/**
 * CRDT core service.
 *
 * <p><b>Purpose</b><br>
 * Main service for conflict-free replicated data types with vector clocks,
 * operation logs, and deterministic merging.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const crdt = new CRDTCore({
 *   replicaId: 'replica-1',
 *   mergeStrategy: 'last-write-wins',
 *   enableTombstones: true,
 *   maxLogSize: 10000,
 *   autoCompactThreshold: 5000,
 * });
 *
 * const operation = crdt.createOperation('insert', 'target-1', { value: 'test' });
 * const result = crdt.applyOperation(operation);
 *
 * const merged = crdt.merge(remoteState);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose CRDT core service
 * @doc.layer product
 * @doc.pattern Service
 */
export class CRDTCore {
  private state: CRDTState;
  private config: CRDTConfig;
  private operationLog: OperationLogEntry[] = [];
  private conflicts: Map<string, Conflict> = new Map();
  private snapshots: Map<string, Snapshot> = new Map();
}

// Re-export types for consumers
export * from './types';

/**
 * Create a new CRDT core service.
 *
 * @param config - CRDT configuration
 *
 * @doc.type constructor
 * @doc.purpose Initialize CRDT core
 * @doc.layer product
 * @doc.pattern Service
 */
constructor(config: CRDTConfig) {
  this.config = config;
  this.state = {
    id: `crdt-${Date.now()}`,
    replicaId: config.replicaId,
    values: new Map(),
    operationLog: [],
    vectorClock: this.createVectorClock(),
    timestamp: Date.now(),
  };
}

  /**
   * Create vector clock.
   *
   * @returns Vector clock
   */
  private createVectorClock(): VectorClock {
  return {
    id: `vc-${Date.now()}`,
    values: new Map([[this.config.replicaId, 0]]),
    timestamp: Date.now(),
  };
}

  /**
   * Increment vector clock.
   *
   * @param clock - Vector clock to increment
   */
  private incrementVectorClock(clock: VectorClock): void {
  const current = clock.values.get(this.config.replicaId) || 0;
  clock.values.set(this.config.replicaId, current + 1);
  clock.timestamp = Date.now();
}

  /**
   * Compare vector clocks.
   *
   * @param a - First clock
   * @param b - Second clock
   * @returns -1 if a < b, 0 if equal, 1 if a > b, 2 if concurrent
   */
  public compareVectorClocks(a: VectorClock, b: VectorClock): number {
  let aGreater = false;
  let bGreater = false;

  const allKeys = new Set([...a.values.keys(), ...b.values.keys()]);

  for (const key of allKeys) {
    const aVal = a.values.get(key) || 0;
    const bVal = b.values.get(key) || 0;

    if (aVal > bVal) aGreater = true;
    if (bVal > aVal) bGreater = true;
  }

  if (aGreater && !bGreater) return 1;
  if (bGreater && !aGreater) return -1;
  if (!aGreater && !bGreater) return 0;
  return 2; // concurrent
}

  /**
   * Create operation.
   *
   * @param type - Operation type
   * @param targetId - Target ID
   * @param data - Operation data
   * @returns Created operation
   */
  public createOperation(
  type: 'insert' | 'delete' | 'update' | 'move',
  targetId: string,
  data: unknown
): CRDTOperation {
  this.incrementVectorClock(this.state.vectorClock);

  return {
    id: `op-${Date.now()}-${Math.random()}`,
    replicaId: this.config.replicaId,
    type,
    targetId,
    vectorClock: JSON.parse(JSON.stringify(this.state.vectorClock)),
    data,
    timestamp: Date.now(),
    parents: Array.from(this.state.values.keys()),
  };
}

  /**
   * Apply operation.
   *
   * @param operation - Operation to apply
   * @returns Operation result
   */
  public applyOperation(operation: CRDTOperation): CRDTOperationResult {
  const startTime = Date.now();

  try {
    // Check for conflicts
    const existingValue = this.state.values.get(operation.targetId);
    if (existingValue && !this.canApplyOperation(operation, existingValue)) {
      const conflict = this.detectConflict(operation, existingValue);
      this.conflicts.set(conflict.id, conflict);

      this.operationLog.push({
        id: `log-${Date.now()}`,
        operation,
        applied: false,
        hasConflict: true,
        conflictMetadata: {
          conflictId: conflict.id,
          type: conflict.type,
          operations: [operation, conflict.operationA],
          timestamp: Date.now(),
        },
      });

      return {
        success: false,
        operationId: operation.id,
        error: `Conflict detected: ${conflict.type}`,
        duration: Date.now() - startTime,
      };
    }

    // Apply operation
    const crdt Value: CRDTValue = {
      id: operation.targetId,
      value: operation.data,
      vectorClock: operation.vectorClock,
      tombstone: operation.type === 'delete',
      timestamp: operation.timestamp,
      replicaId: operation.replicaId,
    };

    this.state.values.set(operation.targetId, crdt Value);

    // Add to operation log
    this.operationLog.push({
      id: `log-${Date.now()}`,
      operation,
      applied: true,
      hasConflict: false,
    });

    // Auto-compact if needed
    if (this.operationLog.length > this.config.autoCompactThreshold) {
      this.compact();
    }

    return {
      success: true,
      operationId: operation.id,
      duration: Date.now() - startTime,
    };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error',
      duration: Date.now() - startTime,
    };
  }
}

  /**
   * Check if operation can be applied.
   *
   * @param operation - Operation
   * @param existing - Existing value
   * @returns True if can apply
   */
  private canApplyOperation(operation: CRDTOperation, existing: CRDTValue): boolean {
  const comparison = this.compareVectorClocks(operation.vectorClock, existing.vectorClock);
  return comparison !== 2; // Not concurrent
}

  /**
   * Detect conflict.
   *
   * @param operation - New operation
   * @param existing - Existing value
   * @returns Detected conflict
   */
  private detectConflict(operation: CRDTOperation, existing: CRDTValue): Conflict {
  return {
    id: `conflict-${Date.now()}`,
    type: 'concurrent-update',
    targetId: operation.targetId,
    operationA: operation,
    operationB: {
      id: `op-existing-${existing.timestamp}`,
      replicaId: existing.replicaId,
      type: 'update',
      targetId: existing.id,
      vectorClock: existing.vectorClock,
      data: existing.value,
      timestamp: existing.timestamp,
      parents: [],
    },
    resolved: false,
    resolutionStrategy: this.config.mergeStrategy,
    timestamp: Date.now(),
  };
}

  /**
   * Merge remote state.
   *
   * @param remoteState - Remote CRDT state
   * @returns Merge result
   */
  public merge(remoteState: CRDTState): MergeResult {
  const startTime = Date.now();
  const mergedState = new Map(this.state.values);
  const conflicts: Conflict[] = [];
  const operationsApplied: CRDTOperation[] = [];

  // Merge remote values
  for (const [key, remoteValue] of remoteState.values) {
    const localValue = this.state.values.get(key);

    if (!localValue) {
      // Remote value doesn't exist locally, add it
      mergedState.set(key, remoteValue);
      operationsApplied.push({
        id: `merged-${key}`,
        replicaId: remoteState.replicaId,
        type: 'insert',
        targetId: key,
        vectorClock: remoteValue.vectorClock,
        data: remoteValue.value,
        timestamp: remoteValue.timestamp,
        parents: [],
      });
    } else {
      // Both have value, check for conflict
      const comparison = this.compareVectorClocks(
        localValue.vectorClock,
        remoteValue.vectorClock
      );

      if (comparison === 2) {
        // Concurrent update - conflict
        const conflict = this.resolveConflict(localValue, remoteValue);
        conflicts.push(conflict);

        if (conflict.resolved) {
          mergedState.set(key, conflict.resolution as CRDTValue);
        }
      } else if (comparison < 0) {
        // Remote is newer
        mergedState.set(key, remoteValue);
      }
      // else local is newer, keep local
    }
  }

  // Update local state
  this.state.values = mergedState;
  this.mergeVectorClocks(this.state.vectorClock, remoteState.vectorClock);

  return {
    id: `merge-${Date.now()}`,
    mergedState,
    conflicts,
    operationsApplied,
    timestamp: Date.now(),
    duration: Date.now() - startTime,
  };
}

  /**
   * Resolve conflict.
   *
   * @param local - Local value
   * @param remote - Remote value
   * @returns Resolved conflict
   */
  private resolveConflict(local: CRDTValue, remote: CRDTValue): Conflict {
  let resolved = local;

  switch (this.config.mergeStrategy) {
    case 'last-write-wins':
      resolved = local.timestamp > remote.timestamp ? local : remote;
      break;
    case 'first-write-wins':
      resolved = local.timestamp < remote.timestamp ? local : remote;
      break;
    case 'merge':
      // Merge values if possible
      if (typeof local.value === 'object' && typeof remote.value === 'object') {
        resolved = {
          ...local,
          value: { ...local.value, ...remote.value },
        };
      }
      break;
  }

  return {
    id: `conflict-${Date.now()}`,
    type: 'concurrent-update',
    targetId: local.id,
    operationA: {
      id: `op-local-${local.timestamp}`,
      replicaId: local.replicaId,
      type: 'update',
      targetId: local.id,
      vectorClock: local.vectorClock,
      data: local.value,
      timestamp: local.timestamp,
      parents: [],
    },
    operationB: {
      id: `op-remote-${remote.timestamp}`,
      replicaId: remote.replicaId,
      type: 'update',
      targetId: remote.id,
      vectorClock: remote.vectorClock,
      data: remote.value,
      timestamp: remote.timestamp,
      parents: [],
    },
    resolved: true,
    resolutionStrategy: this.config.mergeStrategy,
    timestamp: Date.now(),
  };
}

  /**
   * Merge vector clocks.
   *
   * @param local - Local clock
   * @param remote - Remote clock
   */
  private mergeVectorClocks(local: VectorClock, remote: VectorClock): void {
  for(const [key, value] of remote.values) {
  const localValue = local.values.get(key) || 0;
  local.values.set(key, Math.max(localValue, value));
}
local.timestamp = Date.now();
  }

  /**
   * Get state.
   *
   * @returns Current state
   */
  public getState(): CRDTState {
  return this.state;
}

  /**
   * Get value.
   *
   * @param id - Value ID
   * @returns Value or undefined
   */
  public getValue(id: string): CRDTValue | undefined {
  return this.state.values.get(id);
}

  /**
   * Get all values.
   *
   * @returns All values
   */
  public getAllValues(): Map < string, CRDTValue > {
  return new Map(this.state.values);
}

  /**
   * Create snapshot.
   *
   * @param description - Snapshot description
   * @returns Created snapshot
   */
  public createSnapshot(description ?: string): Snapshot {
  const snapshot: Snapshot = {
    id: `snapshot-${Date.now()}`,
    state: new Map(this.state.values),
    vectorClock: JSON.parse(JSON.stringify(this.state.vectorClock)),
    timestamp: Date.now(),
    description,
  };

  this.snapshots.set(snapshot.id, snapshot);
  return snapshot;
}

  /**
   * Restore from snapshot.
   *
   * @param snapshotId - Snapshot ID
   * @returns Operation result
   */
  public restoreSnapshot(snapshotId: string): CRDTOperationResult {
  const startTime = Date.now();

  try {
    const snapshot = this.snapshots.get(snapshotId);
    if (!snapshot) {
      return {
        success: false,
        error: `Snapshot not found: ${snapshotId}`,
        duration: Date.now() - startTime,
      };
    }

    this.state.values = new Map(snapshot.state);
    this.state.vectorClock = JSON.parse(JSON.stringify(snapshot.vectorClock));
    this.state.timestamp = Date.now();

    return {
      success: true,
      duration: Date.now() - startTime,
    };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error',
      duration: Date.now() - startTime,
    };
  }
}

  /**
   * Compact operation log.
   */
  private compact(): void {
  // Keep only recent operations
  const cutoff = this.operationLog.length - this.config.maxLogSize;
  if(cutoff > 0) {
  this.operationLog = this.operationLog.slice(cutoff);
}
  }

  /**
   * Get statistics.
   *
   * @returns CRDT statistics
   */
  public getStatistics(): CRDTStatistics {
  const resolvedConflicts = Array.from(this.conflicts.values()).filter(
    (c) => c.resolved
  ).length;

  return {
    totalOperations: this.operationLog.length,
    totalConflicts: this.conflicts.size,
    resolvedConflicts,
    pendingConflicts: this.conflicts.size - resolvedConflicts,
    operationLogSize: this.operationLog.length,
    stateSize: this.state.values.size,
    lastSyncTimestamp: this.state.timestamp,
  };
}

  /**
   * Export state as JSON.
   *
   * @returns JSON string
   */
  public exportState(): string {
  const exportData = {
    state: {
      id: this.state.id,
      replicaId: this.state.replicaId,
      values: Array.from(this.state.values.entries()),
      vectorClock: {
        id: this.state.vectorClock.id,
        values: Array.from(this.state.vectorClock.values.entries()),
        timestamp: this.state.vectorClock.timestamp,
      },
      timestamp: this.state.timestamp,
    },
    config: this.config,
  };

  return JSON.stringify(exportData, null, 2);
}

  /**
   * Import state from JSON.
   *
   * @param json - JSON string
   * @returns Operation result
   */
  public importState(json: string): CRDTOperationResult {
  const startTime = Date.now();

  try {
    const data = JSON.parse(json);

    this.state = {
      id: data.state.id,
      replicaId: data.state.replicaId,
      values: new Map(data.state.values),
      operationLog: [],
      vectorClock: {
        id: data.state.vectorClock.id,
        values: new Map(data.state.vectorClock.values),
        timestamp: data.state.vectorClock.timestamp,
      },
      timestamp: data.state.timestamp,
    };

    return {
      success: true,
      duration: Date.now() - startTime,
    };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error',
      duration: Date.now() - startTime,
    };
  }
}
}

export type {
  VectorClock,
  CRDTOperation,
  OperationLogEntry,
  StateVector,
  CRDTValue,
  MergeResult,
  Conflict,
  ConflictMetadata,
  CRDTState,
  CRDTOperationResult,
  SyncMessage,
  MergeStrategy,
  CRDTConfig,
  Snapshot,
  CRDTStatistics,
};
