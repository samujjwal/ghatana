/**
 * @fileoverview SceneProjection — builder-to-canvas artboard projection and edit round-trip.
 *
 * Converts a BuilderDocument into a canvas-compatible scene description, and
 * reconciles canvas-side edits (position, size, new nodes) back into the
 * BuilderDocument model.
 */

import type { BuilderDocument, ComponentInstance, NodeId } from './types.js';

// ============================================================================
// Scene Model (canvas-side representation)
// ============================================================================

/** A flat node in the canvas artboard scene. */
export interface SceneNode {
  readonly id: NodeId;
  readonly contractName: string;
  readonly label: string;
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
  readonly parentId: NodeId | null;
  readonly slotName: string | null;
  readonly props: Readonly<Record<string, unknown>>;
  readonly locked: boolean;
  readonly hidden: boolean;
}

/** Canvas viewport state at projection time. */
export interface SceneViewport {
  readonly x: number;
  readonly y: number;
  readonly zoom: number;
}

/** Full artboard scene projection. */
export interface SceneProjection {
  readonly documentId: string;
  readonly documentVersion: string;
  readonly projectedAt: number;
  readonly nodes: readonly SceneNode[];
  readonly viewport: SceneViewport;
}

// ============================================================================
// SceneDelta — diffs coming back from canvas edits
// ============================================================================

export type SceneDeltaKind =
  | 'move'
  | 'resize'
  | 'reorder'
  | 'delete'
  | 'update-props';

export interface SceneDelta {
  readonly kind: SceneDeltaKind;
  readonly nodeId: NodeId;
  readonly payload: SceneDeltaPayload;
}

export type SceneDeltaPayload =
  | MoveDeltaPayload
  | ResizeDeltaPayload
  | ReorderDeltaPayload
  | DeleteDeltaPayload
  | UpdatePropsDeltaPayload;

export interface MoveDeltaPayload {
  readonly kind: 'move';
  readonly x: number;
  readonly y: number;
}

export interface ResizeDeltaPayload {
  readonly kind: 'resize';
  readonly width: number;
  readonly height: number;
}

export interface ReorderDeltaPayload {
  readonly kind: 'reorder';
  readonly newParentId: NodeId | null;
  readonly newSlotName: string | null;
  readonly newIndex: number;
}

export interface DeleteDeltaPayload {
  readonly kind: 'delete';
}

export interface UpdatePropsDeltaPayload {
  readonly kind: 'update-props';
  readonly props: Record<string, unknown>;
}

// ============================================================================
// Projection: BuilderDocument → SceneProjection
// ============================================================================

const DEFAULT_NODE_SIZE = { width: 200, height: 60 };

/**
 * Projects a BuilderDocument into a flat SceneProjection for canvas consumption.
 * All nodes are flattened with parent references preserved.
 */
export function projectToScene(
  document: BuilderDocument,
  viewport: SceneViewport = { x: 0, y: 0, zoom: 1 },
): SceneProjection {
  const sceneNodes: SceneNode[] = [];
  let autoX = 40;

  for (const rootId of document.rootNodes) {
    const node = document.nodes.get(rootId);
    if (node) {
      collectSceneNodes(node, null, null, document, sceneNodes, autoX, 40);
      autoX += DEFAULT_NODE_SIZE.width + 24;
    }
  }

  return {
    documentId: document.id,
    documentVersion: document.version,
    projectedAt: Date.now(),
    nodes: sceneNodes,
    viewport,
  };
}

function collectSceneNodes(
  node: ComponentInstance,
  parentId: NodeId | null,
  slotName: string | null,
  document: BuilderDocument,
  out: SceneNode[],
  x: number,
  y: number,
): void {
  const meta = node.metadata;
  out.push({
    id: node.id,
    contractName: node.contractName,
    label: meta.name ?? node.contractName,
    x: meta.position?.x ?? x,
    y: meta.position?.y ?? y,
    width: meta.size?.width ?? DEFAULT_NODE_SIZE.width,
    height: meta.size?.height ?? DEFAULT_NODE_SIZE.height,
    parentId,
    slotName,
    props: node.props,
    locked: meta.locked ?? false,
    hidden: meta.hidden ?? false,
  });

  let childX = (meta.position?.x ?? x) + 24;
  let childY = (meta.position?.y ?? y) + (meta.size?.height ?? DEFAULT_NODE_SIZE.height) + 16;

  for (const [slot, children] of Object.entries(node.slots)) {
    for (const childId of children) {
      const child = document.nodes.get(childId);
      if (child) {
        collectSceneNodes(child, node.id, slot, document, out, childX, childY);
        childX += DEFAULT_NODE_SIZE.width + 16;
      }
    }
    childY += DEFAULT_NODE_SIZE.height + 16;
  }
}

// ============================================================================
// Reconciliation: SceneDelta[] → BuilderDocument
// ============================================================================

import { produce } from 'immer';

/**
 * Applies a list of scene deltas from canvas edits back into the BuilderDocument.
 * Preserves all builder-side data not visible in the canvas (bindings, ownership).
 */
export function reconcileSceneDeltas(
  document: BuilderDocument,
  deltas: readonly SceneDelta[],
): BuilderDocument {
  return produce(document, (draft) => {
    for (const delta of deltas) {
      const node = draft.nodes.get(delta.nodeId);
      if (!node && delta.payload.kind !== 'delete') continue;

      switch (delta.payload.kind) {
        case 'move': {
          if (!node) break;
          node.metadata = {
            ...node.metadata,
            position: { x: delta.payload.x, y: delta.payload.y },
          };
          break;
        }

        case 'resize': {
          if (!node) break;
          node.metadata = {
            ...node.metadata,
            size: { width: delta.payload.width, height: delta.payload.height },
          };
          break;
        }

        case 'update-props': {
          if (!node) break;
          node.props = { ...node.props, ...delta.payload.props };
          break;
        }

        case 'delete': {
          (draft.nodes as Map<NodeId, ComponentInstance>).delete(delta.nodeId);
          draft.rootNodes = draft.rootNodes.filter((id) => id !== delta.nodeId);
          for (const [, n] of draft.nodes) {
            for (const [slotName, children] of Object.entries(n.slots)) {
              if (children.includes(delta.nodeId)) {
                n.slots = {
                  ...n.slots,
                  [slotName]: children.filter((id) => id !== delta.nodeId),
                };
              }
            }
          }
          break;
        }

        case 'reorder': {
          if (!node) break;
          const { newParentId, newSlotName } = delta.payload;

          // Remove from old position
          draft.rootNodes = draft.rootNodes.filter((id) => id !== delta.nodeId);
          for (const [, n] of draft.nodes) {
            for (const [slotName, children] of Object.entries(n.slots)) {
              if (children.includes(delta.nodeId)) {
                n.slots = {
                  ...n.slots,
                  [slotName]: children.filter((id) => id !== delta.nodeId),
                };
              }
            }
          }

          // Insert at new position
          if (newParentId && newSlotName) {
            const parent = draft.nodes.get(newParentId);
            if (parent) {
              const slot = parent.slots[newSlotName] ?? [];
              parent.slots = {
                ...parent.slots,
                [newSlotName]: [...slot, delta.nodeId],
              };
            }
          } else {
            draft.rootNodes = [...draft.rootNodes, delta.nodeId];
          }
          break;
        }
      }
    }

    draft.metadata = { ...draft.metadata, updatedAt: new Date().toISOString() };
  });
}
