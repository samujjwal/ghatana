import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import type { CanvasState } from '@/components/canvas/workspace/canvasAtoms';

import { createAutoLayoutPreview, useCanvasLayout } from '../useCanvasLayout';

const initialCanvasState: CanvasState = {
  elements: [
    { id: 'node-1', type: 'card', kind: 'component', position: { x: 5, y: 5 } },
    { id: 'node-2', type: 'card', kind: 'node', position: { x: 15, y: 15 } },
    { id: 'locked-1', type: 'guide', kind: 'guide', position: { x: 25, y: 25 } },
  ],
};

describe('createAutoLayoutPreview', () => {
  it('creates a diff without mutating the input elements', () => {
    const preview = createAutoLayoutPreview(initialCanvasState.elements ?? []);

    expect(preview.moves).toEqual([
      {
        elementId: 'node-1',
        from: { x: 5, y: 5 },
        to: { x: 120, y: 120 },
      },
      {
        elementId: 'node-2',
        from: { x: 15, y: 15 },
        to: { x: 340, y: 120 },
      },
    ]);
    expect(initialCanvasState.elements?.[0]?.position).toEqual({ x: 5, y: 5 });
    expect(preview.after[2]?.position).toEqual({ x: 25, y: 25 });
  });
});

describe('useCanvasLayout', () => {
  it('previews, applies, undoes, and redoes auto-layout with audit events', () => {
    let canvasState: CanvasState = initialCanvasState;
    const setGlobalCanvas = vi.fn((updater: (prev: CanvasState) => CanvasState): void => {
      canvasState = updater(canvasState);
    });
    const recordLayoutAudit = vi.fn();

    const { result, rerender } = renderHook(
      ({ state }: { state: CanvasState }) =>
        useCanvasLayout({
          canvasState: state,
          setGlobalCanvas,
          recordLayoutAudit,
        }),
      { initialProps: { state: canvasState } }
    );

    act(() => {
      result.current.previewAutoLayout();
    });

    expect(result.current.layoutPreview?.moves).toHaveLength(2);
    expect(recordLayoutAudit).toHaveBeenCalledWith({
      outcome: 'previewed',
      movedElementCount: 2,
    });

    act(() => {
      result.current.applyAutoLayout();
    });
    rerender({ state: canvasState });

    expect(canvasState.elements?.[0]?.position).toEqual({ x: 120, y: 120 });
    expect(canvasState.elements?.[1]?.position).toEqual({ x: 340, y: 120 });
    expect(result.current.canUndoAutoLayout).toBe(true);
    expect(recordLayoutAudit).toHaveBeenCalledWith({
      outcome: 'applied',
      movedElementCount: 2,
    });

    act(() => {
      expect(result.current.undoAutoLayout()).toBe(true);
    });
    rerender({ state: canvasState });

    expect(canvasState.elements?.[0]?.position).toEqual({ x: 5, y: 5 });
    expect(result.current.canRedoAutoLayout).toBe(true);
    expect(recordLayoutAudit).toHaveBeenCalledWith({
      outcome: 'undone',
      movedElementCount: 2,
    });

    act(() => {
      expect(result.current.redoAutoLayout()).toBe(true);
    });

    expect(canvasState.elements?.[0]?.position).toEqual({ x: 120, y: 120 });
    expect(recordLayoutAudit).toHaveBeenCalledWith({
      outcome: 'redone',
      movedElementCount: 2,
    });
  });
});
