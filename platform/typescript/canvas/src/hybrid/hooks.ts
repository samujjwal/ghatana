/**
 * @ghatana/canvas Hybrid Canvas Hooks
 *
 * React hooks for interacting with the hybrid canvas.
 *
 * @doc.type module
 * @doc.purpose React hooks
 * @doc.layer core
 * @doc.pattern Hooks
 */

import { useCallback, useEffect, useMemo, useRef } from "react";
import { useAtom, useAtomValue, useSetAtom } from "jotai";
import type {
  CanvasElement,
  CanvasNode,
  CanvasEdge,
  ViewportState,
  SelectionState,
  Point,
  RenderingMode,
} from "./types";
import {
  elementsAtom,
  nodesAtom,
  edgesAtom,
  viewportAtom,
  selectionAtom,
  toolAtom,
  renderingModeAtom,
  gridAtom,
  pushHistoryAtom,
  undoAtom,
  redoAtom,
  canUndoAtom,
  canRedoAtom,
  visibleElementsAtom,
  selectedElementsAtom,
  selectedNodesAtom,
  selectedEdgesAtom,
} from "./state";
import {
  getHybridCanvasController,
  type HybridCanvasAPI,
} from "./hybrid-canvas-controller";

/**
 * Hook to get the canvas controller API
 */
export function useHybridCanvas(): HybridCanvasAPI {
  return useMemo(() => getHybridCanvasController(), []);
}

/**
 * Hook for canvas elements with CRUD operations
 */
export function useCanvasElements() {
  const [elements, setElements] = useAtom(elementsAtom);
  const visibleElements = useAtomValue(visibleElementsAtom);
  const selectedElements = useAtomValue(selectedElementsAtom);
  const canvas = useHybridCanvas();

  const addElement = useCallback(
    (element: Omit<CanvasElement, "id"> & { id?: string }) => {
      return canvas.addElement(element);
    },
    [canvas],
  );

  const updateElement = useCallback(
    (id: string, updates: Partial<CanvasElement>) => {
      canvas.updateElement(id, updates);
    },
    [canvas],
  );

  const deleteElement = useCallback(
    (id: string) => {
      canvas.deleteElement(id);
    },
    [canvas],
  );

  const deleteElements = useCallback(
    (ids: string[]) => {
      canvas.deleteElements(ids);
    },
    [canvas],
  );

  return {
    elements,
    visibleElements,
    selectedElements,
    setElements,
    addElement,
    updateElement,
    deleteElement,
    deleteElements,
  };
}

/**
 * Hook for canvas nodes with CRUD operations
 */
export function useCanvasNodes() {
  const [nodes, setNodes] = useAtom(nodesAtom);
  const selectedNodes = useAtomValue(selectedNodesAtom);
  const canvas = useHybridCanvas();

  const addNode = useCallback(
    (node: Omit<CanvasNode, "id"> & { id?: string }) => {
      return canvas.addNode(node);
    },
    [canvas],
  );

  const updateNode = useCallback(
    (id: string, updates: Partial<CanvasNode>) => {
      canvas.updateNode(id, updates);
    },
    [canvas],
  );

  const deleteNode = useCallback(
    (id: string) => {
      canvas.deleteNode(id);
    },
    [canvas],
  );

  const deleteNodes = useCallback(
    (ids: string[]) => {
      canvas.deleteNodes(ids);
    },
    [canvas],
  );

  return {
    nodes,
    selectedNodes,
    setNodes,
    addNode,
    updateNode,
    deleteNode,
    deleteNodes,
  };
}

/**
 * Hook for canvas edges with CRUD operations
 */
export function useCanvasEdges() {
  const [edges, setEdges] = useAtom(edgesAtom);
  const selectedEdges = useAtomValue(selectedEdgesAtom);
  const canvas = useHybridCanvas();

  const addEdge = useCallback(
    (edge: Omit<CanvasEdge, "id"> & { id?: string }) => {
      return canvas.addEdge(edge);
    },
    [canvas],
  );

  const updateEdge = useCallback(
    (id: string, updates: Partial<CanvasEdge>) => {
      canvas.updateEdge(id, updates);
    },
    [canvas],
  );

  const deleteEdge = useCallback(
    (id: string) => {
      canvas.deleteEdge(id);
    },
    [canvas],
  );

  const deleteEdges = useCallback(
    (ids: string[]) => {
      canvas.deleteEdges(ids);
    },
    [canvas],
  );

  return {
    edges,
    selectedEdges,
    setEdges,
    addEdge,
    updateEdge,
    deleteEdge,
    deleteEdges,
  };
}

/**
 * Hook for viewport control
 */
export function useCanvasViewport() {
  const [viewport, setViewport] = useAtom(viewportAtom);
  const canvas = useHybridCanvas();

  const pan = useCallback(
    (dx: number, dy: number) => {
      canvas.pan(dx, dy);
    },
    [canvas],
  );

  const zoom = useCallback(
    (factor: number, center?: Point) => {
      canvas.zoom(factor, center);
    },
    [canvas],
  );

  const zoomTo = useCallback(
    (level: number, center?: Point) => {
      canvas.zoomTo(level, center);
    },
    [canvas],
  );

  const fitToContent = useCallback(
    (padding?: number) => {
      canvas.fitToContent(padding);
    },
    [canvas],
  );

  const centerOn = useCallback(
    (point: Point) => {
      canvas.centerOn(point);
    },
    [canvas],
  );

  return {
    viewport,
    setViewport,
    pan,
    zoom,
    zoomTo,
    fitToContent,
    centerOn,
  };
}

/**
 * Hook for selection management
 */
export function useCanvasSelection() {
  const [selection, setSelection] = useAtom(selectionAtom);
  const selectedElements = useAtomValue(selectedElementsAtom);
  const selectedNodes = useAtomValue(selectedNodesAtom);
  const selectedEdges = useAtomValue(selectedEdgesAtom);
  const canvas = useHybridCanvas();

  const select = useCallback(
    (ids: { elements?: string[]; nodes?: string[]; edges?: string[] }) => {
      canvas.select(ids);
    },
    [canvas],
  );

  const addToSelection = useCallback(
    (ids: { elements?: string[]; nodes?: string[]; edges?: string[] }) => {
      canvas.addToSelection(ids);
    },
    [canvas],
  );

  const removeFromSelection = useCallback(
    (ids: { elements?: string[]; nodes?: string[]; edges?: string[] }) => {
      canvas.removeFromSelection(ids);
    },
    [canvas],
  );

  const clearSelection = useCallback(() => {
    canvas.clearSelection();
  }, [canvas]);

  const selectAll = useCallback(() => {
    canvas.selectAll();
  }, [canvas]);

  const hasSelection = useMemo(() => {
    return (
      selection.elementIds.length > 0 ||
      selection.nodeIds.length > 0 ||
      selection.edgeIds.length > 0
    );
  }, [selection]);

  return {
    selection,
    selectedElements,
    selectedNodes,
    selectedEdges,
    hasSelection,
    select,
    addToSelection,
    removeFromSelection,
    clearSelection,
    selectAll,
  };
}

/**
 * Hook for tool management
 */
export function useCanvasTool() {
  const [tool, setTool] = useAtom(toolAtom);

  return { tool, setTool };
}

/**
 * Hook for rendering mode
 */
export function useCanvasMode() {
  const [mode, setMode] = useAtom(renderingModeAtom);

  return { mode, setMode };
}

/**
 * Hook for grid settings
 */
export function useCanvasGrid() {
  const [grid, setGrid] = useAtom(gridAtom);
  const canvas = useHybridCanvas();

  const toggleGrid = useCallback(() => {
    canvas.toggleGrid();
  }, [canvas]);

  const toggleSnap = useCallback(() => {
    canvas.toggleSnap();
  }, [canvas]);

  return {
    grid,
    setGrid,
    toggleGrid,
    toggleSnap,
  };
}

/**
 * Hook for history (undo/redo)
 */
export function useCanvasHistory() {
  const canUndo = useAtomValue(canUndoAtom);
  const canRedo = useAtomValue(canRedoAtom);
  const undo = useSetAtom(undoAtom);
  const redo = useSetAtom(redoAtom);
  const pushHistory = useSetAtom(pushHistoryAtom);

  return {
    canUndo,
    canRedo,
    undo,
    redo,
    pushHistory,
  };
}

/**
 * Hook for keyboard shortcuts
 */
export function useCanvasKeyboardShortcuts(enabled = true) {
  const canvas = useHybridCanvas();
  const { undo, redo, canUndo, canRedo } = useCanvasHistory();
  const { clearSelection, selectAll } = useCanvasSelection();

  useEffect(() => {
    if (!enabled) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      // Ignore if typing in input
      if (
        e.target instanceof HTMLInputElement ||
        e.target instanceof HTMLTextAreaElement
      ) {
        return;
      }

      const isMod = e.metaKey || e.ctrlKey;

      // Undo: Cmd/Ctrl + Z
      if (isMod && e.key === "z" && !e.shiftKey && canUndo) {
        e.preventDefault();
        undo();
        return;
      }

      // Redo: Cmd/Ctrl + Shift + Z or Cmd/Ctrl + Y
      if ((isMod && e.shiftKey && e.key === "z") || (isMod && e.key === "y")) {
        if (canRedo) {
          e.preventDefault();
          redo();
        }
        return;
      }

      // Select all: Cmd/Ctrl + A
      if (isMod && e.key === "a") {
        e.preventDefault();
        selectAll();
        return;
      }

      // Delete: Delete or Backspace
      if (e.key === "Delete" || e.key === "Backspace") {
        e.preventDefault();
        canvas.deleteSelected();
        return;
      }

      // Escape: Clear selection
      if (e.key === "Escape") {
        clearSelection();
        return;
      }

      // Duplicate: Cmd/Ctrl + D
      if (isMod && e.key === "d") {
        e.preventDefault();
        canvas.duplicateSelected();
        return;
      }

      // Zoom in: Cmd/Ctrl + =
      if (isMod && (e.key === "=" || e.key === "+")) {
        e.preventDefault();
        canvas.zoom(1.2);
        return;
      }

      // Zoom out: Cmd/Ctrl + -
      if (isMod && e.key === "-") {
        e.preventDefault();
        canvas.zoom(0.8);
        return;
      }

      // Reset zoom: Cmd/Ctrl + 0
      if (isMod && e.key === "0") {
        e.preventDefault();
        canvas.zoomTo(1);
        return;
      }

      // Fit to content: Cmd/Ctrl + 1
      if (isMod && e.key === "1") {
        e.preventDefault();
        canvas.fitToContent();
        return;
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [
    enabled,
    canvas,
    undo,
    redo,
    canUndo,
    canRedo,
    clearSelection,
    selectAll,
  ]);
}

/**
 * Hook for drag and drop onto canvas
 */
export function useCanvasDrop(
  onDrop: (type: string, data: unknown, position: Point) => void,
) {
  const canvas = useHybridCanvas();
  const containerRef = useRef<HTMLElement | null>(null);

  const handleDragOver = useCallback((e: DragEvent) => {
    e.preventDefault();
    e.dataTransfer!.dropEffect = "copy";
  }, []);

  const handleDrop = useCallback(
    (e: DragEvent) => {
      e.preventDefault();

      const type = e.dataTransfer?.getData("application/ghatana-canvas-type");
      const dataStr = e.dataTransfer?.getData(
        "application/ghatana-canvas-data",
      );

      if (!type) return;

      const rect = containerRef.current?.getBoundingClientRect();
      if (!rect) return;

      const screenPoint = {
        x: e.clientX - rect.left,
        y: e.clientY - rect.top,
      };

      const canvasPoint = canvas.screenToCanvas(screenPoint);
      const snappedPoint = canvas.snapToGrid(canvasPoint);

      let data: unknown = null;
      if (dataStr) {
        try {
          data = JSON.parse(dataStr);
        } catch {
          // Ignore parse errors
        }
      }

      onDrop(type, data, snappedPoint);
    },
    [canvas, onDrop],
  );

  const setDropRef = useCallback(
    (element: HTMLElement | null) => {
      // Remove old listeners
      if (containerRef.current) {
        containerRef.current.removeEventListener("dragover", handleDragOver);
        containerRef.current.removeEventListener("drop", handleDrop);
      }

      containerRef.current = element;

      // Add new listeners
      if (element) {
        element.addEventListener("dragover", handleDragOver);
        element.addEventListener("drop", handleDrop);
      }
    },
    [handleDragOver, handleDrop],
  );

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (containerRef.current) {
        containerRef.current.removeEventListener("dragover", handleDragOver);
        containerRef.current.removeEventListener("drop", handleDrop);
      }
    };
  }, [handleDragOver, handleDrop]);

  return { setDropRef };
}
