/**
 * @doc.type hook
 * @doc.purpose Manages canvas auto-layout functionality
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback } from 'react';
import type { CanvasState, CanvasElement } from '@/components/canvas/workspace/canvasAtoms';

export interface AutoLayoutMove {
  readonly elementId: string;
  readonly from: CanvasElement['position'];
  readonly to: CanvasElement['position'];
}

export interface AutoLayoutPreview {
  readonly before: CanvasElement[];
  readonly after: CanvasElement[];
  readonly moves: AutoLayoutMove[];
}

export interface AutoLayoutAuditEvent {
  readonly outcome: 'previewed' | 'applied' | 'undone' | 'redone';
  readonly movedElementCount: number;
}

interface UseCanvasLayoutOptions {
  canvasState: CanvasState;
  setGlobalCanvas: (updater: (prev: CanvasState) => CanvasState) => void;
  recordLayoutAudit?: (event: AutoLayoutAuditEvent) => void | Promise<void>;
}

interface AutoLayoutHistoryEntry {
  readonly before: CanvasElement[];
  readonly after: CanvasElement[];
  readonly movedElementCount: number;
}

function shouldLayoutElement(element: CanvasElement): boolean {
  return element.kind === 'component' || element.kind === 'node' || element.kind === undefined;
}

export function createAutoLayoutPreview(elements: CanvasElement[]): AutoLayoutPreview {
  const moves: AutoLayoutMove[] = [];
  const after = elements.map((element, index) => {
    if (!shouldLayoutElement(element)) {
      return element;
    }

    const row = Math.floor(index / 5);
    const column = index % 5;
    const nextPosition = {
      x: 120 + column * 220,
      y: 120 + row * 160,
    };

    if (
      element.position.x !== nextPosition.x ||
      element.position.y !== nextPosition.y
    ) {
      moves.push({
        elementId: element.id,
        from: element.position,
        to: nextPosition,
      });
    }

    return {
      ...element,
      position: nextPosition,
    };
  });

  return {
    before: elements,
    after,
    moves,
  };
}

/**
 * Hook to manage canvas layout operations
 */
export function useCanvasLayout({
  canvasState,
  setGlobalCanvas,
  recordLayoutAudit,
}: UseCanvasLayoutOptions) {
  const [layoutDialogOpen, setLayoutDialogOpen] = useState(false);
  const [layoutPreview, setLayoutPreview] = useState<AutoLayoutPreview | null>(null);
  const [layoutHistory, setLayoutHistory] = useState<AutoLayoutHistoryEntry[]>([]);
  const [layoutHistoryIndex, setLayoutHistoryIndex] = useState(-1);

  const recordAudit = useCallback(
    (event: AutoLayoutAuditEvent): void => {
      if (!recordLayoutAudit) {
        return;
      }

      void Promise.resolve(recordLayoutAudit(event)).catch((error: unknown) => {
        console.error('Auto-layout audit failed:', error);
      });
    },
    [recordLayoutAudit]
  );

  const previewAutoLayout = useCallback((): AutoLayoutPreview => {
    const preview = createAutoLayoutPreview(canvasState.elements ?? []);
    setLayoutPreview(preview);
    recordAudit({
      outcome: 'previewed',
      movedElementCount: preview.moves.length,
    });
    return preview;
  }, [canvasState.elements, recordAudit]);

  const applyAutoLayout = useCallback(() => {
    const preview = layoutPreview ?? createAutoLayoutPreview(canvasState.elements ?? []);

    setGlobalCanvas((prev: CanvasState) => {
      return {
        ...prev,
        elements: preview.after,
      };
    });

    setLayoutHistory((prev) => {
      const next = [...prev.slice(0, layoutHistoryIndex + 1), {
        before: preview.before,
        after: preview.after,
        movedElementCount: preview.moves.length,
      }];
      const capped = next.slice(-20);
      setLayoutHistoryIndex(capped.length - 1);
      return capped;
    });
    setLayoutPreview(null);
    setLayoutDialogOpen(false);
    recordAudit({
      outcome: 'applied',
      movedElementCount: preview.moves.length,
    });
  }, [canvasState.elements, layoutHistoryIndex, layoutPreview, recordAudit, setGlobalCanvas]);

  const undoAutoLayout = useCallback((): boolean => {
    const entry = layoutHistory[layoutHistoryIndex];
    if (!entry) {
      return false;
    }

    setGlobalCanvas((prev: CanvasState) => ({
      ...prev,
      elements: entry.before,
    }));
    setLayoutHistoryIndex((prev) => prev - 1);
    recordAudit({
      outcome: 'undone',
      movedElementCount: entry.movedElementCount,
    });
    return true;
  }, [layoutHistory, layoutHistoryIndex, recordAudit, setGlobalCanvas]);

  const redoAutoLayout = useCallback((): boolean => {
    const entry = layoutHistory[layoutHistoryIndex + 1];
    if (!entry) {
      return false;
    }

    setGlobalCanvas((prev: CanvasState) => ({
      ...prev,
      elements: entry.after,
    }));
    setLayoutHistoryIndex((prev) => prev + 1);
    recordAudit({
      outcome: 'redone',
      movedElementCount: entry.movedElementCount,
    });
    return true;
  }, [layoutHistory, layoutHistoryIndex, recordAudit, setGlobalCanvas]);

  const closeLayoutDialog = useCallback((): void => {
    setLayoutDialogOpen(false);
    setLayoutPreview(null);
  }, []);

  return {
    layoutDialogOpen,
    setLayoutDialogOpen,
    closeLayoutDialog,
    layoutPreview,
    previewAutoLayout,
    applyAutoLayout,
    undoAutoLayout,
    redoAutoLayout,
    canUndoAutoLayout: layoutHistoryIndex >= 0,
    canRedoAutoLayout: layoutHistoryIndex < layoutHistory.length - 1,
  };
}
