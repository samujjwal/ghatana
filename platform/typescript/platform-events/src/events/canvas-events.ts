/**
 * @fileoverview Canvas event taxonomy - all canvas operation events.
 *
 * Required canvas event families (all must be emittable):
 * - canvas.viewport.changed
 * - canvas.selection.changed
 * - canvas.node.created, canvas.node.updated, canvas.node.deleted
 * - canvas.edge.created, canvas.edge.updated, canvas.edge.deleted
 * - canvas.layout.applied
 * - canvas.import.completed, canvas.export.completed
 * - canvas.render.failed
 * - canvas.performance.sampled
 * - canvas.ai.suggestion.shown, canvas.ai.suggestion.accepted, canvas.ai.suggestion.rejected
 * - canvas.ai.action.applied
 * - canvas.ai.review.requested, canvas.ai.review.approved, canvas.ai.review.rejected
 * - canvas.ai.override.invoked
 * - canvas.collaboration.peer.joined, canvas.collaboration.peer.left
 * - canvas.collaboration.conflict.detected, canvas.collaboration.conflict.resolved
 */

import type { AIVisibilityContract } from '../ai/types';

/** Canvas viewport change payload. */
export interface ViewportChangedPayload {
  readonly zoom: number;
  readonly panX: number;
  readonly panY: number;
  readonly width: number;
  readonly height: number;
}

/** Canvas selection change payload. */
export interface SelectionChangedPayload {
  readonly selectedNodeIds: readonly string[];
  readonly selectedEdgeIds: readonly string[];
  readonly previousSelection: readonly string[];
}

/** Canvas node operation payload. */
export interface NodePayload {
  readonly nodeId: string;
  readonly nodeType: string;
  readonly position?: { readonly x: number; readonly y: number };
  readonly data?: Record<string, unknown>;
}

/** Canvas edge operation payload. */
export interface EdgePayload {
  readonly edgeId: string;
  readonly sourceNodeId: string;
  readonly targetNodeId: string;
  readonly edgeType?: string;
}

/** Canvas layout application payload. */
export interface LayoutAppliedPayload {
  readonly layoutAlgorithm: string;
  readonly nodeCount: number;
  readonly durationMs: number;
  readonly appliedToNodeIds: readonly string[];
}

/** Canvas import/export payload. */
export interface ImportExportPayload {
  readonly format: string;
  readonly nodeCount: number;
  readonly edgeCount: number;
  readonly success: boolean;
  readonly errorMessage?: string;
}

/** Canvas render failure payload. */
export interface RenderFailedPayload {
  readonly phase: 'init' | 'update' | 'layout';
  readonly error: string;
  readonly stack?: string;
  readonly affectedNodeIds?: readonly string[];
}

/** Canvas performance sample payload. */
export interface PerformanceSampledPayload {
  readonly metric: 'frameTime' | 'renderTime' | 'interactionLatency';
  readonly value: number;
  readonly unit: 'ms' | 'fps';
  readonly sampleSize: number;
}

/** Canvas AI suggestion payload. */
export interface AISuggestionPayload {
  readonly suggestionId: string;
  readonly kind: string;
  readonly affectedNodeIds?: readonly string[];
  readonly affectedEdgeIds?: readonly string[];
  readonly visibilityContract: AIVisibilityContract;
}

/** Canvas AI action payload. */
export interface AIActionPayload {
  readonly actionId: string;
  readonly actionType: string;
  readonly visibilityContract: AIVisibilityContract;
}

/** Canvas AI review payload. */
export interface AIReviewPayload {
  readonly reviewId: string;
  readonly suggestionId: string;
  readonly approved: boolean;
  readonly reviewer?: 'user' | 'system';
  readonly reason?: string;
}

/** Canvas collaboration peer payload. */
export interface CollaborationPeerPayload {
  readonly peerId: string;
  readonly peerName: string;
  readonly cursorPosition?: { readonly x: number; readonly y: number };
}

/** Canvas collaboration conflict payload. */
export interface CollaborationConflictPayload {
  readonly conflictId: string;
  readonly conflictingNodeIds: readonly string[];
  readonly conflictingPeerIds: readonly string[];
  readonly resolution?: 'merged' | 'rejected' | 'pending';
}

/** All canvas event payload types. */
export interface CanvasEventPayloads {
  'canvas.viewport.changed': ViewportChangedPayload;
  'canvas.selection.changed': SelectionChangedPayload;
  'canvas.node.created': NodePayload;
  'canvas.node.updated': NodePayload;
  'canvas.node.deleted': NodePayload;
  'canvas.edge.created': EdgePayload;
  'canvas.edge.updated': EdgePayload;
  'canvas.edge.deleted': EdgePayload;
  'canvas.layout.applied': LayoutAppliedPayload;
  'canvas.import.completed': ImportExportPayload;
  'canvas.export.completed': ImportExportPayload;
  'canvas.render.failed': RenderFailedPayload;
  'canvas.performance.sampled': PerformanceSampledPayload;
  'canvas.ai.suggestion.shown': AISuggestionPayload;
  'canvas.ai.suggestion.accepted': AISuggestionPayload;
  'canvas.ai.suggestion.rejected': AISuggestionPayload;
  'canvas.ai.action.applied': AIActionPayload;
  'canvas.ai.review.requested': AIReviewPayload;
  'canvas.ai.review.approved': AIReviewPayload;
  'canvas.ai.review.rejected': AIReviewPayload;
  'canvas.ai.override.invoked': AIActionPayload;
  'canvas.collaboration.peer.joined': CollaborationPeerPayload;
  'canvas.collaboration.peer.left': CollaborationPeerPayload;
  'canvas.collaboration.conflict.detected': CollaborationConflictPayload;
  'canvas.collaboration.conflict.resolved': CollaborationConflictPayload;
}

/** Canvas event names as const assertions for type safety. */
export const CanvasEvents = {
  VIEWPORT_CHANGED: 'canvas.viewport.changed',
  SELECTION_CHANGED: 'canvas.selection.changed',
  NODE_CREATED: 'canvas.node.created',
  NODE_UPDATED: 'canvas.node.updated',
  NODE_DELETED: 'canvas.node.deleted',
  EDGE_CREATED: 'canvas.edge.created',
  EDGE_UPDATED: 'canvas.edge.updated',
  EDGE_DELETED: 'canvas.edge.deleted',
  LAYOUT_APPLIED: 'canvas.layout.applied',
  IMPORT_COMPLETED: 'canvas.import.completed',
  EXPORT_COMPLETED: 'canvas.export.completed',
  RENDER_FAILED: 'canvas.render.failed',
  PERFORMANCE_SAMPLED: 'canvas.performance.sampled',
  AI_SUGGESTION_SHOWN: 'canvas.ai.suggestion.shown',
  AI_SUGGESTION_ACCEPTED: 'canvas.ai.suggestion.accepted',
  AI_SUGGESTION_REJECTED: 'canvas.ai.suggestion.rejected',
  AI_ACTION_APPLIED: 'canvas.ai.action.applied',
  AI_REVIEW_REQUESTED: 'canvas.ai.review.requested',
  AI_REVIEW_APPROVED: 'canvas.ai.review.approved',
  AI_REVIEW_REJECTED: 'canvas.ai.review.rejected',
  AI_OVERRIDE_INVOKED: 'canvas.ai.override.invoked',
  COLLABORATION_PEER_JOINED: 'canvas.collaboration.peer.joined',
  COLLABORATION_PEER_LEFT: 'canvas.collaboration.peer.left',
  COLLABORATION_CONFLICT_DETECTED: 'canvas.collaboration.conflict.detected',
  COLLABORATION_CONFLICT_RESOLVED: 'canvas.collaboration.conflict.resolved',
} as const;

/** Type for all canvas event names. */
export type CanvasEventName = (typeof CanvasEvents)[keyof typeof CanvasEvents];

/** All canvas event names as an array for validation. */
export const ALL_CANVAS_EVENT_NAMES: readonly CanvasEventName[] = Object.values(CanvasEvents);
