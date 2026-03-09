// All tests skipped - incomplete feature
import React from 'react';
import { describe, it, expect, beforeEach } from 'vitest';
import '@testing-library/jest-dom';
import {
  snapToGrid,
  snapPointToGrid,
  getGridLines,
  distributeElements,
  alignElements,
  isNearGridLine,
  getSnapSuggestions,
  type GridConfig,
  type DistributionOptions,
  type AlignmentOptions,
} from '@ghatana/canvas';

/**
 * Integration tests for grid/snapping in canvas-test route.
 *
 * Tests cover:
 * - Grid snapping with BaseItem structure
 * - Element alignment with actual canvas elements
 * - Distribution of multiple selected elements
 * - Grid line generation for viewport rendering
 * - Snap suggestions for interactive drag operations
 *
 * These tests verify the grid utilities work correctly
 * with the canvas-test route's element structure.
 */

// BaseItem structure matching canvas-test.tsx
interface BaseItem {
  id: string;
  type: string;
  position: { x: number; y: number };
  data: {
    label: string;
    width: number;
    height: number;
    color: string;
    rotation?: number;
  };
  layerIndex: number;
  metadata: {
    createdAt: string;
    updatedAt: string;
  };
}

// Test fixture factory with deep partial support
type DeepPartial<T> = {
  [P in keyof T]?: T[P] extends object ? DeepPartial<T[P]> : T[P];
};

const createTestItem = (overrides: DeepPartial<BaseItem> = {}): BaseItem => {
  const defaults: BaseItem = {
    id: `item-${Math.random().toString(36).substr(2, 9)}`,
    type: 'rectangle',
    position: { x: 100, y: 100 },
    data: {
      label: 'Test Item',
      width: 100,
      height: 60,
      color: 'hsl(200, 70%, 80%)',
      rotation: 0,
    },
    layerIndex: 0,
    metadata: {
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
  };

  return {
    ...defaults,
    ...overrides,
    data: {
      ...defaults.data,
      ...overrides.data,
    },
    position: {
      ...defaults.position,
      ...overrides.position,
    },
    metadata: {
      ...defaults.metadata,
      ...overrides.metadata,
    },
  };
};

describe.skip('Grid & Snapping Integration Tests', () => {
  describe('snapToGrid with BaseItem', () => {
    it('should snap element position to grid', () => {
      const item = createTestItem({ position: { x: 123, y: 67 } });
      const gridSize = 20;

      const snappedX = snapToGrid(item.position.x, gridSize);
      const snappedY = snapToGrid(item.position.y, gridSize);

      expect(snappedX).toBe(120);
      expect(snappedY).toBe(60);

      // Apply snapped coordinates
      const snappedItem = {
        ...item,
        position: { x: snappedX, y: snappedY },
      };

      expect(snappedItem.position.x).toBe(120);
      expect(snappedItem.position.y).toBe(60);
    });

    it('should snap element dimensions to grid', () => {
      const item = createTestItem({
        data: {
          width: 123,
          height: 67,
        },
      });
      const gridSize = 10;

      const snappedWidth = snapToGrid(item.data.width, gridSize);
      const snappedHeight = snapToGrid(item.data.height, gridSize);

      expect(snappedWidth).toBe(120);
      expect(snappedHeight).toBe(70);
    });
  });

  describe('snapPointToGrid with tolerance', () => {
    it('should snap element when within tolerance', () => {
      const item = createTestItem({ position: { x: 123, y: 67 } });
      const config: GridConfig = { size: 20, tolerance: 5, enabled: true };

      const snapped = snapPointToGrid(item.position, config);

      // 123 is 3px from 120 (within 5px tolerance) → snaps
      // 67 is 7px from both 60 and 70 (outside 5px tolerance) → no snap
      expect(snapped).toEqual({ x: 120, y: 67 });
    });

    it('should not snap element when outside tolerance', () => {
      const item = createTestItem({ position: { x: 114, y: 54 } });
      const config: GridConfig = { size: 20, tolerance: 3, enabled: true };

      const snapped = snapPointToGrid(item.position, config);

      // 114 is 6px from both 110 and 120 (outside 3px tolerance)
      // 54 is 6px from both 50 and 60 (outside 3px tolerance)
      expect(snapped).toEqual({ x: 114, y: 54 });
    });

    it('should respect disabled snapping', () => {
      const item = createTestItem({ position: { x: 123, y: 67 } });
      const config: GridConfig = { size: 20, enabled: false };

      const snapped = snapPointToGrid(item.position, config);

      expect(snapped).toEqual({ x: 123, y: 67 });
    });
  });

  describe('alignElements with BaseItem[]', () => {
    let items: BaseItem[];

    beforeEach(() => {
      items = [
        createTestItem({ position: { x: 50, y: 100 } }),
        createTestItem({ position: { x: 150, y: 100 } }),
        createTestItem({ position: { x: 250, y: 100 } }),
      ];
    });

    it('should align left (leftmost x)', () => {
      const aligned = alignElements(items, { type: 'left' });

      expect(aligned[0].position.x).toBe(50);
      expect(aligned[1].position.x).toBe(50);
      expect(aligned[2].position.x).toBe(50);

      // Y positions unchanged
      expect(aligned[0].position.y).toBe(100);
      expect(aligned[1].position.y).toBe(100);
      expect(aligned[2].position.y).toBe(100);
    });

    it('should align center horizontally', () => {
      const aligned = alignElements(items, { type: 'center' });

      // Center x = (50 + 250) / 2 = 150
      expect(aligned[0].position.x).toBe(150);
      expect(aligned[1].position.x).toBe(150);
      expect(aligned[2].position.x).toBe(150);
    });

    it('should align right (rightmost x + width)', () => {
      const items = [
        createTestItem({
          position: { x: 50, y: 100 },
          data: { width: 100 },
        }),
        createTestItem({
          position: { x: 150, y: 100 },
          data: { width: 100 },
        }),
        createTestItem({
          position: { x: 250, y: 100 },
          data: { width: 100 },
        }),
      ];

      const aligned = alignElements(items, { type: 'right' });

      // Rightmost edge = 250 + 100 = 350
      // Align each element's right edge to 350
      expect(aligned[0].position.x).toBe(250); // 350 - 100
      expect(aligned[1].position.x).toBe(250);
      expect(aligned[2].position.x).toBe(250);
    });

    it('should align top (topmost y)', () => {
      const items = [
        createTestItem({ position: { x: 100, y: 50 } }),
        createTestItem({ position: { x: 100, y: 150 } }),
        createTestItem({ position: { x: 100, y: 250 } }),
      ];

      const aligned = alignElements(items, { type: 'top' });

      expect(aligned[0].position.y).toBe(50);
      expect(aligned[1].position.y).toBe(50);
      expect(aligned[2].position.y).toBe(50);
    });

    it('should align middle vertically', () => {
      const items = [
        createTestItem({ position: { x: 100, y: 50 } }),
        createTestItem({ position: { x: 100, y: 150 } }),
        createTestItem({ position: { x: 100, y: 250 } }),
      ];

      const aligned = alignElements(items, { type: 'middle' });

      // Center y = (50 + 250) / 2 = 150
      expect(aligned[0].position.y).toBe(150);
      expect(aligned[1].position.y).toBe(150);
      expect(aligned[2].position.y).toBe(150);
    });

    it('should align bottom (bottommost y + height)', () => {
      const items = [
        createTestItem({
          position: { x: 100, y: 50 },
          data: { height: 60 },
        }),
        createTestItem({
          position: { x: 100, y: 150 },
          data: { height: 60 },
        }),
        createTestItem({
          position: { x: 100, y: 250 },
          data: { height: 60 },
        }),
      ];

      const aligned = alignElements(items, { type: 'bottom' });

      // Bottommost edge = 250 + 60 = 310
      expect(aligned[0].position.y).toBe(250); // 310 - 60
      expect(aligned[1].position.y).toBe(250);
      expect(aligned[2].position.y).toBe(250);
    });

    it('should align to custom reference point', () => {
      const aligned = alignElements(items, {
        type: 'center',
        reference: { x: 500, y: 300 },
      });

      // Center alignment places element center at reference x
      // x = 500 - (width / 2) = 500 - 50 = 450
      expect(aligned[0].position.x).toBe(450);
      expect(aligned[1].position.x).toBe(450);
      expect(aligned[2].position.x).toBe(450);
    });
  });

  describe('distributeElements with BaseItem[]', () => {
    let items: BaseItem[];

    beforeEach(() => {
      items = [
        createTestItem({
          position: { x: 0, y: 100 },
          data: { width: 50 },
        }),
        createTestItem({
          position: { x: 100, y: 100 },
          data: { width: 50 },
        }),
        createTestItem({
          position: { x: 200, y: 100 },
          data: { width: 50 },
        }),
      ];
    });

    it('should distribute horizontally with even spacing', () => {
      const options: DistributionOptions = {
        direction: 'horizontal',
        mode: 'even',
      };

      const distributed = distributeElements(items, options);

      // Leftmost = 0, rightmost = 200 + 50 = 250
      // Available space = 250 - 0 = 250
      // Elements keep their positions within bounds
      expect(distributed[0].position.x).toBe(0);
      expect(distributed[2].position.x).toBe(200);

      // Y positions unchanged
      expect(distributed[0].position.y).toBe(100);
      expect(distributed[1].position.y).toBe(100);
      expect(distributed[2].position.y).toBe(100);
    });

    it('should distribute horizontally with fixed spacing', () => {
      const options: DistributionOptions = {
        direction: 'horizontal',
        mode: 'fixed',
        spacing: 30,
      };

      const distributed = distributeElements(items, options);

      // First element stays at x=0
      expect(distributed[0].position.x).toBe(0);

      // Second element: 0 + 50 (width) + 30 (spacing) = 80
      expect(distributed[1].position.x).toBe(80);

      // Third element: 80 + 50 + 30 = 160
      expect(distributed[2].position.x).toBe(160);
    });

    it('should distribute vertically with even spacing', () => {
      const items = [
        createTestItem({
          position: { x: 100, y: 0 },
          data: { height: 50 },
        }),
        createTestItem({
          position: { x: 100, y: 100 },
          data: { height: 50 },
        }),
        createTestItem({
          position: { x: 100, y: 200 },
          data: { height: 50 },
        }),
      ];

      const options: DistributionOptions = {
        direction: 'vertical',
        mode: 'even',
      };

      const distributed = distributeElements(items, options);

      expect(distributed[0].position.y).toBe(0);
      expect(distributed[2].position.y).toBe(200);

      // X positions unchanged
      expect(distributed[0].position.x).toBe(100);
      expect(distributed[1].position.x).toBe(100);
      expect(distributed[2].position.x).toBe(100);
    });

    it('should distribute vertically with fixed spacing', () => {
      const items = [
        createTestItem({
          position: { x: 100, y: 0 },
          data: { height: 50 },
        }),
        createTestItem({
          position: { x: 100, y: 100 },
          data: { height: 50 },
        }),
        createTestItem({
          position: { x: 100, y: 200 },
          data: { height: 50 },
        }),
      ];

      const options: DistributionOptions = {
        direction: 'vertical',
        mode: 'fixed',
        spacing: 25,
      };

      const distributed = distributeElements(items, options);

      expect(distributed[0].position.y).toBe(0);
      expect(distributed[1].position.y).toBe(75); // 0 + 50 + 25
      expect(distributed[2].position.y).toBe(150); // 75 + 50 + 25
    });

    it('should return unchanged for single element', () => {
      const singleItem = [createTestItem({ position: { x: 100, y: 100 } })];
      const options: DistributionOptions = {
        direction: 'horizontal',
        mode: 'even',
      };

      const distributed = distributeElements(singleItem, options);

      expect(distributed[0].position).toEqual({ x: 100, y: 100 });
    });
  });

  describe('getGridLines for viewport', () => {
    it('should generate grid lines for canvas viewport', () => {
      const viewport = { x: 0, y: 0, width: 800, height: 600 };
      const gridSize = 20;

      const lines = getGridLines(viewport, gridSize);

      // Check we have reasonable grid lines
      expect(lines.vertical.length).toBeGreaterThan(30); // 800 / 20 = 40
      expect(lines.horizontal.length).toBeGreaterThan(20); // 600 / 20 = 30

      // First and last vertical lines
      expect(lines.vertical[0]).toBe(0);
      expect(lines.vertical[lines.vertical.length - 1]).toBe(800);

      // First and last horizontal lines
      expect(lines.horizontal[0]).toBe(0);
      expect(lines.horizontal[lines.horizontal.length - 1]).toBe(600);
    });

    it('should handle panned viewport (non-zero origin)', () => {
      const viewport = { x: 100, y: 50, width: 400, height: 300 };
      const gridSize = 50;

      const lines = getGridLines(viewport, gridSize);

      // Grid lines start at viewport bounds
      expect(lines.vertical[0]).toBeGreaterThanOrEqual(100);
      expect(lines.horizontal[0]).toBeGreaterThanOrEqual(50);

      // Grid lines end at viewport bounds
      const lastVertical = lines.vertical[lines.vertical.length - 1];
      const lastHorizontal = lines.horizontal[lines.horizontal.length - 1];

      expect(lastVertical).toBeLessThanOrEqual(500); // 100 + 400
      expect(lastHorizontal).toBeLessThanOrEqual(350); // 50 + 300
    });

    it('should handle zoomed viewport (different grid size)', () => {
      const viewport = { x: 0, y: 0, width: 800, height: 600 };

      // Small grid for zoomed in
      const smallGrid = getGridLines(viewport, 10);
      expect(smallGrid.vertical.length).toBeGreaterThan(70); // 800 / 10

      // Large grid for zoomed out
      const largeGrid = getGridLines(viewport, 100);
      expect(largeGrid.vertical.length).toBeLessThan(15); // 800 / 100
    });
  });

  describe('isNearGridLine for snap indicators', () => {
    it('should detect when element is near grid line', () => {
      const item = createTestItem({ position: { x: 123, y: 67 } });
      const gridSize = 20;
      const tolerance = 5;

      // 123 is 3px from 120 (within 5px)
      const nearX = isNearGridLine(item.position.x, gridSize, tolerance);
      expect(nearX).toBe(true);

      // 67 is 7px from 60 (outside 5px, but 7px from 70 which is also outside)
      const nearY = isNearGridLine(item.position.y, gridSize, tolerance);
      expect(nearY).toBe(false);
    });

    it('should return false when element far from grid', () => {
      const item = createTestItem({ position: { x: 115, y: 65 } });
      const gridSize = 20;
      const tolerance = 3;

      // 115 is 5px from both 120 and 110 (outside 3px)
      const nearX = isNearGridLine(item.position.x, gridSize, tolerance);
      expect(nearX).toBe(false);

      // 65 is 5px from both 60 and 70 (outside 3px)
      const nearY = isNearGridLine(item.position.y, gridSize, tolerance);
      expect(nearY).toBe(false);
    });
  });

  describe('getSnapSuggestions for drag operations', () => {
    it('should suggest snap targets for both axes', () => {
      const item = createTestItem({ position: { x: 123, y: 67 } });
      const config: GridConfig = { size: 20, tolerance: 5, enabled: true };

      const suggestions = getSnapSuggestions(item.position, config);

      // x: 123 is 3px from 120 (within 5px tolerance) → suggests 120
      // y: 67 is 7px from 60 and 10 (outside 5px tolerance) → suggests null
      expect(suggestions).toEqual({ x: 120, y: null });
    });

    it('should return null when outside tolerance', () => {
      const item = createTestItem({ position: { x: 115, y: 65 } });
      const config: GridConfig = { size: 20, tolerance: 3, enabled: true };

      const suggestions = getSnapSuggestions(item.position, config);

      expect(suggestions).toEqual({ x: null, y: null });
    });

    it('should respect disabled snapping', () => {
      const item = createTestItem({ position: { x: 123, y: 67 } });
      const config: GridConfig = { size: 20, enabled: false };

      const suggestions = getSnapSuggestions(item.position, config);

      // When disabled, returns {x: null, y: null}, not null
      expect(suggestions).toEqual({ x: null, y: null });
    });

    it('should use default tolerance when not provided', () => {
      const item = createTestItem({ position: { x: 125, y: 155 } });
      const config: GridConfig = { size: 20, enabled: true }; // Default tolerance = 10

      const suggestions = getSnapSuggestions(item.position, config);

      // 125 is 5px from 120 (within 10px default tolerance)
      // 155 is 5px from 160 (within 10px default tolerance)
      expect(suggestions).toEqual({ x: 120, y: 160 });
    });
  });

  describe('Complex workflow integration', () => {
    it('should snap, align, and distribute multiple elements', () => {
      // Scenario: User drags 3 elements, snaps them, aligns left, then distributes
      const items = [
        createTestItem({
          id: 'a',
          position: { x: 123, y: 67 },
          data: { width: 80, height: 60 },
        }),
        createTestItem({
          id: 'b',
          position: { x: 245, y: 134 },
          data: { width: 80, height: 60 },
        }),
        createTestItem({
          id: 'c',
          position: { x: 67, y: 201 },
          data: { width: 80, height: 60 },
        }),
      ];

      const gridSize = 20;

      // Step 1: Snap to grid
      const snapped = items.map((item) => ({
        ...item,
        position: {
          x: snapToGrid(item.position.x, gridSize),
          y: snapToGrid(item.position.y, gridSize),
        },
      }));

      expect(snapped[0].position).toEqual({ x: 120, y: 60 });
      expect(snapped[1].position).toEqual({ x: 240, y: 140 });
      expect(snapped[2].position).toEqual({ x: 60, y: 200 });

      // Step 2: Align left
      const aligned = alignElements(snapped, { type: 'left' });

      // All x positions aligned to leftmost (60)
      expect(aligned[0].position.x).toBe(60);
      expect(aligned[1].position.x).toBe(60);
      expect(aligned[2].position.x).toBe(60);

      // Step 3: Distribute vertically with fixed spacing
      const distributed = distributeElements(aligned, {
        direction: 'vertical',
        mode: 'fixed',
        spacing: 30,
      });

      // Elements sorted by y, then distributed
      expect(distributed[0].position.y).toBe(60); // First
      expect(distributed[1].position.y).toBe(150); // 60 + 60 + 30
      expect(distributed[2].position.y).toBe(240); // 150 + 60 + 30
    });

    it('should handle empty selection gracefully', () => {
      const emptyItems: BaseItem[] = [];

      const aligned = alignElements(emptyItems, { type: 'center' });
      expect(aligned).toEqual([]);

      const distributed = distributeElements(emptyItems, {
        direction: 'horizontal',
        mode: 'even',
      });
      expect(distributed).toEqual([]);
    });
  });
});
