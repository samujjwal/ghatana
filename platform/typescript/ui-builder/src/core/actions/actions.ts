/**
 * @fileoverview Builder Action Implementations
 *
 * Concrete implementations of all builder actions with proper validation
 * and error handling.
 *
 * @doc.type module
 * @doc.purpose Builder action implementations
 * @doc.layer platform
 */

import type {
  BuilderAction,
  ActionContext,
  ActionResult,
  AddNodeAction,
  RemoveNodeAction,
  MoveNodeAction,
  UpdateNodePropsAction,
  BindComponentAction,
  UnbindComponentAction,
  UpdateLayoutAction,
  ValidateDocumentAction,
  UndoAction,
  RedoAction,
} from './types.js';
import type { BuilderDocument, NodeId, ComponentInstance, Binding } from '../builder-document.js';
import { validateBuilderDocument } from '../builder-document.js';
import { createNodeId } from '../types.js';

// ============================================================================
// ACTION VALIDATORS
// ============================================================================

/** Validate add node action. */
export function validateAddNodeAction(
  action: AddNodeAction,
  context: ActionContext,
): readonly string[] {
  const errors: string[] = [];

  if (!action.nodeId || action.nodeId.trim() === '') {
    errors.push('Node ID is required');
  }

  if (!action.parentId || action.parentId.trim() === '') {
    errors.push('Parent ID is required');
  }

  if (!context.document.nodes[action.parentId]) {
    errors.push(`Parent node ${action.parentId} not found`);
  }

  if (context.document.nodes[action.nodeId]) {
    errors.push(`Node ${action.nodeId} already exists`);
  }

  if (!action.component || !action.component.contractName) {
    errors.push('Component with contract name is required');
  }

  if (action.component.id !== action.nodeId) {
    errors.push('Component ID must match action node ID');
  }

  return errors;
}

/** Validate remove node action. */
export function validateRemoveNodeAction(
  action: RemoveNodeAction,
  context: ActionContext,
): readonly string[] {
  const errors: string[] = [];

  if (!action.nodeId || action.nodeId.trim() === '') {
    errors.push('Node ID is required');
  }

  if (!context.document.nodes[action.nodeId]) {
    errors.push(`Node ${action.nodeId} not found`);
  }

  if (action.nodeId === context.document.root) {
    errors.push('Cannot remove root node');
  }

  // Check if node has children
  const layoutNode = context.document.layout.nodes[action.nodeId];
  if (layoutNode?.children && layoutNode.children.length > 0) {
    errors.push('Cannot remove node with children - remove children first');
  }

  return errors;
}

/** Validate move node action. */
export function validateMoveNodeAction(
  action: MoveNodeAction,
  context: ActionContext,
): readonly string[] {
  const errors: string[] = [];

  if (!action.nodeId || action.nodeId.trim() === '') {
    errors.push('Node ID is required');
  }

  if (!context.document.nodes[action.nodeId]) {
    errors.push(`Node ${action.nodeId} not found`);
  }

  if (!action.oldPosition || typeof action.oldPosition.x !== 'number' || typeof action.oldPosition.y !== 'number') {
    errors.push('Old position with valid x, y coordinates is required');
  }

  if (!action.newPosition || typeof action.newPosition.x !== 'number' || typeof action.newPosition.y !== 'number') {
    errors.push('New position with valid x, y coordinates is required');
  }

  return errors;
}

/** Validate update node props action. */
export function validateUpdateNodePropsAction(
  action: UpdateNodePropsAction,
  context: ActionContext,
): readonly string[] {
  const errors: string[] = [];

  if (!action.nodeId || action.nodeId.trim() === '') {
    errors.push('Node ID is required');
  }

  if (!context.document.nodes[action.nodeId]) {
    errors.push(`Node ${action.nodeId} not found`);
  }

  if (!action.oldProps || typeof action.oldProps !== 'object') {
    errors.push('Old props object is required');
  }

  if (!action.newProps || typeof action.newProps !== 'object') {
    errors.push('New props object is required');
  }

  return errors;
}

/** Validate bind component action. */
export function validateBindComponentAction(
  action: BindComponentAction,
  context: ActionContext,
): readonly string[] {
  const errors: string[] = [];

  if (!action.binding || !action.binding.id) {
    errors.push('Binding with ID is required');
  }

  if (!action.binding.source || action.binding.source.trim() === '') {
    errors.push('Binding source is required');
  }

  if (!action.binding.target || action.binding.target.trim() === '') {
    errors.push('Binding target is required');
  }

  // Check if binding already exists
  const existingBinding = context.document.bindings.find(b => b.id === action.binding.id);
  if (existingBinding) {
    errors.push(`Binding ${action.binding.id} already exists`);
  }

  return errors;
}

/** Validate unbind component action. */
export function validateUnbindComponentAction(
  action: UnbindComponentAction,
  context: ActionContext,
): readonly string[] {
  const errors: string[] = [];

  if (!action.bindingId || action.bindingId.trim() === '') {
    errors.push('Binding ID is required');
  }

  const existingBinding = context.document.bindings.find(b => b.id === action.bindingId);
  if (!existingBinding) {
    errors.push(`Binding ${action.bindingId} not found`);
  }

  return errors;
}

/** Validate update layout action. */
export function validateUpdateLayoutAction(
  action: UpdateLayoutAction,
  context: ActionContext,
): readonly string[] {
  const errors: string[] = [];

  if (!action.oldLayout || !action.newLayout) {
    errors.push('Both old and new layouts are required');
  }

  if (!action.newLayout.rootId || !context.document.nodes[action.newLayout.rootId]) {
    errors.push('New layout must reference a valid root node');
  }

  // Check that all layout nodes exist
  for (const nodeId of Object.keys(action.newLayout.nodes)) {
    if (!context.document.nodes[nodeId]) {
      errors.push(`Layout references non-existent node: ${nodeId}`);
    }
  }

  return errors;
}

/** Validate document action (always valid). */
export function validateValidateDocumentAction(): readonly string[] {
  return [];
}

/** Validate undo action (always valid - manager handles state). */
export function validateUndoAction(): readonly string[] {
  return [];
}

/** Validate redo action (always valid - manager handles state). */
export function validateRedoAction(): readonly string[] {
  return [];
}

// ============================================================================
// ACTION EXECUTORS
// ============================================================================

/** Execute add node action. */
export function executeAddNodeAction(
  action: AddNodeAction,
  context: ActionContext,
): ActionResult {
  const errors = validateAddNodeAction(action, context);
  if (errors.length > 0) {
    return { success: false, document: context.document, errors, warnings: [] };
  }

  const newDocument: BuilderDocument = {
    ...context.document,
    nodes: {
      ...context.document.nodes,
      [action.nodeId]: action.component,
    },
    layout: {
      ...context.document.layout,
      nodes: {
        ...context.document.layout.nodes,
        [action.nodeId]: {
          id: action.nodeId,
          type: 'leaf',
          layout: 'absolute',
          layoutProps: action.position ? { x: action.position.x, y: action.position.y } : {},
        },
      },
    },
    metadata: {
      ...context.document.metadata,
      updatedAt: new Date().toISOString(),
      changeCount: (context.document.metadata.changeCount || 0) + 1,
    },
  };

  // Add to parent's children if parent exists in layout
  const parentLayoutNode = newDocument.layout.nodes[action.parentId];
  if (parentLayoutNode) {
    newDocument.layout.nodes[action.parentId] = {
      ...parentLayoutNode,
      children: [...(parentLayoutNode.children || []), action.nodeId],
    };
  }

  const validation = validateBuilderDocument(newDocument);
  return {
    success: validation.valid,
    document: newDocument,
    errors: validation.errors.map(e => e.message),
    warnings: validation.warnings.map(e => e.message),
  };
}

/** Execute remove node action. */
export function executeRemoveNodeAction(
  action: RemoveNodeAction,
  context: ActionContext,
): ActionResult {
  const errors = validateRemoveNodeAction(action, context);
  if (errors.length > 0) {
    return { success: false, document: context.document, errors, warnings: [] };
  }

  const newNodes = { ...context.document.nodes };
  delete newNodes[action.nodeId];

  const newLayoutNodes = { ...context.document.layout.nodes };
  delete newLayoutNodes[action.nodeId];

  // Remove from parent's children
  for (const [nodeId, layoutNode] of Object.entries(newLayoutNodes)) {
    if (layoutNode.children) {
      newLayoutNodes[nodeId] = {
        ...layoutNode,
        children: layoutNode.children.filter(id => id !== action.nodeId),
      };
    }
  }

  // Remove bindings for this node
  const newBindings = context.document.bindings.filter(
    b => !b.target.includes(String(action.nodeId)) && !b.source.includes(String(action.nodeId))
  );

  const newDocument: BuilderDocument = {
    ...context.document,
    nodes: newNodes,
    layout: {
      ...context.document.layout,
      nodes: newLayoutNodes,
    },
    bindings: newBindings,
    metadata: {
      ...context.document.metadata,
      updatedAt: new Date().toISOString(),
      changeCount: (context.document.metadata.changeCount || 0) + 1,
    },
  };

  const validation = validateBuilderDocument(newDocument);
  return {
    success: validation.valid,
    document: newDocument,
    errors: validation.errors.map(e => e.message),
    warnings: validation.warnings.map(e => e.message),
  };
}

/** Execute move node action. */
export function executeMoveNodeAction(
  action: MoveNodeAction,
  context: ActionContext,
): ActionResult {
  const errors = validateMoveNodeAction(action, context);
  if (errors.length > 0) {
    return { success: false, document: context.document, errors, warnings: [] };
  }

  const newLayoutNodes = { ...context.document.layout.nodes };
  const currentLayoutNode = newLayoutNodes[action.nodeId];
  
  if (currentLayoutNode) {
    newLayoutNodes[action.nodeId] = {
      ...currentLayoutNode,
      layoutProps: {
        ...currentLayoutNode.layoutProps,
        x: action.newPosition.x,
        y: action.newPosition.y,
      },
    };
  }

  const newDocument: BuilderDocument = {
    ...context.document,
    layout: {
      ...context.document.layout,
      nodes: newLayoutNodes,
    },
    metadata: {
      ...context.document.metadata,
      updatedAt: new Date().toISOString(),
      changeCount: (context.document.metadata.changeCount || 0) + 1,
    },
  };

  const validation = validateBuilderDocument(newDocument);
  return {
    success: validation.valid,
    document: newDocument,
    errors: validation.errors.map(e => e.message),
    warnings: validation.warnings.map(e => e.message),
  };
}

/** Execute update node props action. */
export function executeUpdateNodePropsAction(
  action: UpdateNodePropsAction,
  context: ActionContext,
): ActionResult {
  const errors = validateUpdateNodePropsAction(action, context);
  if (errors.length > 0) {
    return { success: false, document: context.document, errors, warnings: [] };
  }

  const newNodes = { ...context.document.nodes };
  const currentNode = newNodes[action.nodeId];
  
  if (currentNode) {
    newNodes[action.nodeId] = {
      ...currentNode,
      props: action.newProps,
    };
  }

  const newDocument: BuilderDocument = {
    ...context.document,
    nodes: newNodes,
    metadata: {
      ...context.document.metadata,
      updatedAt: new Date().toISOString(),
      changeCount: (context.document.metadata.changeCount || 0) + 1,
    },
  };

  const validation = validateBuilderDocument(newDocument);
  return {
    success: validation.valid,
    document: newDocument,
    errors: validation.errors.map(e => e.message),
    warnings: validation.warnings.map(e => e.message),
  };
}

/** Execute bind component action. */
export function executeBindComponentAction(
  action: BindComponentAction,
  context: ActionContext,
): ActionResult {
  const errors = validateBindComponentAction(action, context);
  if (errors.length > 0) {
    return { success: false, document: context.document, errors, warnings: [] };
  }

  const newDocument: BuilderDocument = {
    ...context.document,
    bindings: [...context.document.bindings, action.binding],
    metadata: {
      ...context.document.metadata,
      updatedAt: new Date().toISOString(),
      changeCount: (context.document.metadata.changeCount || 0) + 1,
    },
  };

  const validation = validateBuilderDocument(newDocument);
  return {
    success: validation.valid,
    document: newDocument,
    errors: validation.errors.map(e => e.message),
    warnings: validation.warnings.map(e => e.message),
  };
}

/** Execute unbind component action. */
export function executeUnbindComponentAction(
  action: UnbindComponentAction,
  context: ActionContext,
): ActionResult {
  const errors = validateUnbindComponentAction(action, context);
  if (errors.length > 0) {
    return { success: false, document: context.document, errors, warnings: [] };
  }

  const newDocument: BuilderDocument = {
    ...context.document,
    bindings: context.document.bindings.filter(b => b.id !== action.bindingId),
    metadata: {
      ...context.document.metadata,
      updatedAt: new Date().toISOString(),
      changeCount: (context.document.metadata.changeCount || 0) + 1,
    },
  };

  const validation = validateBuilderDocument(newDocument);
  return {
    success: validation.valid,
    document: newDocument,
    errors: validation.errors.map(e => e.message),
    warnings: validation.warnings.map(e => e.message),
  };
}

/** Execute update layout action. */
export function executeUpdateLayoutAction(
  action: UpdateLayoutAction,
  context: ActionContext,
): ActionResult {
  const errors = validateUpdateLayoutAction(action, context);
  if (errors.length > 0) {
    return { success: false, document: context.document, errors, warnings: [] };
  }

  const newDocument: BuilderDocument = {
    ...context.document,
    layout: action.newLayout,
    metadata: {
      ...context.document.metadata,
      updatedAt: new Date().toISOString(),
      changeCount: (context.document.metadata.changeCount || 0) + 1,
    },
  };

  const validation = validateBuilderDocument(newDocument);
  return {
    success: validation.valid,
    document: newDocument,
    errors: validation.errors.map(e => e.message),
    warnings: validation.warnings.map(e => e.message),
  };
}

/** Execute validate document action. */
export function executeValidateDocumentAction(
  _action: ValidateDocumentAction,
  context: ActionContext,
): ActionResult {
  const validation = validateBuilderDocument(context.document);
  return {
    success: validation.valid,
    document: context.document,
    errors: validation.errors.map(e => e.message),
    warnings: validation.warnings.map(e => e.message),
  };
}

/** Execute undo action (handled by action manager). */
export function executeUndoAction(
  _action: UndoAction,
  context: ActionContext,
): ActionResult {
  // This is handled by the ActionManager's undo stack
  return {
    success: false,
    document: context.document,
    errors: ['Undo action must be handled by ActionManager'],
    warnings: [],
  };
}

/** Execute redo action (handled by action manager). */
export function executeRedoAction(
  _action: RedoAction,
  context: ActionContext,
): ActionResult {
  // This is handled by the ActionManager's redo stack
  return {
    success: false,
    document: context.document,
    errors: ['Redo action must be handled by ActionManager'],
    warnings: [],
  };
}
