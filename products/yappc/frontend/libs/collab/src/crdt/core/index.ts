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
  private readonly config: CRDTConfig;
  private operationLog: OperationLogEntry[] = [];
  private readonly conflicts: Map<string, Conflict> = new Map();
  private readonly snapshots: Map<string, Snapshot> = new Map();

  public constructor(config: CRDTConfig) {
    this.config = config;
    this.state = {
      id: `crdt-${Date.now()}`,
      replicaId: config.replicaId,
      values: new Map(),
      operationLog: this.operationLog,
      vectorClock: this.createVectorClock(),
      timestamp: Date.now(),
    };
  }

  private createVectorClock(): VectorClock {
    return {
      id: `vc-${Date.now()}`,
      values: new Map([[this.config.replicaId, 0]]),
      timestamp: Date.now(),
    };
  }

  private cloneVectorClock(clock: VectorClock): VectorClock {
    return {
      id: clock.id,
      values: new Map(clock.values),
      timestamp: clock.timestamp,
    };
  }

  private cloneValue(value: CRDTValue): CRDTValue {
    return {
      ...value,
      vectorClock: this.cloneVectorClock(value.vectorClock),
    };
  }

  private incrementVectorClock(clock: VectorClock): void {
    const current = clock.values.get(this.config.replicaId) ?? 0;
    clock.values.set(this.config.replicaId, current + 1);
    clock.timestamp = Date.now();
  }

  public compareVectorClocks(a: VectorClock, b: VectorClock): number {
    let aGreater = false;
    let bGreater = false;

    const allKeys = new Set([...a.values.keys(), ...b.values.keys()]);

    for (const key of allKeys) {
      const aValue = a.values.get(key) ?? 0;
      const bValue = b.values.get(key) ?? 0;

      if (aValue > bValue) {
        aGreater = true;
      }
      if (bValue > aValue) {
        bGreater = true;
      }
    }

    if (aGreater && !bGreater) {
      return 1;
    }
    if (bGreater && !aGreater) {
      return -1;
    }
    if (!aGreater && !bGreater) {
      return 0;
    }
    return 2;
  }

  public createOperation(
    type: 'insert' | 'delete' | 'update' | 'move',
    targetId: string,
    data: unknown,
  ): CRDTOperation {
    this.incrementVectorClock(this.state.vectorClock);

    return {
      id: `op-${Date.now()}-${Math.random().toString(16).slice(2)}`,
      replicaId: this.config.replicaId,
      type,
      targetId,
      vectorClock: this.cloneVectorClock(this.state.vectorClock),
      data,
      timestamp: Date.now(),
      parents: Array.from(this.state.values.keys()),
    };
  }

  public applyOperation(operation: CRDTOperation): CRDTOperationResult {
    const startTime = Date.now();

    try {
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
        this.state.operationLog = this.operationLog;

        return {
          success: false,
          operationId: operation.id,
          error: `Conflict detected: ${conflict.type}`,
          duration: Date.now() - startTime,
        };
      }

      const crdtValue: CRDTValue = {
        id: operation.targetId,
        value: operation.data,
        vectorClock: this.cloneVectorClock(operation.vectorClock),
        tombstone: operation.type === 'delete',
        timestamp: operation.timestamp,
        replicaId: operation.replicaId,
      };

      this.state.values.set(operation.targetId, crdtValue);
      this.operationLog.push({
        id: `log-${Date.now()}`,
        operation,
        applied: true,
        hasConflict: false,
      });
      this.state.operationLog = this.operationLog;
      this.state.timestamp = Date.now();

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
        operationId: operation.id,
        error: error instanceof Error ? error.message : 'Unknown error',
        duration: Date.now() - startTime,
      };
    }
  }

  private canApplyOperation(operation: CRDTOperation, existing: CRDTValue): boolean {
    const comparison = this.compareVectorClocks(operation.vectorClock, existing.vectorClock);
    return comparison !== 2;
  }

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
        vectorClock: this.cloneVectorClock(existing.vectorClock),
        data: existing.value,
        timestamp: existing.timestamp,
        parents: [],
      },
      resolved: false,
      resolutionStrategy: this.config.mergeStrategy,
      timestamp: Date.now(),
    };
  }

  public merge(remoteState: CRDTState): MergeResult {
    const startTime = Date.now();
    const mergedState = new Map<string, CRDTValue>();
    const conflicts: Conflict[] = [];
    const operationsApplied: CRDTOperation[] = [];

    for (const [key, localValue] of this.state.values.entries()) {
      mergedState.set(key, this.cloneValue(localValue));
    }

    for (const [key, remoteValue] of remoteState.values.entries()) {
      const localValue = mergedState.get(key);

      if (!localValue) {
        mergedState.set(key, this.cloneValue(remoteValue));
        operationsApplied.push({
          id: `merged-${key}`,
          replicaId: remoteState.replicaId,
          type: remoteValue.tombstone ? 'delete' : 'insert',
          targetId: key,
          vectorClock: this.cloneVectorClock(remoteValue.vectorClock),
          data: remoteValue.value,
          timestamp: remoteValue.timestamp,
          parents: [],
        });
        continue;
      }

      const comparison = this.compareVectorClocks(localValue.vectorClock, remoteValue.vectorClock);
      if (comparison === 2) {
        const resolution = this.resolveConflict(localValue, remoteValue);
        conflicts.push(resolution.conflict);
        mergedState.set(key, resolution.resolvedValue);
      } else if (comparison < 0) {
        mergedState.set(key, this.cloneValue(remoteValue));
      }
    }

    this.state.values = mergedState;
    this.mergeVectorClocks(this.state.vectorClock, remoteState.vectorClock);
    this.state.timestamp = Date.now();

    return {
      id: `merge-${Date.now()}`,
      mergedState,
      conflicts,
      operationsApplied,
      timestamp: Date.now(),
      duration: Date.now() - startTime,
    };
  }

  private resolveConflict(
    local: CRDTValue,
    remote: CRDTValue,
  ): { conflict: Conflict; resolvedValue: CRDTValue } {
    let resolvedValue = this.cloneValue(local);

    switch (this.config.mergeStrategy) {
      case 'last-write-wins':
        resolvedValue = this.cloneValue(local.timestamp > remote.timestamp ? local : remote);
        break;
      case 'first-write-wins':
        resolvedValue = this.cloneValue(local.timestamp < remote.timestamp ? local : remote);
        break;
      case 'merge':
        if (
          typeof local.value === 'object' &&
          local.value !== null &&
          typeof remote.value === 'object' &&
          remote.value !== null
        ) {
          resolvedValue = {
            ...this.cloneValue(local),
            value: {
              ...(local.value as Record<string, unknown>),
              ...(remote.value as Record<string, unknown>),
            },
            timestamp: Math.max(local.timestamp, remote.timestamp),
          };
        } else {
          resolvedValue = this.cloneValue(remote.timestamp >= local.timestamp ? remote : local);
        }
        break;
      case 'custom':
      default:
        resolvedValue = this.cloneValue(remote.timestamp >= local.timestamp ? remote : local);
        break;
    }

    return {
      conflict: {
        id: `conflict-${Date.now()}`,
        type: 'concurrent-update',
        targetId: local.id,
        operationA: {
          id: `op-local-${local.timestamp}`,
          replicaId: local.replicaId,
          type: 'update',
          targetId: local.id,
          vectorClock: this.cloneVectorClock(local.vectorClock),
          data: local.value,
          timestamp: local.timestamp,
          parents: [],
        },
        operationB: {
          id: `op-remote-${remote.timestamp}`,
          replicaId: remote.replicaId,
          type: 'update',
          targetId: remote.id,
          vectorClock: this.cloneVectorClock(remote.vectorClock),
          data: remote.value,
          timestamp: remote.timestamp,
          parents: [],
        },
        resolved: true,
        resolutionStrategy: this.config.mergeStrategy,
        timestamp: Date.now(),
      },
      resolvedValue,
    };
  }

  private mergeVectorClocks(local: VectorClock, remote: VectorClock): void {
    for (const [key, value] of remote.values.entries()) {
      const localValue = local.values.get(key) ?? 0;
      local.values.set(key, Math.max(localValue, value));
    }
    local.timestamp = Date.now();
  }

  public getState(): CRDTState {
    return this.state;
  }

  public getValue(id: string): CRDTValue | undefined {
    return this.state.values.get(id);
  }

  public getAllValues(): Map<string, CRDTValue> {
    return new Map(this.state.values);
  }

  public createSnapshot(description?: string): Snapshot {
    const snapshot: Snapshot = {
      id: `snapshot-${Date.now()}`,
      state: new Map(this.state.values),
      vectorClock: this.cloneVectorClock(this.state.vectorClock),
      timestamp: Date.now(),
      description,
    };

    this.snapshots.set(snapshot.id, snapshot);
    return snapshot;
  }

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
      this.state.vectorClock = this.cloneVectorClock(snapshot.vectorClock);
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

  private compact(): void {
    const cutoff = this.operationLog.length - this.config.maxLogSize;
    if (cutoff > 0) {
      this.operationLog = this.operationLog.slice(cutoff);
      this.state.operationLog = this.operationLog;
    }
  }

  public getStatistics(): CRDTStatistics {
    const resolvedConflicts = Array.from(this.conflicts.values()).filter((conflict) => conflict.resolved).length;

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

  public importState(json: string): CRDTOperationResult {
    const startTime = Date.now();

    try {
      const data = JSON.parse(json) as {
        state: {
          id: string;
          replicaId: string;
          values: Array<[string, CRDTValue]>;
          vectorClock: { id: string; values: Array<[string, number]>; timestamp: number };
          timestamp: number;
        };
      };

      this.state = {
        id: data.state.id,
        replicaId: data.state.replicaId,
        values: new Map(data.state.values),
        operationLog: this.operationLog,
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

export * from './types';

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
