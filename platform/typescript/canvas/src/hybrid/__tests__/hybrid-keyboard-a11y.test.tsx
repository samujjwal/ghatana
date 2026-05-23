/**
 * @fileoverview Keyboard and accessibility behavior tests for HybridCanvas.
 */

import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { HybridCanvas } from '../HybridCanvas.js';
import type { CanvasElement, CanvasNode, SelectionState } from '../types.js';

const resizeObserverInstances: ResizeObserver[] = [];

class TestResizeObserver implements ResizeObserver {
  readonly observe = vi.fn();
  readonly unobserve = vi.fn();
  readonly disconnect = vi.fn();

  constructor() {
    resizeObserverInstances.push(this);
  }
}

const elementFixture: CanvasElement = {
  id: 'element-1',
  type: 'note',
  position: { x: 10, y: 20 },
  size: { width: 120, height: 80 },
  data: { label: 'Note' },
};

const nodeFixture: CanvasNode = {
  id: 'node-1',
  type: 'default',
  position: { x: 30, y: 40 },
  data: { label: 'Node' },
};

describe('HybridCanvas keyboard accessibility', () => {
  beforeEach(() => {
    resizeObserverInstances.length = 0;
    vi.stubGlobal('ResizeObserver', TestResizeObserver);
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      callback(0);
      return 1;
    });
    vi.stubGlobal('cancelAnimationFrame', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('exposes a focusable application surface with an accessible label', () => {
    render(
      <HybridCanvas
        mode="freeform-only"
        elements={[elementFixture]}
        width={640}
        height={480}
      />,
    );

    const surface = screen.getByRole('application', { name: 'Hybrid canvas surface' });
    expect(surface.getAttribute('tabindex')).toBe('0');
    expect(surface.getAttribute('data-mode')).toBe('freeform-only');
  });

  it('supports keyboard select-all and arrow-key movement for freeform elements', async () => {
    const onElementsChange = vi.fn<(elements: CanvasElement[]) => void>();
    const onSelectionChange = vi.fn<(selection: SelectionState) => void>();

    render(
      <HybridCanvas
        mode="freeform-only"
        elements={[elementFixture]}
        width={640}
        height={480}
        onElementsChange={onElementsChange}
        onSelectionChange={onSelectionChange}
      />,
    );

    const surface = screen.getByRole('application', { name: 'Hybrid canvas surface' });
    fireEvent.keyDown(surface, { key: 'a', ctrlKey: true });

    await waitFor(() => {
      expect(onSelectionChange).toHaveBeenLastCalledWith(
        expect.objectContaining({ elementIds: ['element-1'] }),
      );
    });

    fireEvent.keyDown(surface, { key: 'ArrowRight' });

    await waitFor(() => {
      expect(onElementsChange).toHaveBeenLastCalledWith([
        expect.objectContaining({
          id: 'element-1',
          position: { x: 11, y: 20 },
        }),
      ]);
    });
  });

  it('supports keyboard movement and deletion for graph nodes without a pointer', async () => {
    const onNodesChange = vi.fn<(nodes: CanvasNode[]) => void>();
    const onEdgesChange = vi.fn();
    const onSelectionChange = vi.fn<(selection: SelectionState) => void>();

    render(
      <HybridCanvas
        mode="freeform-only"
        nodes={[nodeFixture]}
        edges={[{ id: 'edge-1', source: 'node-1', target: 'node-1' }]}
        width={640}
        height={480}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onSelectionChange={onSelectionChange}
      />,
    );

    const surface = screen.getByRole('application', { name: 'Hybrid canvas surface' });
    fireEvent.keyDown(surface, { key: 'a', ctrlKey: true });

    await waitFor(() => {
      expect(onSelectionChange).toHaveBeenLastCalledWith(
        expect.objectContaining({ nodeIds: ['node-1'] }),
      );
    });

    fireEvent.keyDown(surface, { key: 'ArrowDown', shiftKey: true });

    await waitFor(() => {
      expect(onNodesChange).toHaveBeenLastCalledWith([
        expect.objectContaining({
          id: 'node-1',
          position: { x: 30, y: 50 },
        }),
      ]);
    });

    fireEvent.keyDown(surface, { key: 'Delete' });

    await waitFor(() => {
      expect(onNodesChange).toHaveBeenLastCalledWith([]);
      expect(onEdgesChange).toHaveBeenLastCalledWith([]);
      expect(onSelectionChange).toHaveBeenLastCalledWith(
        expect.objectContaining({ nodeIds: [], edgeIds: [] }),
      );
    });
  });
});
