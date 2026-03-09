/**
 * Type definitions for conflict resolution engine.
 *
 * <p><b>Purpose</b><br>
 * Provides comprehensive type definitions for intelligent conflict detection,
 * analysis, and resolution with multiple strategies and user guidance.
 *
 * @doc.type module
 * @doc.purpose Conflict resolution engine types
 * @doc.layer product
 * @doc.pattern Value Object
 */

import type { CRDTOperation, Conflict as CRDTConflict } from '../core/index.js';

/**
 * Conflict type.
 *
 * @doc.type type
 * @doc.purpose Conflict type
 * @doc.layer product
 * @doc.pattern Value Object
 */
export type ConflictType =
  | 'concurrent-update'
  | 'concurrent-delete'
  | 'move-conflict'
  | 'ordering-conflict'
  | 'property-conflict'
  | 'structural-conflict';

/**
 * Resolution strategy.
 *
 * @doc.type type
 * @doc.purpose Resolution strategy
 * @doc.layer product
 * @doc.pattern Value Object
 */
export type ResolutionStrategy =
  | 'last-write-wins'
  | 'first-write-wins'
  | 'merge'
  | 'custom'
  | 'user-guided';

/**
 * Conflict severity.
 *
 * @doc.type type
 * @doc.purpose Conflict severity
 * @doc.layer product
 * @doc.pattern Value Object
 */
export type ConflictSeverity = 'critical' | 'high' | 'medium' | 'low';

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
  type: ConflictType;
  /** Target ID */
  targetId: string;
  /** Operation A */
  operationA: CRDTOperation;
  /** Operation B */
  operationB: CRDTOperation;
  /** Severity level */
  severity: ConflictSeverity;
  /** Timestamp */
  timestamp: number;
}

/**
 * Conflict analysis result.
 *
 * @doc.type interface
 * @doc.purpose Conflict analysis result
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ConflictAnalysis {
  /** Analysis ID */
  id: string;
  /** Conflict */
  conflict: Conflict;
  /** Conflict description */
  description: string;
  /** Suggested resolutions */
  suggestedResolutions: ResolutionSuggestion[];
  /** Automatic resolution possible */
  canAutoResolve: boolean;
  /** Analysis timestamp */
  timestamp: number;
}

/**
 * Resolution suggestion.
 *
 * @doc.type interface
 * @doc.purpose Resolution suggestion
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ResolutionSuggestion {
  /** Suggestion ID */
  id: string;
  /** Strategy */
  strategy: ResolutionStrategy;
  /** Description */
  description: string;
  /** Confidence (0-1) */
  confidence: number;
  /** Resulting value */
  resultingValue: unknown;
  /** Pros */
  pros: string[];
  /** Cons */
  cons: string[];
}

/**
 * Merge strategy configuration.
 *
 * @doc.type interface
 * @doc.purpose Merge strategy configuration
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface MergeStrategyConfig {
  /** Strategy type */
  strategy: ResolutionStrategy;
  /** Strategy parameters */
  parameters?: Record<string, unknown>;
  /** Priority (for multiple strategies) */
  priority?: number;
}

/**
 * Three-way merge input.
 *
 * @doc.type interface
 * @doc.purpose Three-way merge input
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ThreeWayMergeInput {
  /** Base value */
  base: unknown;
  /** Local value */
  local: unknown;
  /** Remote value */
  remote: unknown;
  /** Merge strategy */
  strategy: ResolutionStrategy;
}

/**
 * Three-way merge result.
 *
 * @doc.type interface
 * @doc.purpose Three-way merge result
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ThreeWayMergeResult {
  /** Result ID */
  id: string;
  /** Merged value */
  mergedValue: unknown;
  /** Conflicts detected */
  hasConflicts: boolean;
  /** Conflicts */
  conflicts: Conflict[];
  /** Merge strategy used */
  strategyUsed: ResolutionStrategy;
  /** Timestamp */
  timestamp: number;
}

/**
 * Conflict resolution result.
 *
 * @doc.type interface
 * @doc.purpose Conflict resolution result
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ConflictResolutionResult {
  /** Resolution ID */
  id: string;
  /** Conflict ID */
  conflictId: string;
  /** Resolved flag */
  resolved: boolean;
  /** Resolution strategy */
  strategy: ResolutionStrategy;
  /** Resolved value */
  resolvedValue?: unknown;
  /** Error message */
  error?: string;
  /** Timestamp */
  timestamp: number;
  /** Duration in ms */
  duration: number;
}

/**
 * Conflict report.
 *
 * @doc.type interface
 * @doc.purpose Conflict report
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ConflictReport {
  /** Report ID */
  id: string;
  /** Total conflicts */
  totalConflicts: number;
  /** Resolved conflicts */
  resolvedConflicts: number;
  /** Pending conflicts */
  pendingConflicts: number;
  /** Conflicts by type */
  conflictsByType: Map<ConflictType, number>;
  /** Conflicts by severity */
  conflictsBySeverity: Map<ConflictSeverity, number>;
  /** Report timestamp */
  timestamp: number;
}

/**
 * Resolution history entry.
 *
 * @doc.type interface
 * @doc.purpose Resolution history entry
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ResolutionHistoryEntry {
  /** Entry ID */
  id: string;
  /** Conflict ID */
  conflictId: string;
  /** Resolution strategy */
  strategy: ResolutionStrategy;
  /** Resolved value */
  resolvedValue: unknown;
  /** User ID (if user-guided) */
  userId?: string;
  /** Timestamp */
  timestamp: number;
}

/**
 * Conflict detector configuration.
 *
 * @doc.type interface
 * @doc.purpose Conflict detector configuration
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ConflictDetectorConfig {
  /** Enable concurrent update detection */
  detectConcurrentUpdates: boolean;
  /** Enable concurrent delete detection */
  detectConcurrentDeletes: boolean;
  /** Enable move conflict detection */
  detectMoveConflicts: boolean;
  /** Enable ordering conflict detection */
  detectOrderingConflicts: boolean;
  /** Severity threshold for auto-resolution */
  autoResolveSeverityThreshold: ConflictSeverity;
}

/**
 * Conflict resolution engine configuration.
 *
 * @doc.type interface
 * @doc.purpose Conflict resolution engine configuration
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ConflictResolutionEngineConfig {
  /** Engine ID */
  id: string;
  /** Default merge strategy */
  defaultStrategy: ResolutionStrategy;
  /** Merge strategy configurations */
  strategies: MergeStrategyConfig[];
  /** Conflict detector config */
  detectorConfig: ConflictDetectorConfig;
  /** Enable auto-resolution */
  enableAutoResolution: boolean;
  /** Max resolution attempts */
  maxResolutionAttempts: number;
  /** Keep resolution history */
  keepHistory: boolean;
  /** Max history size */
  maxHistorySize: number;
}

/**
 * Operation result.
 *
 * @doc.type interface
 * @doc.purpose Operation result
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface OperationResult {
  /** Success flag */
  success: boolean;
  /** Result data */
  data?: unknown;
  /** Error message */
  error?: string;
  /** Duration in ms */
  duration: number;
}

/**
 * Conflict statistics.
 *
 * @doc.type interface
 * @doc.purpose Conflict statistics
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ConflictStatistics {
  /** Total conflicts detected */
  totalConflicts: number;
  /** Total conflicts resolved */
  totalResolved: number;
  /** Total conflicts pending */
  totalPending: number;
  /** Average resolution time in ms */
  averageResolutionTime: number;
  /** Success rate (0-1) */
  successRate: number;
  /** Conflicts by type */
  conflictsByType: Map<ConflictType, number>;
  /** Conflicts by severity */
  conflictsBySeverity: Map<ConflictSeverity, number>;
}
