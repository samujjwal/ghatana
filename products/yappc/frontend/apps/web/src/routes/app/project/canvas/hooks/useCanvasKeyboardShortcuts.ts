/**
 * @doc.type hook
 * @doc.purpose Handles basic keyboard shortcuts for canvas interactions
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useCallback, useEffect } from 'react';
import type { Node, Edge } from '@xyflow/react';
import type { SketchTool } from '@ghatana/yappc-canvas/sketch';
import type { CanvasElement, CanvasState } from '@/components/canvas/workspace/canvasAtoms';

interface UseCanvasKeyboardShortcutsOptions {
  nodes: Node[];
  edges: Edge[];
  setGlobalCanvas: (updater: (prev: CanvasState) => CanvasState) => void;
  setCommandPaletteOpen: (open: boolean) => void;
  setActiveSketchTool: (tool: SketchTool) => void;
}

/**
 * Hook to manage basic keyboard shortcuts for the canvas
 * Note: Complex undo/redo/copy/paste operations are still handled in CanvasScene
 */
export function useCanvasKeyboardShortcuts({
  nodes,
  edges,
  setGlobalCanvas,
  setCommandPaletteOpen,
  setActiveSketchTool,
}: UseCanvasKeyboardShortcutsOptions) {
  // Keyboard handlers for canvas element manipulation
  const handleKeyDown = useCallback((event: React.KeyboardEvent) => {
    const key = event.key.toLowerCase();

    // Command palette shortcut
    if ((event.metaKey || event.ctrlKey) && key === 'k') {
      event.preventDefault();
      setCommandPaletteOpen(true);
      return;
    }

    // Delete selected elements
    if (key === 'delete' || key === 'backspace') {
      event.preventDefault();
      const selectedNodes = nodes.filter(node => node.selected);
      const selectedEdges = edges.filter(edge => edge.selected);

      if (selectedNodes.length > 0 || selectedEdges.length > 0) {
        setGlobalCanvas((prev: CanvasState) => {
          const selectedNodeIds = selectedNodes.map(n => n.id);
          const selectedEdgeIds = selectedEdges.map(e => e.id);

          return {
            ...prev,
            elements: prev.elements.filter(
              (el: CanvasElement) => !selectedNodeIds.includes(el.id) && !selectedEdgeIds.includes(el.id)
            ),
            selectedElements: [],
          };
        });
      }
      return;
    }

    // Select all elements
    if ((event.metaKey || event.ctrlKey) && key === 'a') {
      event.preventDefault();
      const allElementIds = [...nodes.map(n => n.id), ...edges.map(e => e.id)];
      setGlobalCanvas((prev: CanvasState) => ({
        ...prev,
        selectedElements: allElementIds,
      }));
      return;
    }

    // Escape to deselect
    if (key === 'escape') {
      event.preventDefault();
      setGlobalCanvas((prev: CanvasState) => ({
        ...prev,
        selectedElements: [],
      }));
      setCommandPaletteOpen(false);
      return;
    }

    // Sketch tool shortcuts
    if (key === 'p') {
      setActiveSketchTool('pen');
    } else if (key === 'r') {
      setActiveSketchTool('rectangle');
    } else if (key === 'v') {
      setActiveSketchTool('select');
    }
  }, [nodes, edges, setGlobalCanvas, setCommandPaletteOpen, setActiveSketchTool]);

  // Global keyboard shortcuts for sketch tools
  useEffect(() => {
    const handleGlobalKeyDown = (event: KeyboardEvent) => {
      const key = event.key.toLowerCase();

      // Command palette shortcut
      if ((event.metaKey || event.ctrlKey) && key === 'k') {
        event.preventDefault();
        setCommandPaletteOpen(true);
        return;
      }

      // Sketch tool shortcuts
      if (key === 'p') {
        setActiveSketchTool('pen');
      } else if (key === 'r') {
        setActiveSketchTool('rectangle');
      } else if (key === 'v') {
        setActiveSketchTool('select');
      }
    };

    window.addEventListener('keydown', handleGlobalKeyDown);
    return () => window.removeEventListener('keydown', handleGlobalKeyDown);
  }, [setCommandPaletteOpen, setActiveSketchTool]);

  return { handleKeyDown };
}
