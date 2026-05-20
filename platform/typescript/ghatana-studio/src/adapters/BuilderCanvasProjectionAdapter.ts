/**
 * @fileoverview Canonical bidirectional adapter between @ghatana/ui-builder (BuilderDocument)
 * and @ghatana/canvas (CanvasNode / CanvasEdge).
 *
 * Provides pure functions:
 * - `builderToCanvas`: projects a BuilderDocument into canvas-ready nodes + edges.
 * - `canvasToBuilder`: merges position/size mutations from canvas back into a
 *   BuilderDocument (partial update — only structure/metadata is affected, prop
 *   values are not overwritten).
 * - `filterCanvasSelectionToNodeIds`: validates canvas selection against document.
 * - `reconcileCanvasGeometryDeltas`: converts geometry deltas to builder operations.
 *
 * Neither function mutates its input.
 *
 * @doc.type module
 * @doc.purpose BuilderDocument ↔ Canvas node/edge projection (canonical adapter)
 * @doc.layer studio
 * @doc.pattern Adapter
 */

import type { BuilderDocument, NodeId, ComponentInstance } from '@ghatana/ui-builder';
import type { HybridCanvasNode, HybridCanvasEdge } from '@ghatana/canvas';

// ============================================================================
// Types
// ============================================================================

/** Data attached to a canvas node that originated from a BuilderDocument node. */
export interface BuilderNodeData extends Record<string, unknown> {
  readonly nodeId: NodeId;
  readonly contractName: string;
  readonly props: Readonly<Record<string, unknown>>;
  readonly label: string;
}

/** Data attached to a canvas edge that represents a slot relationship. */
export interface BuilderEdgeData extends Record<string, unknown> {
  readonly parentId: NodeId;
  readonly childId: NodeId;
  readonly slotName: string;
  readonly relationKind: 'slot';
}

export type BuilderCanvasNode = HybridCanvasNode<BuilderNodeData>;
export type BuilderCanvasEdge = HybridCanvasEdge<BuilderEdgeData>;

export interface BuilderToCanvasResult {
  readonly nodes: BuilderCanvasNode[];
  readonly edges: BuilderCanvasEdge[];
}

// ============================================================================
// Builder → Canvas
// ============================================================================

const GRID_COLUMNS = 4;
const CELL_WIDTH = 240;
const CELL_HEIGHT = 140;
const MARGIN_X = 40;
const MARGIN_Y = 40;

/**
 * Project a `BuilderDocument` to canvas nodes and edges.
 *
 * Positions are taken from `instance.metadata.position` when available,
 * or fall back to a deterministic grid layout.
 */
export function builderToCanvas(doc: BuilderDocument): BuilderToCanvasResult {
  const nodes: BuilderCanvasNode[] = [];
  const edges: BuilderCanvasEdge[] = [];

  let index = 0;
  for (const [nodeId, instance] of Object.entries(doc.nodes)) {
    // RootContainer is an internal structural node; it should not appear on the canvas
    if (instance.contractName === 'RootContainer') continue;

    const metadata = instance.metadata as { position?: { x: number; y: number } };
    const position: { x: number; y: number } = metadata?.position ?? {
      x: MARGIN_X + (index % GRID_COLUMNS) * CELL_WIDTH,
      y: MARGIN_Y + Math.floor(index / GRID_COLUMNS) * CELL_HEIGHT,
    };
    index++;

    nodes.push({
      id: nodeId,
      type: 'default',
      position,
      data: {
        nodeId,
        contractName: instance.contractName,
        props: instance.props,
        label: instance.contractName,
      },
    } as BuilderCanvasNode);

    // Create edges for slot relationships
    for (const [slotName, childIds] of Object.entries(instance.slots) as [string, NodeId[]][]) {
      for (const childId of childIds) {
        edges.push({
          id: `${nodeId}__${slotName}__${childId}`,
          source: nodeId,
          target: childId,
          data: {
            parentId: nodeId as NodeId,
            childId: childId as NodeId,
            slotName,
            relationKind: 'slot',
          },
        } as BuilderCanvasEdge);
      }
    }
  }

  return { nodes, edges };
}

// ============================================================================
// Canvas → Builder (partial sync)
// ============================================================================

export interface CanvasToBuilderOptions {
  /**
   * The original BuilderDocument used as the base for the merge.
   * Only node positions/sizes are updated from the canvas state.
   */
  baseDocument: BuilderDocument;
  /** Updated canvas nodes. */
  canvasNodes: readonly BuilderCanvasNode[];
}

/**
 * Merge canvas node positions back into a BuilderDocument.
 *
 * Returns a new `BuilderDocument` instance with `metadata.position` updated
 * for every node that was found in `canvasNodes`.  Nodes not present in
 * `canvasNodes` are left unchanged.
 */
export function canvasToBuilder(options: CanvasToBuilderOptions): BuilderDocument {
  const { baseDocument, canvasNodes } = options;

  // Build a position map from the canvas node list
  const positionMap = new Map<string, { x: number; y: number }>();
  for (const cn of canvasNodes) {
    if (cn.position) {
      positionMap.set(cn.id, cn.position);
    }
  }

  // Re-build the nodes map with updated positions
  const updatedNodes: Record<string, ComponentInstance> = {};
  for (const [nodeId, instance] of Object.entries(baseDocument.nodes)) {
    const newPosition = positionMap.get(nodeId);
    if (newPosition !== undefined) {
      const existingMetadata =
        (instance.metadata as Record<string, unknown> | undefined) ?? {};
      updatedNodes[nodeId] = {
        ...instance,
        metadata: { ...existingMetadata, position: newPosition },
      } as ComponentInstance;
    } else {
      updatedNodes[nodeId] = instance;
    }
  }

  return { ...baseDocument, nodes: updatedNodes };
}

// ============================================================================
// Selection Validation: canvas string[] → typed NodeId[]
// ============================================================================

/**
 * Helper function to brand a validated string as a NodeId.
 *
 * This function is intentionally unsafe to call directly - it should only be used
 * after the ID has been validated against the document's known node keys.
 */
function asNodeId(id: string): NodeId {
  return id as NodeId;
}

/**
 * Validate and convert canvas-provided string IDs to typed `NodeId` values by
 * checking them against the document's known node keys.
 *
 * This replaces unsafe array casts at canvas adapter boundaries.
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
  const validIds: NodeId[] = [];
  for (const id of canvasNodeIds) {
    if (knownIds.has(id)) {
      validIds.push(asNodeId(id));
    }
  }
  return validIds;
}

// ============================================================================
// Geometry Reconciliation: canvas deltas → builder operations
// ============================================================================

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
