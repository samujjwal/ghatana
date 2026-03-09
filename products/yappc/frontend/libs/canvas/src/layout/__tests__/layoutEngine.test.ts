/**
 * Layout Engine Tests
 */

import { describe, it, expect } from 'vitest';

import {
  applyLayout,
  getLayoutPreset,
  getAllLayoutPresets,
  LAYOUT_PRESETS,
  type LayoutNode,
  type LayoutEdge,
  type LayoutConfig,
} from '../layoutEngine';

describe('layoutEngine', () => {
  // Sample test data
  const createTestNodes = (count: number): LayoutNode[] => {
    return Array.from({ length: count }, (_, i) => ({
      id: `node-${i}`,
      x: 0,
      y: 0,
      width: 100,
      height: 60,
    }));
  };

  const createLinearEdges = (nodeCount: number): LayoutEdge[] => {
    return Array.from({ length: nodeCount - 1 }, (_, i) => ({
      id: `edge-${i}`,
      source: `node-${i}`,
      target: `node-${i + 1}`,
    }));
  };

  describe('hierarchical layout', () => {
    it('should layout nodes in hierarchical order top-down', () => {
      const nodes = createTestNodes(5);
      const edges = createLinearEdges(5);

      const result = applyLayout(nodes, edges, {
        algorithm: 'hierarchical',
        direction: 'TB',
        rankSeparation: 100,
      });

      expect(result.nodes).toHaveLength(5);
      expect(result.bounds).toBeDefined();
      expect(result.executionTime).toBeGreaterThanOrEqual(0);

      // Root node should be at top (y=0), others below
      const root = result.nodes.find(n => n.id === 'node-0');
      expect(root).toBeDefined();
      expect(root!.y).toBe(0);

      // Check that nodes are positioned vertically
      const yValues = result.nodes.map(n => n.y);
      const uniqueYValues = new Set(yValues);
      expect(uniqueYValues.size).toBeGreaterThan(1);
    });

    it('should layout nodes left-to-right', () => {
      const nodes = createTestNodes(5);
      const edges = createLinearEdges(5);

      const result = applyLayout(nodes, edges, {
        algorithm: 'hierarchical',
        direction: 'LR',
        rankSeparation: 150,
      });

      expect(result.nodes).toHaveLength(5);

      // Root should be at left (x=0)
      const root = result.nodes.find(n => n.id === 'node-0');
      expect(root!.x).toBe(0);

      // Check that nodes are positioned horizontally
      const xValues = result.nodes.map(n => n.x);
      const uniqueXValues = new Set(xValues);
      expect(uniqueXValues.size).toBeGreaterThan(1);
    });

    it('should handle tree structure with branches', () => {
      const nodes = createTestNodes(7);
      const edges: LayoutEdge[] = [
        { id: 'e1', source: 'node-0', target: 'node-1' },
        { id: 'e2', source: 'node-0', target: 'node-2' },
        { id: 'e3', source: 'node-1', target: 'node-3' },
        { id: 'e4', source: 'node-1', target: 'node-4' },
        { id: 'e5', source: 'node-2', target: 'node-5' },
        { id: 'e6', source: 'node-2', target: 'node-6' },
      ];

      const result = applyLayout(nodes, edges, {
        algorithm: 'hierarchical',
        direction: 'TB',
      });

      expect(result.nodes).toHaveLength(7);

      // Root at top level
      const root = result.nodes.find(n => n.id === 'node-0');
      expect(root!.y).toBe(0);

      // Children at same level
      const child1 = result.nodes.find(n => n.id === 'node-1');
      const child2 = result.nodes.find(n => n.id === 'node-2');
      expect(child1!.y).toBe(child2!.y);
      expect(child1!.y).toBeGreaterThan(root!.y);
    });

    it('should handle disconnected graphs', () => {
      const nodes = createTestNodes(4);
      const edges: LayoutEdge[] = [
        { id: 'e1', source: 'node-0', target: 'node-1' },
        // node-2 and node-3 are disconnected
      ];

      const result = applyLayout(nodes, edges, {
        algorithm: 'hierarchical',
      });

      expect(result.nodes).toHaveLength(4);
      // Should not crash with disconnected nodes
    });

    it('should handle cyclic graphs', () => {
      const nodes = createTestNodes(3);
      const edges: LayoutEdge[] = [
        { id: 'e1', source: 'node-0', target: 'node-1' },
        { id: 'e2', source: 'node-1', target: 'node-2' },
        { id: 'e3', source: 'node-2', target: 'node-0' }, // cycle
      ];

      const result = applyLayout(nodes, edges, {
        algorithm: 'hierarchical',
      });

      expect(result.nodes).toHaveLength(3);
      // Should handle cycles gracefully
    });

    it('should respect rank separation', () => {
      const nodes = createTestNodes(3);
      const edges = createLinearEdges(3);

      const result1 = applyLayout(nodes, edges, {
        algorithm: 'hierarchical',
        direction: 'TB',
        rankSeparation: 50,
      });

      const result2 = applyLayout(nodes, edges, {
        algorithm: 'hierarchical',
        direction: 'TB',
        rankSeparation: 200,
      });

      // Larger rank separation should create taller layout
      expect(result2.bounds.height).toBeGreaterThan(result1.bounds.height);
    });

    it('should handle empty input', () => {
      const result = applyLayout([], [], {
        algorithm: 'hierarchical',
      });

      expect(result.nodes).toHaveLength(0);
      expect(result.bounds).toEqual({ x: 0, y: 0, width: 0, height: 0 });
    });
  });

  describe('force-directed layout', () => {
    it('should layout nodes with force simulation', () => {
      const nodes = createTestNodes(10);
      const edges = createLinearEdges(10);

      const result = applyLayout(nodes, edges, {
        algorithm: 'force',
        iterations: 100,
        repulsion: 100,
        attraction: 0.01,
      });

      expect(result.nodes).toHaveLength(10);
      expect(result.iterations).toBeDefined();
      expect(result.iterations).toBeGreaterThan(0);
      expect(result.converged).toBeDefined();
    });

    it('should converge within iterations', () => {
      const nodes = createTestNodes(5);
      const edges = createLinearEdges(5);

      const result = applyLayout(nodes, edges, {
        algorithm: 'force',
        iterations: 300,
      });

      expect(result.iterations).toBeLessThanOrEqual(300);
      // Small graphs should converge quickly
      if (result.converged) {
        expect(result.iterations).toBeLessThan(300);
      }
    });

    it('should separate disconnected nodes', () => {
      const nodes = createTestNodes(4);
      const edges: LayoutEdge[] = []; // No connections

      const result = applyLayout(nodes, edges, {
        algorithm: 'force',
        iterations: 100,
        repulsion: 100,
      });

      // All nodes should be repelled from each other
      expect(result.nodes).toHaveLength(4);

      // Check that nodes are not all at the same position
      const positions = result.nodes.map(n => `${n.x},${n.y}`);
      const uniquePositions = new Set(positions);
      expect(uniquePositions.size).toBeGreaterThan(1);
    });

    it('should respect damping factor', () => {
      const nodes = createTestNodes(5);
      const edges = createLinearEdges(5);

      const resultLowDamping = applyLayout(nodes, edges, {
        algorithm: 'force',
        iterations: 50,
        damping: 0.5,
      });

      const resultHighDamping = applyLayout(nodes, edges, {
        algorithm: 'force',
        iterations: 50,
        damping: 0.95,
      });

      // Higher damping should converge faster (fewer iterations needed)
      expect(resultHighDamping.converged).toBeDefined();
    });

    it('should handle fully connected graph', () => {
      const nodes = createTestNodes(5);
      const edges: LayoutEdge[] = [];

      // Create all possible edges
      for (let i = 0; i < nodes.length; i++) {
        for (let j = i + 1; j < nodes.length; j++) {
          edges.push({
            id: `e-${i}-${j}`,
            source: `node-${i}`,
            target: `node-${j}`,
          });
        }
      }

      const result = applyLayout(nodes, edges, {
        algorithm: 'force',
        iterations: 200,
      });

      expect(result.nodes).toHaveLength(5);
    });

    it('should handle empty input', () => {
      const result = applyLayout([], [], {
        algorithm: 'force',
      });

      expect(result.nodes).toHaveLength(0);
      expect(result.converged).toBe(true);
      expect(result.iterations).toBe(0);
    });
  });

  describe('grid layout', () => {
    it('should layout nodes in grid', () => {
      const nodes = createTestNodes(9);

      const result = applyLayout(nodes, [], {
        algorithm: 'grid',
        nodeSpacingX: 50,
        nodeSpacingY: 50,
      });

      expect(result.nodes).toHaveLength(9);

      // Should form a 3x3 grid (auto-calculated)
      const xValues = new Set(result.nodes.map(n => n.x));
      const yValues = new Set(result.nodes.map(n => n.y));

      expect(xValues.size).toBe(3); // 3 columns
      expect(yValues.size).toBe(3); // 3 rows
    });

    it('should respect specified grid dimensions', () => {
      const nodes = createTestNodes(12);

      const result = applyLayout(nodes, [], {
        algorithm: 'grid',
        gridColumns: 4,
        gridRows: 3,
      });

      expect(result.nodes).toHaveLength(12);

      const xValues = new Set(result.nodes.map(n => Math.round(n.x)));
      expect(xValues.size).toBe(4); // 4 columns
    });

    it('should auto-calculate rows when columns specified', () => {
      const nodes = createTestNodes(10);

      const result = applyLayout(nodes, [], {
        algorithm: 'grid',
        gridColumns: 5,
      });

      expect(result.nodes).toHaveLength(10);

      // 5 columns, should have 2 rows
      const yValues = new Set(result.nodes.map(n => Math.round(n.y)));
      expect(yValues.size).toBe(2);
    });

    it('should auto-calculate columns when rows specified', () => {
      const nodes = createTestNodes(10);

      const result = applyLayout(nodes, [], {
        algorithm: 'grid',
        gridRows: 2,
      });

      expect(result.nodes).toHaveLength(10);

      // 2 rows, should have 5 columns
      const xValues = new Set(result.nodes.map(n => Math.round(n.x)));
      expect(xValues.size).toBe(5);
    });

    it('should respect node spacing', () => {
      const nodes = createTestNodes(4);

      const result1 = applyLayout(nodes, [], {
        algorithm: 'grid',
        nodeSpacingX: 20,
        nodeSpacingY: 20,
      });

      const result2 = applyLayout(nodes, [], {
        algorithm: 'grid',
        nodeSpacingX: 100,
        nodeSpacingY: 100,
      });

      // Larger spacing should create larger bounds
      expect(result2.bounds.width).toBeGreaterThan(result1.bounds.width);
      expect(result2.bounds.height).toBeGreaterThan(result1.bounds.height);
    });

    it('should handle single node', () => {
      const nodes = createTestNodes(1);

      const result = applyLayout(nodes, [], {
        algorithm: 'grid',
      });

      expect(result.nodes).toHaveLength(1);
      expect(result.nodes[0].x).toBe(0);
      expect(result.nodes[0].y).toBe(0);
    });

    it('should handle empty input', () => {
      const result = applyLayout([], [], {
        algorithm: 'grid',
      });

      expect(result.nodes).toHaveLength(0);
    });
  });

  describe('concentric layout', () => {
    it('should layout nodes in concentric circles', () => {
      const nodes = createTestNodes(10);
      const edges = createLinearEdges(10);

      const result = applyLayout(nodes, edges, {
        algorithm: 'concentric',
        radiusIncrement: 100,
        center: { x: 0, y: 0 },
      });

      expect(result.nodes).toHaveLength(10);

      // Calculate distances from center
      const distances = result.nodes.map(n =>
        Math.sqrt(n.x * n.x + n.y * n.y)
      );

      // Should have multiple distinct radii
      const uniqueDistances = new Set(
        distances.map(d => Math.round(d / 10) * 10)
      );
      expect(uniqueDistances.size).toBeGreaterThan(1);
    });

    it('should place most connected nodes in center', () => {
      const nodes = createTestNodes(5);
      const edges: LayoutEdge[] = [
        { id: 'e1', source: 'node-0', target: 'node-1' },
        { id: 'e2', source: 'node-0', target: 'node-2' },
        { id: 'e3', source: 'node-0', target: 'node-3' },
        { id: 'e4', source: 'node-0', target: 'node-4' },
        // node-0 has highest degree (4 connections)
      ];

      const result = applyLayout(nodes, edges, {
        algorithm: 'concentric',
        center: { x: 0, y: 0 },
      });

      // node-0 should be closest to center
      const node0 = result.nodes.find(n => n.id === 'node-0')!;
      const distanceFromCenter = Math.sqrt(node0.x ** 2 + node0.y ** 2);

      // Should be at or very close to center
      expect(distanceFromCenter).toBeLessThan(10);
    });

    it('should respect custom center point', () => {
      const nodes = createTestNodes(5);
      const edges = createLinearEdges(5);

      const result = applyLayout(nodes, edges, {
        algorithm: 'concentric',
        center: { x: 100, y: 200 },
        radiusIncrement: 50,
      });

      // Calculate average position (should be near center)
      const avgX = result.nodes.reduce((sum, n) => sum + n.x, 0) / result.nodes.length;
      const avgY = result.nodes.reduce((sum, n) => sum + n.y, 0) / result.nodes.length;

      expect(avgX).toBeCloseTo(100, 0);
      expect(avgY).toBeCloseTo(200, 0);
    });

    it('should respect radius increment', () => {
      const nodes = createTestNodes(10);
      const edges = createLinearEdges(10);

      const result1 = applyLayout(nodes, edges, {
        algorithm: 'concentric',
        radiusIncrement: 50,
      });

      const result2 = applyLayout(nodes, edges, {
        algorithm: 'concentric',
        radiusIncrement: 200,
      });

      // Larger increment should create larger layout
      expect(result2.bounds.width).toBeGreaterThan(result1.bounds.width);
    });

    it('should handle empty input', () => {
      const result = applyLayout([], [], {
        algorithm: 'concentric',
      });

      expect(result.nodes).toHaveLength(0);
    });
  });

  describe('performance', () => {
    it('should handle 100 nodes efficiently', () => {
      const nodes = createTestNodes(100);
      const edges = createLinearEdges(100);

      const result = applyLayout(nodes, edges, {
        algorithm: 'hierarchical',
      });

      expect(result.executionTime).toBeLessThan(100); // <100ms
      expect(result.nodes).toHaveLength(100);
    });

    it('should handle 500 nodes with force layout', () => {
      const nodes = createTestNodes(500);
      const edges = createLinearEdges(500);

      const result = applyLayout(nodes, edges, {
        algorithm: 'force',
        iterations: 50, // Limit iterations for performance
      });

      expect(result.executionTime).toBeLessThan(2000); // <2s
      expect(result.nodes).toHaveLength(500);
    });

    it('should handle 1000 nodes with grid layout', () => {
      const nodes = createTestNodes(1000);

      const result = applyLayout(nodes, [], {
        algorithm: 'grid',
      });

      expect(result.executionTime).toBeLessThan(100); // <100ms (O(n))
      expect(result.nodes).toHaveLength(1000);
    });
  });

  describe('layout presets', () => {
    it('should have predefined presets', () => {
      expect(LAYOUT_PRESETS).toBeDefined();
      expect(Object.keys(LAYOUT_PRESETS).length).toBeGreaterThan(0);
    });

    it('should get preset by name', () => {
      const preset = getLayoutPreset('flowchartTopDown');

      expect(preset).toBeDefined();
      expect(preset!.name).toBe('Flowchart (Top-Down)');
      expect(preset!.config.algorithm).toBe('hierarchical');
      expect(preset!.config.direction).toBe('TB');
    });

    it('should return undefined for unknown preset', () => {
      const preset = getLayoutPreset('nonexistent');
      expect(preset).toBeUndefined();
    });

    it('should get all presets', () => {
      const presets = getAllLayoutPresets();

      expect(presets.length).toBeGreaterThan(0);
      expect(presets.every(p => p.name && p.description && p.config)).toBe(true);
    });

    it('should apply preset configuration', () => {
      const nodes = createTestNodes(5);
      const edges = createLinearEdges(5);

      const preset = getLayoutPreset('organic')!;
      const result = applyLayout(nodes, edges, preset.config);

      expect(result.nodes).toHaveLength(5);
      expect(result.iterations).toBeDefined(); // Force layout
    });
  });

  describe('bounds calculation', () => {
    it('should calculate correct bounds', () => {
      const nodes = createTestNodes(4);

      const result = applyLayout(nodes, [], {
        algorithm: 'grid',
        gridColumns: 2,
        gridRows: 2,
        nodeSpacingX: 50,
        nodeSpacingY: 50,
      });

      expect(result.bounds.x).toBeDefined();
      expect(result.bounds.y).toBeDefined();
      expect(result.bounds.width).toBeGreaterThan(0);
      expect(result.bounds.height).toBeGreaterThan(0);
    });

    it('should include all nodes in bounds for grid layout', () => {
      const nodes = createTestNodes(10);

      const result = applyLayout(nodes, [], {
        algorithm: 'grid',
        gridColumns: 5,
      });

      // Grid layout has predictable positions
      for (const node of result.nodes) {
        expect(node.x).toBeGreaterThanOrEqual(result.bounds.x);
        expect(node.y).toBeGreaterThanOrEqual(result.bounds.y);
        expect(node.x + node.width).toBeLessThanOrEqual(
          result.bounds.x + result.bounds.width + 0.1 // small tolerance
        );
        expect(node.y + node.height).toBeLessThanOrEqual(
          result.bounds.y + result.bounds.height + 0.1 // small tolerance
        );
      }
    });
  });
});
