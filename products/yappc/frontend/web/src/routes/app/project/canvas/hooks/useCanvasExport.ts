/**
 * @doc.type hook
 * @doc.purpose Manages canvas export functionality
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback } from 'react';
import type { CanvasState } from '@/components/canvas/workspace/canvasAtoms';

interface UseCanvasExportOptions {
  canvasState: CanvasState;
}

/**
 * Hook to manage canvas export functionality
 */
export function useCanvasExport({ canvasState }: UseCanvasExportOptions) {
  const [exportMenuOpen, setExportMenuOpen] = useState(false);

  const handleExportJson = useCallback(() => {
    try {
      const data = JSON.stringify(canvasState, null, 2);
      const blob = new Blob([data], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `canvas-${Date.now()}.json`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
      setExportMenuOpen(false);
    } catch (error) {
      console.error('Failed to export canvas JSON', error);
    }
  }, [canvasState]);

  return {
    exportMenuOpen,
    setExportMenuOpen,
    handleExportJson,
  };
}
