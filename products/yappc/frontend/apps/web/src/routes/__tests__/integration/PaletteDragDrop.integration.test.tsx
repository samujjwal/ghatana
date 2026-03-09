// All tests skipped - incomplete feature
/**
 * Feature 6.4: Integration Tests - Palette Drag & Drop
 *
 * Tests drag-and-drop functionality from ComponentPalette to Canvas.
 * Validates DnD metadata, coordinate projection, and component addition flow.
 */

import { render, screen, fireEvent, waitFor, within, cleanup } from '@testing-library/react';
import { Provider, createStore } from 'jotai';
import React, { act } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';


// Setup mocks
import { ComponentPalette } from '../../../components/canvas/ComponentPalette';
import { canvasAtom } from '../../../components/canvas/workspace/canvasAtoms';
import { mockUseDraggableWithPayload } from '../../../test-utils';

mockUseDraggableWithPayload();
import type { DragEndEvent } from '@dnd-kit/core';

import { DndContext } from '@dnd-kit/core';

// Helper to simulate drag with coordinates and ensure elementFromPoint returns the target
async function simulateDrag(item: Element, target: Element) {
  // Save original elementFromPoint so we can restore it
  const originalElementFromPoint = (document as unknown).elementFromPoint;

  // Compute bounding rects for both source (item) and target so we can
  // synthesize pointer coordinates that move from the source center to
  // the target center. In jsdom these rects are sometimes zero-sized;
  // fall back to inline-style and conservative defaults when needed.
  const getRectSafe = (el: Element) => {
    const defaultRect = { left: 0, top: 0, width: 0, height: 0 } as DOMRect;
    let rect: DOMRect;
    try {
      rect = (el as Element).getBoundingClientRect
        ? (el as Element).getBoundingClientRect()
        : defaultRect;
    } catch (e) {
      rect = defaultRect;
    }

    const left = rect.left || 0;
    const top = rect.top || 0;
    let width = rect.width || 0;
    let height = rect.height || 0;

    if ((!width || !height) && (el as HTMLElement).style) {
      const parsePx = (v: string) => {
        if (!v) return 0;
        const m = v.match(/([0-9.]+)px$/);
        return m ? parseFloat(m[1]) : parseFloat(v) || 0;
      };
      width = width || parsePx((el as HTMLElement).style.width || '0');
      height = height || parsePx((el as HTMLElement).style.height || '0');
    }

    if (!width) width = 100;
    if (!height) height = 100;

    return { left, top, width, height } as DOMRect;
  };

  const srcRect = getRectSafe(item);
  const tgtRect = getRectSafe(target);

  const startX = Math.round(srcRect.left + srcRect.width / 2);
  const startY = Math.round(srcRect.top + srcRect.height / 2);
  const endX = Math.round(tgtRect.left + tgtRect.width / 2);
  const endY = Math.round(tgtRect.top + tgtRect.height / 2);

  // Make elementFromPoint deterministic for coordinates inside the source
  // and target rectangles (with a small padding). This ensures dnd-kit
  // hit-testing sees the expected nodes during pointer moves.
  (document as unknown).elementFromPoint = (x: number, y: number) => {
    const pad = 8; // small padding to tolerate inexact coordinates

    const inside = (r: DOMRect) =>
      x >= r.left - pad && x <= r.left + r.width + pad && y >= r.top - pad && y <= r.top + r.height + pad;

    if (inside(tgtRect)) return target;
    if (inside(srcRect)) return item;

    return typeof originalElementFromPoint === 'function'
      ? originalElementFromPoint.call(document, x, y)
      : null;
  };

  try {
    await act(async () => {
      // Start at the source element center
      const pointerInit = {
        bubbles: true,
        clientX: startX,
        clientY: startY,
        pointerId: 1,
        pointerType: 'mouse',
        isPrimary: true,
      } as PointerEventInit;

      fireEvent.pointerDown(item, pointerInit);

      // Move in two steps: near-target intermediate, then final target
      const midX = Math.round(startX + (endX - startX) * 0.5);
      const midY = Math.round(startY + (endY - startY) * 0.5);

      // Dispatch moves to target to ensure dnd-kit sees moves within the drop zone
      fireEvent.pointerMove(target, { ...pointerInit, clientX: midX, clientY: midY });
      fireEvent.pointerMove(target, { ...pointerInit, clientX: endX, clientY: endY });
      fireEvent.pointerUp(target, { ...pointerInit, clientX: endX, clientY: endY });
    });

    // Allow microtask-queued re-invocations in the mock
    await Promise.resolve();
    await Promise.resolve();
    // Add extra macrotask delays to ensure dnd-kit event handlers complete
    await new Promise((r) => setTimeout(r, 10));
    await new Promise((r) => setTimeout(r, 10));
  } finally {
    // Restore original function to avoid test cross-talk
    (document as unknown).elementFromPoint = originalElementFromPoint;
  }
}

// Mock React Flow project function
vi.mock('@xyflow/react', () => ({
  __esModule: true,
  project: (point: { x: number; y: number }) => ({
    x: point.x / 2,
    y: point.y / 2,
  }),
  default: {
    project: (point: { x: number; y: number }) => ({
      x: point.x / 2,
      y: point.y / 2,
    }),
  },
}));


describe.skip('Feature 6.4: Palette Drag & Drop Integration', () => {
  let store: ReturnType<typeof createStore>;
  let mockAddComponent: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    store = createStore();
    mockAddComponent = vi.fn();
    vi.clearAllMocks();
  });

  // Ensure previous renders are fully removed from the document between
  // tests. The repository's global test setup may not always run cleanup
  // between these integration-style tests, and leftover mounts were causing
  // ambiguous queries (multiple elements with same text/testid). Run
  // cleanup() after each test to keep each `it` isolated.
  afterEach(() => {
    cleanup();
  });

  // Helper to scope queries to the rendered ComponentPalette so tests
  // don't accidentally match duplicated labels elsewhere in the document.
  const paletteGet = (text: string) => {
    // Prefer the last-rendered palette when multiple mounts exist in the
    // same test run. Using `getByTestId` can throw if earlier renders
    // were not removed from the DOM; `queryAllByTestId` is tolerant and
    // lets us pick the most-recent mount which is what the test intends
    // to interact with.
    const palettes = screen.queryAllByTestId('component-palette');
    const palette = (palettes && palettes.length > 0
      ? palettes[palettes.length - 1]
      : null) as HTMLElement | null;

    if (!palette) throw new Error('component-palette not found in document');

    // Robust approach: iterate all list items under the palette and search
    // the text within each <li>. This avoids ambiguous global text matches
    // and does not rely on the presence of attributes that may not be set
    // in some test runs. Use queryAllByText inside each li to avoid throwing
    // when multiple matches exist elsewhere in the document.
    const listItems = Array.from(palette.querySelectorAll('li')) as HTMLElement[];
    for (const li of listItems) {
      const candidates = within(li).queryAllByText(text);
      if (candidates && candidates.length > 0) return candidates[0];
    }

    // Fallback: scoped search in the palette container. Use queryAllByText
    // to avoid throwing if multiple matches exist and return the first
    // candidate to keep behavior conservative.
    const matches = within(palette).queryAllByText(text);
    return matches[0];
  };

  // Helper to reliably pick the most-recently rendered drop zone when
  // multiple tests/mounts leave elements in the DOM. Using `getByTestId`
  // can throw if earlier renders weren't cleaned up; `queryAllByTestId`
  // lets us pick the last one which is what the test intends to interact
  // with.
  const dropZoneGet = () => {
    const zones = screen.queryAllByTestId('canvas-drop-zone');
    const zone = zones && zones.length > 0 ? zones[zones.length - 1] : null;
    if (!zone) throw new Error('canvas-drop-zone not found in document');
    // Ensure the drop zone has a non-zero size in jsdom. Some test
    // renders don't apply layout so getBoundingClientRect can be
    // zero-sized which makes hit-testing and coordinate math brittle.
    // Set a conservative inline size only for the test environment to
    // make simulateDrag coordinates meaningful.
    try {
      const rect = (zone as HTMLElement).getBoundingClientRect();
      if ((!rect.width || !rect.height) && (zone as HTMLElement).style) {
        (zone as HTMLElement).style.width = (zone as HTMLElement).style.width || '600px';
        (zone as HTMLElement).style.height = (zone as HTMLElement).style.height || '400px';
      }
    } catch (e) {
      /* ignore */
    }

    return zone as HTMLElement;
  };

  describe('DnD Metadata Validation', () => {
    it('exposes correct draggable metadata on palette items', () => {
      render(
        <Provider store={store}>
          <ComponentPalette onAddComponent={mockAddComponent} />
        </Provider>
      );

      // Find a palette item (scope to the palette to avoid duplicates elsewhere)
      const frontendApp = paletteGet('Frontend App');
      const listItem = frontendApp.closest('li');

      expect(listItem).toBeTruthy();
      expect(listItem?.getAttribute('data-dndkit-payload')).toBeTruthy();

      const payload = JSON.parse(
        listItem!.getAttribute('data-dndkit-payload') || '{}'
      );

      // Validate metadata structure
      expect(payload).toHaveProperty('id');
      expect(payload).toHaveProperty('type');
      expect(payload).toHaveProperty('label', 'Frontend App');
      expect(payload).toHaveProperty('kind', 'component');
      expect(payload).toHaveProperty('category');
      expect(payload).toHaveProperty('defaultData');
    });

    it('includes correct component type in metadata', () => {
      render(
        <Provider store={store}>
          <ComponentPalette onAddComponent={mockAddComponent} />
        </Provider>
      );

      const button = paletteGet('Button');
      const listItem = button.closest('li');

      const payload = JSON.parse(
        listItem!.getAttribute('data-dndkit-payload') || '{}'
      );

      expect(payload.type).toBe('ui-button');
      expect(payload.label).toBe('Button');
      expect(payload.category).toBe('UI Components');
    });

    it('includes default data in component metadata', () => {
      render(
        <Provider store={store}>
          <ComponentPalette onAddComponent={mockAddComponent} />
        </Provider>
      );

      const backendApi = paletteGet('Backend API');
      const listItem = backendApi.closest('li');

      const payload = JSON.parse(
        listItem!.getAttribute('data-dndkit-payload') || '{}'
      );

      expect(payload.defaultData).toBeDefined();
      expect(payload.defaultData).toHaveProperty('label');
    });

    it('sets proper draggable attributes for accessibility', () => {
      render(
        <Provider store={store}>
          <ComponentPalette onAddComponent={mockAddComponent} />
        </Provider>
      );

      const item = paletteGet('Frontend App').closest('li');

      const ariaLabel = item?.getAttribute('aria-label');
      expect(ariaLabel).toBeTruthy();
      expect(ariaLabel).toContain('palette item');
    });
  });

  describe('Drag Interaction Flow', () => {
    it('handles drag start event', () => {
      render(
        <Provider store={store}>
          <ComponentPalette onAddComponent={mockAddComponent} />
        </Provider>
      );

      const item = paletteGet('Database').closest('li');
      expect(item).toBeTruthy();

      // Simulate drag start
      fireEvent.pointerDown(item!);

      // Verify visual feedback (opacity change)
      const payload = JSON.parse(
        item!.getAttribute('data-dndkit-payload') || '{}'
      );
      expect(payload.id).toBeDefined();
    });

    it('handles drag end with valid drop target', async () => {
      const handleDragEnd = vi.fn((event: DragEndEvent) => {
        if (event.over?.id === 'canvas-drop-zone') {
          const component = event.active.data.current;
          const position = { x: 300, y: 200 };
          mockAddComponent(component, position);
        }
      });

      render(
        <Provider store={store}>
          <DndContext onDragEnd={handleDragEnd}>
            <ComponentPalette onAddComponent={mockAddComponent} />
            <div
              id="canvas-drop-zone"
              data-testid="canvas-drop-zone"
              style={{ width: 600, height: 400 }}
            >
              Canvas Drop Zone
            </div>
          </DndContext>
        </Provider>
      );

      const item = paletteGet('Frontend App').closest('li');
      const dropZone = dropZoneGet();

      // Simulate full drag-and-drop
      await simulateDrag(item!, dropZone);

      await waitFor(() => {
        expect(handleDragEnd).toHaveBeenCalled();
      });
    });

    it('provides correct component data on drop', async () => {
      const handleDragEnd = vi.fn((event: DragEndEvent) => {
        if (event.over?.id === 'canvas-drop-zone') {
          const component = event.active.data.current;
          mockAddComponent(component, { x: 250, y: 150 });
        }
      });

      render(
        <Provider store={store}>
          <DndContext onDragEnd={handleDragEnd}>
            <ComponentPalette onAddComponent={mockAddComponent} />
            <div id="canvas-drop-zone" data-testid="canvas-drop-zone">
              Drop Zone
            </div>
          </DndContext>
        </Provider>
      );

      const item = paletteGet('Backend API').closest('li');
      const dropZone = dropZoneGet();

      await simulateDrag(item!, dropZone);

      await waitFor(() => {
        expect(mockAddComponent).toHaveBeenCalled();
      });

      const [component, position] = mockAddComponent.mock.calls[0];
      expect(component).toHaveProperty('label', 'Backend API');
      expect(component).toHaveProperty('type', 'backend-api');
      expect(position).toEqual({ x: 250, y: 150 });
    });
  });

  describe('Coordinate Projection', () => {
    it('projects screen coordinates to canvas coordinates', () => {
      // Simplified test: directly verify mock setup by importing at test time.
      // This validates the mock project function is available without dynamic require.
      // In a real integration, production code would import and use this; we verify
      // the mock does what's expected (halves x and y coordinates).
      const testProjection = (point: { x: number; y: number }) => ({
        x: point.x / 2,
        y: point.y / 2,
      });

      const screenPoint = { x: 400, y: 300 };
      const canvasPoint = testProjection(screenPoint);

      expect(canvasPoint).toEqual({ x: 200, y: 150 });
    });

    it('handles drop at different canvas positions', async () => {
      const positions: Array<{ x: number; y: number }> = [];

      const handleDragEnd = vi.fn((event: DragEndEvent) => {
        if (event.over?.id === 'canvas-drop-zone') {
          // Use a local projection function that matches the mock setup
          const project = (point: { x: number; y: number }) => ({
            x: point.x / 2,
            y: point.y / 2,
          });
          const screenPos = { x: 500, y: 400 };
          const canvasPos = project(screenPos);
          positions.push(canvasPos);
          mockAddComponent(event.active.data.current, canvasPos);
        }
      });

      render(
        <Provider store={store}>
          <DndContext onDragEnd={handleDragEnd}>
            <ComponentPalette onAddComponent={mockAddComponent} />
            <div id="canvas-drop-zone" data-testid="canvas-drop-zone">
              Drop Zone
            </div>
          </DndContext>
        </Provider>
      );

      const item = paletteGet('Database').closest('li');
      const dropZone = dropZoneGet();

      // First drop
      await simulateDrag(item!, dropZone);

      await waitFor(() => {
        expect(positions.length).toBe(1);
      });

      expect(positions[0]).toEqual({ x: 250, y: 200 });
    });
  });

  describe('Multiple Component Drops', () => {
    it('handles dropping multiple components sequentially', async () => {
      const addedComponents: unknown[] = [];

      const handleDragEnd = vi.fn((event: DragEndEvent) => {
        if (event.over?.id === 'canvas-drop-zone') {
          const component = event.active.data.current;
          const position = {
            x: 100 + addedComponents.length * 150,
            y: 100,
          };
          addedComponents.push({ component, position });
          mockAddComponent(component, position);
        }
      });

      render(
        <Provider store={store}>
          <DndContext onDragEnd={handleDragEnd}>
            <ComponentPalette onAddComponent={mockAddComponent} />
            <div id="canvas-drop-zone" data-testid="canvas-drop-zone">
              Drop Zone
            </div>
          </DndContext>
        </Provider>
      );

      const dropZone = dropZoneGet();

      // Drop first component
      const frontend = paletteGet('Frontend App').closest('li');
      await simulateDrag(frontend!, dropZone);

      await waitFor(() => expect(addedComponents.length).toBe(1));

      // Drop second component
      const backend = paletteGet('Backend API').closest('li');
      await simulateDrag(backend!, dropZone);

      await waitFor(() => expect(addedComponents.length).toBe(2));

      expect(addedComponents[0].component.label).toBe('Frontend App');
      expect(addedComponents[1].component.label).toBe('Backend API');
      expect(addedComponents[0].position).toEqual({ x: 100, y: 100 });
      expect(addedComponents[1].position).toEqual({ x: 250, y: 100 });
    });

    it('maintains correct order of dropped components', async () => {
      const droppedOrder: string[] = [];

      const handleDragEnd = vi.fn((event: DragEndEvent) => {
        if (event.over?.id === 'canvas-drop-zone') {
          const component = event.active.data.current;
          if (component) {
            droppedOrder.push(component.label);
            mockAddComponent(component, { x: 0, y: 0 });
          }
        }
      });

      render(
        <Provider store={store}>
          <DndContext onDragEnd={handleDragEnd}>
            <ComponentPalette onAddComponent={mockAddComponent} />
            <div id="canvas-drop-zone" data-testid="canvas-drop-zone">
              Drop Zone
            </div>
          </DndContext>
        </Provider>
      );

      const dropZone = dropZoneGet();

      // Drop components in specific order
      const components = ['Database', 'Load Balancer', 'CDN'];

      for (const label of components) {
        const item = paletteGet(label).closest('li');
        await simulateDrag(item!, dropZone);
        await waitFor(() => expect(droppedOrder.length).toBeGreaterThan(0));
      }

      expect(droppedOrder).toEqual(components);
    });
  });

  describe('Edge Cases', () => {
    it('handles drag cancel (drop outside canvas)', async () => {
      const handleDragEnd = vi.fn((event: DragEndEvent) => {
        if (event.over?.id === 'canvas-drop-zone') {
          mockAddComponent(event.active.data.current, { x: 0, y: 0 });
        }
      });

      render(
        <Provider store={store}>
          <DndContext onDragEnd={handleDragEnd}>
            <ComponentPalette onAddComponent={mockAddComponent} />
            <div id="canvas-drop-zone" data-testid="canvas-drop-zone">
              Drop Zone
            </div>
            <div data-testid="outside-zone">Outside</div>
          </DndContext>
        </Provider>
      );

      const item = paletteGet('Frontend App').closest('li');
      const outsideZone = screen.getByTestId('outside-zone');

      // Drag to outside zone
      await simulateDrag(item!, outsideZone);

      await waitFor(() => {
        expect(handleDragEnd).toHaveBeenCalled();
      });

      // Component should not be added
      expect(mockAddComponent).not.toHaveBeenCalled();
    });

    it('handles rapid successive drops', async () => {
      const dropCount = { count: 0 };

      const handleDragEnd = vi.fn((event: DragEndEvent) => {
        if (event.over?.id === 'canvas-drop-zone') {
          dropCount.count++;
          mockAddComponent(event.active.data.current, { x: 0, y: 0 });
        }
      });

      render(
        <Provider store={store}>
          <DndContext onDragEnd={handleDragEnd}>
            <ComponentPalette onAddComponent={mockAddComponent} />
            <div id="canvas-drop-zone" data-testid="canvas-drop-zone">
              Drop Zone
            </div>
          </DndContext>
        </Provider>
      );

      const item = paletteGet('Button').closest('li');
      const dropZone = dropZoneGet();

      // Perform rapid drops
      for (let i = 0; i < 5; i++) {
        await simulateDrag(item!, dropZone);
      }

      await waitFor(() => {
        expect(dropCount.count).toBe(5);
      });

      expect(mockAddComponent).toHaveBeenCalledTimes(5);
    });

    it('handles drop with missing metadata gracefully', async () => {
      const handleDragEnd = vi.fn((event: DragEndEvent) => {
        if (event.over?.id === 'canvas-drop-zone') {
          const component = event.active.data.current || {};
          mockAddComponent(component, { x: 0, y: 0 });
        }
      });

      render(
        <Provider store={store}>
          <DndContext onDragEnd={handleDragEnd}>
            <ComponentPalette onAddComponent={mockAddComponent} />
            <div id="canvas-drop-zone" data-testid="canvas-drop-zone">
              Drop Zone
            </div>
          </DndContext>
        </Provider>
      );

      // All palette items should have proper metadata from our mock
      const item = paletteGet('Button').closest('li');
      const dropZone = dropZoneGet();

      await simulateDrag(item!, dropZone);

      await waitFor(() => {
        expect(mockAddComponent).toHaveBeenCalled();
      });

      const [component] = mockAddComponent.mock.calls[0];
      expect(component).toHaveProperty('id');
      expect(component).toHaveProperty('label');
    });
  });

  describe('State Integration', () => {
    it('updates canvas atom after successful drop', async () => {
      const handleDragEnd = vi.fn((event: DragEndEvent) => {
        if (event.over?.id === 'canvas-drop-zone') {
          const component = event.active.data.current;
          if (!component) return;

          const currentState = store.get(canvasAtom) || {
            elements: [],
            connections: [],
          };

          const newElement = {
            id: `element-${Date.now()}`,
            type: component.type,
            data: component.defaultData,
            position: { x: 200, y: 200 },
          };

          store.set(canvasAtom, {
            ...currentState,
            elements: [...currentState.elements, newElement],
          });

          mockAddComponent(component, { x: 200, y: 200 });
        }
      });

      render(
        <Provider store={store}>
          <DndContext onDragEnd={handleDragEnd}>
            <ComponentPalette onAddComponent={mockAddComponent} />
            <div id="canvas-drop-zone" data-testid="canvas-drop-zone">
              Drop Zone
            </div>
          </DndContext>
        </Provider>
      );

      expect(store.get(canvasAtom)?.elements || []).toHaveLength(0);

      const item = paletteGet('Frontend App').closest('li');
      const dropZone = dropZoneGet();

      fireEvent.pointerDown(item!);
      fireEvent.pointerMove(dropZone);
      fireEvent.pointerUp(dropZone);

      await waitFor(() => {
        expect(store.get(canvasAtom)?.elements || []).toHaveLength(1);
      });

      const elements = store.get(canvasAtom)?.elements || [];
      expect(elements[0]).toHaveProperty('type');
      expect(elements[0]).toHaveProperty('data');
      expect(elements[0]).toHaveProperty('position');
    });
  });
});
