/**
 * @fileoverview Projection: LogicalArtifactModel → CanvasDocument.
 *
 * Projects a LogicalArtifactModel into a CanvasDocument-shaped representation
 * that can be fed to @ghatana/canvas. Each artifact node becomes a CanvasNode,
 * each artifact edge becomes a CanvasEdge, and layout positions are computed
 * deterministically using a layered graph layout algorithm.
 *
 * @doc.type module
 * @doc.purpose Project LogicalArtifactModel into CanvasDocument shape
 * @doc.layer platform
 * @doc.pattern Adapter
 */

import type { ArtifactNode, ArtifactEdge, LogicalArtifactModel } from "@ghatana/artifact-contracts";
import type { FidelityReport, LossPoint } from "@ghatana/artifact-contracts";
import { computeFidelityReport } from "@ghatana/artifact-contracts";

// ============================================================================
// PROJECTED TYPES
// ============================================================================
// Mirrors @ghatana/canvas CanvasDocument structure to avoid circular dependency.

export interface ProjectedCanvasPosition {
  readonly x: number;
  readonly y: number;
}

export interface ProjectedCanvasNode {
  readonly id: string;
  readonly type: "artifactNode";
  readonly position: ProjectedCanvasPosition;
  readonly data: {
    readonly label: string;
    readonly kind: ArtifactNode["kind"];
    readonly classificationConfidence: number;
    readonly usesDesignSystem: boolean;
    readonly exportedSymbols: readonly string[];
    readonly sourceRef?: {
      readonly relativePath: string;
      readonly startLine: number;
      readonly endLine: number;
    };
  };
}

export interface ProjectedCanvasEdge {
  readonly id: string;
  readonly source: string;
  readonly target: string;
  readonly label?: string;
  readonly data: {
    readonly kind: ArtifactEdge["kind"];
    readonly importSpecifier?: string;
  };
}

export interface ProjectedCanvasDocument {
  readonly schemaVersion: string;
  readonly documentId: string;
  readonly label: string;
  readonly nodes: readonly ProjectedCanvasNode[];
  readonly edges: readonly ProjectedCanvasEdge[];
  readonly metadata: {
    readonly sourceModelId: string;
    readonly projectedAt: string;
  };
}

// ============================================================================
// OPTIONS
// ============================================================================

export interface ProjectCanvasOptions {
  /**
   * Layout algorithm for positioning nodes.
   * - "grid": Simple grid layout (default, deterministic).
   * - "layered": Layered Sugiyama-style layout based on dependency depth.
   */
  readonly layoutAlgorithm?: "grid" | "layered";
  /**
   * Grid layout parameters (used when layoutAlgorithm === "grid").
   */
  readonly grid?: {
    readonly columns?: number;
    readonly cellWidth?: number;
    readonly cellHeight?: number;
    readonly marginX?: number;
    readonly marginY?: number;
  };
  readonly schemaVersion?: string;
}

const DEFAULT_OPTIONS: Required<ProjectCanvasOptions> = {
  layoutAlgorithm: "grid",
  grid: {
    columns: 4,
    cellWidth: 240,
    cellHeight: 140,
    marginX: 40,
    marginY: 40,
  },
  schemaVersion: "1.0.0",
};

// ============================================================================
// RESULT
// ============================================================================

export interface ProjectCanvasResult {
  readonly document: ProjectedCanvasDocument;
  readonly fidelityReport: FidelityReport;
}

// ============================================================================
// LAYOUT HELPERS
// ============================================================================

/**
 * Compute a simple grid layout for an ordered list of node IDs.
 */
function gridLayout(
  nodeIds: readonly string[],
  opts: Required<ProjectCanvasOptions>["grid"],
): Map<string, ProjectedCanvasPosition> {
  const {
    columns = 4,
    cellWidth = 240,
    cellHeight = 140,
    marginX = 40,
    marginY = 40,
  } = opts;

  const positions = new Map<string, ProjectedCanvasPosition>();
  nodeIds.forEach((id, index) => {
    const col = index % columns;
    const row = Math.floor(index / columns);
    positions.set(id, {
      x: marginX + col * cellWidth,
      y: marginY + row * cellHeight,
    });
  });
  return positions;
}

/**
 * Compute a layered layout based on topological order derived from edges.
 * Nodes with no incoming edges are placed in layer 0 (top).
 */
function layeredLayout(
  nodeIds: readonly string[],
  edges: readonly ArtifactEdge[],
  opts: Required<ProjectCanvasOptions>["grid"],
): Map<string, ProjectedCanvasPosition> {
  const {
    cellWidth = 240,
    cellHeight = 160,
    marginX = 40,
    marginY = 40,
  } = opts;

  // Compute in-degree for each node
  const inDegree = new Map<string, number>(nodeIds.map((id) => [id, 0]));
  for (const edge of edges) {
    const current = inDegree.get(edge.toId);
    if (current !== undefined) {
      inDegree.set(edge.toId, current + 1);
    }
  }

  // Assign layers via BFS (Kahn's algorithm)
  const layers = new Map<string, number>();
  const queue: string[] = nodeIds.filter((id) => (inDegree.get(id) ?? 0) === 0);
  let layer = 0;
  while (queue.length > 0) {
    const next: string[] = [];
    for (const id of queue) {
      layers.set(id, layer);
    }
    // Find successors
    for (const id of queue) {
      for (const edge of edges) {
        if (edge.fromId === id) {
          const deg = inDegree.get(edge.toId);
          if (deg !== undefined) {
            const newDeg = deg - 1;
            inDegree.set(edge.toId, newDeg);
            if (newDeg === 0) next.push(edge.toId);
          }
        }
      }
    }
    queue.length = 0;
    queue.push(...next);
    layer++;
  }

  // Assign positions within each layer
  const layerGroups = new Map<number, string[]>();
  for (const [id, l] of layers.entries()) {
    const group = layerGroups.get(l) ?? [];
    group.push(id);
    layerGroups.set(l, group);
  }
  // Nodes not placed (cycles) go to last layer
  const maxLayer = layerGroups.size;
  for (const id of nodeIds) {
    if (!layers.has(id)) {
      const group = layerGroups.get(maxLayer) ?? [];
      group.push(id);
      layerGroups.set(maxLayer, group);
    }
  }

  const positions = new Map<string, ProjectedCanvasPosition>();
  for (const [l, ids] of layerGroups.entries()) {
    ids.forEach((id, i) => {
      positions.set(id, {
        x: marginX + i * cellWidth,
        y: marginY + l * cellHeight,
      });
    });
  }
  return positions;
}

// ============================================================================
// MAIN PROJECTION
// ============================================================================

/**
 * Project a LogicalArtifactModel into a CanvasDocument-shaped object.
 *
 * Layout positions are computed deterministically — no random placement.
 * Consumers can feed the resulting document directly into @ghatana/canvas APIs.
 *
 * @doc.type function
 * @doc.purpose Project LogicalArtifactModel → CanvasDocument
 * @doc.layer platform
 * @doc.pattern Adapter
 */
export function projectToCanvas(
  model: LogicalArtifactModel,
  options?: ProjectCanvasOptions,
): ProjectCanvasResult {
  const opts: Required<ProjectCanvasOptions> = {
    ...DEFAULT_OPTIONS,
    ...options,
    grid: { ...DEFAULT_OPTIONS.grid, ...options?.grid },
  };

  const lossPoints: LossPoint[] = [];
  const nodeIds = Object.keys(model.nodes);

  // Compute positions
  const positions =
    opts.layoutAlgorithm === "layered"
      ? layeredLayout(nodeIds, model.edges, opts.grid)
      : gridLayout(nodeIds, opts.grid);

  // Project nodes
  const projectedNodes: ProjectedCanvasNode[] = nodeIds.map((nodeId) => {
    const node = model.nodes[nodeId]!;
    const position = positions.get(nodeId) ?? { x: 0, y: 0 };

    if (node.classificationConfidence < 0.7) {
      lossPoints.push({
        code: "low-confidence-canvas-node",
        description: `Canvas node "${node.displayName}" projected with low confidence.`,
        severity: "info",
        sourceRef: node.sourceRef,
        confidenceImpact: (1 - node.classificationConfidence) * 0.05,
      });
    }

    return {
      id: node.id,
      type: "artifactNode",
      position,
      data: {
        label: node.displayName,
        kind: node.kind,
        classificationConfidence: node.classificationConfidence,
        usesDesignSystem: node.usesDesignSystem,
        exportedSymbols: node.exportedSymbols,
        sourceRef: node.sourceRef
          ? {
              relativePath: node.sourceRef.file.relativePath,
              startLine: node.sourceRef.span?.startLine ?? 1,
              endLine: node.sourceRef.span?.endLine ?? 1,
            }
          : undefined,
      },
    };
  });

  // Project edges (only edges within the model's node set)
  const nodeSet = new Set(nodeIds);
  const projectedEdges: ProjectedCanvasEdge[] = model.edges
    .filter((edge) => nodeSet.has(edge.fromId) && nodeSet.has(edge.toId))
    .map((edge) => ({
      id: edge.id,
      source: edge.fromId,
      target: edge.toId,
      label: edge.kind,
      data: {
        kind: edge.kind,
        importSpecifier: edge.importSpecifier,
      },
    }));

  // Warn about cross-boundary edges not projected
  const externalEdgeCount = model.edges.length - projectedEdges.length;
  if (externalEdgeCount > 0) {
    lossPoints.push({
      code: "external-edges-omitted",
      description: `${externalEdgeCount} edge(s) to external modules were not projected to canvas.`,
      severity: "info",
      confidenceImpact: 0,
    });
  }

  const document: ProjectedCanvasDocument = {
    schemaVersion: opts.schemaVersion,
    documentId: model.modelId,
    label: model.label,
    nodes: projectedNodes,
    edges: projectedEdges,
    metadata: {
      sourceModelId: model.modelId,
      projectedAt: new Date().toISOString(),
    },
  };

  const fidelityReport = computeFidelityReport(
    lossPoints,
    model.modelId,
    "pipeline",
  );

  return { document, fidelityReport };
}
