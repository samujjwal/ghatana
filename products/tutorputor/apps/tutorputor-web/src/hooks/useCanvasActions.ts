import { useAtom } from 'jotai';
import { canvasNodesAtom, canvasEdgesAtom, selectedNodesAtom, canvasHistoryAtom } from '../state/canvasAtoms';
import { useCallback } from 'react';
import type { Node, Edge } from '@xyflow/react';

/**
 * useCanvasActions - Custom hook for canvas actions
 */
export function useCanvasActions() {
  const [nodes, setNodes] = useAtom(canvasNodesAtom);
  const [edges, setEdges] = useAtom(canvasEdgesAtom);
  const [selectedNodes, setSelectedNodes] = useAtom(selectedNodesAtom);
  const [history, setHistory] = useAtom(canvasHistoryAtom);

  const selectNode = useCallback((nodeId: string) => {
    setSelectedNodes((prev) => 
      prev.includes(nodeId) ? prev : [...prev, nodeId]
    );
  }, [setSelectedNodes]);

  const deselectNode = useCallback((nodeId: string) => {
    setSelectedNodes((prev) => prev.filter((id) => id !== nodeId));
  }, [setSelectedNodes]);

  const clearSelection = useCallback(() => {
    setSelectedNodes([]);
  }, [setSelectedNodes]);

  const addNode = useCallback((node: Node) => {
    setNodes((prev) => [...prev, node]);
  }, [setNodes]);

  const removeNode = useCallback((nodeId: string) => {
    setNodes((prev) => prev.filter((n) => n.id !== nodeId));
    setEdges((prev) => prev.filter((e) => e.source !== nodeId && e.target !== nodeId));
  }, [setNodes, setEdges]);

  const updateNode = useCallback((nodeId: string, updates: Partial<Node>) => {
    setNodes((prev) =>
      prev.map((n) => (n.id === nodeId ? { ...n, ...updates } : n))
    );
  }, [setNodes]);

  const undo = useCallback(() => {
    setHistory((prev) => {
      if (prev.past.length === 0) return prev;
      
      const newPast = prev.past.slice(0, -1);
      const newPresent = prev.past[prev.past.length - 1];
      
      return {
        past: newPast,
        present: newPresent,
        future: [prev.present, ...prev.future],
      };
    });
  }, [setHistory]);

  const redo = useCallback(() => {
    setHistory((prev) => {
      if (prev.future.length === 0) return prev;
      
      const [newPresent, ...newFuture] = prev.future;
      
      return {
        past: [...prev.past, prev.present],
        present: newPresent,
        future: newFuture,
      };
    });
  }, [setHistory]);

  return {
    selectNode,
    deselectNode,
    clearSelection,
    addNode,
    removeNode,
    updateNode,
    undo,
    redo,
    selectedNodes,
    nodes,
    edges,
  };
}
