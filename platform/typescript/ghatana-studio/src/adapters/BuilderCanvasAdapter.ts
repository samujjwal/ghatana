/**
 * @fileoverview BuilderCanvasAdapter — typed bi-directional bridge between
 * BuilderDocument (canonical model) and @ghatana/canvas node/edge format.
 *
 * Responsibilities:
 * - Convert BuilderDocument components → canvas nodes/edges (projection)
 * - Validate canvas selection string[] → typed NodeId[] (no unsafe cast)
 * - Convert canvas geometry deltas → builder document operations (reconciliation)
 *
 * All conversions are pure functions with no side effects.
 *
 * @doc.type module
 * @doc.purpose BuilderDocument ↔ canvas format adapter
 * @doc.layer platform
 * @doc.pattern Adapter
 */

import type { BuilderDocument, NodeId, ComponentInstance } from '@ghatana/ui-builder';

// ============================================================================
// CANVAS NODE/EDGE SHAPE (matches @ghatana/canvas HybridCanvas props)
// ============================================================================

/**
 * A canvas node as expected by HybridCanvas `nodes` prop.
 */
export interface CanvasNode {
  id: string;
  type: 'node';
  data: {
    contractName: string;
    props: Record<string, unknown>;
    label: string;
    nodeId: NodeId;
  };
  position: { x: number; y: number };
  selected?: boolean;
}

/**
 * A canvas edge as expected by HybridCanvas `edges` prop.
 */
export interface CanvasEdge {
  id: string;
  type: 'edge';
  source: string;
  target: string;
  data: {
    slot: string;
    type: 'slot';
  };
}

/**
 * The result of projecting a BuilderDocument onto canvas-compatible nodes and edges.
 */
export interface BuilderCanvasProjection {
  nodes: readonly CanvasNode[];
  edges: readonly CanvasEdge[];
}

/**
 * A geometry delta applied to a node in the canvas.
 */
export interface CanvasNodeGeometryDelta {
  /** The canvas node ID whose geometry changed. */
  canvasNodeId: string;
  /** New position, if changed. */
  position?: { x: number; y: number };
  /** New size, if changed. */
  size?: { width: number; height: number };
}

/**
 * A builder operation produced by reconciling canvas geometry deltas.
 */
export interface BuilderGeometryOperation {
  kind: 'update-node-geometry';
  nodeId: NodeId;
  position?: { x: number; y: number };
  size?: { width: number; height: number };
}

// ============================================================================
// PROJECTION: BuilderDocument → Canvas
// ============================================================================

/**
 * Project a BuilderDocument into canvas nodes and edges.
 *
 * Nodes:
 * - Each ComponentInstance (except RootContainer) becomes a canvas node.
 * - Position is taken from `instance.metadata.position` if present.
 *
 * Edges:
 * - Each slot child relationship becomes a directed edge: parent → child.
 *
 * @param document - The canonical BuilderDocument to project.
 * @param selectedNodeIds - Currently selected NodeIds (for `selected` flag).
 * @returns Canvas-compatible nodes and edges.
 */
export function projectBuilderDocumentToCanvas(
  document: BuilderDocument,
  selectedNodeIds: readonly NodeId[] = [],
): BuilderCanvasProjection {
  const selectedSet = new Set<string>(selectedNodeIds);
  const nodes: CanvasNode[] = [];
  const edges: CanvasEdge[] = [];

  for (const [nodeId, instance] of Object.entries(document.nodes)) {
    const typedInstance = instance as ComponentInstance;
    // Skip the root container — it has no visual representation on canvas.
    if (typedInstance.contractName === 'RootContainer') {
      continue;
    }

    const position = (typedInstance.metadata as { position?: { x: number; y: number } })
      ?.position ?? { x: 0, y: 0 };

    nodes.push({
      id: nodeId,
      type: 'node',
      data: {
        contractName: typedInstance.contractName,
        props: { ...typedInstance.props },
        label: (typedInstance.metadata as { name?: string })?.name ?? typedInstance.contractName,
        nodeId: nodeId as NodeId,
      },
      position,
      selected: selectedSet.has(nodeId),
    });

    // Emit edges for all slot children.
    for (const [slotName, childIds] of Object.entries(typedInstance.slots)) {
      for (const childId of childIds) {
        edges.push({
          id: `${nodeId}--${slotName}--${childId}`,
          type: 'edge',
          source: nodeId,
          target: childId,
          data: {
            slot: slotName,
            type: 'slot',
          },
        });
      }
    }
  }

  return { nodes, edges };
}

// ============================================================================
// VALIDATED SELECTION MAPPING: canvas string[] → typed NodeId[]
// ============================================================================

/**
 * Validate and convert canvas-provided string IDs to typed `NodeId` values by
 * checking them against the document's known node keys.
 *
 * This replaces the unsafe `selection.nodeIds as NodeId[]` cast that was
 * previously in VisualCanvas.tsx.
 *
 * @param document - The current BuilderDocument (used as the source of truth
 *   for valid node IDs).
 * @param canvasNodeIds - Raw string IDs from the canvas selection event.
 * @returns Only the IDs that exist in the document, branded as `NodeId`.
 */
export function filterCanvasSelectionToNodeIds(
  document: BuilderDocument,
  canvasNodeIds: readonly string[],
): NodeId[] {
  const knownIds = new Set<string>(Object.keys(document.nodes));
  return canvasNodeIds.filter(id => knownIds.has(id)) as NodeId[];
}

// ============================================================================
// RECONCILIATION: canvas geometry delta → builder operations
// ============================================================================

/**
 * Convert canvas geometry deltas (from drag/resize interactions) into typed
 * builder geometry operations.
 *
 * Only deltas whose `canvasNodeId` corresponds to a document node are emitted;
 * unknown IDs (e.g. virtual canvas groups) are silently dropped.
 *
 * @param document - The current BuilderDocument.
 * @param deltas - Geometry deltas from the canvas change event.
 * @returns Builder geometry operations for known nodes.
 */
export function reconcileCanvasGeometryDeltas(
  document: BuilderDocument,
  deltas: readonly CanvasNodeGeometryDelta[],
): BuilderGeometryOperation[] {
  const knownIds = new Set<string>(Object.keys(document.nodes));
  const ops: BuilderGeometryOperation[] = [];

  for (const delta of deltas) {
    if (!knownIds.has(delta.canvasNodeId)) {
      continue;
    }

    const op: BuilderGeometryOperation = {
      kind: 'update-node-geometry',
      nodeId: delta.canvasNodeId as NodeId,
    };
    if (delta.position !== undefined) {
      op.position = delta.position;
    }
    if (delta.size !== undefined) {
      op.size = delta.size;
    }
    ops.push(op);
  }

  return ops;
}
