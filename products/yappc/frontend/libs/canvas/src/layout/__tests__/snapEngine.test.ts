import { describe, it, expect } from 'vitest';

import {
  snapToGrid,
  snapPointToGrid,
  getGridLines,
  distributeElements,
  alignElements,
  isNearGridLine,
  getSnapSuggestions,
  type GridConfig,
  type Point,
  type DistributionOptions,
  type AlignmentOptions,
} from './snapEngine';

describe('snapEngine', () => {
  describe('snapToGrid', () => {
    it('should snap to nearest grid point', () => {
      expect(snapToGrid(0, 10)).toBe(0);
      expect(snapToGrid(5, 10)).toBe(10);
      expect(snapToGrid(14, 10)).toBe(10);
      expect(snapToGrid(15, 10)).toBe(20);
      expect(snapToGrid(23, 10)).toBe(20);
    });

    it('should handle negative values', () => {
      expect(snapToGrid(-5, 10)).toBe(-0); // Math.round(-0.5) = -0 in JavaScript
      expect(snapToGrid(-6, 10)).toBe(-10);
      expect(snapToGrid(-15, 10)).toBe(-10); // Math.round(-1.5) = -1, rounds half toward zero
    });

    it('should handle different grid sizes', () => {
      expect(snapToGrid(17, 5)).toBe(15);
      expect(snapToGrid(18, 5)).toBe(20);
      expect(snapToGrid(42, 20)).toBe(40);
      expect(snapToGrid(51, 20)).toBe(60);
    });

    it('should handle values already on grid', () => {
      expect(snapToGrid(0, 10)).toBe(0);
      expect(snapToGrid(10, 10)).toBe(10);
      expect(snapToGrid(100, 10)).toBe(100);
    });
  });

  describe('snapPointToGrid', () => {
    const config: GridConfig = {
      size: 10,
      tolerance: 5,
      enabled: true,
    };

    it('should snap point within tolerance', () => {
      const point: Point = { x: 23, y: 47 };
      const snapped = snapPointToGrid(point, config);
      expect(snapped).toEqual({ x: 20, y: 50 });
    });

    it('should not snap point outside tolerance', () => {
      const point: Point = { x: 24, y: 46 };
      const snapped = snapPointToGrid(point, { ...config, tolerance: 3 });
      expect(snapped).toEqual({ x: 24, y: 46 }); // Both outside tolerance
    });

    it('should use default tolerance (half grid size)', () => {
      const point: Point = { x: 23, y: 47 };
      const snapped = snapPointToGrid(point, { size: 10, enabled: true });
      expect(snapped).toEqual({ x: 20, y: 50 }); // Within 5px (half of 10)
    });

    it('should not snap when disabled', () => {
      const point: Point = { x: 23, y: 47 };
      const snapped = snapPointToGrid(point, { ...config, enabled: false });
      expect(snapped).toEqual({ x: 23, y: 47 });
    });

    it('should handle negative coordinates', () => {
      const point: Point = { x: -23, y: -47 };
      const snapped = snapPointToGrid(point, config);
      expect(snapped).toEqual({ x: -20, y: -50 });
    });
  });

  describe('getGridLines', () => {
    it('should generate grid lines within viewport', () => {
      const viewport = { x: 0, y: 0, width: 100, height: 100 };
      const gridSize = 20;
      const lines = getGridLines(viewport, gridSize);

      expect(lines.vertical).toEqual([0, 20, 40, 60, 80, 100]);
      expect(lines.horizontal).toEqual([0, 20, 40, 60, 80, 100]);
    });

    it('should handle viewport not starting at origin', () => {
      const viewport = { x: 15, y: 25, width: 50, height: 50 };
      const gridSize = 10;
      const lines = getGridLines(viewport, gridSize);

      // Vertical: starts at 10 (floor(15/10)*10), ends at 70 (ceil(65/10)*10)
      expect(lines.vertical).toEqual([10, 20, 30, 40, 50, 60, 70]);
      // Horizontal: starts at 20 (floor(25/10)*10), ends at 80 (ceil(75/10)*10)
      expect(lines.horizontal).toEqual([20, 30, 40, 50, 60, 70, 80]);
    });

    it('should handle negative viewport coordinates', () => {
      const viewport = { x: -50, y: -50, width: 100, height: 100 };
      const gridSize = 25;
      const lines = getGridLines(viewport, gridSize);

      expect(lines.vertical).toContain(-50);
      expect(lines.vertical).toContain(0);
      expect(lines.vertical).toContain(50);
      expect(lines.horizontal).toContain(-50);
      expect(lines.horizontal).toContain(0);
      expect(lines.horizontal).toContain(50);
    });

    it('should handle different grid sizes', () => {
      const viewport = { x: 0, y: 0, width: 60, height: 60 };
      const lines5 = getGridLines(viewport, 5);
      const lines20 = getGridLines(viewport, 20);

      expect(lines5.vertical.length).toBeGreaterThan(lines20.vertical.length);
      expect(lines5.horizontal.length).toBeGreaterThan(
        lines20.horizontal.length
      );
    });
  });

  describe('distributeElements', () => {
    const createTestElement = (
      x: number,
      y: number,
      width: number = 50,
      height: number = 30
    ) => ({
      id: `el-${x}-${y}`,
      position: { x, y },
      data: { width, height },
    });

    describe('horizontal distribution', () => {
      it('should distribute elements evenly (even mode)', () => {
        const elements = [
          createTestElement(0, 100),
          createTestElement(100, 100),
          createTestElement(200, 100),
        ];

        const options: DistributionOptions = {
          direction: 'horizontal',
          mode: 'even',
        };

        const distributed = distributeElements(elements, options);

        // First and last should stay in place
        expect(distributed[0].position.x).toBe(0);
        expect(distributed[2].position.x).toBe(200);

        // Middle element position stays at 100 (no change in even distribution)
        expect(distributed[1].position.x).toBe(100);
      });

      it('should distribute with fixed spacing', () => {
        const elements = [
          createTestElement(0, 100, 40, 30),
          createTestElement(100, 100, 40, 30),
          createTestElement(200, 100, 40, 30),
        ];

        const options: DistributionOptions = {
          direction: 'horizontal',
          mode: 'fixed',
          spacing: 20,
        };

        const distributed = distributeElements(elements, options);

        expect(distributed[0].position.x).toBe(0);
        expect(distributed[1].position.x).toBe(60); // 0 + 40 + 20
        expect(distributed[2].position.x).toBe(120); // 60 + 40 + 20
      });

      it('should return unchanged for single element', () => {
        const elements = [createTestElement(100, 100)];
        const options: DistributionOptions = {
          direction: 'horizontal',
          mode: 'even',
        };

        const distributed = distributeElements(elements, options);
        expect(distributed).toEqual(elements);
      });

      it('should sort elements by x position before distributing', () => {
        const elements = [
          createTestElement(200, 100),
          createTestElement(0, 100),
          createTestElement(100, 100),
        ];

        const options: DistributionOptions = {
          direction: 'horizontal',
          mode: 'fixed',
          spacing: 10,
        };

        const distributed = distributeElements(elements, options);

        // Should be sorted: 0, 100, 200 -> distributed as 0, 60, 120
        expect(distributed[0].position.x).toBe(0);
        expect(distributed[1].position.x).toBe(60);
        expect(distributed[2].position.x).toBe(120);
      });
    });

    describe('vertical distribution', () => {
      it('should distribute elements evenly (even mode)', () => {
        const elements = [
          createTestElement(100, 0),
          createTestElement(100, 100),
          createTestElement(100, 200),
        ];

        const options: DistributionOptions = {
          direction: 'vertical',
          mode: 'even',
        };

        const distributed = distributeElements(elements, options);

        // First and last should stay in place
        expect(distributed[0].position.y).toBe(0);
        expect(distributed[2].position.y).toBe(200);
      });

      it('should distribute with fixed spacing', () => {
        const elements = [
          createTestElement(100, 0, 40, 30),
          createTestElement(100, 100, 40, 30),
          createTestElement(100, 200, 40, 30),
        ];

        const options: DistributionOptions = {
          direction: 'vertical',
          mode: 'fixed',
          spacing: 15,
        };

        const distributed = distributeElements(elements, options);

        expect(distributed[0].position.y).toBe(0);
        expect(distributed[1].position.y).toBe(45); // 0 + 30 + 15
        expect(distributed[2].position.y).toBe(90); // 45 + 30 + 15
      });

      it('should sort elements by y position before distributing', () => {
        const elements = [
          createTestElement(100, 200),
          createTestElement(100, 0),
          createTestElement(100, 100),
        ];

        const options: DistributionOptions = {
          direction: 'vertical',
          mode: 'fixed',
          spacing: 10,
        };

        const distributed = distributeElements(elements, options);

        expect(distributed[0].position.y).toBe(0);
        expect(distributed[1].position.y).toBe(40);
        expect(distributed[2].position.y).toBe(80);
      });
    });
  });

  describe('alignElements', () => {
    const createTestElement = (
      x: number,
      y: number,
      width: number = 50,
      height: number = 30
    ) => ({
      id: `el-${x}-${y}`,
      position: { x, y },
      data: { width, height },
    });

    describe('horizontal alignment', () => {
      it('should align left', () => {
        const elements = [
          createTestElement(100, 50),
          createTestElement(200, 100),
          createTestElement(150, 150),
        ];

        const options: AlignmentOptions = { type: 'left' };
        const aligned = alignElements(elements, options);

        // All should align to leftmost (x=100)
        expect(aligned[0].position.x).toBe(100);
        expect(aligned[1].position.x).toBe(100);
        expect(aligned[2].position.x).toBe(100);
      });

      it('should align center', () => {
        const elements = [
          createTestElement(100, 50, 50, 30),
          createTestElement(200, 100, 50, 30),
        ];

        const options: AlignmentOptions = { type: 'center' };
        const aligned = alignElements(elements, options);

        // Center of bounds: (100 + 250) / 2 = 175
        // Elements should be centered at 175
        expect(aligned[0].position.x).toBe(150); // 175 - 25
        expect(aligned[1].position.x).toBe(150);
      });

      it('should align right', () => {
        const elements = [
          createTestElement(100, 50, 50, 30),
          createTestElement(200, 100, 50, 30),
          createTestElement(150, 150, 50, 30),
        ];

        const options: AlignmentOptions = { type: 'right' };
        const aligned = alignElements(elements, options);

        // All should align right edge to rightmost (x=250)
        expect(aligned[0].position.x).toBe(200); // 250 - 50
        expect(aligned[1].position.x).toBe(200);
        expect(aligned[2].position.x).toBe(200);
      });

      it('should align to custom reference point', () => {
        const elements = [
          createTestElement(100, 50, 50, 30),
          createTestElement(200, 100, 50, 30),
        ];

        const options: AlignmentOptions = {
          type: 'left',
          reference: { x: 300, y: 0 },
        };

        const aligned = alignElements(elements, options);

        expect(aligned[0].position.x).toBe(300);
        expect(aligned[1].position.x).toBe(300);
      });
    });

    describe('vertical alignment', () => {
      it('should align top', () => {
        const elements = [
          createTestElement(50, 100),
          createTestElement(100, 200),
          createTestElement(150, 150),
        ];

        const options: AlignmentOptions = { type: 'top' };
        const aligned = alignElements(elements, options);

        // All should align to topmost (y=100)
        expect(aligned[0].position.y).toBe(100);
        expect(aligned[1].position.y).toBe(100);
        expect(aligned[2].position.y).toBe(100);
      });

      it('should align middle', () => {
        const elements = [
          createTestElement(50, 100, 50, 30),
          createTestElement(100, 200, 50, 30),
        ];

        const options: AlignmentOptions = { type: 'middle' };
        const aligned = alignElements(elements, options);

        // Middle of bounds: (100 + 230) / 2 = 165
        expect(aligned[0].position.y).toBe(150); // 165 - 15
        expect(aligned[1].position.y).toBe(150);
      });

      it('should align bottom', () => {
        const elements = [
          createTestElement(50, 100, 50, 30),
          createTestElement(100, 200, 50, 30),
          createTestElement(150, 150, 50, 30),
        ];

        const options: AlignmentOptions = { type: 'bottom' };
        const aligned = alignElements(elements, options);

        // All should align bottom edge to bottommost (y=230)
        expect(aligned[0].position.y).toBe(200); // 230 - 30
        expect(aligned[1].position.y).toBe(200);
        expect(aligned[2].position.y).toBe(200);
      });
    });

    it('should return empty array for empty input', () => {
      const options: AlignmentOptions = { type: 'left' };
      const aligned = alignElements([], options);
      expect(aligned).toEqual([]);
    });
  });

  describe('isNearGridLine', () => {
    it('should return true when within tolerance', () => {
      expect(isNearGridLine(23, 10, 5)).toBe(true); // 23 is 3px from 20
      expect(isNearGridLine(17, 10, 5)).toBe(true); // 17 is 3px from 20
      expect(isNearGridLine(5, 10, 5)).toBe(true); // 5 is 5px from 0 or 10
    });

    it('should return false when outside tolerance', () => {
      expect(isNearGridLine(24, 10, 3)).toBe(false); // 24 is 4px from 20
      expect(isNearGridLine(16, 10, 3)).toBe(false); // 16 is 4px from 20
    });

    it('should handle values on grid line', () => {
      expect(isNearGridLine(0, 10, 5)).toBe(true);
      expect(isNearGridLine(10, 10, 5)).toBe(true);
      expect(isNearGridLine(100, 10, 5)).toBe(true);
    });

    it('should handle negative values', () => {
      expect(isNearGridLine(-23, 10, 5)).toBe(true); // -23 is 3px from -20
      expect(isNearGridLine(-26, 10, 5)).toBe(true); // -26 is 4px from -30 (within tolerance)
    });
  });

  describe('getSnapSuggestions', () => {
    const config: GridConfig = {
      size: 10,
      tolerance: 5,
      enabled: true,
    };

    it('should suggest snap for both axes within tolerance', () => {
      const point: Point = { x: 23, y: 47 };
      const suggestions = getSnapSuggestions(point, config);

      expect(suggestions.x).toBe(20);
      expect(suggestions.y).toBe(50);
    });

    it('should suggest snap for one axis only', () => {
      const point: Point = { x: 23, y: 44 };
      const suggestions = getSnapSuggestions(point, {
        ...config,
        tolerance: 3,
      });

      expect(suggestions.x).toBe(20); // 3px from 20
      expect(suggestions.y).toBe(null); // 6px from nearest grid
    });

    it('should suggest null for both axes when outside tolerance', () => {
      const point: Point = { x: 24, y: 46 };
      const suggestions = getSnapSuggestions(point, {
        ...config,
        tolerance: 3,
      });

      expect(suggestions.x).toBe(null);
      expect(suggestions.y).toBe(null);
    });

    it('should return null when snapping is disabled', () => {
      const point: Point = { x: 23, y: 47 };
      const suggestions = getSnapSuggestions(point, {
        ...config,
        enabled: false,
      });

      expect(suggestions.x).toBe(null);
      expect(suggestions.y).toBe(null);
    });

    it('should use default tolerance', () => {
      const point: Point = { x: 23, y: 47 };
      const suggestions = getSnapSuggestions(point, {
        size: 10,
        enabled: true,
      });

      expect(suggestions.x).toBe(20); // Within 5px (half of 10)
      expect(suggestions.y).toBe(50);
    });
  });
});
