/**
 * Conflict resolution engine service.
 *
 * <p><b>Purpose</b><br>
 * Provides intelligent conflict detection, analysis, and resolution with
 * multiple strategies and user guidance for real-time collaboration.
 *
 * @doc.type module
 * @doc.purpose Conflict resolution engine
 * @doc.layer product
 * @doc.pattern Service
 */

import type { CRDTOperation } from '../core/index.js';

import { autoResolveOperations } from './auto-resolver.js';
import type { AutoResolutionResult } from './auto-resolver.js';
import type {
  Conflict,
  ConflictType,
  ResolutionStrategy,
  ConflictSeverity,
  ConflictAnalysis,
  ResolutionSuggestion,
  MergeStrategyConfig,
  ThreeWayMergeInput,
  ThreeWayMergeResult,
  ConflictResolutionResult,
  ConflictReport,
  ResolutionHistoryEntry,
  ConflictDetectorConfig,
  ConflictResolutionEngineConfig,
  OperationResult,
  ConflictStatistics,
} from './types';

/**
 * Conflict resolution engine service.
 *
 * <p><b>Purpose</b><br>
 * Main service for intelligent conflict detection, analysis, and resolution
 * with multiple strategies and user guidance.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const engine = new ConflictResolutionEngine({
 *   id: 'engine-1',
 *   defaultStrategy: 'last-write-wins',
 *   strategies: [
 *     { strategy: 'last-write-wins', priority: 1 },
 *     { strategy: 'merge', priority: 2 },
 *   ],
 *   detectorConfig: { ... },
 *   enableAutoResolution: true,
 *   maxResolutionAttempts: 3,
 *   keepHistory: true,
 *   maxHistorySize: 1000,
 * });
 *
 * const analysis = engine.analyzeConflict(conflict);
 * const result = engine.resolveConflict(conflict, 'last-write-wins');
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Conflict resolution engine
 * @doc.layer product
 * @doc.pattern Service
 */
export class ConflictResolutionEngine {
  private config: ConflictResolutionEngineConfig;
  private conflicts: Map<string, Conflict> = new Map();
  private resolutionHistory: ResolutionHistoryEntry[] = [];
  private statistics: ConflictStatistics;

  /**
   * Create a new conflict resolution engine.
   *
   * @param config - Engine configuration
   *
   * @doc.type constructor
   * @doc.purpose Initialize engine
   * @doc.layer product
   * @doc.pattern Service
   */
  constructor(config: ConflictResolutionEngineConfig) {
    this.config = config;
    this.statistics = {
      totalConflicts: 0,
      totalResolved: 0,
      totalPending: 0,
      averageResolutionTime: 0,
      successRate: 0,
      conflictsByType: new Map(),
      conflictsBySeverity: new Map(),
    };
  }

  /**
   * Detect conflicts.
   *
   * @param operationA - First operation
   * @param operationB - Second operation
   * @returns Detected conflicts
   */
  public detectConflicts(
    operationA: CRDTOperation,
    operationB: CRDTOperation
  ): Conflict[] {
    const conflicts: Conflict[] = [];

    // Check if operations target the same resource
    if (operationA.targetId !== operationB.targetId) {
      return conflicts;
    }

    // Only concurrent operations can conflict.
    if (!this.isConcurrent(operationA.vectorClock, operationB.vectorClock)) {
      return conflicts;
    }

    if (
      this.config.detectorConfig.detectOrderingConflicts &&
      operationA.type === 'insert' &&
      operationB.type === 'insert' &&
      this.targetsSamePosition(operationA, operationB)
    ) {
      conflicts.push(
        this.createConflict('ordering-conflict', operationA, operationB)
      );
    }

    // Concurrent update conflict
    if (
      this.config.detectorConfig.detectConcurrentUpdates &&
      operationA.type === 'update' &&
      operationB.type === 'update' &&
      this.touchesSameField(operationA, operationB)
    ) {
      conflicts.push(
        this.createConflict('concurrent-update', operationA, operationB)
      );
    }

    // Concurrent delete conflict
    if (
      this.config.detectorConfig.detectConcurrentDeletes &&
      operationA.type === 'delete' &&
      operationB.type === 'delete'
    ) {
      conflicts.push(
        this.createConflict('concurrent-delete', operationA, operationB)
      );
    }

    // Update-delete conflict
    if (
      (operationA.type === 'update' && operationB.type === 'delete') ||
      (operationA.type === 'delete' && operationB.type === 'update')
    ) {
      conflicts.push(
        this.createConflict('structural-conflict', operationA, operationB)
      );
    }

    if (
      (operationA.type === 'delete' && operationB.type === 'insert') ||
      (operationA.type === 'insert' && operationB.type === 'delete')
    ) {
      conflicts.push(
        this.createConflict('structural-conflict', operationA, operationB)
      );
    }

    // Move conflict
    if (
      this.config.detectorConfig.detectMoveConflicts &&
      operationA.type === 'move' &&
      operationB.type === 'move'
    ) {
      conflicts.push(
        this.createConflict('move-conflict', operationA, operationB)
      );
    }

    return conflicts;
  }

  /**
   *
   */
  private isConcurrent(
    a: CRDTOperation['vectorClock'],
    b: CRDTOperation['vectorClock']
  ): boolean {
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

    return aGreater && bGreater;
  }

  /**
   *
   */
  private touchesSameField(
    operationA: CRDTOperation,
    operationB: CRDTOperation
  ): boolean {
    const fieldsA = this.extractFieldNames(operationA.data);
    const fieldsB = this.extractFieldNames(operationB.data);

    if (fieldsA.size === 0 || fieldsB.size === 0) {
      return true;
    }

    for (const field of fieldsA) {
      if (fieldsB.has(field)) {
        return true;
      }
    }

    return false;
  }

  /**
   *
   */
  private extractFieldNames(data: unknown): Set<string> {
    if (!this.isObjectRecord(data)) {
      return new Set();
    }

    const explicitField = data['field'];
    if (typeof explicitField === 'string' && explicitField.length > 0) {
      return new Set([explicitField]);
    }

    const payload = data['value'];
    if (this.isObjectRecord(payload)) {
      return new Set(Object.keys(payload));
    }

    const keys = Object.keys(data).filter(
      (key) => !['field', 'value', 'position', 'index'].includes(key)
    );
    return new Set(keys);
  }

  /**
   *
   */
  private targetsSamePosition(
    operationA: CRDTOperation,
    operationB: CRDTOperation
  ): boolean {
    const positionA = this.extractPosition(operationA.data);
    const positionB = this.extractPosition(operationB.data);

    return positionA !== null && positionB !== null && positionA === positionB;
  }

  /**
   *
   */
  private extractPosition(data: unknown): number | null {
    if (!this.isObjectRecord(data)) {
      return null;
    }

    const directPosition = data['position'];
    if (typeof directPosition === 'number') {
      return directPosition;
    }

    const directIndex = data['index'];
    if (typeof directIndex === 'number') {
      return directIndex;
    }

    return null;
  }

  /**
   *
   */
  private isObjectRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
  }

  /**
   * Create conflict.
   *
   * @param type - Conflict type
   * @param operationA - First operation
   * @param operationB - Second operation
   * @returns Created conflict
   */
  private createConflict(
    type: ConflictType,
    operationA: CRDTOperation,
    operationB: CRDTOperation
  ): Conflict {
    const severity = this.calculateSeverity(type);

    return {
      id: `conflict-${Date.now()}-${Math.random()}`,
      type,
      targetId: operationA.targetId,
      operationA,
      operationB,
      severity,
      timestamp: Date.now(),
    };
  }

  /**
   * Calculate conflict severity.
   *
   * @param type - Conflict type
   * @returns Severity level
   */
  private calculateSeverity(type: ConflictType): ConflictSeverity {
    switch (type) {
      case 'concurrent-delete':
        return 'critical';
      case 'structural-conflict':
        return 'high';
      case 'move-conflict':
        return 'high';
      case 'concurrent-update':
        return 'medium';
      case 'ordering-conflict':
        return 'low';
      case 'property-conflict':
        return 'low';
      default:
        return 'medium';
    }
  }

  /**
   * Analyze conflict.
   *
   * @param conflict - Conflict to analyze
   * @returns Conflict analysis
   */
  public analyzeConflict(conflict: Conflict): ConflictAnalysis {
    const suggestions = this.generateResolutionSuggestions(conflict);
    const autoResolution = this.getAutoResolution(conflict);
    const canAutoResolve =
      autoResolution.resolved || suggestions.some((s) => s.confidence > 0.8);

    return {
      id: `analysis-${Date.now()}`,
      conflict,
      description: this.generateConflictDescription(conflict),
      suggestedResolutions: suggestions,
      canAutoResolve,
      timestamp: Date.now(),
    };
  }

  /**
   * Generate resolution suggestions.
   *
   * @param conflict - Conflict
   * @returns Resolution suggestions
   */
  private generateResolutionSuggestions(
    conflict: Conflict
  ): ResolutionSuggestion[] {
    const suggestions: ResolutionSuggestion[] = [];
    const autoResolution = this.getAutoResolution(conflict);

    if (autoResolution.resolved) {
      suggestions.push({
        id: `suggestion-auto-${Date.now()}`,
        strategy: autoResolution.operations.length > 1 ? 'merge' : 'custom',
        description: autoResolution.description,
        confidence: 0.95,
        resultingValue: autoResolution.operations,
        pros: ['Deterministic', 'Fast', 'Preserves collaboration flow'],
        cons: [
          'Rule-based resolution may still need user review for semantic intent',
        ],
      });
    }

    // Last-write-wins suggestion
    const lwwValue =
      conflict.operationA.timestamp > conflict.operationB.timestamp
        ? conflict.operationA.data
        : conflict.operationB.data;

    suggestions.push({
      id: `suggestion-lww-${Date.now()}`,
      strategy: 'last-write-wins',
      description: 'Keep the most recent change',
      confidence: 0.9,
      resultingValue: lwwValue,
      pros: ['Simple', 'Deterministic', 'No data loss'],
      cons: ['May lose recent edits', 'Not always semantically correct'],
    });

    // First-write-wins suggestion
    const fwwValue =
      conflict.operationA.timestamp < conflict.operationB.timestamp
        ? conflict.operationA.data
        : conflict.operationB.data;

    suggestions.push({
      id: `suggestion-fww-${Date.now()}`,
      strategy: 'first-write-wins',
      description: 'Keep the first change',
      confidence: 0.7,
      resultingValue: fwwValue,
      pros: ['Preserves original intent', 'Deterministic'],
      cons: ['May lose recent changes', 'Counterintuitive'],
    });

    // Merge suggestion
    if (
      typeof conflict.operationA.data === 'object' &&
      typeof conflict.operationB.data === 'object'
    ) {
      const mergedValue = {
        ...conflict.operationA.data,
        ...conflict.operationB.data,
      };

      suggestions.push({
        id: `suggestion-merge-${Date.now()}`,
        strategy: 'merge',
        description: 'Merge both changes',
        confidence: 0.75,
        resultingValue: mergedValue,
        pros: ['Preserves both changes', 'Semantic merge'],
        cons: ['May have unintended side effects', 'Complex logic'],
      });
    }

    return suggestions;
  }

  /**
   *
   */
  public getAutoResolution(conflict: Conflict): AutoResolutionResult {
    return autoResolveOperations(conflict.operationA, conflict.operationB);
  }

  /**
   *
   */
  public autoResolveConflict(conflict: Conflict): ConflictResolutionResult {
    const startTime = Date.now();
    const autoResolution = this.getAutoResolution(conflict);

    if (!autoResolution.resolved) {
      return {
        id: `resolution-${Date.now()}`,
        conflictId: conflict.id,
        resolved: false,
        strategy: 'custom',
        error: autoResolution.description,
        timestamp: Date.now(),
        duration: Date.now() - startTime,
      };
    }

    this.recordResolution(conflict.id, 'custom', autoResolution.operations);

    return {
      id: `resolution-${Date.now()}`,
      conflictId: conflict.id,
      resolved: true,
      strategy: 'custom',
      resolvedValue: autoResolution.operations,
      timestamp: Date.now(),
      duration: Date.now() - startTime,
    };
  }

  /**
   * Generate conflict description.
   *
   * @param conflict - Conflict
   * @returns Description
   */
  private generateConflictDescription(conflict: Conflict): string {
    return `${conflict.type} on ${conflict.targetId}: ${conflict.operationA.type} vs ${conflict.operationB.type}`;
  }

  /**
   * Resolve conflict.
   *
   * @param conflict - Conflict to resolve
   * @param strategy - Resolution strategy
   * @param customResolver - Custom resolver function
   * @returns Resolution result
   */
  public resolveConflict(
    conflict: Conflict,
    strategy: ResolutionStrategy,
    customResolver?: (a: unknown, b: unknown) => any
  ): ConflictResolutionResult {
    const startTime = Date.now();

    try {
      let resolvedValue: unknown;

      switch (strategy) {
        case 'last-write-wins':
          resolvedValue =
            conflict.operationA.timestamp > conflict.operationB.timestamp
              ? conflict.operationA.data
              : conflict.operationB.data;
          break;

        case 'first-write-wins':
          resolvedValue =
            conflict.operationA.timestamp < conflict.operationB.timestamp
              ? conflict.operationA.data
              : conflict.operationB.data;
          break;

        case 'merge':
          resolvedValue = this.mergeValues(
            conflict.operationA.data,
            conflict.operationB.data
          );
          break;

        case 'custom':
          if (!customResolver) {
            throw new Error('Custom resolver required for custom strategy');
          }
          resolvedValue = customResolver(
            conflict.operationA.data,
            conflict.operationB.data
          );
          break;

        default:
          throw new Error(`Unknown strategy: ${strategy}`);
      }

      // Record resolution
      this.recordResolution(conflict.id, strategy, resolvedValue);

      // Update statistics
      this.statistics.totalResolved++;
      this.statistics.totalPending = Math.max(
        0,
        this.statistics.totalPending - 1
      );

      return {
        id: `resolution-${Date.now()}`,
        conflictId: conflict.id,
        resolved: true,
        strategy,
        resolvedValue,
        timestamp: Date.now(),
        duration: Date.now() - startTime,
      };
    } catch (error) {
      return {
        id: `resolution-${Date.now()}`,
        conflictId: conflict.id,
        resolved: false,
        strategy,
        error: error instanceof Error ? error.message : 'Unknown error',
        timestamp: Date.now(),
        duration: Date.now() - startTime,
      };
    }
  }

  /**
   * Merge values.
   *
   * @param valueA - First value
   * @param valueB - Second value
   * @returns Merged value
   */
  private mergeValues(valueA: unknown, valueB: unknown): unknown {
    if (typeof valueA !== 'object' || typeof valueB !== 'object') {
      return valueB; // Default to second value
    }

    return { ...valueA, ...valueB };
  }

  /**
   * Three-way merge.
   *
   * @param input - Merge input
   * @returns Merge result
   */
  public threeWayMerge(input: ThreeWayMergeInput): ThreeWayMergeResult {
    const startTime = Date.now();
    const conflicts: Conflict[] = [];

    // Check for conflicts
    if (input.local !== input.base && input.remote !== input.base) {
      // Both sides changed
      if (input.local !== input.remote) {
        // Different changes - conflict
        conflicts.push({
          id: `conflict-3way-${Date.now()}`,
          type: 'concurrent-update',
          targetId: 'merge-target',
          operationA: {
            id: 'local-op',
            replicaId: 'local',
            type: 'update',
            targetId: 'merge-target',
            vectorClock: { id: 'vc', values: new Map(), timestamp: Date.now() },
            data: input.local,
            timestamp: Date.now(),
            parents: [],
          },
          operationB: {
            id: 'remote-op',
            replicaId: 'remote',
            type: 'update',
            targetId: 'merge-target',
            vectorClock: { id: 'vc', values: new Map(), timestamp: Date.now() },
            data: input.remote,
            timestamp: Date.now(),
            parents: [],
          },
          severity: 'medium',
          timestamp: Date.now(),
        });
      }
    }

    // Determine merged value
    let mergedValue = input.base;

    if (input.local !== input.base && input.remote === input.base) {
      mergedValue = input.local; // Only local changed
    } else if (input.remote !== input.base && input.local === input.base) {
      mergedValue = input.remote; // Only remote changed
    } else if (
      input.local !== input.base &&
      input.remote !== input.base &&
      input.local === input.remote
    ) {
      mergedValue = input.local; // Same change on both sides
    } else if (conflicts.length === 0) {
      mergedValue = input.remote; // Default to remote
    } else {
      // Resolve conflict using strategy
      mergedValue = this.resolveConflict(
        conflicts[0],
        input.strategy
      ).resolvedValue;
    }

    return {
      id: `merge-${Date.now()}`,
      mergedValue,
      hasConflicts: conflicts.length > 0,
      conflicts,
      strategyUsed: input.strategy,
      timestamp: Date.now(),
    };
  }

  /**
   * Record resolution.
   *
   * @param conflictId - Conflict ID
   * @param strategy - Resolution strategy
   * @param resolvedValue - Resolved value
   */
  private recordResolution(
    conflictId: string,
    strategy: ResolutionStrategy,
    resolvedValue: unknown
  ): void {
    const entry: ResolutionHistoryEntry = {
      id: `history-${Date.now()}`,
      conflictId,
      strategy,
      resolvedValue,
      timestamp: Date.now(),
    };

    this.resolutionHistory.push(entry);

    // Limit history size
    if (this.resolutionHistory.length > this.config.maxHistorySize) {
      this.resolutionHistory.shift();
    }
  }

  /**
   * Get conflict report.
   *
   * @returns Conflict report
   */
  public getConflictReport(): ConflictReport {
    const conflictsByType = new Map<ConflictType, number>();
    const conflictsBySeverity = new Map<ConflictSeverity, number>();

    for (const conflict of this.conflicts.values()) {
      conflictsByType.set(
        conflict.type,
        (conflictsByType.get(conflict.type) || 0) + 1
      );
      conflictsBySeverity.set(
        conflict.severity,
        (conflictsBySeverity.get(conflict.severity) || 0) + 1
      );
    }

    return {
      id: `report-${Date.now()}`,
      totalConflicts: this.conflicts.size,
      resolvedConflicts: this.statistics.totalResolved,
      pendingConflicts: this.statistics.totalPending,
      conflictsByType,
      conflictsBySeverity,
      timestamp: Date.now(),
    };
  }

  /**
   * Get statistics.
   *
   * @returns Conflict statistics
   */
  public getStatistics(): ConflictStatistics {
    return { ...this.statistics };
  }

  /**
   * Get resolution history.
   *
   * @param limit - Max entries to return
   * @returns Resolution history
   */
  public getResolutionHistory(limit: number = 100): ResolutionHistoryEntry[] {
    return this.resolutionHistory.slice(-limit);
  }

  /**
   * Clear conflicts.
   *
   * @returns Operation result
   */
  public clearConflicts(): OperationResult {
    const startTime = Date.now();

    try {
      const count = this.conflicts.size;
      this.conflicts.clear();

      return {
        success: true,
        data: { cleared: count },
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
  Conflict,
  ConflictType,
  ResolutionStrategy,
  ConflictSeverity,
  ConflictAnalysis,
  ResolutionSuggestion,
  MergeStrategyConfig,
  ThreeWayMergeInput,
  ThreeWayMergeResult,
  ConflictResolutionResult,
  ConflictReport,
  ResolutionHistoryEntry,
  ConflictDetectorConfig,
  ConflictResolutionEngineConfig,
  OperationResult,
  ConflictStatistics,
};
export { autoResolveOperations } from './auto-resolver.js';
export type {
  AutoResolutionResult,
  AutoResolutionRule,
} from './auto-resolver.js';
