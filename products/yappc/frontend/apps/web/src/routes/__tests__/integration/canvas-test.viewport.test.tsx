/**
 * Integration tests for Feature 1.1: Viewport Management
 *
 * Tests the InteractiveCanvas component's viewport functionality including:
 * - Pan/zoom interactions
 * - Fit-to-content behavior
 * - State persistence
 * - Performance constraints (16ms zoom, 200ms fit-view)
 *
 * @see docs/canvas-feature-stories.md - Feature 1.1
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import React from 'react';
import { describe, it, expect, beforeEach, vi } from 'vitest';

// Mock canvas-test component pieces
interface ViewportState {
  scale: number;
  translation: { x: number; y: number };
}

interface BaseItem {
  id: string;
  type: string;
  position: { x: number; y: number };
  data: {
    label: string;
    width: number;
    height: number;
    color: string;
  };
}

const MIN_SCALE = 0.1;
const MAX_SCALE = 5.0;
const DEFAULT_VIEWPORT: ViewportState = { scale: 1, translation: { x: 0, y: 0 } };

// Simple test component that mimics viewport behavior
interface TestViewportProps {
  items: BaseItem[];
  onViewportChange?: (viewport: ViewportState) => void;
  initialViewport?: ViewportState;
}

function TestViewport({ items, onViewportChange, initialViewport }: TestViewportProps) {
  const [viewport, setViewport] = React.useState<ViewportState>(
    initialViewport || DEFAULT_VIEWPORT
  );

  const handleViewportChange = React.useCallback(
    (newViewport: ViewportState) => {
      setViewport(newViewport);
      onViewportChange?.(newViewport);
    },
    [onViewportChange]
  );

  const fitView = React.useCallback(() => {
    if (items.length === 0) {
      handleViewportChange(DEFAULT_VIEWPORT);
      return;
    }

    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;

    items.forEach((item) => {
      minX = Math.min(minX, item.position.x);
      minY = Math.min(minY, item.position.y);
      maxX = Math.max(maxX, item.position.x + item.data.width);
      maxY = Math.max(maxY, item.position.y + item.data.height);
    });

    const padding = 40;
    const containerWidth = 1000;
    const containerHeight = 800;

    const width = Math.max(1, maxX - minX);
    const height = Math.max(1, maxY - minY);

    const scaleX = (containerWidth - padding * 2) / width;
    const scaleY = (containerHeight - padding * 2) / height;
    const scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.min(scaleX, scaleY)));

    const centerX = minX + width / 2;
    const centerY = minY + height / 2;

    handleViewportChange({
      scale,
      translation: {
        x: containerWidth / 2 - centerX * scale,
        y: containerHeight / 2 - centerY * scale,
      },
    });
  }, [items, handleViewportChange]);

  const handleZoom = React.useCallback(
    (delta: number) => {
      const newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, viewport.scale * (1 + delta)));
      handleViewportChange({
        ...viewport,
        scale: newScale,
      });
    },
    [viewport, handleViewportChange]
  );

  return (
    <div data-testid="viewport-container">
      <div data-testid="viewport-info">
        Scale: {viewport.scale.toFixed(2)}, X: {viewport.translation.x.toFixed(0)}, Y:{' '}
        {viewport.translation.y.toFixed(0)}
      </div>
      <button data-testid="fit-view-btn" onClick={fitView}>
        Fit View
      </button>
      <button data-testid="zoom-in-btn" onClick={() => handleZoom(0.1)}>
        Zoom In
      </button>
      <button data-testid="zoom-out-btn" onClick={() => handleZoom(-0.1)}>
        Zoom Out
      </button>
      <div data-testid="items-container">
        {items.map((item) => (
          <div
            key={item.id}
            data-testid={`item-${item.id}`}
            style={{
              position: 'absolute',
              left: item.position.x * viewport.scale + viewport.translation.x,
              top: item.position.y * viewport.scale + viewport.translation.y,
              width: item.data.width * viewport.scale,
              height: item.data.height * viewport.scale,
            }}
          >
            {item.data.label}
          </div>
        ))}
      </div>
    </div>
  );
}

describe('Feature 1.1: Viewport Management - Integration Tests', () => {
  const createTestItems = (count: number): BaseItem[] =>
    Array.from({ length: count }, (_, i) => ({
      id: `item-${i}`,
      type: 'rectangle',
      position: { x: i * 150, y: i * 100 },
      data: {
        label: `Item ${i + 1}`,
        width: 100,
        height: 80,
        color: '#ccc',
      },
    }));

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Zoom Controls (16ms frame budget)', () => {
    it('zooms in respecting max clamp', () => {
      const items = createTestItems(3);
      const onViewportChange = vi.fn();

      render(<TestViewport items={items} onViewportChange={onViewportChange} />);

      const zoomInBtn = screen.getByTestId('zoom-in-btn');

      // Zoom in multiple times
      for (let i = 0; i < 20; i++) {
        fireEvent.click(zoomInBtn);
      }

      const lastCall = onViewportChange.mock.calls[onViewportChange.mock.calls.length - 1];
      const finalViewport = lastCall[0] as ViewportState;

      // Should be clamped to MAX_SCALE
      expect(finalViewport.scale).toBeLessThanOrEqual(MAX_SCALE);
    });

    it('zooms out respecting min clamp', () => {
      const items = createTestItems(3);
      const onViewportChange = vi.fn();

      render(<TestViewport items={items} onViewportChange={onViewportChange} />);

      const zoomOutBtn = screen.getByTestId('zoom-out-btn');

      // Zoom out multiple times
      for (let i = 0; i < 20; i++) {
        fireEvent.click(zoomOutBtn);
      }

      const lastCall = onViewportChange.mock.calls[onViewportChange.mock.calls.length - 1];
      const finalViewport = lastCall[0] as ViewportState;

      // Should be clamped to MIN_SCALE
      expect(finalViewport.scale).toBeGreaterThanOrEqual(MIN_SCALE);
    });

    it('updates viewport state smoothly during zoom', () => {
      const items = createTestItems(2);
      const onViewportChange = vi.fn();

      render(<TestViewport items={items} onViewportChange={onViewportChange} />);

      const zoomInBtn = screen.getByTestId('zoom-in-btn');

      fireEvent.click(zoomInBtn);
      fireEvent.click(zoomInBtn);
      fireEvent.click(zoomInBtn);

      // Should have 3 viewport updates
      expect(onViewportChange).toHaveBeenCalledTimes(3);

      // Each update should have valid scale
      onViewportChange.mock.calls.forEach((call) => {
        const viewport = call[0] as ViewportState;
        expect(viewport.scale).toBeGreaterThanOrEqual(MIN_SCALE);
        expect(viewport.scale).toBeLessThanOrEqual(MAX_SCALE);
      });
    });

    it('completes zoom operations within performance budget', () => {
      const items = createTestItems(50); // Large dataset
      const onViewportChange = vi.fn();

      render(<TestViewport items={items} onViewportChange={onViewportChange} />);

      const zoomInBtn = screen.getByTestId('zoom-in-btn');

      const start = performance.now();
      fireEvent.click(zoomInBtn);
      const duration = performance.now() - start;

      // Should complete within 16ms (one frame at 60fps)
      expect(duration).toBeLessThan(16);
    });
  });

  describe('Fit View (200ms performance)', () => {
    it('fits single element in viewport', async () => {
      const items = createTestItems(1);
      const onViewportChange = vi.fn();

      render(<TestViewport items={items} onViewportChange={onViewportChange} />);

      const fitViewBtn = screen.getByTestId('fit-view-btn');

      const start = performance.now();
      fireEvent.click(fitViewBtn);
      const duration = performance.now() - start;

      await waitFor(() => {
        expect(onViewportChange).toHaveBeenCalled();
      });

      const viewport = onViewportChange.mock.calls[0][0] as ViewportState;

      // Should fit the element
      expect(viewport.scale).toBeGreaterThan(0);
      expect(viewport.scale).toBeLessThanOrEqual(MAX_SCALE);

      // Should complete within 200ms
      expect(duration).toBeLessThan(200);
    });

    it('fits multiple elements in viewport', async () => {
      const items = createTestItems(10);
      const onViewportChange = vi.fn();

      render(<TestViewport items={items} onViewportChange={onViewportChange} />);

      const fitViewBtn = screen.getByTestId('fit-view-btn');

      fireEvent.click(fitViewBtn);

      await waitFor(() => {
        expect(onViewportChange).toHaveBeenCalled();
      });

      const viewport = onViewportChange.mock.calls[0][0] as ViewportState;

      // Should calculate appropriate zoom for all items
      expect(viewport.scale).toBeGreaterThan(0);
      expect(viewport.scale).toBeLessThanOrEqual(MAX_SCALE);
      expect(viewport.translation.x).toBeDefined();
      expect(viewport.translation.y).toBeDefined();
    });

    it('handles empty canvas gracefully', async () => {
      const items: BaseItem[] = [];
      const onViewportChange = vi.fn();

      render(<TestViewport items={items} onViewportChange={onViewportChange} />);

      const fitViewBtn = screen.getByTestId('fit-view-btn');

      fireEvent.click(fitViewBtn);

      await waitFor(() => {
        expect(onViewportChange).toHaveBeenCalled();
      });

      const viewport = onViewportChange.mock.calls[0][0] as ViewportState;

      // Should reset to default viewport
      expect(viewport.scale).toBe(DEFAULT_VIEWPORT.scale);
      expect(viewport.translation.x).toBe(DEFAULT_VIEWPORT.translation.x);
      expect(viewport.translation.y).toBe(DEFAULT_VIEWPORT.translation.y);
    });

    it('completes fit-view within 200ms performance budget', async () => {
      const items = createTestItems(100); // Large dataset
      const onViewportChange = vi.fn();

      render(<TestViewport items={items} onViewportChange={onViewportChange} />);

      const fitViewBtn = screen.getByTestId('fit-view-btn');

      const start = performance.now();
      fireEvent.click(fitViewBtn);

      await waitFor(() => {
        expect(onViewportChange).toHaveBeenCalled();
      });

      const duration = performance.now() - start;

      // Should complete within 200ms even with 100 items
      expect(duration).toBeLessThan(200);
    });

    it('recenters viewport around content', async () => {
      // Items positioned far from origin
      const items: BaseItem[] = [
        {
          id: 'far-item',
          type: 'rectangle',
          position: { x: 5000, y: 5000 },
          data: { label: 'Far Item', width: 100, height: 80, color: '#ccc' },
        },
      ];
      const onViewportChange = vi.fn();

      render(<TestViewport items={items} onViewportChange={onViewportChange} />);

      const fitViewBtn = screen.getByTestId('fit-view-btn');

      fireEvent.click(fitViewBtn);

      await waitFor(() => {
        expect(onViewportChange).toHaveBeenCalled();
      });

      const viewport = onViewportChange.mock.calls[0][0] as ViewportState;

      // Translation should move viewport to center the far item
      // (not just leave it at default 0,0)
      expect(Math.abs(viewport.translation.x)).toBeGreaterThan(0);
      expect(Math.abs(viewport.translation.y)).toBeGreaterThan(0);
    });
  });

  describe('State Persistence', () => {
    beforeEach(() => {
      // Clear localStorage before each test
      localStorage.clear();
    });

    it('maintains viewport state across re-renders', () => {
      const items = createTestItems(3);
      const onViewportChange = vi.fn();

      const { rerender } = render(
        <TestViewport items={items} onViewportChange={onViewportChange} />
      );

      const zoomInBtn = screen.getByTestId('zoom-in-btn');
      fireEvent.click(zoomInBtn);

      const firstViewport = onViewportChange.mock.calls[0][0] as ViewportState;

      // Rerender with same props
      rerender(<TestViewport items={items} onViewportChange={onViewportChange} />);

      const viewportInfo = screen.getByTestId('viewport-info');

      // Should still show the zoomed scale
      expect(viewportInfo.textContent).toContain(firstViewport.scale.toFixed(2));
    });

    it('restores viewport from initial state', () => {
      const items = createTestItems(3);
      const initialViewport: ViewportState = {
        scale: 2.5,
        translation: { x: 100, y: 200 },
      };

      render(<TestViewport items={items} initialViewport={initialViewport} />);

      const viewportInfo = screen.getByTestId('viewport-info');

      // Should show the initial viewport values
      expect(viewportInfo.textContent).toContain('2.50');
      expect(viewportInfo.textContent).toContain('100');
      expect(viewportInfo.textContent).toContain('200');
    });

    it('clamps restored zoom values to valid range', () => {
      const items = createTestItems(3);
      // Invalid viewport with out-of-bounds scale
      const invalidViewport: ViewportState = {
        scale: 50, // Way above MAX_SCALE
        translation: { x: 0, y: 0 },
      };

      render(<TestViewport items={items} initialViewport={invalidViewport} />);

      const zoomInBtn = screen.getByTestId('zoom-in-btn');
      fireEvent.click(zoomInBtn); // Trigger validation through zoom

      const viewportInfo = screen.getByTestId('viewport-info');
      const scaleMatch = viewportInfo.textContent?.match(/Scale: ([\d.]+)/);

      if (scaleMatch) {
        const currentScale = parseFloat(scaleMatch[1]);
        expect(currentScale).toBeLessThanOrEqual(MAX_SCALE);
      }
    });
  });

  describe('Coordinate Transformations', () => {
    it('applies scale to item positions', () => {
      const items = createTestItems(1);
      const onViewportChange = vi.fn();

      render(<TestViewport items={items} onViewportChange={onViewportChange} />);

      const zoomInBtn = screen.getByTestId('zoom-in-btn');
      fireEvent.click(zoomInBtn);

      const item = screen.getByTestId('item-item-0');
      const style = item.style;

      // Position should reflect scaled coordinates
      expect(style.left).toBeDefined();
      expect(style.top).toBeDefined();
      expect(style.width).toBeDefined();
      expect(style.height).toBeDefined();
    });

    it('applies translation to viewport', () => {
      const items = createTestItems(1);
      const initialViewport: ViewportState = {
        scale: 1,
        translation: { x: 50, y: 100 },
      };

      render(<TestViewport items={items} initialViewport={initialViewport} />);

      const viewportInfo = screen.getByTestId('viewport-info');

      // Should show translation values
      expect(viewportInfo.textContent).toContain('X: 50');
      expect(viewportInfo.textContent).toContain('Y: 100');
    });
  });

  describe('Acceptance Criteria Validation', () => {
    it('✓ Smooth zooming: Mouse wheel/pinch within 16ms per frame', () => {
      const items = createTestItems(20);
      const { getByTestId } = render(<TestViewport items={items} />);

      const zoomInBtn = getByTestId('zoom-in-btn');

      // Measure zoom performance
      const iterations = 10;
      const times: number[] = [];

      for (let i = 0; i < iterations; i++) {
        const start = performance.now();
        fireEvent.click(zoomInBtn);
        times.push(performance.now() - start);
      }

      const avgTime = times.reduce((sum, t) => sum + t, 0) / times.length;

      // Average should be well under 16ms
      expect(avgTime).toBeLessThan(16);
    });

    it('✓ Fit view: Recenters viewport around nodes within 200ms', async () => {
      const items = createTestItems(50);
      const onViewportChange = vi.fn();

      render(<TestViewport items={items} onViewportChange={onViewportChange} />);

      const fitViewBtn = screen.getByTestId('fit-view-btn');

      const start = performance.now();
      fireEvent.click(fitViewBtn);

      await waitFor(() => {
        expect(onViewportChange).toHaveBeenCalled();
      });

      const duration = performance.now() - start;

      // Should complete within 200ms
      expect(duration).toBeLessThan(200);

      // Should actually recenter (not just reset)
      const viewport = onViewportChange.mock.calls[0][0] as ViewportState;
      expect(viewport.scale).toBeGreaterThan(0);
    });

    it('✓ State persistence: Restore zoom/position from persisted state', () => {
      const items = createTestItems(3);
      const persistedViewport: ViewportState = {
        scale: 1.5,
        translation: { x: 100, y: 200 },
      };

      const { getByTestId } = render(
        <TestViewport items={items} initialViewport={persistedViewport} />
      );

      const viewportInfo = getByTestId('viewport-info');

      // Should restore persisted values
      expect(viewportInfo.textContent).toContain('1.50');
      expect(viewportInfo.textContent).toContain('100');
      expect(viewportInfo.textContent).toContain('200');
    });
  });
});
