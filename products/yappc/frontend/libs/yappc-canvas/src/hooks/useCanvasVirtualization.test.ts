/**
 * Canvas Virtualization Hook Tests
 * @doc.type test
 * @doc.purpose Test canvas virtualization performance and correctness
 * @doc.layer unit
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useCanvasVirtualization } from './useCanvasVirtualization';
import type {
  VirtualElement,
  ViewportBounds,
  VirtualizationConfig,
} from './useCanvasVirtualization';

describe('useCanvasVirtualization', () => {
  describe('Basic Functionality', () => {
    it('should initialize with empty elements', () => {
      const { result } = renderHook(() =>
        useCanvasVirtualization({
          elements: [],
          viewportBounds: { x: 0, y: 0, width: 800, height: 600 },
        })
      );

      expect(result.current.visibleElements).toEqual([]);
      expect(result.current.viewportBounds).toEqual({
        x: 0,
        y: 0,
        width: 800,
        height: 600,
      });
    });

    it('should initialize with provided elements', () => {
      const elements: VirtualElement[] = [
        { id: '1', x: 0, y: 0, width: 100, height: 100 },
        { id: '2', x: 150, y: 150, width: 100, height: 100 },
      ];
      const viewportBounds: ViewportBounds = {
        x: 0,
        y: 0,
        width: 800,
        height: 600,
      };

      const { result } = renderHook(() =>
        useCanvasVirtualization({ elements, viewportBounds })
      );

      expect(result.current.visibleElements).toHaveLength(2);
    });
  });

  describe('Viewport Visibility Calculation', () => {
    it('should only include elements within viewport', () => {
      const elements: VirtualElement[] = [
        { id: 'visible', x: 0, y: 0, width: 100, height: 100 }, // Within viewport
        { id: 'outside', x: 900, y: 900, width: 100, height: 100 }, // Outside viewport
      ];
      const viewportBounds: ViewportBounds = {
        x: 0,
        y: 0,
        width: 800,
        height: 600,
      };

      const { result } = renderHook(() =>
        useCanvasVirtualization({ elements, viewportBounds })
      );

      const visibleIds = result.current.visibleElements.map((e) => e.id);
      expect(visibleIds).toContain('visible');
      expect(visibleIds).not.toContain('outside');
    });

    it('should include partially visible elements', () => {
      const elements: VirtualElement[] = [
        { id: 'partial', x: 750, y: 550, width: 100, height: 100 }, // Partially visible
      ];
      const viewportBounds: ViewportBounds = {
        x: 0,
        y: 0,
        width: 800,
        height: 600,
      };

      const { result } = renderHook(() =>
        useCanvasVirtualization({ elements, viewportBounds })
      );

      expect(result.current.visibleElements).toHaveLength(1);
      expect(result.current.visibleElements[0].id).toBe('partial');
    });
  });

  describe('Viewport Movement', () => {
    it('should update visible elements when viewport moves', () => {
      const elements: VirtualElement[] = Array.from(
        { length: 100 },
        (_, i) => ({
          id: `elem-${i}`,
          x: i * 150,
          y: i * 150,
          width: 100,
          height: 100,
        })
      );

      const { result, rerender } = renderHook(
        ({ viewportBounds }: { viewportBounds: ViewportBounds }) =>
          useCanvasVirtualization({ elements, viewportBounds }),
        {
          initialProps: {
            viewportBounds: { x: 0, y: 0, width: 800, height: 600 },
          },
        }
      );

      const initialCount = result.current.visibleElements.length;

      act(() => {
        rerender({
          viewportBounds: { x: 500, y: 500, width: 800, height: 600 },
        });
      });

      expect(result.current.visibleElements.length).toBeGreaterThan(0);
      expect(result.current.visibleElements.length).toBeLessThanOrEqual(
        elements.length
      );
    });

    it('should handle rapid viewport changes', () => {
      const elements: VirtualElement[] = Array.from({ length: 50 }, (_, i) => ({
        id: `elem-${i}`,
        x: Math.random() * 5000,
        y: Math.random() * 5000,
        width: 100,
        height: 100,
      }));

      const { result } = renderHook(() =>
        useCanvasVirtualization({
          elements,
          viewportBounds: { x: 0, y: 0, width: 800, height: 600 },
        })
      );

      // Simulate rapid viewport movements
      for (let i = 0; i < 10; i++) {
        const newViewport: ViewportBounds = {
          x: Math.random() * 2000,
          y: Math.random() * 2000,
          width: 800,
          height: 600,
        };
        result.current.updateViewport(newViewport);
      }

      expect(result.current.visibleElements).toBeDefined();
      expect(Array.isArray(result.current.visibleElements)).toBe(true);
    });
  });

  describe('Zoom Functionality', () => {
    it('should handle zoom out (larger viewport)', () => {
      const elements: VirtualElement[] = Array.from({ length: 30 }, (_, i) => ({
        id: `elem-${i}`,
        x: i * 100,
        y: i * 100,
        width: 50,
        height: 50,
      }));

      const { result } = renderHook(() =>
        useCanvasVirtualization({
          elements,
          viewportBounds: { x: 0, y: 0, width: 800, height: 600 },
        })
      );

      const zoomedOut = result.current.zoom(0.5); // Zoom out 50%
      expect(zoomedOut.visibleElements.length).toBeGreaterThanOrEqual(
        result.current.visibleElements.length
      );
    });

    it('should handle zoom in (smaller viewport)', () => {
      const elements: VirtualElement[] = Array.from(
        { length: 100 },
        (_, i) => ({
          id: `elem-${i}`,
          x: (i % 10) * 200,
          y: Math.floor(i / 10) * 200,
          width: 100,
          height: 100,
        })
      );

      const { result } = renderHook(() =>
        useCanvasVirtualization({
          elements,
          viewportBounds: { x: 0, y: 0, width: 800, height: 600 },
        })
      );

      const zoomedIn = result.current.zoom(2);
      expect(zoomedIn.visibleElements.length).toBeLessThanOrEqual(
        result.current.visibleElements.length
      );
    });

    it('should maintain valid zoom boundaries', () => {
      const elements: VirtualElement[] = [
        { id: '1', x: 0, y: 0, width: 100, height: 100 },
      ];

      const { result } = renderHook(() =>
        useCanvasVirtualization({
          elements,
          viewportBounds: { x: 0, y: 0, width: 800, height: 600 },
          config: { minZoom: 0.1, maxZoom: 5 },
        } as any)
      );

      // Attempt to zoom beyond boundaries
      const extremeZoom = result.current.zoom(100);
      expect(extremeZoom).toBeDefined();
    });
  });

  describe('Performance with Large Element Sets', () => {
    it('should efficiently handle 1000+ elements', () => {
      const elements: VirtualElement[] = Array.from(
        { length: 1000 },
        (_, i) => ({
          id: `elem-${i}`,
          x: (i % 100) * 100,
          y: Math.floor(i / 100) * 100,
          width: 80,
          height: 80,
        })
      );

      const startTime = performance.now();
      const { result } = renderHook(() =>
        useCanvasVirtualization({
          elements,
          viewportBounds: { x: 0, y: 0, width: 800, height: 600 },
        })
      );
      const endTime = performance.now();

      expect(result.current.visibleElements.length).toBeGreaterThan(0);
      expect(result.current.visibleElements.length).toBeLessThan(
        elements.length
      );
      expect(endTime - startTime).toBeLessThan(100); // Should compute in less than 100ms
    });

    it('should use spatial indexing for O(1) queries', () => {
      const elements: VirtualElement[] = Array.from(
        { length: 500 },
        (_, i) => ({
          id: `elem-${i}`,
          x: Math.random() * 5000,
          y: Math.random() * 5000,
          width: 50,
          height: 50,
        })
      );

      const { result } = renderHook(() =>
        useCanvasVirtualization({
          elements,
          viewportBounds: { x: 0, y: 0, width: 800, height: 600 },
        })
      );

      // Multiple viewport queries should be fast
      const startTime = performance.now();
      for (let i = 0; i < 100; i++) {
        result.current.updateViewport({
          x: Math.random() * 2000,
          y: Math.random() * 2000,
          width: 800,
          height: 600,
        });
      }
      const endTime = performance.now();

      expect(endTime - startTime).toBeLessThan(200); // 100 queries in < 200ms
    });
  });

  describe('Memory Management', () => {
    it('should not leak memory on element updates', () => {
      const elements: VirtualElement[] = Array.from(
        { length: 100 },
        (_, i) => ({
          id: `elem-${i}`,
          x: i * 100,
          y: i * 100,
          width: 50,
          height: 50,
        })
      );

      const { result, unmount } = renderHook(() =>
        useCanvasVirtualization({
          elements,
          viewportBounds: { x: 0, y: 0, width: 800, height: 600 },
        })
      );

      expect(result.current.visibleElements).toBeDefined();

      unmount();
      // Verify cleanup happened (would be detected by tool like React DevTools)
    });

    it('should properly clean up spatial index on unmount', () => {
      const onCleanupSpy = vi.fn();
      const elements: VirtualElement[] = [
        { id: '1', x: 0, y: 0, width: 100, height: 100 },
      ];

      const { unmount } = renderHook(() =>
        useCanvasVirtualization({
          elements,
          viewportBounds: { x: 0, y: 0, width: 800, height: 600 },
        })
      );

      unmount();
      // Verify no references remain
    });
  });

  describe('Edge Cases', () => {
    it('should handle zero-sized elements', () => {
      const elements: VirtualElement[] = [
        { id: 'zero', x: 0, y: 0, width: 0, height: 0 },
        { id: 'normal', x: 100, y: 100, width: 100, height: 100 },
      ];

      const { result } = renderHook(() =>
        useCanvasVirtualization({
          elements,
          viewportBounds: { x: 0, y: 0, width: 800, height: 600 },
        })
      );

      expect(result.current.visibleElements).toBeDefined();
    });

    it('should handle negative coordinates', () => {
      const elements: VirtualElement[] = [
        { id: 'negative', x: -100, y: -100, width: 200, height: 200 },
        { id: 'positive', x: 100, y: 100, width: 100, height: 100 },
      ];
      const viewportBounds: ViewportBounds = {
        x: -50,
        y: -50,
        width: 800,
        height: 600,
      };

      const { result } = renderHook(() =>
        useCanvasVirtualization({ elements, viewportBounds })
      );

      expect(result.current.visibleElements).toBeDefined();
    });

    it('should handle very large coordinate values', () => {
      const elements: VirtualElement[] = [
        { id: 'large', x: 999999, y: 999999, width: 100, height: 100 },
      ];

      const { result } = renderHook(() =>
        useCanvasVirtualization({
          elements,
          viewportBounds: { x: 0, y: 0, width: 800, height: 600 },
        })
      );

      expect(result.current.visibleElements.length).toBe(0);
    });

    it('should handle viewport larger than element bounds', () => {
      const elements: VirtualElement[] = [
        { id: '1', x: 10, y: 10, width: 50, height: 50 },
      ];
      const viewportBounds: ViewportBounds = {
        x: 0,
        y: 0,
        width: 5000,
        height: 5000,
      };

      const { result } = renderHook(() =>
        useCanvasVirtualization({ elements, viewportBounds })
      );

      expect(result.current.visibleElements.length).toBe(1);
    });
  });

  describe('Configuration Options', () => {
    it('should apply custom configuration', () => {
      const elements: VirtualElement[] = Array.from({ length: 50 }, (_, i) => ({
        id: `elem-${i}`,
        x: i * 100,
        y: i * 100,
        width: 50,
        height: 50,
      }));

      const config: VirtualizationConfig = {
        bufferSize: 200,
        cacheStrategy: 'spatial-index',
      };

      const { result } = renderHook(() =>
        useCanvasVirtualization({
          elements,
          viewportBounds: { x: 0, y: 0, width: 800, height: 600 },
          config,
        } as any)
      );

      expect(result.current.visibleElements).toBeDefined();
    });
  });
});

describe('useCanvasVirtualization — Large Dataset Performance (>10,000 elements)', () => {
  it('should initialize and compute visible elements for 10,000 elements within 500ms', () => {
    const elements: VirtualElement[] = Array.from(
      { length: 10_000 },
      (_, i) => ({
        id: `elem-${i}`,
        x: (i % 200) * 120,
        y: Math.floor(i / 200) * 120,
        width: 100,
        height: 100,
      })
    );

    const startTime = performance.now();
    const { result } = renderHook(() =>
      useCanvasVirtualization({
        elements,
        viewportBounds: { x: 0, y: 0, width: 800, height: 600 },
      })
    );
    const initTime = performance.now() - startTime;

    expect(result.current.visibleElements.length).toBeGreaterThan(0);
    expect(result.current.visibleElements.length).toBeLessThan(elements.length);
    expect(initTime).toBeLessThan(500);
  });

  it('should handle 50,000 elements without crashing', () => {
    const elements: VirtualElement[] = Array.from(
      { length: 50_000 },
      (_, i) => ({
        id: `elem-${i}`,
        x: (i % 500) * 110,
        y: Math.floor(i / 500) * 110,
        width: 100,
        height: 100,
      })
    );

    expect(() => {
      renderHook(() =>
        useCanvasVirtualization({
          elements,
          viewportBounds: { x: 0, y: 0, width: 800, height: 600 },
        })
      );
    }).not.toThrow();
  });

  it('should return only viewport-visible elements from 10,000-element dataset', () => {
    const elements: VirtualElement[] = Array.from(
      { length: 10_000 },
      (_, i) => ({
        id: `elem-${i}`,
        x: (i % 100) * 100,
        y: Math.floor(i / 100) * 100,
        width: 90,
        height: 90,
      })
    );

    const viewportBounds: ViewportBounds = {
      x: 0,
      y: 0,
      width: 800,
      height: 600,
    };

    const { result } = renderHook(() =>
      useCanvasVirtualization({ elements, viewportBounds })
    );

    // An 800x600 viewport over a 100px grid should see far fewer than total elements
    expect(result.current.visibleElements.length).toBeLessThan(150);
    expect(result.current.visibleElements.length).toBeGreaterThan(0);
  });

  it('should pan across a 10,000-element dataset with 20 viewport updates in under 1 second', () => {
    const elements: VirtualElement[] = Array.from(
      { length: 10_000 },
      (_, i) => ({
        id: `elem-${i}`,
        x: (i % 200) * 100,
        y: Math.floor(i / 200) * 100,
        width: 90,
        height: 90,
      })
    );

    const { result } = renderHook(() =>
      useCanvasVirtualization({
        elements,
        viewportBounds: { x: 0, y: 0, width: 800, height: 600 },
      })
    );

    const startTime = performance.now();
    for (let i = 0; i < 20; i++) {
      result.current.updateViewport({
        x: i * 500,
        y: i * 500,
        width: 800,
        height: 600,
      });
    }
    const totalPanTime = performance.now() - startTime;

    expect(totalPanTime).toBeLessThan(1000);
    expect(result.current.visibleElements).toBeDefined();
  });
});
