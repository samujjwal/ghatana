/**
 * @fileoverview Builder document operations - immutable operations using immer.
 *
 * Each mutating operation accepts an optional `OperationEventBus` so callers
 * can receive structured @ghatana/platform-events-compatible payloads for
 * every document change without coupling the pure transformation logic to a
 * concrete event transport.
 */

import { produce } from 'immer';
import type { BuilderDocument, ComponentInstance, NodeId, Binding } from './types';
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
  let insertedId: NodeId | undefined;
  // Capture contractName before produce to avoid any Immer freeze issues.
  const { contractName } = instance;
  const next = produce(document, (draft) => {
    const id = createNodeId();
    insertedId = id;
    const newInstance: ComponentInstance = { ...instance, id };
    
    // Add to nodes map
    (draft.nodes as Map<NodeId, ComponentInstance>).set(id, newInstance);
    
    // Add to parent's slot or root
    if (parentId && slotName) {
      const parent = draft.nodes.get(parentId);
      if (parent) {
        const slots = { ...parent.slots };
        const slot = slots[slotName] ?? [];
        slots[slotName] = [...slot, id];
        parent.slots = slots;
      }
    } else {
      draft.rootNodes = [...draft.rootNodes, id];
    }
    
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });
  bus?.onNodeInserted?.({
    documentId: document.id,
    nodeId: insertedId!,
    contractName,
    parentId,
    slotName,
  });
  return next;
}

/** Move a node to a new parent or slot. */
export function moveNode(
  document: BuilderDocument,
  nodeId: NodeId,
  newParentId: NodeId | null,
  newSlotName?: string,
  bus?: OperationEventBus,
): BuilderDocument {
  const next = produce(document, (draft) => {
    // Remove from current location
    const currentParent = findParent(draft, nodeId);
    if (currentParent) {
      const parent = draft.nodes.get(currentParent);
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
      draft.rootNodes = draft.rootNodes.filter((id) => id !== nodeId);
    }
    
    // Add to new location
    if (newParentId && newSlotName) {
      const parent = draft.nodes.get(newParentId);
      if (parent) {
        const slots = { ...parent.slots };
        const slot = slots[newSlotName] ?? [];
        slots[newSlotName] = [...slot, nodeId];
        parent.slots = slots;
      }
    } else {
      draft.rootNodes = [...draft.rootNodes, nodeId];
    }
    
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });
  bus?.onNodeMoved?.({ documentId: document.id, nodeId, newParentId, newSlotName });
  return next;
}

/** Delete a node and its children. */
export function deleteNode(document: BuilderDocument, nodeId: NodeId, bus?: OperationEventBus): BuilderDocument {
  const idsToDelete = collectNodeIds(document, nodeId);
  const next = produce(document, (draft) => {
    // Remove from parent
    const currentParent = findParent(draft, nodeId);
    if (currentParent) {
      const parent = draft.nodes.get(currentParent);
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
      draft.rootNodes = draft.rootNodes.filter((id) => !idsToDelete.includes(id));
    }
    
    // Delete all nodes
    for (const id of idsToDelete) {
      (draft.nodes as Map<NodeId, ComponentInstance>).delete(id);
    }
    
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });
  bus?.onNodeDeleted?.({ documentId: document.id, nodeId, deletedCount: idsToDelete.length });
  return next;
}

/** Update a node's props. */
export function updateNodeProps(
  document: BuilderDocument,
  nodeId: NodeId,
  props: Record<string, unknown>,
  bus?: OperationEventBus,
): BuilderDocument {
  const next = produce(document, (draft) => {
    const node = draft.nodes.get(nodeId);
    if (node) {
      node.props = { ...node.props, ...props };
    }
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });
  bus?.onNodePropsUpdated?.({ documentId: document.id, nodeId, updatedKeys: Object.keys(props) });
  return next;
}

/** Add a binding to a node. */
export function addBinding(
  document: BuilderDocument,
  nodeId: NodeId,
  binding: Binding,
  bus?: OperationEventBus,
): BuilderDocument {
  const next = produce(document, (draft) => {
    const node = draft.nodes.get(nodeId);
    if (node) {
      node.bindings = [...node.bindings, binding];
    }
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });
  bus?.onBindingAdded?.({
    documentId: document.id,
    nodeId,
    bindingId: binding.id,
    propPath: binding.target,
    kind: binding.type,
  });
  return next;
}

/** Remove a binding from a node. */
export function removeBinding(
  document: BuilderDocument,
  nodeId: NodeId,
  bindingId: string,
  bus?: OperationEventBus,
): BuilderDocument {
  const next = produce(document, (draft) => {
    const node = draft.nodes.get(nodeId);
    if (node) {
      node.bindings = node.bindings.filter((b) => b.id !== bindingId);
    }
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });
  bus?.onBindingRemoved?.({ documentId: document.id, nodeId, bindingId });
  return next;
}

// ============================================================================
// Utilities
// ============================================================================

/** Find the parent node ID of a given node. */
function findParent(document: BuilderDocument, nodeId: NodeId): NodeId | null {
  // Check root nodes
  if (document.rootNodes.includes(nodeId)) {
    return null;
  }
  
  // Search through all nodes' slots
  for (const [id, node] of document.nodes) {
    for (const children of Object.values(node.slots)) {
      if (children.includes(nodeId)) {
        return id;
      }
    }
  }
  
  return null;
}

/** Collect all node IDs in a subtree (including the root). */
function collectNodeIds(document: BuilderDocument, rootId: NodeId): readonly NodeId[] {
  const ids: NodeId[] = [rootId];
  const node = document.nodes.get(rootId);
  
  if (node) {
    for (const children of Object.values(node.slots)) {
      for (const childId of children) {
        ids.push(...collectNodeIds(document, childId));
      }
    }
  }
  
  return ids;
}
