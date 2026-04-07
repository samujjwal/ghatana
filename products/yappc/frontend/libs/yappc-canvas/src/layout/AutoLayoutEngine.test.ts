/**
 * Auto Layout Engine Tests
 * @doc.type test
 * @doc.purpose Test graph layout algorithms and correctness
 * @doc.layer unit
 */

import { describe, it, expect } from 'vitest';
import { AutoLayoutEngine } from './AutoLayoutEngine';
import type {
  LayoutElement,
  LayoutEdge,
  LayoutConfig,
  LayoutResult,
} from './AutoLayoutEngine';

describe('AutoLayoutEngine', () => {
  describe('Layout Algorithm Selection', () => {
    it('should support hierarchical layout', () => {
      const elements: LayoutElement[] = [
        { id: '1', width: 100, height: 100 },
        { id: '2', width: 100, height: 100 },
      ];
      const edges: LayoutEdge[] = [{ source: '1', target: '2' }];

      const result = AutoLayoutEngine.hierarchicalLayout(elements, edges, {});

      expect(result).toBeDefined();
      expect(result.positions).toBeDefined();
    });

    it('should support force-directed layout', () => {
      const elements: LayoutElement[] = [
        { id: '1', width: 100, height: 100 },
        { id: '2', width: 100, height: 100 },
        { id: '3', width: 100, height: 100 },
      ];
      const edges: LayoutEdge[] = [
        { source: '1', target: '2' },
        { source: '2', target: '3' },
      ];

      const result = AutoLayoutEngine.forceDirectedLayout(elements, edges, {});

      expect(result).toBeDefined();
      expect(Object.keys(result.positions).length).toBe(elements.length);
    });

    it('should support grid layout', () => {
      const elements: LayoutElement[] = Array.from({ length: 9 }, (_, i) => ({
        id: `${i}`,
        width: 100,
        height: 100,
      }));

      const result = AutoLayoutEngine.gridLayout(elements, {});

      expect(result).toBeDefined();
      expect(Object.keys(result.positions).length).toBe(elements.length);
    });

    it('should support circular layout', () => {
      const elements: LayoutElement[] = Array.from({ length: 5 }, (_, i) => ({
        id: `${i}`,
        width: 100,
        height: 100,
      }));
      const edges: LayoutEdge[] = [
        { source: '0', target: '1' },
        { source: '1', target: '2' },
        { source: '2', target: '3' },
        { source: '3', target: '4' },
      ];

      const result = AutoLayoutEngine.circularLayout(elements, edges, {});

      expect(result).toBeDefined();
      expect(result.positions).toBeDefined();
    });

    it('should support tree layout', () => {
      const elements: LayoutElement[] = [
        { id: 'root', width: 100, height: 100 },
        { id: 'left', width: 100, height: 100 },
        { id: 'right', width: 100, height: 100 },
      ];
      const edges: LayoutEdge[] = [
        { source: 'root', target: 'left' },
        { source: 'root', target: 'right' },
      ];

      const result = AutoLayoutEngine.treeLayout(elements, edges, {});

      expect(result).toBeDefined();
      expect(result.positions).toBeDefined();
    });
  });

  describe('Layout Quality', () => {
    it('should minimize edge crossings', () => {
      const elements: LayoutElement[] = Array.from({ length: 6 }, (_, i) => ({
        id: `${i}`,
        width: 100,
        height: 100,
      }));
      const edges: LayoutEdge[] = [
        { source: '0', target: '3' },
        { source: '1', target: '4' },
        { source: '2', target: '5' },
      ];

      const result = AutoLayoutEngine.hierarchicalLayout(elements, edges, {});

      expect(result.metrics?.edgeCrossings).toBeLessThanOrEqual(edges.length);
    });

    it('should maintain aspect ratio', () => {
      const elements: LayoutElement[] = Array.from({ length: 12 }, (_, i) => ({
        id: `${i}`,
        width: 100,
        height: 100,
      }));

      const result = AutoLayoutEngine.gridLayout(elements, {});

      const positions = Object.values(result.positions);
      const xValues = positions.map((p) => p.x);
      const yValues = positions.map((p) => p.y);

      const width = Math.max(...xValues) - Math.min(...xValues);
      const height = Math.max(...yValues) - Math.min(...yValues);

      const aspectRatio = width / height;
      expect(aspectRatio).toBeGreaterThan(0.5);
      expect(aspectRatio).toBeLessThan(2);
    });

    it('should respect minimum spacing', () => {
      const elements: LayoutElement[] = [
        { id: '1', width: 100, height: 100 },
        { id: '2', width: 100, height: 100 },
      ];
      const edges: LayoutEdge[] = [{ source: '1', target: '2' }];

      const config: LayoutConfig = { spacing: 200 };
      const result = AutoLayoutEngine.hierarchicalLayout(
        elements,
        edges,
        config
      );

      const pos1 = result.positions['1'];
      const pos2 = result.positions['2'];

      const distance = Math.sqrt(
        Math.pow(pos2.x - pos1.x, 2) + Math.pow(pos2.y - pos1.y, 2)
      );

      expect(distance).toBeGreaterThanOrEqual(config.spacing);
    });
  });

  describe('Performance', () => {
    it('should compute layout for 100+ nodes efficiently', () => {
      const elements: LayoutElement[] = Array.from({ length: 100 }, (_, i) => ({
        id: `${i}`,
        width: 100,
        height: 100,
      }));

      const edges: LayoutEdge[] = Array.from({ length: 100 }, (_, i) => ({
        source: `${i}`,
        target: `${(i + 1) % 100}`,
      }));

      const startTime = performance.now();
      const result = AutoLayoutEngine.forceDirectedLayout(elements, edges, {});
      const endTime = performance.now();

      expect(result).toBeDefined();
      expect(endTime - startTime).toBeLessThan(500); // Should complete in <500ms
    });
  });

  describe('Edge Cases', () => {
    it('should handle single element', () => {
      const elements: LayoutElement[] = [{ id: '1', width: 100, height: 100 }];

      const result = AutoLayoutEngine.gridLayout(elements, {});

      expect(result.positions['1']).toBeDefined();
      expect(result.positions['1'].x).toBeDefined();
      expect(result.positions['1'].y).toBeDefined();
    });

    it('should handle disconnected nodes', () => {
      const elements: LayoutElement[] = Array.from({ length: 4 }, (_, i) => ({
        id: `${i}`,
        width: 100,
        height: 100,
      }));
      const edges: LayoutEdge[] = [
        { source: '0', target: '1' },
        // 2 and 3 are disconnected
      ];

      const result = AutoLayoutEngine.forceDirectedLayout(elements, edges, {});

      expect(Object.keys(result.positions).length).toBe(4);
    });

    it('should handle cyclic graphs', () => {
      const elements: LayoutElement[] = [
        { id: '1', width: 100, height: 100 },
        { id: '2', width: 100, height: 100 },
        { id: '3', width: 100, height: 100 },
      ];
      const edges: LayoutEdge[] = [
        { source: '1', target: '2' },
        { source: '2', target: '3' },
        { source: '3', target: '1' }, // Creates cycle
      ];

      const result = AutoLayoutEngine.forceDirectedLayout(elements, edges, {});

      expect(result).toBeDefined();
      expect(Object.keys(result.positions).length).toBe(3);
    });

    it('should handle self-loops', () => {
      const elements: LayoutElement[] = [{ id: '1', width: 100, height: 100 }];
      const edges: LayoutEdge[] = [{ source: '1', target: '1' }]; // Self-loop

      const result = AutoLayoutEngine.circularLayout(elements, edges, {});

      expect(result.positions['1']).toBeDefined();
    });
  });
});
