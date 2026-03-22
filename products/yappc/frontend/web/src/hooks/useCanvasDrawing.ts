/**
 * useCanvasDrawing - Canvas Drawing Operations Hook
 *
 * Provides freehand drawing operations (pen/pencil/marker/highlighter) and
 * sticky-note creation for the canvas, delegating stroke management to
 * DrawingManager.
 *
 * @doc.type hook
 * @doc.purpose Canvas drawing and sticky-note operations
 * @doc.layer hooks
 * @doc.pattern Hook
 */

import { useCallback } from 'react';
import { useAtom } from 'jotai';
import { canvasAtom } from '../state/atoms/unifiedCanvasAtom';
import { type DrawingStroke, type Point } from '../lib/canvas/DrawingManager';
import { type HierarchicalNode } from '../lib/canvas/HierarchyManager';
import { type DrawingManager } from '../lib/canvas/DrawingManager';

export interface UseCanvasDrawingReturn {
  startDrawing: (
    point: Point,
    tool?: 'pen' | 'pencil' | 'marker' | 'highlighter',
    color?: string,
    width?: number,
    opacity?: number
  ) => void;
  continueDrawing: (point: Point) => void;
  endDrawing: () => DrawingStroke | null;
  clearDrawings: () => void;
  createStickyNote: (
    position: { x: number; y: number },
    color?: 'yellow' | 'pink' | 'blue' | 'green' | 'purple'
  ) => HierarchicalNode;
}

export function useCanvasDrawing(
  drawingManager: DrawingManager,
  addNode: (node: Partial<HierarchicalNode>) => HierarchicalNode
): UseCanvasDrawingReturn {
  const [, setCanvasState] = useAtom(canvasAtom);

  const startDrawing = useCallback(
    (
      point: Point,
      tool: 'pen' | 'pencil' | 'marker' | 'highlighter' | 'eraser' = 'pen',
      color = '#000000',
      width = 2,
      opacity = 1
    ) => {
      console.log('[Canvas] Starting drawing at:', point);
      drawingManager.startStroke(point, tool, color, width, opacity);
    },
    [drawingManager]
  );

  const continueDrawing = useCallback(
    (point: Point) => drawingManager.addPoint(point),
    [drawingManager]
  );

  const endDrawing = useCallback((): DrawingStroke | null => {
    const stroke = drawingManager.endStroke();
    console.log('[Canvas] Ended drawing, stroke:', stroke);

    if (stroke && stroke.points.length > 1) {
      setCanvasState((prev) => {
        const drawings = Array.isArray(prev.drawings) ? prev.drawings : [];
        return { ...prev, drawings: [...drawings, stroke] };
      });
      console.log('[Canvas] Drawing persisted to SVG overlay:', stroke.id);
      return stroke;
    }
    return null;
  }, [drawingManager, setCanvasState]);

  const clearDrawings = useCallback(() => {
    setCanvasState((prev) => ({ ...prev, drawings: [] }));
  }, [setCanvasState]);

  const createStickyNote = useCallback(
    (
      position: { x: number; y: number },
      color: 'yellow' | 'pink' | 'blue' | 'green' | 'purple' = 'yellow'
    ): HierarchicalNode => {
      console.log('[Canvas] Creating sticky note at:', position, 'color:', color);
      return addNode({
        type: 'sticky',
        position,
        size: { width: 200, height: 200 },
        data: { text: '', color, fontSize: 'medium' },
      });
    },
    [addNode]
  );

  return {
    startDrawing,
    continueDrawing,
    endDrawing,
    clearDrawings,
    createStickyNote,
  };
}
