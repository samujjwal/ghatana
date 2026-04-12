/**
 * @fileoverview User visibility contracts and sync status types.
 */

import type { CorrelationId } from '../events/base';

/** Full visibility contract for user operations. */
export interface VisibilityContract {
  readonly whatIsDoing: string;
  readonly whatChanged: readonly ChangeRecord[];
  readonly whatIsSuggested: readonly SuggestionRecord[];
  readonly whatIsApplied: readonly AppliedRecord[];
  readonly whatRequiresUserAction: readonly ActionRequiredRecord[];
  readonly whatCanBeUndone: readonly UndoableRecord[];
  readonly whatIsSyncing: readonly SyncingRecord[];
  readonly correlationId: CorrelationId;
}

/** Record of a change. */
export interface ChangeRecord {
  readonly changeId: string;
  readonly description: string;
  readonly timestamp: string;
  readonly actor: 'user' | 'ai' | 'system';
  readonly triggeredBy: 'explicit' | 'implicit';
}

/** Record of a suggestion. */
export interface SuggestionRecord {
  readonly suggestionId: string;
  readonly description: string;
  readonly confidence: number;
  readonly canApply: boolean;
  readonly requiresReview: boolean;
}

/** Record of an applied change. */
export interface AppliedRecord {
  readonly applicationId: string;
  readonly description: string;
  readonly appliedAt: string;
  readonly canRollback: boolean;
}

/** Record of an action requiring user attention. */
export interface ActionRequiredRecord {
  readonly actionId: string;
  readonly actionType: 'review' | 'approval' | 'conflict-resolution' | 'error-recovery';
  readonly description: string;
  readonly priority: 'low' | 'medium' | 'high' | 'critical';
  readonly deadline?: string;
}

/** Record of an undoable action. */
export interface UndoableRecord {
  readonly actionId: string;
  readonly description: string;
  readonly canUndo: boolean;
  readonly undoLabel: string;
}

/** Record of something currently syncing. */
export interface SyncingRecord {
  readonly syncId: string;
  readonly description: string;
  readonly progress: number; // 0-100
  readonly estimatedCompletion?: string;
}

/** Provenance record for tracking origin and lineage. */
export interface ProvenanceRecord {
  readonly source: string; // origin system or tool
  readonly author: string; // user or agent ID
  readonly generatorVersion: string; // version of the generating system
  readonly migrationLineage?: readonly string[]; // previous versions/migrations
  readonly triggeredBy: 'explicit' | 'implicit';
  readonly createdAt: string;
  readonly modifiedAt: string;
}

/** Operation record for audit trails. */
export interface OperationRecord {
  readonly operationId: string;
  readonly actor: string;
  readonly trigger: string;
  readonly riskLevel: 'low' | 'medium' | 'high' | 'critical';
  readonly reviewState: 'not-required' | 'pending' | 'approved' | 'rejected';
  readonly rollbackSemantics: 'immediate' | 'deferred' | 'none';
  readonly telemetryRef: CorrelationId;
  readonly timestamp: string;
}

/** Sync status for the four synchronized representations (§1C). */
export interface SyncStatus {
  readonly designSystem: 'synced' | 'syncing' | 'conflict' | 'unknown';
  readonly builderDocument: 'synced' | 'syncing' | 'conflict' | 'unknown';
  readonly visualProjection: 'synced' | 'syncing' | 'stale' | 'unknown';
  readonly codeProjection: 'synced' | 'syncing' | 'conflict' | 'user-modified' | 'unknown';
  readonly lastSyncAt?: string;
  readonly syncInProgress: boolean;
}

/** Code ownership regions. */
export type CodeOwnership = 'generated' | 'user-authored' | 'protected' | 'manual-merge-required';

/** Ownership region for code/canvas. */
export interface OwnershipRegion {
  readonly regionId: string;
  readonly regionType: 'component' | 'style' | 'logic' | 'data';
  readonly ownership: CodeOwnership;
  readonly owner?: string; // user or system ID
  readonly generatedBy?: string; // generation operation ID
  readonly protectedReason?: string;
  readonly startLine?: number;
  readonly endLine?: number;
}

/** Round-trip fidelity levels. */
export type RoundTripFidelity = 'lossless' | 'assisted' | 'preview-only';

/** Creates a provenance record. */
export function createProvenanceRecord(
  source: string,
  author: string,
  generatorVersion: string,
  triggeredBy: 'explicit' | 'implicit' = 'explicit'
): ProvenanceRecord {
  const now = new Date().toISOString();
  return {
    source,
    author,
    generatorVersion,
    triggeredBy,
    createdAt: now,
    modifiedAt: now,
  };
}

/** Creates an operation record. */
export function createOperationRecord(
  actor: string,
  trigger: string,
  riskLevel: 'low' | 'medium' | 'high' | 'critical',
  telemetryRef: CorrelationId
): OperationRecord {
  return {
    operationId: generateUUID(),
    actor,
    trigger,
    riskLevel,
    reviewState: 'not-required',
    rollbackSemantics: riskLevel === 'low' ? 'immediate' : 'deferred',
    telemetryRef,
    timestamp: new Date().toISOString(),
  };
}

/** Creates a default sync status. */
export function createDefaultSyncStatus(): SyncStatus {
  return {
    designSystem: 'unknown',
    builderDocument: 'unknown',
    visualProjection: 'unknown',
    codeProjection: 'unknown',
    syncInProgress: false,
  };
}

/** Generates a UUID v4. */
function generateUUID(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
