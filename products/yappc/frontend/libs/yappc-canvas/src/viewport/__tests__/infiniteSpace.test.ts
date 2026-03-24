import { describe, it, expect } from 'vitest';

import {
  shouldShiftOrigin,
  computeOriginShiftDelta,
  getViewportBounds,
  screenToWorld,
  worldToScreen,
  isPointVisible,
  isRectVisible,
  getTiledBackgroundOffset,
  clampZoom,
  fitElementsInView,
  zoomAtPoint,
  testCoordinateAccuracy,
  validateOriginShiftConfig,
  type Point,
  type Viewport,
} from './infiniteSpace';

describe('infiniteSpace', () => {
  describe('shouldShiftOrigin', () => {
    it('should return true when translation exceeds threshold', () => {
      expect(shouldShiftOrigin({ x: 1800, y: 0 }, 1000)).toBe(true);
      expect(shouldShiftOrigin({ x: 0, y: -1200 }, 1000)).toBe(true);
      expect(shouldShiftOrigin({ x: 1500, y: 1500 }, 1000)).toBe(true);
    });

    it('should return false when translation is below threshold', () => {
      expect(shouldShiftOrigin({ x: 500, y: 400 }, 1200)).toBe(false);
      expect(shouldShiftOrigin({ x: -999, y: 999 }, 1000)).toBe(false);
    });

    it('should handle edge cases', () => {
      expect(shouldShiftOrigin({ x: 1000, y: 0 }, 1000)).toBe(true); // Exactly at threshold
      expect(shouldShiftOrigin({ x: 999, y: 0 }, 1000)).toBe(false); // Just below
      expect(shouldShiftOrigin({ x: 0, y: 0 }, 1000)).toBe(false); // Zero translation
    });

    it('should guard against invalid threshold', () => {
      expect(shouldShiftOrigin({ x: 2000, y: 0 }, 0)).toBe(false);
      expect(shouldShiftOrigin({ x: 2000, y: 0 }, -100)).toBe(false);
      expect(shouldShiftOrigin({ x: 2000, y: 0 }, NaN)).toBe(false);
      expect(shouldShiftOrigin({ x: 2000, y: 0 }, Infinity)).toBe(false);
    });
  });

  describe('computeOriginShiftDelta', () => {
    it('should compute world delta from screen translation', () => {
      expect(computeOriginShiftDelta({ x: 800, y: -400 }, 2)).toEqual({
        x: 400,
        y: -200,
      });
      expect(computeOriginShiftDelta({ x: 1000, y: 500 }, 1)).toEqual({
        x: 1000,
        y: 500,
      });
      expect(computeOriginShiftDelta({ x: 200, y: 100 }, 0.5)).toEqual({
        x: 400,
        y: 200,
      });
    });

    it('should handle zero and negative scales', () => {
      expect(computeOriginShiftDelta({ x: 1000, y: 500 }, 0)).toEqual({
        x: 0,
        y: 0,
      });
      expect(computeOriginShiftDelta({ x: 1000, y: 500 }, NaN)).toEqual({
        x: 0,
        y: 0,
      });
      expect(computeOriginShiftDelta({ x: 1000, y: 500 }, Infinity)).toEqual({
        x: 0,
        y: 0,
      });
    });

    it('should handle zero translation', () => {
      expect(computeOriginShiftDelta({ x: 0, y: 0 }, 2)).toEqual({
        x: 0,
        y: 0,
      });
    });
  });

  describe('getViewportBounds', () => {
    it('should calculate bounds correctly', () => {
      const viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 1,
        width: 800,
        height: 600,
      };

      const bounds = getViewportBounds(viewport);

      expect(bounds.minX).toBe(-400);
      expect(bounds.maxX).toBe(400);
      expect(bounds.minY).toBe(-300);
      expect(bounds.maxY).toBe(300);
      expect(bounds.width).toBe(800);
      expect(bounds.height).toBe(600);
    });

    it('should account for zoom level', () => {
      const viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 2, // Zoomed in 2x
        width: 800,
        height: 600,
      };

      const bounds = getViewportBounds(viewport);

      // At 2x zoom, visible area is half the size
      expect(bounds.width).toBe(400);
      expect(bounds.height).toBe(300);
    });

    it('should account for viewport center offset', () => {
      const viewport: Viewport = {
        center: { x: 1000, y: 500 },
        zoom: 1,
        width: 800,
        height: 600,
      };

      const bounds = getViewportBounds(viewport);

      expect(bounds.minX).toBe(600); // 1000 - 400
      expect(bounds.maxX).toBe(1400); // 1000 + 400
      expect(bounds.minY).toBe(200); // 500 - 300
      expect(bounds.maxY).toBe(800); // 500 + 300
    });
  });

  describe('coordinate conversion', () => {
    const viewport: Viewport = {
      center: { x: 0, y: 0 },
      zoom: 1,
      width: 800,
      height: 600,
    };

    it('should convert screen to world coordinates', () => {
      // Center of screen
      expect(screenToWorld({ x: 400, y: 300 }, viewport)).toEqual({
        x: 0,
        y: 0,
      });

      // Top-left corner
      expect(screenToWorld({ x: 0, y: 0 }, viewport)).toEqual({
        x: -400,
        y: -300,
      });

      // Bottom-right corner
      expect(screenToWorld({ x: 800, y: 600 }, viewport)).toEqual({
        x: 400,
        y: 300,
      });
    });

    it('should convert world to screen coordinates', () => {
      // World origin
      expect(worldToScreen({ x: 0, y: 0 }, viewport)).toEqual({
        x: 400,
        y: 300,
      });

      // Top-left of viewport
      expect(worldToScreen({ x: -400, y: -300 }, viewport)).toEqual({
        x: 0,
        y: 0,
      });

      // Bottom-right of viewport
      expect(worldToScreen({ x: 400, y: 300 }, viewport)).toEqual({
        x: 800,
        y: 600,
      });
    });

    it('should round-trip accurately', () => {
      const worldPoint = { x: 123.456, y: -789.012 };

      const screenPoint = worldToScreen(worldPoint, viewport);
      const backToWorld = screenToWorld(screenPoint, viewport);

      expect(backToWorld.x).toBeCloseTo(worldPoint.x, 10);
      expect(backToWorld.y).toBeCloseTo(worldPoint.y, 10);
    });

    it('should handle zoomed viewport', () => {
      const zoomedViewport: Viewport = {
        center: { x: 100, y: 50 },
        zoom: 2,
        width: 800,
        height: 600,
      };

      const worldPoint = { x: 100, y: 50 };
      const screenPoint = worldToScreen(worldPoint, zoomedViewport);

      // Center of world should be at center of screen
      expect(screenPoint.x).toBeCloseTo(400, 1);
      expect(screenPoint.y).toBeCloseTo(300, 1);
    });
  });

  describe('isPointVisible', () => {
    const viewport: Viewport = {
      center: { x: 0, y: 0 },
      zoom: 1,
      width: 800,
      height: 600,
    };

    it('should detect visible points', () => {
      expect(isPointVisible({ x: 0, y: 0 }, viewport)).toBe(true);
      expect(isPointVisible({ x: 100, y: 100 }, viewport)).toBe(true);
      expect(isPointVisible({ x: -200, y: -100 }, viewport)).toBe(true);
    });

    it('should detect points outside viewport', () => {
      expect(isPointVisible({ x: 500, y: 0 }, viewport)).toBe(false);
      expect(isPointVisible({ x: 0, y: 400 }, viewport)).toBe(false);
      expect(isPointVisible({ x: -500, y: 0 }, viewport)).toBe(false);
    });

    it('should handle margin parameter', () => {
      const margin = 100;

      // Just outside viewport, but within margin
      expect(isPointVisible({ x: 450, y: 0 }, viewport, margin)).toBe(true);

      // Outside viewport and margin
      expect(isPointVisible({ x: 600, y: 0 }, viewport, margin)).toBe(false);
    });

    it('should handle edge cases', () => {
      // Exactly at viewport edge
      expect(isPointVisible({ x: 400, y: 0 }, viewport)).toBe(true);
      expect(isPointVisible({ x: -400, y: 0 }, viewport)).toBe(true);
    });
  });

  describe('isRectVisible', () => {
    const viewport: Viewport = {
      center: { x: 0, y: 0 },
      zoom: 1,
      width: 800,
      height: 600,
    };

    it('should detect fully visible rectangles', () => {
      expect(
        isRectVisible({ x: -50, y: -50, width: 100, height: 100 }, viewport)
      ).toBe(true);
    });

    it('should detect partially visible rectangles', () => {
      // Rectangle extends beyond viewport
      expect(
        isRectVisible({ x: 300, y: 0, width: 200, height: 100 }, viewport)
      ).toBe(true);

      // Rectangle starts outside but extends into viewport
      expect(
        isRectVisible({ x: -500, y: 0, width: 200, height: 100 }, viewport)
      ).toBe(true);
    });

    it('should detect rectangles completely outside viewport', () => {
      expect(
        isRectVisible({ x: 500, y: 0, width: 100, height: 100 }, viewport)
      ).toBe(false);
      expect(
        isRectVisible({ x: 0, y: 400, width: 100, height: 100 }, viewport)
      ).toBe(false);
      expect(
        isRectVisible({ x: -600, y: 0, width: 100, height: 100 }, viewport)
      ).toBe(false);
    });

    it('should handle margin parameter', () => {
      const margin = 50;

      // Rectangle just outside viewport, but within margin
      expect(
        isRectVisible({ x: 420, y: 0, width: 50, height: 50 }, viewport, margin)
      ).toBe(true);

      // Rectangle outside viewport and margin
      expect(
        isRectVisible({ x: 500, y: 0, width: 50, height: 50 }, viewport, margin)
      ).toBe(false);
    });
  });

  describe('getTiledBackgroundOffset', () => {
    it('should calculate offset for origin-centered viewport', () => {
      const viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 1,
        width: 800,
        height: 600,
      };

      const offset = getTiledBackgroundOffset(viewport, 50);

      // At origin, offset should be minimal
      expect(offset.x).toBeGreaterThanOrEqual(0);
      expect(offset.x).toBeLessThan(50);
      expect(offset.y).toBeGreaterThanOrEqual(0);
      expect(offset.y).toBeLessThan(50);
    });

    it('should calculate offset for panned viewport', () => {
      const viewport: Viewport = {
        center: { x: 123, y: 456 },
        zoom: 1,
        width: 800,
        height: 600,
      };

      const offset = getTiledBackgroundOffset(viewport, 50);

      // Offset should be modulo tile size
      expect(offset.x).toBeGreaterThanOrEqual(0);
      expect(offset.x).toBeLessThan(50);
      expect(offset.y).toBeGreaterThanOrEqual(0);
      expect(offset.y).toBeLessThan(50);
    });

    it('should scale offset with zoom', () => {
      const viewport1: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 1,
        width: 800,
        height: 600,
      };

      const viewport2: Viewport = {
        ...viewport1,
        zoom: 2,
      };

      const offset1 = getTiledBackgroundOffset(viewport1, 50);
      const offset2 = getTiledBackgroundOffset(viewport2, 50);

      // At higher zoom, offset should be scaled proportionally
      expect(offset2.x).toBeCloseTo(offset1.x * 2, 1);
      expect(offset2.y).toBeCloseTo(offset1.y * 2, 1);
    });

    it('should handle different tile sizes', () => {
      const viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 1,
        width: 800,
        height: 600,
      };

      const offset20 = getTiledBackgroundOffset(viewport, 20);
      const offset100 = getTiledBackgroundOffset(viewport, 100);

      expect(offset20.x).toBeLessThan(20);
      expect(offset100.x).toBeLessThan(100);
    });
  });

  describe('clampZoom', () => {
    it('should clamp to default range', () => {
      expect(clampZoom(0.05)).toBe(0.1); // Below min
      expect(clampZoom(10)).toBe(5.0); // Above max
      expect(clampZoom(1.5)).toBe(1.5); // Within range
    });

    it('should use custom min/max', () => {
      expect(clampZoom(0.3, 0.5, 2.0)).toBe(0.5);
      expect(clampZoom(3.0, 0.5, 2.0)).toBe(2.0);
      expect(clampZoom(1.0, 0.5, 2.0)).toBe(1.0);
    });

    it('should handle edge cases', () => {
      expect(clampZoom(0.1)).toBe(0.1); // Exactly at min
      expect(clampZoom(5.0)).toBe(5.0); // Exactly at max
    });

    it('should guard against non-finite values', () => {
      // All non-finite values default to 1.0 as a safe fallback
      expect(clampZoom(NaN)).toBe(1.0);
      expect(clampZoom(Infinity)).toBe(1.0);
      expect(clampZoom(-Infinity)).toBe(1.0);
    });
  });

  describe('fitElementsInView', () => {
    const viewport = { width: 800, height: 600 };

    it('should fit single element', () => {
      const elements = [{ x: 0, y: 0, width: 100, height: 100 }];

      const result = fitElementsInView(elements, viewport, 40);

      expect(result).not.toBeNull();
      expect(result!.center.x).toBe(50); // Center of element
      expect(result!.center.y).toBe(50);
      expect(result!.zoom).toBeGreaterThan(0);
    });

    it('should fit multiple elements', () => {
      const elements = [
        { x: 0, y: 0, width: 100, height: 100 },
        { x: 200, y: 100, width: 100, height: 100 },
        { x: 100, y: 200, width: 100, height: 100 },
      ];

      const result = fitElementsInView(elements, viewport, 40);

      expect(result).not.toBeNull();
      // Center should be at centroid of all elements
      expect(result!.center.x).toBeCloseTo(150, 0);
      expect(result!.center.y).toBeCloseTo(150, 0);
    });

    it('should return null for empty array', () => {
      expect(fitElementsInView([], viewport)).toBeNull();
    });

    it('should handle invalid element bounds', () => {
      const elements = [{ x: Infinity, y: 0, width: 100, height: 100 }];

      expect(fitElementsInView(elements, viewport)).toBeNull();
    });

    it('should apply padding correctly', () => {
      const elements = [{ x: 0, y: 0, width: 800, height: 600 }];

      // With no padding, zoom should be 1.0 (fills viewport exactly)
      const noPadding = fitElementsInView(elements, viewport, 0);
      expect(noPadding!.zoom).toBeCloseTo(1.0, 1);

      // With padding, zoom should be less (content smaller to fit padding)
      const withPadding = fitElementsInView(elements, viewport, 40);
      expect(withPadding!.zoom).toBeLessThan(1.0);
    });

    it('should clamp zoom to safe range', () => {
      // Very small element should not cause excessive zoom
      const tinyElements = [{ x: 0, y: 0, width: 1, height: 1 }];
      const result = fitElementsInView(tinyElements, viewport);

      expect(result!.zoom).toBeLessThanOrEqual(5.0);
    });
  });

  describe('zoomAtPoint', () => {
    it('should zoom while keeping point fixed', () => {
      const viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 1,
        width: 800,
        height: 600,
      };

      // Zoom in at center of screen
      const zoomPoint = { x: 400, y: 300 };
      const result = zoomAtPoint(viewport, 0.1, zoomPoint);

      // Zoom should increase
      expect(result.zoom).toBeGreaterThan(viewport.zoom);

      // Center point should still map to same screen location
      const screenAfter = worldToScreen({ x: 0, y: 0 }, result);
      expect(screenAfter.x).toBeCloseTo(400, 0);
      expect(screenAfter.y).toBeCloseTo(300, 0);
    });

    it('should zoom at off-center point', () => {
      const viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 1,
        width: 800,
        height: 600,
      };

      // Zoom in at top-left corner
      const zoomPoint = { x: 200, y: 150 };
      const worldBefore = screenToWorld(zoomPoint, viewport);

      const result = zoomAtPoint(viewport, 0.2, zoomPoint);

      const worldAfter = screenToWorld(zoomPoint, result);

      // Point should remain fixed in world space
      expect(worldAfter.x).toBeCloseTo(worldBefore.x, 1);
      expect(worldAfter.y).toBeCloseTo(worldBefore.y, 1);
    });

    it('should not zoom beyond limits', () => {
      const viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 4.9, // Near max
        width: 800,
        height: 600,
      };

      const zoomPoint = { x: 400, y: 300 };
      const result = zoomAtPoint(viewport, 1.0, zoomPoint); // Try to zoom way in

      // Should clamp to max zoom
      expect(result.zoom).toBeLessThanOrEqual(5.0);
    });

    it('should handle zoom out', () => {
      const viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 2,
        width: 800,
        height: 600,
      };

      const zoomPoint = { x: 400, y: 300 };
      const result = zoomAtPoint(viewport, -0.5, zoomPoint);

      expect(result.zoom).toBeLessThan(viewport.zoom);
    });
  });

  describe('testCoordinateAccuracy', () => {
    it('should have high accuracy for typical viewports', () => {
      const viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 1,
        width: 800,
        height: 600,
      };

      const point = { x: 123.456, y: 789.012 };
      const error = testCoordinateAccuracy(point, viewport);

      // Error should be negligible (sub-pixel)
      expect(error).toBeLessThan(0.001);
    });

    it('should detect precision loss at extreme scales', () => {
      const viewport: Viewport = {
        center: { x: 1000000, y: 1000000 }, // Very far from origin
        zoom: 0.001, // Very zoomed out
        width: 800,
        height: 600,
      };

      const point = { x: 1000000, y: 1000000 };
      const error = testCoordinateAccuracy(point, viewport);

      // Still should be accurate with double precision
      expect(error).toBeLessThan(1);
    });
  });

  describe('validateOriginShiftConfig', () => {
    it('should use default values', () => {
      const config = validateOriginShiftConfig({});

      expect(config.threshold).toBe(1800);
      expect(config.minThreshold).toBe(400);
      expect(config.maxThreshold).toBe(5000);
    });

    it('should clamp threshold to valid range', () => {
      expect(validateOriginShiftConfig({ threshold: 100 }).threshold).toBe(400);
      expect(validateOriginShiftConfig({ threshold: 10000 }).threshold).toBe(
        5000
      );
    });

    it('should accept valid threshold', () => {
      expect(validateOriginShiftConfig({ threshold: 1500 }).threshold).toBe(
        1500
      );
    });

    it('should respect custom min/max', () => {
      const config = validateOriginShiftConfig({
        threshold: 600,
        minThreshold: 500,
        maxThreshold: 3000,
      });

      expect(config.threshold).toBe(600);
      expect(config.minThreshold).toBe(500);
      expect(config.maxThreshold).toBe(3000);
    });

    it('should clamp to custom range', () => {
      const config = validateOriginShiftConfig({
        threshold: 100,
        minThreshold: 500,
        maxThreshold: 3000,
      });

      expect(config.threshold).toBe(500); // Clamped to custom min
    });
  });
});
