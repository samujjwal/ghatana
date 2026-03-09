/**
 * useCanvasExport Hook
 *
 * Manages export/import handlers for the canvas including
 * JSON, SVG, PNG export and JSON import.
 *
 * @doc.type hook
 * @doc.purpose Canvas export and import handlers
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useCallback } from 'react';

interface UseCanvasExportOptions {
  canvas: {
    downloadJSON: (filename: string) => void;
    downloadSVG: (filename: string) => void;
    exportToPNG: (element: HTMLElement) => Promise<string>;
    importFromJSON: (json: string) => void;
  };
  projectId: string | undefined;
  canvasRef: React.RefObject<HTMLDivElement | null>;
  setExportMenuAnchor: (v: null) => void;
}

export function useCanvasExport({
  canvas,
  projectId,
  canvasRef,
  setExportMenuAnchor,
}: UseCanvasExportOptions) {
  const handleExportJSON = useCallback(() => {
    canvas.downloadJSON(`canvas-${projectId || 'export'}-${Date.now()}.json`);
    setExportMenuAnchor(null);
  }, [canvas, projectId, setExportMenuAnchor]);

  const handleExportSVG = useCallback(() => {
    canvas.downloadSVG(`canvas-${projectId || 'export'}-${Date.now()}.svg`);
    setExportMenuAnchor(null);
  }, [canvas, projectId, setExportMenuAnchor]);

  const handleExportPNG = useCallback(async () => {
    if (canvasRef.current) {
      try {
        const dataURL = await canvas.exportToPNG(canvasRef.current);
        const link = document.createElement('a');
        link.download = `canvas-${projectId || 'export'}-${Date.now()}.png`;
        link.href = dataURL;
        link.click();
      } catch (error) {
        console.error('PNG export failed:', error);
      }
    }
    setExportMenuAnchor(null);
  }, [canvas, projectId, canvasRef, setExportMenuAnchor]);

  const handleImportJSON = useCallback(() => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json';
    input.onchange = (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (file) {
        const reader = new FileReader();
        reader.onload = (event) => {
          try {
            const json = event.target?.result as string;
            canvas.importFromJSON(json);
          } catch (error) {
            console.error('Import failed:', error);
          }
        };
        reader.readAsText(file);
      }
    };
    input.click();
    setExportMenuAnchor(null);
  }, [canvas, setExportMenuAnchor]);

  return {
    handleExportJSON,
    handleExportSVG,
    handleExportPNG,
    handleImportJSON,
  };
}
