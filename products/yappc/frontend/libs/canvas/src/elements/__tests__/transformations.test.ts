/**
 * @vitest-environment jsdom
 */
import { describe, it, expect } from 'vitest';

import {
  snapValue,
  snapRotation,
  calculateRotationDelta,
  applyRotation,
  updateLayerOrder,
  batchUpdatePositions,
  calculateSnapLines,
  getBoundingBox,
  type BaseElement,
  type Point,
} from './transformations';

describe('transformations', () => {
  describe('snapValue', () => {
    it('should snap to nearest point within tolerance', () => {
      const result = snapValue(102, [100, 200, 300], 5);
      expect(result.snapped).toBe(true);
      expect(result.value).toBe(100);
    });

    it('should not snap if outside tolerance', () => {
      const result = snapValue(110, [100, 200, 300], 5);
      expect(result.snapped).toBe(false);
      expect(result.value).toBe(110);
    });

    it('should handle empty snap points', () => {
      const result = snapValue(150, [], 10);
      expect(result.snapped).toBe(false);
      expect(result.value).toBe(150);
    });

    it('should snap to closest point when multiple within tolerance', () => {
      const result = snapValue(105, [100, 108, 200], 10);
      expect(result.snapped).toBe(true);
      expect(result.value).toBe(100); // Closer than 108
    });
  });

  describe('snapRotation', () => {
    it('should snap to nearest 15° increment', () => {
      expect(snapRotation(17, 15)).toBe(15);
      expect(snapRotation(23, 15)).toBe(30);
      expect(snapRotation(7, 15)).toBe(0);
    });

    it('should handle 45° snapping', () => {
      expect(snapRotation(20, 45)).toBe(0);
      expect(snapRotation(50, 45)).toBe(45);
      expect(snapRotation(70, 45)).toBe(90);
    });

    it('should normalize negative angles', () => {
      expect(snapRotation(-10, 15)).toBe(345); // -10 → 350 → snap to 345
      expect(snapRotation(-370, 15)).toBe(345); // -370 → 350 → snap to 345
    });

    it('should normalize angles >360', () => {
      expect(snapRotation(370, 15)).toBe(15); // 370 → 10 → snap to 15
      expect(snapRotation(730, 15)).toBe(15); // 730 → 10 → snap to 15
    });

    it('should return original angle if snapAngle is 0', () => {
      expect(snapRotation(123.45, 0)).toBe(123.45);
    });

    it('should return original angle if snapAngle is negative', () => {
      expect(snapRotation(123.45, -15)).toBe(123.45);
    });
  });

  describe('calculateRotationDelta', () => {
    const center: Point = { x: 100, y: 100 };

    it('should calculate 90° clockwise rotation', () => {
      const start: Point = { x: 150, y: 100 }; // Right
      const current: Point = { x: 100, y: 150 }; // Bottom
      const delta = calculateRotationDelta(center, start, current);
      expect(Math.round(delta)).toBe(90);
    });

    it('should calculate 90° counter-clockwise rotation', () => {
      const start: Point = { x: 150, y: 100 }; // Right
      const current: Point = { x: 100, y: 50 }; // Top
      const delta = calculateRotationDelta(center, start, current);
      expect(Math.round(delta)).toBe(-90);
    });

    it('should handle initial rotation offset', () => {
      const start: Point = { x: 150, y: 100 };
      const current: Point = { x: 100, y: 150 };
      const delta = calculateRotationDelta(center, start, current, 45);
      expect(Math.round(delta)).toBe(135);
    });

    it('should handle 180° rotation', () => {
      const start: Point = { x: 150, y: 100 };
      const current: Point = { x: 50, y: 100 };
      const delta = calculateRotationDelta(center, start, current);
      expect(Math.abs(Math.round(delta))).toBe(180);
    });
  });

  describe('applyRotation', () => {
    const element: BaseElement = {
      id: 'test-1',
      position: { x: 0, y: 0 },
      data: { rotation: 0 },
      layerIndex: 0,
    };

    it('should apply rotation without snapping', () => {
      const result = applyRotation(element, 37.5, 0);
      expect(result.data.rotation).toBe(37.5);
      expect(result.id).toBe('test-1'); // Preserves other properties
    });

    it('should apply rotation with snapping', () => {
      const result = applyRotation(element, 37.5, 15);
      expect(result.data.rotation).toBe(45); // Snapped to nearest 15°
    });

    it('should preserve element structure', () => {
      const complexElement = {
        ...element,
        data: { ...element.data, width: 100, height: 50, label: 'Test' },
      };
      const result = applyRotation(complexElement, 90);
      expect(result.data.width).toBe(100);
      expect(result.data.height).toBe(50);
      expect(result.data.label).toBe('Test');
      expect(result.data.rotation).toBe(90);
    });
  });

  describe('updateLayerOrder', () => {
    const elements: BaseElement[] = [
      { id: 'a', position: { x: 0, y: 0 }, data: {}, layerIndex: 0 },
      { id: 'b', position: { x: 0, y: 0 }, data: {}, layerIndex: 1 },
      { id: 'c', position: { x: 0, y: 0 }, data: {}, layerIndex: 2 },
    ];

    it('should move element forward one layer', () => {
      const result = updateLayerOrder(elements, 'a', 'forward');
      const updated = result.find((el) => el.id === 'a');
      expect(updated?.layerIndex).toBeGreaterThan(0);
    });

    it('should move element backward one layer', () => {
      const result = updateLayerOrder(elements, 'c', 'backward');
      const updated = result.find((el) => el.id === 'c');
      expect(updated?.layerIndex).toBeLessThan(2);
    });

    it('should move element to front', () => {
      const result = updateLayerOrder(elements, 'a', 'front');
      const updated = result.find((el) => el.id === 'a');
      const maxIndex = Math.max(...result.map((el) => el.layerIndex));
      expect(updated?.layerIndex).toBe(maxIndex);
    });

    it('should move element to back', () => {
      const result = updateLayerOrder(elements, 'c', 'back');
      const updated = result.find((el) => el.id === 'c');
      expect(updated?.layerIndex).toBe(0);
    });

    it('should handle non-existent element', () => {
      const result = updateLayerOrder(elements, 'nonexistent', 'forward');
      expect(result).toEqual(elements);
    });

    it('should not move backward below 0', () => {
      const result = updateLayerOrder(elements, 'a', 'backward');
      const updated = result.find((el) => el.id === 'a');
      expect(updated?.layerIndex).toBeGreaterThanOrEqual(0);
    });
  });

  describe('batchUpdatePositions', () => {
    const elements: BaseElement[] = [
      { id: 'a', position: { x: 0, y: 0 }, data: {}, layerIndex: 0 },
      { id: 'b', position: { x: 100, y: 100 }, data: {}, layerIndex: 1 },
      { id: 'c', position: { x: 200, y: 200 }, data: {}, layerIndex: 2 },
    ];

    it('should update positions for selected elements', () => {
      const result = batchUpdatePositions(elements, ['a', 'c'], {
        dx: 50,
        dy: 25,
      });

      expect(result.find((el) => el.id === 'a')?.position).toEqual({
        x: 50,
        y: 25,
      });
      expect(result.find((el) => el.id === 'b')?.position).toEqual({
        x: 100,
        y: 100,
      }); // Unchanged
      expect(result.find((el) => el.id === 'c')?.position).toEqual({
        x: 250,
        y: 225,
      });
    });

    it('should handle empty selection', () => {
      const result = batchUpdatePositions(elements, [], { dx: 50, dy: 25 });
      expect(result).toEqual(elements);
    });

    it('should handle negative deltas', () => {
      const result = batchUpdatePositions(elements, ['b'], {
        dx: -50,
        dy: -50,
      });
      expect(result.find((el) => el.id === 'b')?.position).toEqual({
        x: 50,
        y: 50,
      });
    });

    it('should preserve element properties', () => {
      const complexElements = elements.map((el) => ({
        ...el,
        data: { ...el.data, label: `Label ${el.id}` },
      }));
      const result = batchUpdatePositions(complexElements, ['a'], {
        dx: 10,
        dy: 10,
      });
      expect(result.find((el) => el.id === 'a')?.data.label).toBe('Label a');
    });
  });

  describe('calculateSnapLines', () => {
    const movingElements: BaseElement[] = [
      {
        id: 'moving-1',
        position: { x: 100, y: 100 },
        data: { width: 100, height: 50 },
        layerIndex: 0,
      },
    ];

    const staticElements: BaseElement[] = [
      {
        id: 'static-1',
        position: { x: 100, y: 200 },
        data: { width: 100, height: 50 },
        layerIndex: 1,
      },
      {
        id: 'static-2',
        position: { x: 300, y: 100 },
        data: { width: 100, height: 50 },
        layerIndex: 2,
      },
    ];

    it('should detect vertical alignment', () => {
      const lines = calculateSnapLines(movingElements, staticElements, 10);
      const verticalLines = lines.filter((l) => l.orientation === 'vertical');
      expect(verticalLines.length).toBeGreaterThan(0);
      expect(verticalLines.some((l) => l.position === 100)).toBe(true);
    });

    it('should detect horizontal alignment', () => {
      const lines = calculateSnapLines(movingElements, staticElements, 10);
      const horizontalLines = lines.filter(
        (l) => l.orientation === 'horizontal'
      );
      expect(horizontalLines.length).toBeGreaterThan(0);
      expect(horizontalLines.some((l) => l.position === 100)).toBe(true);
    });

    it('should respect tolerance threshold', () => {
      const farMoving: BaseElement[] = [
        {
          id: 'far',
          position: { x: 500, y: 500 },
          data: { width: 100, height: 50 },
          layerIndex: 0,
        },
      ];
      const lines = calculateSnapLines(farMoving, staticElements, 10);
      expect(lines.length).toBe(0);
    });

    it('should detect center alignment', () => {
      const centered: BaseElement[] = [
        {
          id: 'centered',
          position: { x: 100, y: 100 },
          data: { width: 100, height: 50 },
          layerIndex: 0,
        },
      ];
      const lines = calculateSnapLines(centered, staticElements, 10);
      const centerLines = lines.filter(
        (l) => l.orientation === 'vertical' && l.position === 150
      );
      expect(centerLines.length).toBeGreaterThan(0);
    });

    it('should handle elements without dimensions', () => {
      const noDimensions: BaseElement[] = [
        {
          id: 'no-dims',
          position: { x: 100, y: 100 },
          data: {},
          layerIndex: 0,
        },
      ];
      const lines = calculateSnapLines(noDimensions, staticElements, 10);
      expect(lines.length).toBeGreaterThanOrEqual(0); // Should not throw
    });
  });

  describe('getBoundingBox', () => {
    it('should calculate bounding box for multiple elements', () => {
      const elements: BaseElement[] = [
        {
          id: 'a',
          position: { x: 0, y: 0 },
          data: { width: 100, height: 50 },
          layerIndex: 0,
        },
        {
          id: 'b',
          position: { x: 200, y: 100 },
          data: { width: 100, height: 50 },
          layerIndex: 1,
        },
      ];

      const bbox = getBoundingBox(elements);
      expect(bbox).toEqual({
        x: 0,
        y: 0,
        width: 300,
        height: 150,
      });
    });

    it('should handle elements without dimensions', () => {
      const elements: BaseElement[] = [
        { id: 'a', position: { x: 10, y: 20 }, data: {}, layerIndex: 0 },
        { id: 'b', position: { x: 30, y: 40 }, data: {}, layerIndex: 1 },
      ];

      const bbox = getBoundingBox(elements);
      expect(bbox).toEqual({
        x: 10,
        y: 20,
        width: 20,
        height: 20,
      });
    });

    it('should return null for empty array', () => {
      const bbox = getBoundingBox([]);
      expect(bbox).toBeNull();
    });

    it('should handle single element', () => {
      const elements: BaseElement[] = [
        {
          id: 'a',
          position: { x: 50, y: 75 },
          data: { width: 200, height: 100 },
          layerIndex: 0,
        },
      ];

      const bbox = getBoundingBox(elements);
      expect(bbox).toEqual({
        x: 50,
        y: 75,
        width: 200,
        height: 100,
      });
    });

    it('should handle negative positions', () => {
      const elements: BaseElement[] = [
        {
          id: 'a',
          position: { x: -100, y: -50 },
          data: { width: 100, height: 50 },
          layerIndex: 0,
        },
        {
          id: 'b',
          position: { x: 50, y: 25 },
          data: { width: 100, height: 50 },
          layerIndex: 1,
        },
      ];

      const bbox = getBoundingBox(elements);
      expect(bbox).toEqual({
        x: -100,
        y: -50,
        width: 250,
        height: 125,
      });
    });
  });
});
