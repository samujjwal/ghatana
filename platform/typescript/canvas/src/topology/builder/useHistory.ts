/**
 * useHistory Hook - Undo/Redo functionality
 * 
 * @doc.type hook
 * @doc.purpose Manage diagram history for undo/redo
 * @doc.layer shared
 */

import { useState, useCallback, useRef, useEffect } from 'react';
import type { Node, Edge } from '@xyflow/react';
import type { DiagramSnapshot, HistoryManager } from './types';

interface UseHistoryOptions {
  maxHistorySize?: number;
  debounceMs?: number;
}

interface UseHistoryReturn {
  canUndo: boolean;
  canRedo: boolean;
  undo: () => { nodes: Node[]; edges: Edge[] } | null;
  redo: () => { nodes: Node[]; edges: Edge[] } | null;
  pushHistory: (nodes: Node[], edges: Edge[]) => void;
  clearHistory: () => void;
}

const MAX_HISTORY_SIZE = 50;
const DEBOUNCE_MS = 300;

/**
 * Hook for managing diagram history with undo/redo
 */
export function useHistory(options: UseHistoryOptions = {}): UseHistoryReturn {
  const { maxHistorySize = MAX_HISTORY_SIZE, debounceMs = DEBOUNCE_MS } = options;
  
  const [history, setHistory] = useState<HistoryManager>({
    past: [],
    present: null,
    future: [],
  });

  const debounceTimer = useRef<NodeJS.Timeout | null>(null);
  const pendingSnapshot = useRef<DiagramSnapshot | null>(null);

  // Flush pending snapshot on unmount
  useEffect(() => {
    return () => {
      if (debounceTimer.current) {
        clearTimeout(debounceTimer.current);
      }
    };
  }, []);

  const pushHistory = useCallback((nodes: Node[], edges: Edge[]) => {
    const snapshot: DiagramSnapshot = {
      nodes: JSON.parse(JSON.stringify(nodes)),
      edges: JSON.parse(JSON.stringify(edges)),
      timestamp: Date.now(),
    };

    pendingSnapshot.current = snapshot;

    // Debounce history updates
    if (debounceTimer.current) {
      clearTimeout(debounceTimer.current);
    }

    debounceTimer.current = setTimeout(() => {
      if (pendingSnapshot.current) {
        setHistory((prev) => {
          const newPast = prev.present
            ? [...prev.past, prev.present].slice(-maxHistorySize)
            : prev.past;

          return {
            past: newPast,
            present: pendingSnapshot.current,
            future: [], // Clear future on new action
          };
        });
        pendingSnapshot.current = null;
      }
    }, debounceMs);
  }, [maxHistorySize, debounceMs]);

  const undo = useCallback((): { nodes: Node[]; edges: Edge[] } | null => {
    if (history.past.length === 0) return null;

    const previous = history.past[history.past.length - 1];
    const newPast = history.past.slice(0, -1);

    setHistory({
      past: newPast,
      present: previous,
      future: history.present ? [history.present, ...history.future] : history.future,
    });

    return {
      nodes: JSON.parse(JSON.stringify(previous.nodes)),
      edges: JSON.parse(JSON.stringify(previous.edges)),
    };
  }, [history]);

  const redo = useCallback((): { nodes: Node[]; edges: Edge[] } | null => {
    if (history.future.length === 0) return null;

    const next = history.future[0];
    const newFuture = history.future.slice(1);

    setHistory({
      past: history.present ? [...history.past, history.present] : history.past,
      present: next,
      future: newFuture,
    });

    return {
      nodes: JSON.parse(JSON.stringify(next.nodes)),
      edges: JSON.parse(JSON.stringify(next.edges)),
    };
  }, [history]);

  const clearHistory = useCallback(() => {
    setHistory({
      past: [],
      present: null,
      future: [],
    });
    pendingSnapshot.current = null;
    if (debounceTimer.current) {
      clearTimeout(debounceTimer.current);
    }
  }, []);

  return {
    canUndo: history.past.length > 0,
    canRedo: history.future.length > 0,
    undo,
    redo,
    pushHistory,
    clearHistory,
  };
}
