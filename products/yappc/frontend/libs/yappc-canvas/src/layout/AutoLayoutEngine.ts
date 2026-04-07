/**
 * @file Auto-Layout Engine for YAPPC Canvas
 * Automatically arranges diagram elements using graph layout algorithms
 *
 * @doc.type utility
 * @doc.purpose Automatically arrange canvas elements for better visualization
 * @doc.layer presentation
 * @doc.pattern LayoutAlgorithm
 *
 * @example
 * ```typescript
 * const layout = new AutoLayoutEngine();
 * const positions = layout.calculateLayout(elements, 'hierarchical');
 * ```
 */

// ============================================================================
// Types
// ============================================================================

export interface LayoutElement {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  type: 'node' | 'group';
  parentId?: string;
}

export interface LayoutEdge {
  id: string;
  source: string;
  target: string;
  type?: 'straight' | 'orthogonal' | 'curved';
}

export interface LayoutConfig {
  algorithm: 'hierarchical' | 'force-directed' | 'grid' | 'circular' | 'tree';
  direction?: 'vertical' | 'horizontal';
  spacing?: number;
  padding?: number;
  align?: 'left' | 'center' | 'right';
}

export interface LayoutResult {
  positions: Map<string, { x: number; y: number }>;
  edges: LayoutEdge[];
  bounds: { width: number; height: number };
}

// ============================================================================
// Auto Layout Engine
// ============================================================================

/**
 *
 */
export class AutoLayoutEngine {
  private config: LayoutConfig;

  /**
   *
   */
  constructor(config: LayoutConfig) {
    this.config = {
      spacing: 50,
      padding: 20,
      direction: 'vertical',
      align: 'center',
      ...config,
    };
  }

  /**
   * Calculate optimal layout for elements
   * @doc.purpose Main entry point for auto-layout
   */
  calculateLayout(
    elements: LayoutElement[],
    edges: LayoutEdge[]
  ): LayoutResult {
    switch (this.config.algorithm) {
      case 'hierarchical':
        return this.hierarchicalLayout(elements, edges);
      case 'force-directed':
        return this.forceDirectedLayout(elements, edges);
      case 'grid':
        return this.gridLayout(elements);
      case 'circular':
        return this.circularLayout(elements);
      case 'tree':
        return this.treeLayout(elements, edges);
      default:
        return this.hierarchicalLayout(elements, edges);
    }
  }

  /**
   * Hierarchical/Sugiyama layout for flow diagrams
   * @doc.purpose Layered layout for flowcharts and process diagrams
   */
  private hierarchicalLayout(
    elements: LayoutElement[],
    edges: LayoutEdge[]
  ): LayoutResult {
    // Build adjacency list
    const graph = this.buildGraph(elements, edges);

    // 1. Assign layers using longest path layering
    const layers = this.assignLayers(graph, elements);

    // 2. Minimize crossings with barycenter method
    this.minimizeCrossings(layers, edges);

    // 3. Assign coordinates
    const positions = new Map<string, { x: number; y: number }>();
    const spacing = this.config.spacing || 50;
    const isVertical = this.config.direction === 'vertical';

    layers.forEach((layer, layerIndex) => {
      const layerSize = layer.length;
      const maxElementSize = isVertical
        ? Math.max(
            ...layer.map((id) => elements.find((e) => e.id === id)?.width || 0)
          )
        : Math.max(
            ...layer.map((id) => elements.find((e) => e.id === id)?.height || 0)
          );

      layer.forEach((elementId, index) => {
        const element = elements.find((e) => e.id === elementId);
        if (!element) return;

        if (isVertical) {
          const y =
            layerIndex * (maxElementSize + spacing) + this.config.padding!;
          const totalWidth =
            layerSize * element.width + (layerSize - 1) * spacing;
          const startX = -totalWidth / 2;
          const x =
            startX + index * (element.width + spacing) + element.width / 2;
          positions.set(elementId, { x, y });
        } else {
          const x =
            layerIndex * (maxElementSize + spacing) + this.config.padding!;
          const totalHeight =
            layerSize * element.height + (layerSize - 1) * spacing;
          const startY = -totalHeight / 2;
          const y =
            startY + index * (element.height + spacing) + element.height / 2;
          positions.set(elementId, { x, y });
        }
      });
    });

    // Calculate bounds
    const bounds = this.calculateBounds(positions, elements);

    return { positions, edges, bounds };
  }

  /**
   * Force-directed layout for organic arrangements
   * @doc.purpose Physics-based layout for organic network diagrams
   */
  private forceDirectedLayout(
    elements: LayoutElement[],
    edges: LayoutEdge[]
  ): LayoutResult {
    const positions = new Map<string, { x: number; y: number }>();
    const velocities = new Map<string, { vx: number; vy: number }>();

    // Initialize random positions
    elements.forEach((element) => {
      positions.set(element.id, {
        x: Math.random() * 400 - 200,
        y: Math.random() * 400 - 200,
      });
      velocities.set(element.id, { vx: 0, vy: 0 });
    });

    // Simulation parameters
    const repulsionForce = 1000;
    const springLength = 100;
    const springStrength = 0.1;
    const damping = 0.8;
    const iterations = 100;

    // Run simulation
    for (let i = 0; i < iterations; i++) {
      // Calculate repulsion
      elements.forEach((element1) => {
        let fx = 0,
          fy = 0;
        const pos1 = positions.get(element1.id)!;

        elements.forEach((element2) => {
          if (element1.id === element2.id) return;

          const pos2 = positions.get(element2.id)!;
          const dx = pos1.x - pos2.x;
          const dy = pos1.y - pos2.y;
          const distance = Math.sqrt(dx * dx + dy * dy) || 1;

          const force = repulsionForce / (distance * distance);
          fx += (dx / distance) * force;
          fy += (dy / distance) * force;
        });

        // Apply spring forces for connected nodes
        edges.forEach((edge) => {
          if (edge.source === element1.id || edge.target === element1.id) {
            const otherId =
              edge.source === element1.id ? edge.target : edge.source;
            const pos2 = positions.get(otherId)!;
            const dx = pos1.x - pos2.x;
            const dy = pos1.y - pos2.y;
            const distance = Math.sqrt(dx * dx + dy * dy) || 1;

            const displacement = distance - springLength;
            const force = displacement * springStrength;

            fx -= (dx / distance) * force;
            fy -= (dy / distance) * force;
          }
        });

        // Update velocity and position
        const velocity = velocities.get(element1.id)!;
        velocity.vx = (velocity.vx + fx) * damping;
        velocity.vy = (velocity.vy + fy) * damping;

        pos1.x += velocity.vx;
        pos1.y += velocity.vy;
      });
    }

    // Center the layout
    const bounds = this.calculateBounds(positions, elements);
    const centerX = bounds.width / 2;
    const centerY = bounds.height / 2;

    positions.forEach((pos) => {
      pos.x -= centerX;
      pos.y -= centerY;
    });

    return { positions, edges, bounds };
  }

  /**
   * Grid layout for uniform arrangements
   * @doc.purpose Simple grid-based layout for dashboards
   */
  private gridLayout(elements: LayoutElement[]): LayoutResult {
    const positions = new Map<string, { x: number; y: number }>();
    const spacing = this.config.spacing || 50;
    const cols = Math.ceil(Math.sqrt(elements.length));

    elements.forEach((element, index) => {
      const col = index % cols;
      const row = Math.floor(index / cols);

      positions.set(element.id, {
        x: col * (element.width + spacing) + element.width / 2,
        y: row * (element.height + spacing) + element.height / 2,
      });
    });

    const bounds = this.calculateBounds(positions, elements);
    return { positions, edges: [], bounds };
  }

  /**
   * Circular layout for radial arrangements
   * @doc.purpose Radial layout for hub-and-spoke diagrams
   */
  private circularLayout(elements: LayoutElement[]): LayoutResult {
    const positions = new Map<string, { x: number; y: number }>();
    const centerX = 0;
    const centerY = 0;
    const radius = 200;

    elements.forEach((element, index) => {
      const angle = (index / elements.length) * 2 * Math.PI - Math.PI / 2;
      positions.set(element.id, {
        x: centerX + radius * Math.cos(angle),
        y: centerY + radius * Math.sin(angle),
      });
    });

    const bounds = this.calculateBounds(positions, elements);
    return { positions, edges: [], bounds };
  }

  /**
   * Tree layout for hierarchical structures
   * @doc.purpose Specialized layout for tree structures
   */
  private treeLayout(
    elements: LayoutElement[],
    edges: LayoutEdge[]
  ): LayoutResult {
    // Build tree structure
    const tree = this.buildTree(elements, edges);
    const positions = new Map<string, { x: number; y: number }>();
    const spacing = this.config.spacing || 50;

    // Calculate positions recursively
    let currentY = 0;

    const layoutNode = (nodeId: string, depth: number): number => {
      const node = tree.get(nodeId);
      if (!node) return 0;

      const x = depth * spacing * 3;

      if (node.children.length === 0) {
        // Leaf node
        positions.set(nodeId, { x, y: currentY });
        currentY += spacing;
        return currentY - spacing;
      } else {
        // Parent node - center above children
        const childYs = node.children.map((childId) =>
          layoutNode(childId, depth + 1)
        );
        const minChildY = Math.min(...childYs);
        const maxChildY = Math.max(...childYs);
        const y = (minChildY + maxChildY) / 2;

        positions.set(nodeId, { x, y });
        return y;
      }
    };

    // Find root and layout
    const rootId = Array.from(tree.keys()).find((id) => {
      return !edges.some((e) => e.target === id);
    });

    if (rootId) {
      layoutNode(rootId, 0);
    }

    const bounds = this.calculateBounds(positions, elements);
    return { positions, edges, bounds };
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  /**
   *
   */
  private buildGraph(
    elements: LayoutElement[],
    edges: LayoutEdge[]
  ): Map<string, Set<string>> {
    const graph = new Map<string, Set<string>>();

    elements.forEach((e) => graph.set(e.id, new Set()));

    edges.forEach((edge) => {
      graph.get(edge.source)?.add(edge.target);
      graph.get(edge.target)?.add(edge.source);
    });

    return graph;
  }

  /**
   *
   */
  private assignLayers(
    graph: Map<string, Set<string>>,
    elements: LayoutElement[]
  ): string[][] {
    const layers: string[][] = [];
    const assigned = new Set<string>();

    // Find nodes with no predecessors (sources)
    let currentLayer = elements
      .filter((e) => {
        const predecessors = Array.from(graph.entries())
          .filter(([_, targets]) => targets.has(e.id))
          .map(([source]) => source);
        return predecessors.length === 0;
      })
      .map((e) => e.id);

    while (currentLayer.length > 0) {
      layers.push(currentLayer);
      currentLayer.forEach((id) => assigned.add(id));

      // Find next layer
      const nextLayer = new Set<string>();
      currentLayer.forEach((id) => {
        graph.get(id)?.forEach((target) => {
          if (!assigned.has(target)) {
            const allPredecessorsAssigned = Array.from(graph.entries())
              .filter(([_, targets]) => targets.has(target))
              .every(([source]) => assigned.has(source));

            if (allPredecessorsAssigned) {
              nextLayer.add(target);
            }
          }
        });
      });

      currentLayer = Array.from(nextLayer);
    }

    return layers;
  }

  /**
   *
   */
  private minimizeCrossings(layers: string[][], edges: LayoutEdge[]): void {
    // Simple barycenter heuristic
    for (let i = 0; i < layers.length - 1; i++) {
      const currentLayer = layers[i];
      const nextLayer = layers[i + 1];

      // Calculate barycenter for each node in next layer
      const barycenters = new Map<string, number>();

      nextLayer.forEach((nodeId) => {
        const connectedFromCurrent = edges
          .filter((e) => e.target === nodeId && currentLayer.includes(e.source))
          .map((e) => currentLayer.indexOf(e.source));

        if (connectedFromCurrent.length > 0) {
          const avg =
            connectedFromCurrent.reduce((a, b) => a + b, 0) /
            connectedFromCurrent.length;
          barycenters.set(nodeId, avg);
        } else {
          barycenters.set(nodeId, nextLayer.indexOf(nodeId));
        }
      });

      // Sort by barycenter
      nextLayer.sort(
        (a, b) => (barycenters.get(a) || 0) - (barycenters.get(b) || 0)
      );
    }
  }

  /**
   *
   */
  private buildTree(
    elements: LayoutElement[],
    edges: LayoutEdge[]
  ): Map<string, { children: string[] }> {
    const tree = new Map<string, { children: string[] }>();

    elements.forEach((e) => {
      tree.set(e.id, { children: [] });
    });

    edges.forEach((edge) => {
      tree.get(edge.source)?.children.push(edge.target);
    });

    return tree;
  }

  /**
   *
   */
  private calculateBounds(
    positions: Map<string, { x: number; y: number }>,
    elements: LayoutElement[]
  ): { width: number; height: number } {
    let minX = Infinity,
      maxX = -Infinity,
      minY = Infinity,
      maxY = -Infinity;

    positions.forEach((pos, id) => {
      const element = elements.find((e) => e.id === id);
      const halfWidth = (element?.width || 0) / 2;
      const halfHeight = (element?.height || 0) / 2;

      minX = Math.min(minX, pos.x - halfWidth);
      maxX = Math.max(maxX, pos.x + halfWidth);
      minY = Math.min(minY, pos.y - halfHeight);
      maxY = Math.max(maxY, pos.y + halfHeight);
    });

    const padding = this.config.padding || 20;
    return {
      width: maxX - minX + padding * 2,
      height: maxY - minY + padding * 2,
    };
  }
}

// ============================================================================
// Hook for React Integration
// ============================================================================

/**
 * React hook for auto-layout functionality
 * @doc.purpose Integrate auto-layout into React components
 */
export interface UseAutoLayoutReturn {
  applyLayout: (
    elements: LayoutElement[],
    edges: LayoutEdge[],
    config: LayoutConfig
  ) => LayoutResult;
}

export function useAutoLayout(): UseAutoLayoutReturn {
  const applyLayout = (
    elements: LayoutElement[],
    edges: LayoutEdge[],
    config: LayoutConfig
  ) => {
    const engine = new AutoLayoutEngine(config);
    return engine.calculateLayout(elements, edges);
  };

  return { applyLayout };
}

export default AutoLayoutEngine;
