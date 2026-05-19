/**
 * @fileoverview Builder document operations - immutable operations using immer.
 *
 * Each mutating operation accepts an optional `OperationEventBus` so callers
 * can receive structured @ghatana/platform-events-compatible payloads for
 * every document change without coupling the pure transformation logic to a
 * concrete event transport.
 */

import { produce } from 'immer';
import type { BuilderDocument } from './builder-document.js';
import { attachBuilderDocumentCompatibility, normalizeBuilderDocument } from './builder-document.js';
import type { ComponentInstance, NodeId, Binding } from './types';
import { createNodeId } from './types';

// ============================================================================
// Operation Event Bus (Milestone E — Visibility & Observability Wiring)
// ============================================================================

/** Structured payload emitted after a node is inserted. */
export interface NodeInsertedPayload {
  readonly documentId: string;
  readonly nodeId: NodeId;
  readonly contractName: string;
  readonly parentId: NodeId | undefined;
  readonly slotName: string | undefined;
}

/** Structured payload emitted after a node is moved. */
export interface NodeMovedPayload {
  readonly documentId: string;
  readonly nodeId: NodeId;
  readonly newParentId: NodeId | null;
  readonly newSlotName: string | undefined;
}

/** Structured payload emitted after a node is deleted. */
export interface NodeDeletedPayload {
  readonly documentId: string;
  readonly nodeId: NodeId;
  readonly deletedCount: number;
}

/** Structured payload emitted after node props are updated. */
export interface NodePropsUpdatedPayload {
  readonly documentId: string;
  readonly nodeId: NodeId;
  readonly updatedKeys: readonly string[];
}

/** Structured payload emitted after a binding is added. */
export interface BindingAddedPayload {
  readonly documentId: string;
  readonly nodeId: NodeId;
  readonly bindingId: string;
  /** Maps to Binding.target — the component property path being bound. */
  readonly propPath: string;
  /** Maps to Binding.type — the binding type (data, event, slot, etc.). */
  readonly kind: string;
}

/** Structured payload emitted after a binding is removed. */
export interface BindingRemovedPayload {
  readonly documentId: string;
  readonly nodeId: NodeId;
  readonly bindingId: string;
}

/**
 * Callback interface for receiving structured operation events.
 * Pass an implementation of this interface to any operation function to
 * observe mutations without coupling operations to a specific event bus.
 *
 * Example usage with @ghatana/platform-events:
 * ```ts
 * import { createPlatformEvent, BuilderEvents } from '@ghatana/platform-events';
 *
 * const bus: OperationEventBus = {
 *   onNodeInserted: (p) => emitToBackend(createPlatformEvent(BuilderEvents.COMPONENT_INSERTED, {
 *     componentId: p.nodeId,
 *     contractName: p.contractName,
 *     parentId: p.parentId,
 *     slotName: p.slotName,
 *   })),
 * };
 * ```
 */
export interface OperationEventBus {
  onNodeInserted?: (payload: NodeInsertedPayload) => void;
  onNodeMoved?: (payload: NodeMovedPayload) => void;
  onNodeDeleted?: (payload: NodeDeletedPayload) => void;
  onNodePropsUpdated?: (payload: NodePropsUpdatedPayload) => void;
  onBindingAdded?: (payload: BindingAddedPayload) => void;
  onBindingRemoved?: (payload: BindingRemovedPayload) => void;
}

/** A no-op event bus for use in tests or environments without observability. */
export const noopEventBus: OperationEventBus = {};

function nextUpdatedAt(previous?: string): string {
  const now = new Date();
  if (previous && now.toISOString() === previous) {
    now.setTime(now.getTime() + 1);
  }
  return now.toISOString();
}

// ============================================================================
// Document Operations
// ============================================================================

/** Insert a new component instance into the document. */
export function insertNode(
  document: BuilderDocument,
  instance: Omit<ComponentInstance, 'id'>,
  parentId?: NodeId,
  slotName?: string,
  bus?: OperationEventBus,
): BuilderDocument {
  if (!instance.contractName) {
    throw new Error('insertNode: contractName is required');
  }
  document = normalizeBuilderDocument(document);
  let insertedId: NodeId | undefined;
  // Capture contractName before produce to avoid any Immer freeze issues.
  const { contractName } = instance;
  const next = produce(document, (draft) => {
    const id = createNodeId();
    insertedId = id;
    const newInstance: ComponentInstance = {
      ...instance,
      id,
      slots: { ...instance.slots },
      bindings: [...instance.bindings],
      metadata: {
        ...instance.metadata,
      },
    };
    
    // Add to nodes record (canonical uses Record, not Map)
    // Spread metadata to create mutable copy for Immer compatibility with platform-events readonly arrays
    draft.nodes[id] = newInstance as unknown as typeof draft.nodes[string];
    
    // Add to parent's slot or root layout
    if (parentId && slotName) {
      const parent = draft.nodes[parentId];
      if (parent) {
        const slots = { ...parent.slots };
        const slot = slots[slotName] ?? [];
        slots[slotName] = [...slot, id];
        parent.slots = slots;
      }
    } else {
      // Add to root layout children
      const rootLayoutNode = draft.layout.nodes[draft.layout.rootId];
      if (rootLayoutNode) {
        rootLayoutNode.children = [...(rootLayoutNode.children ?? []), id];
      }
    }
    
    draft.metadata = { ...draft.metadata, updatedAt: nextUpdatedAt(document.metadata.updatedAt) };
  });
  bus?.onNodeInserted?.({
    documentId: document.documentId,
    nodeId: insertedId!,
    contractName,
    parentId,
    slotName,
  });
  return attachBuilderDocumentCompatibility(next);
}

/** Move a node to a new parent or slot. */
export function moveNode(
  document: BuilderDocument,
  nodeId: NodeId,
  newParentId: NodeId | null,
  newSlotName?: string,
  bus?: OperationEventBus,
): BuilderDocument {
  document = normalizeBuilderDocument(document);
  const next = produce(document, (draft) => {
    // Remove from current location (canonical uses Record and layout structure)
    const currentParent = findParent(draft, nodeId);
    if (currentParent) {
      const parent = draft.nodes[currentParent];
      if (parent) {
        for (const [slotName, children] of Object.entries(parent.slots)) {
          if (children.includes(nodeId)) {
            const slots = { ...parent.slots };
            slots[slotName] = children.filter((id) => id !== nodeId);
            parent.slots = slots;
            break;
          }
        }
      }
    } else {
      // Remove from root layout children
      const rootLayoutNode = draft.layout.nodes[draft.layout.rootId];
      if (rootLayoutNode?.children) {
        rootLayoutNode.children = rootLayoutNode.children.filter((id) => id !== nodeId);
      }
    }
    
    // Add to new location
    if (newParentId && newSlotName) {
      const parent = draft.nodes[newParentId];
      if (parent) {
        const slots = { ...parent.slots };
        const slot = slots[newSlotName] ?? [];
        slots[newSlotName] = [...slot, nodeId];
        parent.slots = slots;
      }
    } else {
      // Add to root layout children
      const rootLayoutNode = draft.layout.nodes[draft.layout.rootId];
      if (rootLayoutNode) {
        rootLayoutNode.children = [...(rootLayoutNode.children ?? []), nodeId];
      }
    }
    
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });
  bus?.onNodeMoved?.({ documentId: document.documentId, nodeId, newParentId, newSlotName });
  return attachBuilderDocumentCompatibility(next);
}

/** Update the visual position of a node stored in its metadata. */
export function setNodePosition(
  document: BuilderDocument,
  nodeId: NodeId,
  position: { x: number; y: number },
): BuilderDocument {
  document = normalizeBuilderDocument(document);
  const next = produce(document, (draft) => {
    const node = draft.nodes[nodeId];
    if (node) {
      node.metadata = { ...node.metadata, position };
    }
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });
  return attachBuilderDocumentCompatibility(next);
}

/** Duplicate a node, giving the copy a new unique ID and inserting it at the same level. */
export function duplicateNode(
  document: BuilderDocument,
  nodeId: NodeId,
): BuilderDocument {
  document = normalizeBuilderDocument(document);
  const source = document.nodes[nodeId];
  if (!source) return document;
  const newId = createNodeId();
  const next = produce(document, (draft) => {
    draft.nodes[newId] = {
      ...source,
      id: newId,
      metadata: { ...source.metadata, name: `${source.metadata?.name ?? source.contractName} Copy` },
      slots: Object.fromEntries(Object.entries(source.slots).map(([k, v]) => [k, [...v]])),
      bindings: [...source.bindings],
    } as typeof draft.nodes[string];
    // Insert after the original in root layout
    const rootLayoutNode = draft.layout.nodes[draft.layout.rootId];
    if (rootLayoutNode) {
      const idx = (rootLayoutNode.children ?? []).indexOf(nodeId);
      const children = [...(rootLayoutNode.children ?? [])];
      if (idx >= 0) {
        children.splice(idx + 1, 0, newId);
      } else {
        children.push(newId);
      }
      rootLayoutNode.children = children;
    }
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });
  return attachBuilderDocumentCompatibility(next);
}

/**
 * Apply multiple operations atomically inside a callback.
 * The callback receives the current document and must return the updated document.
 */
export function batchOperations(
  document: BuilderDocument,
  callback: (draft: BuilderDocument) => BuilderDocument,
): BuilderDocument {
  document = normalizeBuilderDocument(document);
  const result = callback(document);
  return attachBuilderDocumentCompatibility(normalizeBuilderDocument(result));
}

/** Delete a node and its children. */
export function deleteNode(document: BuilderDocument, nodeId: NodeId, bus?: OperationEventBus): BuilderDocument {
  document = normalizeBuilderDocument(document);
  const idsToDelete = collectNodeIds(document, nodeId);
  const next = produce(document, (draft) => {
    // Remove from parent (canonical uses Record and layout structure)
    const currentParent = findParent(draft, nodeId);
    if (currentParent) {
      const parent = draft.nodes[currentParent];
      if (parent) {
        for (const [slotName, children] of Object.entries(parent.slots)) {
          if (children.includes(nodeId)) {
            const slots = { ...parent.slots };
            slots[slotName] = children.filter((id) => !idsToDelete.includes(id));
            parent.slots = slots;
            break;
          }
        }
      }
    } else {
      // Remove from root layout children
      const rootLayoutNode = draft.layout.nodes[draft.layout.rootId];
      if (rootLayoutNode?.children) {
        rootLayoutNode.children = rootLayoutNode.children.filter((id) => !idsToDelete.includes(id));
      }
    }
    
    // Delete all nodes (canonical uses Record, not Map)
    for (const id of idsToDelete) {
      delete draft.nodes[id];
    }
    
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });
  bus?.onNodeDeleted?.({ documentId: document.documentId, nodeId, deletedCount: idsToDelete.length });
  return attachBuilderDocumentCompatibility(next);
}

/** Update a node's props. */
export function updateNodeProps(
  document: BuilderDocument,
  nodeId: NodeId,
  props: Record<string, unknown>,
  bus?: OperationEventBus,
): BuilderDocument {
  document = normalizeBuilderDocument(document);
  const next = produce(document, (draft) => {
    const node = draft.nodes[nodeId];
    if (node) {
      node.props = { ...node.props, ...props };
    }
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });
  bus?.onNodePropsUpdated?.({ documentId: document.documentId, nodeId, updatedKeys: Object.keys(props) });
  return attachBuilderDocumentCompatibility(next);
}

/** Add a binding to a node. */
export function addBinding(
  document: BuilderDocument,
  nodeId: NodeId,
  binding: Binding,
  bus?: OperationEventBus,
): BuilderDocument {
  document = normalizeBuilderDocument(document);
  const next = produce(document, (draft) => {
    const node = draft.nodes[nodeId];
    if (node) {
      node.bindings = [...node.bindings, binding];
    }
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });
  bus?.onBindingAdded?.({
    documentId: document.documentId,
    nodeId,
    bindingId: binding.id,
    propPath: binding.target,
    kind: binding.type,
  });
  return attachBuilderDocumentCompatibility(next);
}

/** Remove a binding from a node. */
export function removeBinding(
  document: BuilderDocument,
  nodeId: NodeId,
  bindingId: string,
  bus?: OperationEventBus,
): BuilderDocument {
  document = normalizeBuilderDocument(document);
  const next = produce(document, (draft) => {
    const node = draft.nodes[nodeId];
    if (node) {
      node.bindings = node.bindings.filter((b) => b.id !== bindingId);
    }
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });
  bus?.onBindingRemoved?.({ documentId: document.documentId, nodeId, bindingId });
  return attachBuilderDocumentCompatibility(next);
}

// ============================================================================
// Reorder — move a node within its current slot/root list
// ============================================================================

/** Reorder a node within its current parent slot or the root list. */
export function reorderNode(
  document: BuilderDocument,
  nodeId: NodeId,
  toIndex: number,
): BuilderDocument {
  document = normalizeBuilderDocument(document);
  return attachBuilderDocumentCompatibility(produce(document, (draft) => {
    const parentId = findParent(draft, nodeId);

    if (parentId === null) {
      // Root-level reorder (canonical uses layout structure)
      const rootLayoutNode = draft.layout.nodes[draft.layout.rootId];
      if (!rootLayoutNode?.children) return;
      const list = [...rootLayoutNode.children];
      const from = list.indexOf(nodeId);
      if (from === -1) return;
      list.splice(from, 1);
      list.splice(Math.min(toIndex, list.length), 0, nodeId);
      rootLayoutNode.children = list;
    } else {
      const parent = draft.nodes[parentId];
      if (!parent) return;
      for (const [slotName, children] of Object.entries(parent.slots)) {
        const from = children.indexOf(nodeId);
        if (from === -1) continue;
        const list = [...children];
        list.splice(from, 1);
        list.splice(Math.min(toIndex, list.length), 0, nodeId);
        const slots = { ...parent.slots };
        slots[slotName] = list;
        parent.slots = slots;
        break;
      }
    }

    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  }));
}

// ============================================================================
// Resize / Reposition
// ============================================================================

/** Update the size of a node in the builder canvas. */
export function resizeNode(
  document: BuilderDocument,
  nodeId: NodeId,
  size: { readonly width: number; readonly height: number },
): BuilderDocument {
  document = normalizeBuilderDocument(document);
  return attachBuilderDocumentCompatibility(produce(document, (draft) => {
    const node = draft.nodes[nodeId];
    if (node) {
      node.metadata = { ...node.metadata, size };
    }
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  }));
}

/** Update the position of a node in the builder canvas. */
export function repositionNode(
  document: BuilderDocument,
  nodeId: NodeId,
  position: { readonly x: number; readonly y: number },
): BuilderDocument {
  document = normalizeBuilderDocument(document);
  return attachBuilderDocumentCompatibility(produce(document, (draft) => {
    const node = draft.nodes[nodeId];
    if (node) {
      node.metadata = { ...node.metadata, position };
    }
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  }));
}

// ============================================================================
// Responsive Overrides
// ============================================================================

import type { ResponsiveVariant } from './types';

/**
 * Set or replace the responsive variant for a specific breakpoint on a node.
 * Existing variants at other breakpoints are preserved.
 */
export function setResponsiveVariant(
  document: BuilderDocument,
  nodeId: NodeId,
  variant: ResponsiveVariant,
): BuilderDocument {
  document = normalizeBuilderDocument(document);
  return attachBuilderDocumentCompatibility(produce(document, (draft) => {
    const node = draft.nodes[nodeId];
    if (!node) return;
    const existing = node.metadata.responsiveVariants ?? [];
    const filtered = existing.filter((v) => v.breakpoint !== variant.breakpoint);
    node.metadata = {
      ...node.metadata,
      responsiveVariants: [...filtered, variant],
    };
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  }));
}

/**
 * Remove the responsive variant for a specific breakpoint from a node.
 */
export function removeResponsiveVariant(
  document: BuilderDocument,
  nodeId: NodeId,
  breakpoint: string,
): BuilderDocument {
  document = normalizeBuilderDocument(document);
  return attachBuilderDocumentCompatibility(produce(document, (draft) => {
    const node = draft.nodes[nodeId];
    if (!node) return;
    const existing = node.metadata.responsiveVariants ?? [];
    node.metadata = {
      ...node.metadata,
      responsiveVariants: existing.filter((v) => v.breakpoint !== breakpoint),
    };
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  }));
}

// ============================================================================
// Action-Graph Editing
// ============================================================================

import type { ActionDefinition } from './types';

/**
 * Add or replace an action definition on a node.
 * If an action with the same `id` already exists it is replaced; otherwise
 * the new action is appended.
 */
export function upsertAction(
  document: BuilderDocument,
  nodeId: NodeId,
  action: ActionDefinition,
): BuilderDocument {
  document = normalizeBuilderDocument(document);
  return attachBuilderDocumentCompatibility(produce(document, (draft) => {
    const node = draft.nodes[nodeId];
    if (!node) return;
    const existing = node.metadata.actions ?? [];
    const idx = existing.findIndex((a) => a.id === action.id);
    const next = [...existing];
    if (idx >= 0) {
      next[idx] = action;
    } else {
      next.push(action);
    }
    node.metadata = { ...node.metadata, actions: next };
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  }));
}

/**
 * Remove an action definition from a node by ID.
 */
export function removeAction(
  document: BuilderDocument,
  nodeId: NodeId,
  actionId: string,
): BuilderDocument {
  document = normalizeBuilderDocument(document);
  return attachBuilderDocumentCompatibility(produce(document, (draft) => {
    const node = draft.nodes[nodeId];
    if (!node) return;
    node.metadata = {
      ...node.metadata,
      actions: (node.metadata.actions ?? []).filter((a) => a.id !== actionId),
    };
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  }));
}

// ============================================================================
// Batch Updates
// ============================================================================

/**
 * Apply a sequence of document transformation functions in a single atomic
 * step.  Each function receives the document produced by the previous one.
 * This is a lightweight alternative to a formal command pattern when the
 * caller needs to combine several operations without interleaved re-renders.
 *
 * @example
 * const next = batchUpdate(document, [
 *   (d) => insertNode(d, nodeA),
 *   (d) => updateNodeProps(d, nodeA.id, { label: 'Hello' }),
 * ]);
 */
export function batchUpdate(
  document: BuilderDocument,
  operations: ReadonlyArray<(doc: BuilderDocument) => BuilderDocument>,
): BuilderDocument {
  return attachBuilderDocumentCompatibility(operations.reduce((doc, op) => op(doc), normalizeBuilderDocument(document)));
}

// ============================================================================
// Undo / Redo Snapshots
// ============================================================================

/**
 * An in-memory undo/redo stack backed by document snapshots.
 *
 * Usage:
 * ```ts
 * const history = createUndoStack(initialDocument, { maxDepth: 50 });
 * history.push(afterInsertNode);
 * const prev = history.undo();   // returns the document before insertNode
 * const next = history.redo();   // returns afterInsertNode again
 * ```
 */
export interface UndoStack {
  /** Push a new document snapshot onto the stack. */
  readonly push: (document: BuilderDocument) => void;
  /** Undo the most recent operation. Returns `undefined` if at the start. */
  readonly undo: () => BuilderDocument | undefined;
  /** Redo the most recently undone operation. Returns `undefined` if nothing to redo. */
  readonly redo: () => BuilderDocument | undefined;
  /** True when there is at least one snapshot to undo. */
  readonly canUndo: boolean;
  /** True when there is at least one snapshot to redo. */
  readonly canRedo: boolean;
  /** The current document (tip of the undo stack). */
  readonly current: BuilderDocument;
  /** Clear all history. */
  readonly clear: () => void;
}

export interface UndoStackOptions {
  /** Maximum number of snapshots to keep (default 100). */
  readonly maxDepth?: number;
}

/**
 * Create an undo/redo stack seeded with an initial document snapshot.
 */
export function createUndoStack(
  initial: BuilderDocument,
  options: UndoStackOptions = {},
): UndoStack {
  const maxDepth = options.maxDepth ?? 100;
  // Past snapshots (most recent at the end), current document, future snapshots
  let past: BuilderDocument[] = [];
  let present: BuilderDocument = normalizeBuilderDocument(initial);
  let future: BuilderDocument[] = [];

  return {
    get canUndo() { return past.length > 0; },
    get canRedo() { return future.length > 0; },
    get current() { return present; },

    push(document) {
      past = [...past, present].slice(-maxDepth);
      present = normalizeBuilderDocument(document);
      future = [];
    },

    undo() {
      if (past.length === 0) return undefined;
      const prev = past[past.length - 1];
      past = past.slice(0, -1);
      future = [present, ...future];
      present = prev;
      return attachBuilderDocumentCompatibility(present);
    },

    redo() {
      if (future.length === 0) return undefined;
      const [next, ...rest] = future;
      past = [...past, present];
      future = rest;
      present = next;
      return attachBuilderDocumentCompatibility(present);
    },

    clear() {
      past = [];
      future = [];
    },
  };
}

// ============================================================================
// Conflict-Safe Merge Hooks
// ============================================================================

/**
 * Describes a conflict between a local document state and a remote document
 * state arriving from a collaboration peer.
 */
export interface DocumentConflict {
  readonly nodeId: NodeId;
  /** The operation path where the conflict was detected (e.g. `props.label`). */
  readonly path: string;
  readonly localValue: unknown;
  readonly remoteValue: unknown;
  /** Timestamp of the local change. */
  readonly localTimestamp: string;
  /** Timestamp of the remote change. */
  readonly remoteTimestamp: string;
}

/**
 * Resolution instruction returned by a conflict resolver.
 */
export type ConflictResolution =
  | { readonly strategy: 'accept-local' }
  | { readonly strategy: 'accept-remote' }
  | { readonly strategy: 'merge'; readonly mergedValue: unknown };

/**
 * A function that resolves a single conflict between local and remote state.
 * The default (`lastWriteWins`) picks the value with the later timestamp.
 */
export type ConflictResolver = (conflict: DocumentConflict) => ConflictResolution;

/** Default conflict resolver: last-write-wins by timestamp. */
export const lastWriteWins: ConflictResolver = (conflict) => {
  return conflict.remoteTimestamp >= conflict.localTimestamp
    ? { strategy: 'accept-remote' }
    : { strategy: 'accept-local' };
};

/**
 * Merge `remote` onto `local` using the provided conflict resolver.
 *
 * This is a **shallow** merge over individual node props and metadata fields —
 * it is not a full CRDT merge. For production collaboration, integrate a
 * proper CRDT library (e.g. Yjs) at the product layer and use this hook
 * only for resolving conflicts flagged by the CRDT framework.
 *
 * @returns The merged document plus any conflicts that were detected.
 */
export function mergeDocuments(
  local: BuilderDocument,
  remote: BuilderDocument,
  resolver: ConflictResolver = lastWriteWins,
): { readonly merged: BuilderDocument; readonly conflicts: readonly DocumentConflict[] } {
  local = normalizeBuilderDocument(local);
  remote = normalizeBuilderDocument(remote);
  const detectedConflicts: DocumentConflict[] = [];

  const merged = produce(local, (draft) => {
    // Merge in nodes that exist in remote but not in local (additions)
    for (const [nodeId, remoteNode] of Object.entries(remote.nodes)) {
      if (!(nodeId in draft.nodes)) {
        draft.nodes[nodeId] = remoteNode as unknown as typeof draft.nodes[string];
        continue;
      }

      const localNode = draft.nodes[nodeId];

      // Merge props field-by-field
      const mergedProps: Record<string, unknown> = { ...localNode.props };
      for (const [key, remoteVal] of Object.entries(remoteNode.props)) {
        const localVal = localNode.props[key];
        if (JSON.stringify(localVal) !== JSON.stringify(remoteVal)) {
          const localTs = local.metadata.updatedAt;
          const remoteTs = remote.metadata.updatedAt;
          const conflict: DocumentConflict = {
            nodeId: nodeId as NodeId,
            path: `props.${key}`,
            localValue: localVal,
            remoteValue: remoteVal,
            localTimestamp: localTs,
            remoteTimestamp: remoteTs,
          };
          detectedConflicts.push(conflict);
          const resolution = resolver(conflict);
          if (resolution.strategy === 'accept-remote') {
            mergedProps[key] = remoteVal;
          } else if (resolution.strategy === 'merge') {
            mergedProps[key] = resolution.mergedValue;
          }
          // 'accept-local' — keep localVal (already set)
        }
      }
      localNode.props = mergedProps;
    }

    // Use the later updatedAt timestamp
    const localTs = local.metadata.updatedAt;
    const remoteTs = remote.metadata.updatedAt;
    draft.metadata = {
      ...draft.metadata,
      updatedAt: remoteTs > localTs ? remoteTs : localTs,
      changeCount: Math.max(
        local.metadata.changeCount ?? 0,
        remote.metadata.changeCount ?? 0,
      ),
    };
  });

  return { merged: attachBuilderDocumentCompatibility(merged), conflicts: detectedConflicts };
}

// ============================================================================
// Utilities
// ============================================================================

/** Find the parent node ID of a given node. */
function findParent(document: BuilderDocument, nodeId: NodeId): NodeId | null {
  // Check root layout children (canonical uses layout structure)
  const rootLayoutNode = document.layout.nodes[document.layout.rootId];
  if (rootLayoutNode?.children?.includes(nodeId)) {
    return null;
  }
  
  // Search through all nodes' slots
  for (const [id, node] of Object.entries(document.nodes)) {
    for (const children of Object.values(node.slots)) {
      if (children.includes(nodeId)) {
        return id as NodeId;
      }
    }
  }
  
  return null;
}

/** Collect all node IDs in a subtree (including the root). */
function collectNodeIds(document: BuilderDocument, rootId: NodeId): readonly NodeId[] {
  const ids: NodeId[] = [rootId];
  const node = document.nodes[rootId];
  
  if (node) {
    for (const children of Object.values(node.slots)) {
      for (const childId of children) {
        ids.push(...collectNodeIds(document, childId));
      }
    }
  }
  
  return ids;
}
