/**
 * Layout Engine - Automatic Layout Algorithms
 *
 * Provides multiple layout algorithms for automatic node positioning:
 * - Hierarchical (Dagre): Top-down tree/flowchart layouts
 * - Force-directed: Physics-based organic layouts
 * - Grid: Simple grid-based layouts
 * - Concentric: Radial/circular layouts
 *
 * Performance targets:
 * - Dagre: <2s for 2,000 nodes
 * - Force: Converges within configured iterations
 * - Grid: O(n) linear time
 *
 * @module layout/layoutEngine
 */

import type { Point, Bounds } from '../types/canvas-document';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export type LayoutAlgorithm = 'hierarchical' | 'force' | 'grid' | 'concentric';

/**
 *
 */
export type LayoutDirection = 'TB' | 'BT' | 'LR' | 'RL'; // Top-Bottom, Bottom-Top, Left-Right, Right-Left

/**
 *
 */
export interface LayoutConfig {
  /** Layout algorithm to use */
  algorithm: LayoutAlgorithm;

  /** Direction for hierarchical layouts */
  direction?: LayoutDirection;

  /** Horizontal spacing between nodes */
  nodeSpacingX?: number;

  /** Vertical spacing between nodes */
  nodeSpacingY?: number;

  /** Rank separation for hierarchical layouts */
  rankSeparation?: number;

  /** Number of iterations for force-directed layouts */
  iterations?: number;

  /** Repulsion strength for force-directed layouts */
  repulsion?: number;

  /** Attraction strength for force-directed layouts */
  attraction?: number;

  /** Damping factor for force-directed layouts (0-1) */
  damping?: number;

  /** Grid columns for grid layouts (auto if not specified) */
  gridColumns?: number;

  /** Grid rows for grid layouts (auto if not specified) */
  gridRows?: number;

  /** Center point for concentric layouts */
  center?: Point;

  /** Radius increment for concentric layouts */
  radiusIncrement?: number;

  /** Whether to animate the layout */
  animate?: boolean;

  /** Animation duration in ms */
  animationDuration?: number;
}

/**
 *
 */
export interface LayoutNode {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 *
 */
export interface LayoutEdge {
  id: string;
  source: string;
  target: string;
}

/**
 *
 */
export interface LayoutResult {
  /** Updated node positions */
  nodes: LayoutNode[];

  /** Bounding box of the layout */
  bounds: Bounds;

  /** Execution time in milliseconds */
  executionTime: number;

  /** Number of iterations performed (for force-directed) */
  iterations?: number;

  /** Whether layout converged (for force-directed) */
  converged?: boolean;
}

/**
 *
 */
export interface LayoutPreset {
  name: string;
  description: string;
  config: LayoutConfig;
}

// ============================================================================
// Default Configurations
// ============================================================================

const DEFAULT_CONFIG: Required<LayoutConfig> = {
  algorithm: 'hierarchical',
  direction: 'TB',
  nodeSpacingX: 50,
  nodeSpacingY: 50,
  rankSeparation: 100,
  iterations: 300,
  repulsion: 100,
  attraction: 0.01,
  damping: 0.9,
  gridColumns: 0,
  gridRows: 0,
  center: { x: 0, y: 0 },
  radiusIncrement: 100,
  animate: false,
  animationDuration: 500,
};

// ============================================================================
// Main Layout Function
// ============================================================================

/**
 * Apply automatic layout to nodes
 */
export function applyLayout(
  nodes: readonly LayoutNode[],
  edges: readonly LayoutEdge[],
  config: LayoutConfig
): LayoutResult {
  const startTime = performance.now();
  const cfg = { ...DEFAULT_CONFIG, ...config };

  let result: LayoutResult;

  switch (cfg.algorithm) {
    case 'hierarchical':
      result = layoutHierarchical(nodes, edges, cfg);
      break;
    case 'force':
      result = layoutForceDirected(nodes, edges, cfg);
      break;
    case 'grid':
      result = layoutGrid(nodes, cfg);
      break;
    case 'concentric':
      result = layoutConcentric(nodes, edges, cfg);
      break;
    default:
      throw new Error(`Unknown layout algorithm: ${config.algorithm}`);
  }

  result.executionTime = performance.now() - startTime;
  return result;
}

// ============================================================================
// Hierarchical Layout (Dagre-style)
// ============================================================================

/**
 * Hierarchical layout using Sugiyama algorithm approach
 * Simplified implementation for performance
 */
function layoutHierarchical(
  nodes: readonly LayoutNode[],
  edges: readonly LayoutEdge[],
  config: Required<LayoutConfig>
): LayoutResult {
  if (nodes.length === 0) {
    return {
      nodes: [],
      bounds: { x: 0, y: 0, width: 0, height: 0 },
      executionTime: 0,
    };
  }

  // Build adjacency list
  const adjacency = new Map<string, string[]>();
  const inDegree = new Map<string, number>();

  for (const node of nodes) {
    adjacency.set(node.id, []);
    inDegree.set(node.id, 0);
  }

  for (const edge of edges) {
    adjacency.get(edge.source)?.push(edge.target);
    inDegree.set(edge.target, (inDegree.get(edge.target) || 0) + 1);
  }

  // Topological sort to assign ranks (levels)
  const ranks = assignRanks(nodes, adjacency, inDegree);

  // Group nodes by rank
  const rankGroups = new Map<number, LayoutNode[]>();
  let maxRank = 0;

  for (const node of nodes) {
    const rank = ranks.get(node.id) || 0;
    maxRank = Math.max(maxRank, rank);

    if (!rankGroups.has(rank)) {
      rankGroups.set(rank, []);
    }
    rankGroups.get(rank)!.push(node);
  }

  // Position nodes based on direction
  const positioned = positionHierarchical(
    rankGroups,
    maxRank,
    config.direction,
    config.nodeSpacingX,
    config.nodeSpacingY,
    config.rankSeparation
  );

  const bounds = calculateBounds(positioned);

  return {
    nodes: positioned,
    bounds,
    executionTime: 0,
  };
}

/**
 * Assign ranks to nodes using topological sort (Kahn's algorithm)
 */
function assignRanks(
  nodes: readonly LayoutNode[],
  adjacency: Map<string, string[]>,
  inDegree: Map<string, number>
): Map<string, number> {
  const ranks = new Map<string, number>();
  const queue: string[] = [];

  // Find all nodes with no incoming edges (roots)
  for (const node of nodes) {
    if ((inDegree.get(node.id) || 0) === 0) {
      queue.push(node.id);
      ranks.set(node.id, 0);
    }
  }

  // Handle disconnected nodes
  if (queue.length === 0 && nodes.length > 0) {
    queue.push(nodes[0].id);
    ranks.set(nodes[0].id, 0);
  }

  // BFS to assign ranks
  const inDegreeClone = new Map(inDegree);

  while (queue.length > 0) {
    const nodeId = queue.shift()!;
    const currentRank = ranks.get(nodeId) || 0;

    const neighbors = adjacency.get(nodeId) || [];
    for (const neighbor of neighbors) {
      const degree = (inDegreeClone.get(neighbor) || 0) - 1;
      inDegreeClone.set(neighbor, degree);

      if (degree === 0) {
        ranks.set(neighbor, currentRank + 1);
        queue.push(neighbor);
      }
    }
  }

  // Assign rank 0 to any remaining unranked nodes
  for (const node of nodes) {
    if (!ranks.has(node.id)) {
      ranks.set(node.id, 0);
    }
  }

  return ranks;
}

/**
 * Position nodes based on their ranks
 */
function positionHierarchical(
  rankGroups: Map<number, LayoutNode[]>,
  maxRank: number,
  direction: LayoutDirection,
  spacingX: number,
  spacingY: number,
  rankSep: number
): LayoutNode[] {
  const positioned: LayoutNode[] = [];
  const isVertical = direction === 'TB' || direction === 'BT';
  const isReversed = direction === 'BT' || direction === 'RL';

  for (let rank = 0; rank <= maxRank; rank++) {
    const displayRank = isReversed ? maxRank - rank : rank;
    const nodesInRank = rankGroups.get(rank) || [];

    // Calculate total width/height of this rank
    const totalSize = nodesInRank.reduce(
      (sum, node) => sum + (isVertical ? node.width : node.height) + spacingX,
      -spacingX
    );

    let offset = -totalSize / 2; // Center the rank

    for (const node of nodesInRank) {
      if (isVertical) {
        positioned.push({
          ...node,
          x: offset,
          y: displayRank * rankSep,
        });
        offset += node.width + spacingX;
      } else {
        positioned.push({
          ...node,
          x: displayRank * rankSep,
          y: offset,
        });
        offset += node.height + spacingY;
      }
    }
  }

  return positioned;
}

// ============================================================================
// Force-Directed Layout
// ============================================================================

/**
 * Force-directed layout using physics simulation
 * Based on Fruchterman-Reingold algorithm
 */
function layoutForceDirected(
  nodes: readonly LayoutNode[],
  edges: readonly LayoutEdge[],
  config: Required<LayoutConfig>
): LayoutResult {
  if (nodes.length === 0) {
    return {
      nodes: [],
      bounds: { x: 0, y: 0, width: 0, height: 0 },
      executionTime: 0,
      iterations: 0,
      converged: true,
    };
  }

  // Initialize positions randomly if needed
  const positioned = nodes.map((node) => ({
    ...node,
    x: node.x || (Math.random() - 0.5) * 1000,
    y: node.y || (Math.random() - 0.5) * 1000,
    vx: 0,
    vy: 0,
  }));

  const nodeMap = new Map(positioned.map((n) => [n.id, n]));

  // Build edge list
  const edgeList = edges
    .map((e) => ({
      source: nodeMap.get(e.source),
      target: nodeMap.get(e.target),
    }))
    .filter((e) => e.source && e.target) as Array<{
    source: (typeof positioned)[0];
    target: (typeof positioned)[0];
  }>;

  const { iterations: maxIterations, repulsion, attraction, damping } = config;
  let iterations = 0;
  const convergenceThreshold = 0.5;
  let maxMovement = Infinity;

  // Simulation loop
  while (iterations < maxIterations && maxMovement > convergenceThreshold) {
    maxMovement = 0;

    // Calculate repulsion forces (all pairs)
    for (let i = 0; i < positioned.length; i++) {
      const node1 = positioned[i];
      node1.vx = 0;
      node1.vy = 0;

      for (let j = 0; j < positioned.length; j++) {
        if (i === j) continue;

        const node2 = positioned[j];
        const dx = node1.x - node2.x;
        const dy = node1.y - node2.y;
        const distSq = dx * dx + dy * dy + 0.01; // Avoid division by zero
        const dist = Math.sqrt(distSq);

        const force = (repulsion * repulsion) / dist;

        node1.vx += (dx / dist) * force;
        node1.vy += (dy / dist) * force;
      }
    }

    // Calculate attraction forces (connected nodes)
    for (const edge of edgeList) {
      const dx = edge.target.x - edge.source.x;
      const dy = edge.target.y - edge.source.y;
      const dist = Math.sqrt(dx * dx + dy * dy + 0.01);

      const force = (dist * dist) * attraction;

      const fx = (dx / dist) * force;
      const fy = (dy / dist) * force;

      edge.source.vx += fx;
      edge.source.vy += fy;
      edge.target.vx -= fx;
      edge.target.vy -= fy;
    }

    // Apply forces and update positions
    for (const node of positioned) {
      node.vx *= damping;
      node.vy *= damping;

      node.x += node.vx;
      node.y += node.vy;

      const movement = Math.abs(node.vx) + Math.abs(node.vy);
      maxMovement = Math.max(maxMovement, movement);
    }

    iterations++;
  }

  const finalNodes = positioned.map(({ vx, vy, ...node }) => node);
  const bounds = calculateBounds(finalNodes);

  return {
    nodes: finalNodes,
    bounds,
    executionTime: 0,
    iterations,
    converged: maxMovement <= convergenceThreshold,
  };
}

// ============================================================================
// Grid Layout
// ============================================================================

/**
 * Simple grid layout
 */
function layoutGrid(
  nodes: readonly LayoutNode[],
  config: Required<LayoutConfig>
): LayoutResult {
  if (nodes.length === 0) {
    return {
      nodes: [],
      bounds: { x: 0, y: 0, width: 0, height: 0 },
      executionTime: 0,
    };
  }

  let { gridColumns, gridRows, nodeSpacingX, nodeSpacingY } = config;

  // Auto-calculate grid dimensions
  if (gridColumns === 0 && gridRows === 0) {
    gridColumns = Math.ceil(Math.sqrt(nodes.length));
    gridRows = Math.ceil(nodes.length / gridColumns);
  } else if (gridColumns === 0) {
    gridColumns = Math.ceil(nodes.length / gridRows);
  } else if (gridRows === 0) {
    gridRows = Math.ceil(nodes.length / gridColumns);
  }

  // Calculate cell dimensions
  const maxWidth = Math.max(...nodes.map((n) => n.width));
  const maxHeight = Math.max(...nodes.map((n) => n.height));

  const cellWidth = maxWidth + nodeSpacingX;
  const cellHeight = maxHeight + nodeSpacingY;

  // Position nodes
  const positioned = nodes.map((node, index) => {
    const col = index % gridColumns;
    const row = Math.floor(index / gridColumns);

    return {
      ...node,
      x: col * cellWidth,
      y: row * cellHeight,
    };
  });

  const bounds = calculateBounds(positioned);

  return {
    nodes: positioned,
    bounds,
    executionTime: 0,
  };
}

// ============================================================================
// Concentric Layout
// ============================================================================

/**
 * Concentric (radial) layout
 */
function layoutConcentric(
  nodes: readonly LayoutNode[],
  edges: readonly LayoutEdge[],
  config: Required<LayoutConfig>
): LayoutResult {
  if (nodes.length === 0) {
    return {
      nodes: [],
      bounds: { x: 0, y: 0, width: 0, height: 0 },
      executionTime: 0,
    };
  }

  // Calculate node degrees (number of connections)
  const degrees = new Map<string, number>();
  for (const node of nodes) {
    degrees.set(node.id, 0);
  }

  for (const edge of edges) {
    degrees.set(edge.source, (degrees.get(edge.source) || 0) + 1);
    degrees.set(edge.target, (degrees.get(edge.target) || 0) + 1);
  }

  // Sort nodes by degree (descending)
  const sorted = [...nodes].sort((a, b) => {
    const degreeA = degrees.get(a.id) || 0;
    const degreeB = degrees.get(b.id) || 0;
    return degreeB - degreeA;
  });

  // Place nodes in concentric circles
  const { center, radiusIncrement } = config;
  const positioned: LayoutNode[] = [];

  let currentRadius = 0;
  let nodesInRing = 1;
  let nodeIndex = 0;

  while (nodeIndex < sorted.length) {
    const nodesThisRing = Math.min(nodesInRing, sorted.length - nodeIndex);
    const angleStep = (2 * Math.PI) / nodesThisRing;

    for (let i = 0; i < nodesThisRing; i++) {
      const node = sorted[nodeIndex++];
      const angle = i * angleStep;

      positioned.push({
        ...node,
        x: center.x + currentRadius * Math.cos(angle),
        y: center.y + currentRadius * Math.sin(angle),
      });
    }

    currentRadius += radiusIncrement;
    nodesInRing = Math.ceil(2 * Math.PI * currentRadius / 100); // Estimate nodes per ring
  }

  const bounds = calculateBounds(positioned);

  return {
    nodes: positioned,
    bounds,
    executionTime: 0,
  };
}

// ============================================================================
// Utilities
// ============================================================================

/**
 * Calculate bounding box for positioned nodes
 */
function calculateBounds(nodes: readonly LayoutNode[]): Bounds {
  if (nodes.length === 0) {
    return { x: 0, y: 0, width: 0, height: 0 };
  }

  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;

  for (const node of nodes) {
    minX = Math.min(minX, node.x);
    minY = Math.min(minY, node.y);
    maxX = Math.max(maxX, node.x + node.width);
    maxY = Math.max(maxY, node.y + node.height);
  }

  return {
    x: minX,
    y: minY,
    width: maxX - minX,
    height: maxY - minY,
  };
}

// ============================================================================
// Layout Presets
// ============================================================================

export const LAYOUT_PRESETS: Record<string, LayoutPreset> = {
  flowchartTopDown: {
    name: 'Flowchart (Top-Down)',
    description: 'Hierarchical layout from top to bottom',
    config: {
      algorithm: 'hierarchical',
      direction: 'TB',
      rankSeparation: 100,
      nodeSpacingX: 50,
      nodeSpacingY: 50,
    },
  },

  flowchartLeftRight: {
    name: 'Flowchart (Left-Right)',
    description: 'Hierarchical layout from left to right',
    config: {
      algorithm: 'hierarchical',
      direction: 'LR',
      rankSeparation: 150,
      nodeSpacingX: 50,
      nodeSpacingY: 50,
    },
  },

  organic: {
    name: 'Organic',
    description: 'Force-directed layout with natural spacing',
    config: {
      algorithm: 'force',
      iterations: 300,
      repulsion: 100,
      attraction: 0.01,
      damping: 0.9,
    },
  },

  compactGrid: {
    name: 'Compact Grid',
    description: 'Grid layout with minimal spacing',
    config: {
      algorithm: 'grid',
      nodeSpacingX: 20,
      nodeSpacingY: 20,
    },
  },

  wideGrid: {
    name: 'Wide Grid',
    description: 'Grid layout with generous spacing',
    config: {
      algorithm: 'grid',
      nodeSpacingX: 100,
      nodeSpacingY: 100,
    },
  },

  radial: {
    name: 'Radial',
    description: 'Concentric circles with most connected nodes in center',
    config: {
      algorithm: 'concentric',
      radiusIncrement: 150,
      center: { x: 0, y: 0 },
    },
  },
};

/**
 * Get a layout preset by name
 */
export function getLayoutPreset(name: string): LayoutPreset | undefined {
  return LAYOUT_PRESETS[name];
}

/**
 * Get all available layout presets
 */
export function getAllLayoutPresets(): LayoutPreset[] {
  return Object.values(LAYOUT_PRESETS);
}
