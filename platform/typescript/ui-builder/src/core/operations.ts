/**
 * @fileoverview Builder document operations - immutable operations using immer.
 */

import { produce } from 'immer';
import type { BuilderDocument, ComponentInstance, NodeId, Binding } from './types';
import { createNodeId } from './types';

// ============================================================================
// Document Operations
// ============================================================================

/** Insert a new component instance into the document. */
export function insertNode(
  document: BuilderDocument,
  instance: Omit<ComponentInstance, 'id'>,
  parentId?: NodeId,
  slotName?: string,
): BuilderDocument {
  return produce(document, (draft) => {
    const id = createNodeId();
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
}

/** Move a node to a new parent or slot. */
export function moveNode(
  document: BuilderDocument,
  nodeId: NodeId,
  newParentId: NodeId | null,
  newSlotName?: string,
): BuilderDocument {
  return produce(document, (draft) => {
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
}

/** Delete a node and its children. */
export function deleteNode(document: BuilderDocument, nodeId: NodeId): BuilderDocument {
  return produce(document, (draft) => {
    const idsToDelete = collectNodeIds(draft, nodeId);
    
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
}

/** Update a node's props. */
export function updateNodeProps(
  document: BuilderDocument,
  nodeId: NodeId,
  props: Record<string, unknown>,
): BuilderDocument {
  return produce(document, (draft) => {
    const node = draft.nodes.get(nodeId);
    if (node) {
      node.props = { ...node.props, ...props };
    }
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });
}

/** Add a binding to a node. */
export function addBinding(
  document: BuilderDocument,
  nodeId: NodeId,
  binding: Binding,
): BuilderDocument {
  return produce(document, (draft) => {
    const node = draft.nodes.get(nodeId);
    if (node) {
      node.bindings = [...node.bindings, binding];
    }
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });
}

/** Remove a binding from a node. */
export function removeBinding(
  document: BuilderDocument,
  nodeId: NodeId,
  bindingId: string,
): BuilderDocument {
  return produce(document, (draft) => {
    const node = draft.nodes.get(nodeId);
    if (node) {
      node.bindings = node.bindings.filter((b) => b.id !== bindingId);
    }
    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });
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
