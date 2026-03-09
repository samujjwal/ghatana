/**
 * Type definitions for CRDT core.
 *
 * <p><b>Purpose</b><br>
 * Provides comprehensive type definitions for conflict-free replicated data types
 * with vector clocks, operation logs, and deterministic merging.
 *
 * @doc.type module
 * @doc.purpose CRDT core types
 * @doc.layer product
 * @doc.pattern Value Object
 */

/**
 * Vector clock for causality tracking.
 *
 * @doc.type interface
 * @doc.purpose Vector clock definition
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface VectorClock {
  /** Clock ID */
  id: string;
  /** Clock values by replica ID */
  values: Map<string, number>;
  /** Timestamp */
  timestamp: number;
}

/**
 * CRDT operation.
 *
 * @doc.type interface
 * @doc.purpose CRDT operation definition
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface CRDTOperation {
  /** Operation ID */
  id: string;
  /** Replica ID */
  replicaId: string;
  /** Operation type */
  type: 'insert' | 'delete' | 'update' | 'move';
  /** Target ID */
  targetId: string;
  /** Vector clock */
  vectorClock: VectorClock;
  /** Operation data */
  data: unknown;
  /** Timestamp */
  timestamp: number;
  /** Parent operation IDs */
  parents: string[];
}

/**
 * Operation log entry.
 *
 * @doc.type interface
 * @doc.purpose Operation log entry
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface OperationLogEntry {
  /** Entry ID */
  id: string;
  /** Operation */
  operation: CRDTOperation;
  /** Applied flag */
  applied: boolean;
  /** Conflict flag */
  hasConflict: boolean;
  /** Conflict metadata */
  conflictMetadata?: ConflictMetadata;
}

/**
 * State vector for synchronization.
 *
 * @doc.type interface
 * @doc.purpose State vector definition
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface StateVector {
  /** Vector ID */
  id: string;
  /** Clock values by replica ID */
  values: Map<string, number>;
  /** Timestamp */
  timestamp: number;
}

/**
 * CRDT value with metadata.
 *
 * @doc.type interface
 * @doc.purpose CRDT value definition
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface CRDTValue {
  /** Value ID */
  id: string;
  /** Actual value */
  value: unknown;
  /** Vector clock */
  vectorClock: VectorClock;
  /** Tombstone flag */
  tombstone: boolean;
  /** Timestamp */
  timestamp: number;
  /** Replica ID */
  replicaId: string;
}

/**
 * Merge result.
 *
 * @doc.type interface
 * @doc.purpose Merge result definition
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface MergeResult {
  /** Merge ID */
  id: string;
  /** Merged state */
  mergedState: Map<string, CRDTValue>;
  /** Conflicts detected */
  conflicts: Conflict[];
  /** Operations applied */
  operationsApplied: CRDTOperation[];
  /** Timestamp */
  timestamp: number;
  /** Duration in ms */
  duration: number;
}

/**
 * Conflict definition.
 *
 * @doc.type interface
 * @doc.purpose Conflict definition
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface Conflict {
  /** Conflict ID */
  id: string;
  /** Conflict type */
  type: 'concurrent-update' | 'concurrent-delete' | 'move-conflict' | 'ordering-conflict';
  /** Target ID */
  targetId: string;
  /** Operation A */
  operationA: CRDTOperation;
  /** Operation B */
  operationB: CRDTOperation;
  /** Resolved flag */
  resolved: boolean;
  /** Resolution strategy */
  resolutionStrategy?: 'last-write-wins' | 'first-write-wins' | 'merge' | 'custom';
  /** Timestamp */
  timestamp: number;
}

/**
 * Conflict metadata.
 *
 * @doc.type interface
 * @doc.purpose Conflict metadata
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ConflictMetadata {
  /** Conflict ID */
  conflictId: string;
  /** Conflict type */
  type: string;
  /** Involved operations */
  operations: CRDTOperation[];
  /** Resolution */
  resolution?: unknown;
  /** Timestamp */
  timestamp: number;
}

/**
 * CRDT state.
 *
 * @doc.type interface
 * @doc.purpose CRDT state definition
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface CRDTState {
  /** State ID */
  id: string;
  /** Replica ID */
  replicaId: string;
  /** Current values */
  values: Map<string, CRDTValue>;
  /** Operation log */
  operationLog: OperationLogEntry[];
  /** Vector clock */
  vectorClock: VectorClock;
  /** Timestamp */
  timestamp: number;
}

/**
 * CRDT operation result.
 *
 * @doc.type interface
 * @doc.purpose CRDT operation result
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface CRDTOperationResult {
  /** Success flag */
  success: boolean;
  /** Operation ID */
  operationId?: string;
  /** Error message */
  error?: string;
  /** Duration in ms */
  duration: number;
}

/**
 * Synchronization message.
 *
 * @doc.type interface
 * @doc.purpose Synchronization message
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface SyncMessage {
  /** Message ID */
  id: string;
  /** Source replica ID */
  sourceReplicaId: string;
  /** Target replica ID */
  targetReplicaId: string;
  /** Operations to sync */
  operations: CRDTOperation[];
  /** State vector */
  stateVector: StateVector;
  /** Timestamp */
  timestamp: number;
}

/**
 * Merge strategy.
 *
 * @doc.type type
 * @doc.purpose Merge strategy
 * @doc.layer product
 * @doc.pattern Value Object
 */
export type MergeStrategy = 'last-write-wins' | 'first-write-wins' | 'merge' | 'custom';

/**
 * CRDT configuration.
 *
 * @doc.type interface
 * @doc.purpose CRDT configuration
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface CRDTConfig {
  /** Replica ID */
  replicaId: string;
  /** Merge strategy */
  mergeStrategy: MergeStrategy;
  /** Enable tombstones */
  enableTombstones: boolean;
  /** Max operation log size */
  maxLogSize: number;
  /** Auto-compact threshold */
  autoCompactThreshold: number;
}

/**
 * Snapshot for state persistence.
 *
 * @doc.type interface
 * @doc.purpose Snapshot definition
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface Snapshot {
  /** Snapshot ID */
  id: string;
  /** State */
  state: Map<string, CRDTValue>;
  /** Vector clock */
  vectorClock: VectorClock;
  /** Timestamp */
  timestamp: number;
  /** Description */
  description?: string;
}

/**
 * CRDT statistics.
 *
 * @doc.type interface
 * @doc.purpose CRDT statistics
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface CRDTStatistics {
  /** Total operations */
  totalOperations: number;
  /** Total conflicts */
  totalConflicts: number;
  /** Resolved conflicts */
  resolvedConflicts: number;
  /** Pending conflicts */
  pendingConflicts: number;
  /** Operation log size */
  operationLogSize: number;
  /** State size */
  stateSize: number;
  /** Last sync timestamp */
  lastSyncTimestamp: number;
}
