/**
 * Feature 1.1: Viewport Management - Viewport Store Unit Tests
 *
 * Tests viewport state management including pan, zoom, fit-to-content,
 * zoom clamping, and state persistence.
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';

import {
  clampZoom,
  fitElementsInView,
  zoomAtPoint,
  screenToWorld,
  worldToScreen,
  getViewportBounds,
  type Point,
  type Viewport,
} from './infiniteSpace';

describe('Feature 1.1: Viewport Store', () => {
  describe('Zoom Clamping', () => {
    it('clamps zoom to minimum value', () => {
      expect(clampZoom(0.05, 0.1, 5.0)).toBe(0.1);
      expect(clampZoom(-1, 0.1, 5.0)).toBe(0.1);
      expect(clampZoom(0, 0.1, 5.0)).toBe(0.1);
    });

    it('clamps zoom to maximum value', () => {
      expect(clampZoom(10, 0.1, 5.0)).toBe(5.0);
      expect(clampZoom(100, 0.1, 5.0)).toBe(5.0);
    });

    it('allows zoom within valid range', () => {
      expect(clampZoom(1.0, 0.1, 5.0)).toBe(1.0);
      expect(clampZoom(2.5, 0.1, 5.0)).toBe(2.5);
      expect(clampZoom(0.5, 0.1, 5.0)).toBe(0.5);
    });

    it('handles edge cases at boundaries', () => {
      expect(clampZoom(0.1, 0.1, 5.0)).toBe(0.1);
      expect(clampZoom(5.0, 0.1, 5.0)).toBe(5.0);
    });

    it('uses default min/max when not provided', () => {
      expect(clampZoom(0.05)).toBe(0.1); // Default min
      expect(clampZoom(10)).toBe(5.0); // Default max
      expect(clampZoom(2.0)).toBe(2.0); // Within default range
    });
  });

  describe('Smooth Zooming (16ms frame budget)', () => {
    it('calculates zoom at point correctly', () => {
      const viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 1.0,
        width: 800,
        height: 600,
      };

      const zoomPoint: Point = { x: 400, y: 300 }; // Screen coordinates
      const zoomDelta = Math.log(2.0); // ln(2.0 / 1.0) to get to 2.0x

      const result = zoomAtPoint(viewport, zoomDelta, zoomPoint);

      expect(result.zoom).toBeCloseTo(2.0, 5);
      expect(result.center).toBeDefined();
      // Center should shift to keep the point fixed
      expect(typeof result.center.x).toBe('number');
      expect(typeof result.center.y).toBe('number');
    });

    it('preserves cursor position during zoom', () => {
      const viewport: Viewport = {
        center: { x: 100, y: 100 },
        zoom: 1.0,
        width: 1000,
        height: 800,
      };

      const cursorScreen: Point = { x: 600, y: 400 }; // Right of center
      const worldPointBefore = screenToWorld(cursorScreen, viewport);

      const zoomDelta = Math.log(2.0); // Zoom to 2.0x
      const newViewport = zoomAtPoint(viewport, zoomDelta, cursorScreen);
      const worldPointAfter = screenToWorld(cursorScreen, {
        ...viewport,
        center: newViewport.center,
        zoom: newViewport.zoom,
      });

      // Cursor should point to same world coordinates
      expect(Math.abs(worldPointBefore.x - worldPointAfter.x)).toBeLessThan(0.1);
      expect(Math.abs(worldPointBefore.y - worldPointAfter.y)).toBeLessThan(0.1);
    });

    it('handles multiple zoom steps smoothly', () => {
      let viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 1.0,
        width: 800,
        height: 600,
      };

      const zoomPoint: Point = { x: 400, y: 300 };

      // Simulate 5 zoom steps (typical mouse wheel)
      const zoomSteps = [1.1, 1.2, 1.3, 1.4, 1.5];

      zoomSteps.forEach((targetZoom) => {
        const zoomDelta = Math.log(targetZoom / viewport.zoom);
        const result = zoomAtPoint(viewport, zoomDelta, zoomPoint);
        viewport = {
          ...viewport,
          center: result.center,
          zoom: result.zoom,
        };

        expect(result.zoom).toBeCloseTo(targetZoom, 5);
        expect(isFinite(result.center.x)).toBe(true);
        expect(isFinite(result.center.y)).toBe(true);
      });
    });

    it('respects zoom clamps during zoom-at-point', () => {
      const viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 1.0,
        width: 800,
        height: 600,
      };

      const zoomPoint: Point = { x: 400, y: 300 };

      // Try to zoom beyond max (assuming MAX_ZOOM = 5.0)
      const zoomDeltaMax = Math.log(10.0 / viewport.zoom); // Try to zoom to 10x
      const resultMax = zoomAtPoint(viewport, zoomDeltaMax, zoomPoint);
      expect(resultMax.zoom).toBeLessThanOrEqual(5.0); // Should be clamped

      // Try to zoom below min (assuming MIN_ZOOM = 0.1)
      const zoomDeltaMin = Math.log(0.01 / viewport.zoom); // Try to zoom to 0.01x
      const resultMin = zoomAtPoint(viewport, zoomDeltaMin, zoomPoint);
      expect(resultMin.zoom).toBeGreaterThanOrEqual(0.1); // Should be clamped
    });
  });

  describe('Fit View (200ms performance)', () => {
    it('fits single element in viewport', () => {
      const elements = [{ x: 100, y: 100, width: 200, height: 150 }];
      const viewport = { width: 1000, height: 800 };
      const padding = 40;

      const result = fitElementsInView(elements, viewport, padding);

      expect(result).not.toBeNull();
      expect(result!.center.x).toBe(200); // x + width/2
      expect(result!.center.y).toBe(175); // y + height/2
      expect(result!.zoom).toBeGreaterThan(0);
      expect(result!.zoom).toBeLessThanOrEqual(5.0);
    });

    it('fits multiple elements in viewport', () => {
      const elements = [
        { x: 0, y: 0, width: 100, height: 100 },
        { x: 500, y: 500, width: 100, height: 100 },
        { x: 1000, y: 1000, width: 100, height: 100 },
      ];
      const viewport = { width: 1000, height: 800 };

      const result = fitElementsInView(elements, viewport);

      expect(result).not.toBeNull();
      // Center should be at middle of bounding box
      expect(result!.center.x).toBe(550); // (0 + 1100) / 2
      expect(result!.center.y).toBe(550); // (0 + 1100) / 2
      expect(result!.zoom).toBeGreaterThan(0);
    });

    it('handles empty element array', () => {
      const elements: Array<{ x: number; y: number; width: number; height: number }> = [];
      const viewport = { width: 1000, height: 800 };

      const result = fitElementsInView(elements, viewport);

      expect(result).toBeNull();
    });

    it('respects padding parameter', () => {
      const elements = [{ x: 0, y: 0, width: 500, height: 400 }]; // Larger element
      const viewport = { width: 1000, height: 800 };

      const resultSmallPadding = fitElementsInView(elements, viewport, 20);
      const resultLargePadding = fitElementsInView(elements, viewport, 100);

      expect(resultSmallPadding).not.toBeNull();
      expect(resultLargePadding).not.toBeNull();

      // Larger padding should result in smaller zoom (or equal if both hit clamps)
      expect(resultLargePadding!.zoom).toBeLessThanOrEqual(
        resultSmallPadding!.zoom
      );
    });

    it('handles very small elements', () => {
      const elements = [{ x: 500, y: 500, width: 1, height: 1 }];
      const viewport = { width: 1000, height: 800 };

      const result = fitElementsInView(elements, viewport);

      expect(result).not.toBeNull();
      expect(result!.center.x).toBe(500.5);
      expect(result!.center.y).toBe(500.5);
      // Should not zoom in excessively
      expect(result!.zoom).toBeLessThanOrEqual(5.0);
    });

    it('handles very large elements', () => {
      const elements = [{ x: 0, y: 0, width: 10000, height: 10000 }];
      const viewport = { width: 1000, height: 800 };

      const result = fitElementsInView(elements, viewport);

      expect(result).not.toBeNull();
      expect(result!.center.x).toBe(5000);
      expect(result!.center.y).toBe(5000);
      // Should zoom out to fit (clamped to min zoom of 0.1)
      expect(result!.zoom).toBe(0.1); // Will be clamped to MIN_ZOOM
    });

    it('calculates correct zoom for aspect ratio mismatch', () => {
      const elements = [{ x: 0, y: 0, width: 1000, height: 100 }]; // Wide
      const viewport = { width: 800, height: 800 }; // Square

      const result = fitElementsInView(elements, viewport);

      expect(result).not.toBeNull();
      // Should be constrained by width
      expect(result!.zoom).toBeLessThan(1.0);
    });
  });

  describe('Coordinate Transformations', () => {
    it('converts screen to world coordinates', () => {
      const viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 1.0,
        width: 800,
        height: 600,
      };

      const screenPoint: Point = { x: 400, y: 300 }; // Center of screen
      const worldPoint = screenToWorld(screenPoint, viewport);

      expect(worldPoint.x).toBeCloseTo(0, 1);
      expect(worldPoint.y).toBeCloseTo(0, 1);
    });

    it('converts world to screen coordinates', () => {
      const viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 1.0,
        width: 800,
        height: 600,
      };

      const worldPoint: Point = { x: 0, y: 0 };
      const screenPoint = worldToScreen(worldPoint, viewport);

      expect(screenPoint.x).toBeCloseTo(400, 1);
      expect(screenPoint.y).toBeCloseTo(300, 1);
    });

    it('roundtrip conversion preserves coordinates', () => {
      const viewport: Viewport = {
        center: { x: 100, y: 200 },
        zoom: 2.0,
        width: 1000,
        height: 800,
      };

      const original: Point = { x: 500, y: 300 };
      const screen = worldToScreen(original, viewport);
      const world = screenToWorld(screen, viewport);

      expect(world.x).toBeCloseTo(original.x, 1);
      expect(world.y).toBeCloseTo(original.y, 1);
    });

    it('handles zoomed viewport correctly', () => {
      const viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 2.0,
        width: 800,
        height: 600,
      };

      const screenPoint: Point = { x: 500, y: 300 }; // Right of center
      const worldPoint = screenToWorld(screenPoint, viewport);

      // At 2x zoom, screen offset of 100 = world offset of 50
      expect(worldPoint.x).toBeCloseTo(50, 1);
      expect(worldPoint.y).toBeCloseTo(0, 1);
    });

    it('handles translated viewport correctly', () => {
      const viewport: Viewport = {
        center: { x: 200, y: 150 },
        zoom: 1.0,
        width: 800,
        height: 600,
      };

      const screenPoint: Point = { x: 400, y: 300 }; // Screen center
      const worldPoint = screenToWorld(screenPoint, viewport);

      // Should map to viewport center in world
      expect(worldPoint.x).toBeCloseTo(200, 1);
      expect(worldPoint.y).toBeCloseTo(150, 1);
    });
  });

  describe('Viewport Bounds', () => {
    it('calculates viewport bounds correctly', () => {
      const viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 1.0,
        width: 800,
        height: 600,
      };

      const bounds = getViewportBounds(viewport);

      expect(bounds.minX).toBe(-400);
      expect(bounds.maxX).toBe(400);
      expect(bounds.minY).toBe(-300);
      expect(bounds.maxY).toBe(300);
    });

    it('adjusts bounds based on zoom', () => {
      const viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 2.0,
        width: 800,
        height: 600,
      };

      const bounds = getViewportBounds(viewport);

      // At 2x zoom, visible area is half the size
      expect(bounds.minX).toBe(-200);
      expect(bounds.maxX).toBe(200);
      expect(bounds.minY).toBe(-150);
      expect(bounds.maxY).toBe(150);
    });

    it('adjusts bounds based on center position', () => {
      const viewport: Viewport = {
        center: { x: 500, y: 300 },
        zoom: 1.0,
        width: 800,
        height: 600,
      };

      const bounds = getViewportBounds(viewport);

      expect(bounds.minX).toBe(100); // 500 - 400
      expect(bounds.maxX).toBe(900); // 500 + 400
      expect(bounds.minY).toBe(0); // 300 - 300
      expect(bounds.maxY).toBe(600); // 300 + 300
    });
  });

  describe('State Persistence', () => {
    beforeEach(() => {
      // Mock localStorage
      const localStorageMock = (() => {
        let store: Record<string, string> = {};
        return {
          getItem: (key: string) => store[key] || null,
          setItem: (key: string, value: string) => {
            store[key] = value;
          },
          removeItem: (key: string) => {
            delete store[key];
          },
          clear: () => {
            store = {};
          },
        };
      })();

      Object.defineProperty(window, 'localStorage', {
        value: localStorageMock,
        writable: true,
      });
    });

    it('persists viewport state to localStorage', () => {
      const viewport = {
        center: { x: 100, y: 200 },
        zoom: 1.5,
        width: 1000,
        height: 800,
      };

      const key = 'test-viewport';
      localStorage.setItem(key, JSON.stringify(viewport));

      const stored = localStorage.getItem(key);
      expect(stored).not.toBeNull();

      const parsed = JSON.parse(stored!);
      expect(parsed.center.x).toBe(100);
      expect(parsed.center.y).toBe(200);
      expect(parsed.zoom).toBe(1.5);
    });

    it('restores viewport state from localStorage', () => {
      const savedState = {
        center: { x: 250, y: 350 },
        zoom: 2.0,
        width: 1200,
        height: 900,
      };

      const key = 'test-viewport';
      localStorage.setItem(key, JSON.stringify(savedState));

      const restored = JSON.parse(localStorage.getItem(key)!);

      expect(restored.center.x).toBe(250);
      expect(restored.center.y).toBe(350);
      expect(restored.zoom).toBe(2.0);
    });

    it('handles missing localStorage gracefully', () => {
      const key = 'non-existent-viewport';
      const stored = localStorage.getItem(key);

      expect(stored).toBeNull();
    });

    it('handles corrupted localStorage data', () => {
      const key = 'corrupted-viewport';
      localStorage.setItem(key, 'invalid-json{]');

      expect(() => {
        JSON.parse(localStorage.getItem(key)!);
      }).toThrow();
    });

    it('clamps restored zoom values', () => {
      const savedState = {
        center: { x: 0, y: 0 },
        zoom: 100.0, // Invalid zoom
        width: 1000,
        height: 800,
      };

      const key = 'test-viewport';
      localStorage.setItem(key, JSON.stringify(savedState));

      const restored = JSON.parse(localStorage.getItem(key)!);
      const clampedZoom = clampZoom(restored.zoom, 0.1, 5.0);

      expect(clampedZoom).toBe(5.0);
    });
  });

  describe('Performance Constraints', () => {
    it('zoom calculations complete quickly (<16ms)', () => {
      const viewport: Viewport = {
        center: { x: 0, y: 0 },
        zoom: 1.0,
        width: 1920,
        height: 1080,
      };

      const iterations = 1000;
      const start = performance.now();

      for (let i = 0; i < iterations; i++) {
        const targetZoom = 1.0 + (i % 10) * 0.1;
        const zoomDelta = Math.log(targetZoom / viewport.zoom);
        zoomAtPoint(viewport, zoomDelta, { x: 960, y: 540 });
      }

      const duration = performance.now() - start;
      const avgTime = duration / iterations;

      // Should average well below 16ms per operation
      expect(avgTime).toBeLessThan(1);
    });

    it('fit view calculations complete quickly (<200ms)', () => {
      const elements = Array.from({ length: 1000 }, (_, i) => ({
        x: (i % 100) * 100,
        y: Math.floor(i / 100) * 100,
        width: 80,
        height: 80,
      }));

      const viewport = { width: 1920, height: 1080 };

      const start = performance.now();
      const result = fitElementsInView(elements, viewport);
      const duration = performance.now() - start;

      expect(result).not.toBeNull();
      expect(duration).toBeLessThan(200);
    });

    it('coordinate transformations are fast', () => {
      const viewport: Viewport = {
        center: { x: 500, y: 500 },
        zoom: 1.5,
        width: 1920,
        height: 1080,
      };

      const iterations = 10000;
      const start = performance.now();

      for (let i = 0; i < iterations; i++) {
        const screen: Point = { x: i % 1920, y: (i / 1920) % 1080 };
        const world = screenToWorld(screen, viewport);
        worldToScreen(world, viewport);
      }

      const duration = performance.now() - start;
      const avgTime = duration / iterations;

      // Should be very fast (<0.01ms per roundtrip)
      expect(avgTime).toBeLessThan(0.1);
    });
  });
});
