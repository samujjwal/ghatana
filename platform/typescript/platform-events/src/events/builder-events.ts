/**
 * @fileoverview Builder event taxonomy - all UI builder operation events.
 *
 * Required builder event families:
 * - builder.document.loaded
 * - builder.component.inserted, builder.component.moved, builder.component.resized, builder.component.configured
 * - builder.pattern.applied
 * - builder.preview.started, builder.preview.updated, builder.preview.failed
 * - builder.codegen.completed, builder.codegen.failed
 * - builder.import.started, builder.import.completed, builder.import.review.required
 * - builder.ai.suggestion.shown, builder.ai.suggestion.accepted, builder.ai.suggestion.rejected
 * - builder.ai.action.applied
 * - builder.review.requested, builder.review.completed
 * - builder.sync.started, builder.sync.completed, builder.sync.conflict
 * - builder.code.edited, builder.code.ownership.changed
 */

import type { AIVisibilityContract } from '../ai/types';
import type { CodeOwnership } from '../visibility/types';

/** Builder document lifecycle payload. */
export interface DocumentLoadedPayload {
  readonly documentId: string;
  readonly designSystemRef: string;
  readonly version: string;
  readonly nodeCount: number;
}

/** Builder component operation payload. */
export interface ComponentOperationPayload {
  readonly componentId: string;
  readonly contractName: string;
  readonly parentId?: string;
  readonly slotName?: string;
  readonly position?: { readonly x: number; readonly y: number };
  readonly props?: Record<string, unknown>;
}

/** Builder pattern application payload. */
export interface PatternAppliedPayload {
  readonly patternId: string;
  readonly patternName: string;
  readonly affectedComponentIds: readonly string[];
  readonly appliedProps: Record<string, unknown>;
}

/** Builder preview lifecycle payload. */
export interface PreviewPayload {
  readonly sessionId: string;
  readonly trustLevel: string;
  readonly deviceProfile?: string;
  readonly viewport?: { readonly width: number; readonly height: number };
  readonly errorMessage?: string;
}

/** Builder code generation payload. */
export interface CodegenPayload {
  readonly documentId: string;
  readonly target: 'react' | 'vanilla' | 'html';
  readonly outputSize: number;
  readonly fidelity: 'lossless' | 'assisted' | 'preview-only';
  readonly success: boolean;
  readonly errorMessage?: string;
}

/** Builder import payload. */
export interface ImportPayload {
  readonly importId: string;
  readonly source: string;
  readonly sourceFormat: 'tsx' | 'html' | 'json';
  readonly nodeCount: number;
  readonly requiresReview: boolean;
  readonly reviewReason?: string;
}

/** Builder AI suggestion payload. */
export interface BuilderAISuggestionPayload {
  readonly suggestionId: string;
  readonly kind: 'component' | 'layout' | 'style' | 'binding';
  readonly affectedComponentIds: readonly string[];
  readonly visibilityContract: AIVisibilityContract;
}

/** Builder AI action payload. */
export interface BuilderAIActionPayload {
  readonly actionId: string;
  readonly actionType: string;
  readonly affectedComponentIds: readonly string[];
  readonly visibilityContract: AIVisibilityContract;
}

/** Builder review payload. */
export interface ReviewPayload {
  readonly reviewId: string;
  readonly reviewType: 'ai-suggestion' | 'import' | 'merge-conflict';
  readonly approved: boolean;
  readonly approver?: string;
  readonly reason?: string;
}

/** Builder sync payload. */
export interface SyncPayload {
  readonly syncId: string;
  readonly source: 'design-system' | 'code' | 'canvas';
  readonly target: 'design-system' | 'code' | 'canvas';
  readonly status: 'started' | 'completed' | 'conflict';
  readonly conflictDetails?: {
    readonly conflictingComponentIds: readonly string[];
    readonly resolutionStrategy: string;
  };
}

/** Builder code edit payload. */
export interface CodeEditedPayload {
  readonly editId: string;
  readonly documentId: string;
  readonly sourceCode: string;
  readonly ownershipChanges: readonly {
    readonly region: string;
    readonly previousOwnership: CodeOwnership;
    readonly newOwnership: CodeOwnership;
  }[];
}

/** Preview policy decision payload — emitted when trust/CSP policy is applied to a sandbox. */
export interface PreviewPolicyPayload {
  readonly sessionId: string;
  readonly documentId: string;
  readonly documentTrustLevel: string;
  readonly effectiveTrustLevel: string;
  readonly previewMode: 'untrusted' | 'semi-trusted' | 'trusted-controlled' | 'trusted-local';
  readonly runtimeMode: 'authoring' | 'staging' | 'production' | 'demo';
  readonly trustDowngraded: boolean;
  readonly downgradeReason?: string;
  readonly sandboxTokens: readonly string[];
}

/**
 * Loss-point payload — emitted when import/export identifies a round-trip loss point.
 * One event per detected loss point allows consumers to aggregate and alert.
 */
export interface LossPointPayload {
  readonly documentId: string;
  readonly direction: 'import' | 'export';
  readonly lossType: 'comment' | 'formatting' | 'import-order' | 'custom-code' | 'unsupported-pattern';
  readonly location?: string;
  readonly description: string;
  readonly confidence: number;
}

/** Collaboration state transition payload. */
export interface CollabPayload {
  readonly sessionId: string;
  readonly documentId: string;
  readonly userId: string;
  readonly participantCount: number;
  readonly conflictDetails?: {
    readonly conflictingRegions: readonly string[];
    readonly resolutionStrategy: 'accept-remote' | 'accept-local' | 'merge' | 'manual';
  };
}

/** All builder event payload types. */
export interface BuilderEventPayloads {
  'builder.document.loaded': DocumentLoadedPayload;
  'builder.component.inserted': ComponentOperationPayload;
  'builder.component.moved': ComponentOperationPayload;
  'builder.component.resized': ComponentOperationPayload;
  'builder.component.configured': ComponentOperationPayload;
  'builder.pattern.applied': PatternAppliedPayload;
  'builder.preview.started': PreviewPayload;
  'builder.preview.updated': PreviewPayload;
  'builder.preview.failed': PreviewPayload;
  'builder.preview.policy.applied': PreviewPolicyPayload;
  'builder.preview.trust.downgraded': PreviewPolicyPayload;
  'builder.codegen.completed': CodegenPayload;
  'builder.codegen.failed': CodegenPayload;
  'builder.import.started': ImportPayload;
  'builder.import.completed': ImportPayload;
  'builder.import.review.required': ImportPayload;
  'builder.import.losspoint.detected': LossPointPayload;
  'builder.export.losspoint.detected': LossPointPayload;
  'builder.ai.suggestion.shown': BuilderAISuggestionPayload;
  'builder.ai.suggestion.accepted': BuilderAISuggestionPayload;
  'builder.ai.suggestion.rejected': BuilderAISuggestionPayload;
  'builder.ai.action.applied': BuilderAIActionPayload;
  'builder.review.requested': ReviewPayload;
  'builder.review.completed': ReviewPayload;
  'builder.sync.started': SyncPayload;
  'builder.sync.completed': SyncPayload;
  'builder.sync.conflict': SyncPayload;
  'builder.collab.joined': CollabPayload;
  'builder.collab.left': CollabPayload;
  'builder.collab.conflict.detected': CollabPayload;
  'builder.collab.conflict.resolved': CollabPayload;
  'builder.code.edited': CodeEditedPayload;
  'builder.code.ownership.changed': CodeEditedPayload;
}

/** Builder event names as const assertions. */
export const BuilderEvents = {
  DOCUMENT_LOADED: 'builder.document.loaded',
  COMPONENT_INSERTED: 'builder.component.inserted',
  COMPONENT_MOVED: 'builder.component.moved',
  COMPONENT_RESIZED: 'builder.component.resized',
  COMPONENT_CONFIGURED: 'builder.component.configured',
  PATTERN_APPLIED: 'builder.pattern.applied',
  PREVIEW_STARTED: 'builder.preview.started',
  PREVIEW_UPDATED: 'builder.preview.updated',
  PREVIEW_FAILED: 'builder.preview.failed',
  PREVIEW_POLICY_APPLIED: 'builder.preview.policy.applied',
  PREVIEW_TRUST_DOWNGRADED: 'builder.preview.trust.downgraded',
  CODEGEN_COMPLETED: 'builder.codegen.completed',
  CODEGEN_FAILED: 'builder.codegen.failed',
  IMPORT_STARTED: 'builder.import.started',
  IMPORT_COMPLETED: 'builder.import.completed',
  IMPORT_REVIEW_REQUIRED: 'builder.import.review.required',
  IMPORT_LOSSPOINT_DETECTED: 'builder.import.losspoint.detected',
  EXPORT_LOSSPOINT_DETECTED: 'builder.export.losspoint.detected',
  AI_SUGGESTION_SHOWN: 'builder.ai.suggestion.shown',
  AI_SUGGESTION_ACCEPTED: 'builder.ai.suggestion.accepted',
  AI_SUGGESTION_REJECTED: 'builder.ai.suggestion.rejected',
  AI_ACTION_APPLIED: 'builder.ai.action.applied',
  REVIEW_REQUESTED: 'builder.review.requested',
  REVIEW_COMPLETED: 'builder.review.completed',
  SYNC_STARTED: 'builder.sync.started',
  SYNC_COMPLETED: 'builder.sync.completed',
  SYNC_CONFLICT: 'builder.sync.conflict',
  COLLAB_JOINED: 'builder.collab.joined',
  COLLAB_LEFT: 'builder.collab.left',
  COLLAB_CONFLICT_DETECTED: 'builder.collab.conflict.detected',
  COLLAB_CONFLICT_RESOLVED: 'builder.collab.conflict.resolved',
  CODE_EDITED: 'builder.code.edited',
  CODE_OWNERSHIP_CHANGED: 'builder.code.ownership.changed',
} as const;

/** Type for all builder event names. */
export type BuilderEventName = (typeof BuilderEvents)[keyof typeof BuilderEvents];

/** All builder event names as an array for validation. */
export const ALL_BUILDER_EVENT_NAMES: readonly BuilderEventName[] = Object.values(BuilderEvents);
