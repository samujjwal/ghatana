/**
 * @fileoverview Builder Action Types
 *
 * Type definitions for all builder actions with strict validation
 * and undo/redo support.
 *
 * @doc.type module
 * @doc.purpose Builder action type definitions
 * @doc.layer platform
 */

import type { BuilderDocument } from '../builder-document.js';
import type { NodeId, ComponentInstance, Binding } from '../types.js';

export interface Position {
  readonly x: number;
  readonly y: number;
}

export interface Size {
  readonly width: number;
  readonly height: number;
}

// ============================================================================
// ACTION BASE TYPES
// ============================================================================

/** Base action interface. */
export interface BaseAction {
  readonly id: string;
  readonly type: string;
  readonly timestamp: string;
  readonly description: string;
}

/** Action result. */
export interface ActionResult {
  readonly success: boolean;
  readonly document: BuilderDocument;
  readonly errors: readonly string[];
  readonly warnings: readonly string[];
}

/** Undo/redo stack entry. */
export interface HistoryEntry {
  readonly action: BuilderAction;
  readonly result: ActionResult;
  readonly timestamp: string;
}

// ============================================================================
// SPECIFIC ACTION TYPES
// ============================================================================

/** Add node action. */
export interface AddNodeAction extends BaseAction {
  readonly type: 'add-node';
  readonly nodeId: NodeId;
  readonly parentId: NodeId;
  readonly component: BuilderDocument['nodes'][string];
  readonly position?: Position;
  readonly size?: Size;
}

/** Remove node action. */
export interface RemoveNodeAction extends BaseAction {
  readonly type: 'remove-node';
  readonly nodeId: NodeId;
  readonly component: BuilderDocument['nodes'][string];
}

/** Move node action. */
export interface MoveNodeAction extends BaseAction {
  readonly type: 'move-node';
  readonly nodeId: NodeId;
  readonly oldPosition: Position;
  readonly newPosition: Position;
}

/** Update node props action. */
export interface UpdateNodePropsAction extends BaseAction {
  readonly type: 'update-node-props';
  readonly nodeId: NodeId;
  readonly oldProps: Record<string, unknown>;
  readonly newProps: Record<string, unknown>;
  readonly path?: string; // for nested prop updates
}

/** Bind component action. */
export interface BindComponentAction extends BaseAction {
  readonly type: 'bind-component';
  readonly binding: Binding;
}

/** Unbind component action. */
export interface UnbindComponentAction extends BaseAction {
  readonly type: 'unbind-component';
  readonly bindingId: string;
  readonly binding: Binding;
}

/** Update layout action. */
export interface UpdateLayoutAction extends BaseAction {
  readonly type: 'update-layout';
  readonly oldLayout: BuilderDocument['layout'];
  readonly newLayout: BuilderDocument['layout'];
}

/** Validate document action. */
export interface ValidateDocumentAction extends BaseAction {
  readonly type: 'validate-document';
}

/** Undo action. */
export interface UndoAction extends BaseAction {
  readonly type: 'undo';
}

/** Redo action. */
export interface RedoAction extends BaseAction {
  readonly type: 'redo';
}

/** Batch action used to group multiple actions into one history entry. */
export interface BatchAction extends BaseAction {
  readonly type: 'batch';
  readonly actionCount: number;
}

/** Union type for all builder actions. */
export type BuilderAction =
  | AddNodeAction
  | RemoveNodeAction
  | MoveNodeAction
  | UpdateNodePropsAction
  | BindComponentAction
  | UnbindComponentAction
  | UpdateLayoutAction
  | ValidateDocumentAction
  | UndoAction
  | RedoAction
  | BatchAction;

// ============================================================================
// ACTION EXECUTION CONTEXT
// ============================================================================

/** Action execution context. */
export interface ActionContext {
  readonly document: BuilderDocument;
  readonly userId?: string;
  readonly sessionId?: string;
  readonly dryRun?: boolean;
  readonly skipValidation?: boolean;
}

/** Action validator function. */
export type ActionValidator<T extends BuilderAction = BuilderAction> = (
  action: T,
  context: ActionContext,
) => readonly string[];

/** Action executor function. */
export type ActionExecutor<T extends BuilderAction = BuilderAction> = (
  action: T,
  context: ActionContext,
) => ActionResult;

// ============================================================================
// ACTION REGISTRATION
// ============================================================================

/** Action registration metadata. */
export interface ActionRegistration<T extends BuilderAction = BuilderAction> {
  readonly type: T['type'];
  readonly validator: ActionValidator<T>;
  readonly executor: ActionExecutor<T>;
  readonly description: string;
  readonly category: 'node' | 'layout' | 'binding' | 'document' | 'history';
}

// ============================================================================
// ACTION MANAGER CONFIGURATION
// ============================================================================

/** Action manager configuration. */
export interface ActionManagerConfig {
  maxHistorySize?: number;
  enableUndoRedo?: boolean;
  autoValidate?: boolean;
  trackChanges?: boolean;
}
