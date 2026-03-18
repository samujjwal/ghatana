/**
 * @doc.type hook
 * @doc.purpose Manages canvas auto-layout functionality
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback } from 'react';
import type { CanvasState, CanvasElement } from '@/components/canvas/workspace/canvasAtoms';

interface UseCanvasLayoutOptions {
  setGlobalCanvas: (updater: (prev: CanvasState) => CanvasState) => void;
}

/**
 * Hook to manage canvas layout operations
 */
export function useCanvasLayout({ setGlobalCanvas }: UseCanvasLayoutOptions) {
  const [layoutDialogOpen, setLayoutDialogOpen] = useState(false);

  const applyAutoLayout = useCallback(() => {
    setGlobalCanvas((prev: CanvasState) => {
      const layoutElements = prev.elements.map((element: CanvasElement, index: number) => {
        if (element.kind === 'component' || element.kind === 'node' || element.kind === undefined) {
          const row = Math.floor(index / 5);
          const column = index % 5;
          return {
            ...element,
            position: {
              x: 120 + column * 220,
              y: 120 + row * 160,
            },
          };
        }
        return element;
      });

      return {
        ...prev,
        elements: layoutElements,
      };
    });

    setLayoutDialogOpen(false);
  }, [setGlobalCanvas]);

  return {
    layoutDialogOpen,
    setLayoutDialogOpen,
    applyAutoLayout,
  };
}
