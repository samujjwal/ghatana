import { useAtom } from 'jotai';
import { useEffect, useCallback } from 'react';
import type { WorkflowDefinition } from '@/types/workflow.types';
import {
  workflowAtom,
  historyAtom,
  historyIndexAtom,
  undoAtom,
  redoAtom,
  saveToHistoryAtom,
} from '@/stores/workflow.store';
import {
  saveWorkflowState,
  saveHistory,
  saveHistoryIndex,
  loadWorkflowState,
  loadHistory,
  loadHistoryIndex,
  isStorageAvailable,
} from '@/lib/persistence';

/**
 * Hook for undo/redo functionality with persistence.
 *
 * <p><b>Purpose</b><br>
 * Provides simplified undo/redo API with automatic persistence to localStorage.
 * Handles keyboard shortcuts (Ctrl+Z for undo, Ctrl+Y for redo).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * function WorkflowEditor() {
 *   const { undo, redo, canUndo, canRedo, history, historyIndex } = useUndoRedo();
 *
 *   return (
 *     <div>
 *       <button onClick={undo} disabled={!canUndo}>Undo</button>
 *       <button onClick={redo} disabled={!canRedo}>Redo</button>
 *       <p>History: {historyIndex + 1} / {history.length}</p>
 *     </div>
 *   );
 * }
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Automatic state persistence to localStorage
 * - Keyboard shortcut handling (Ctrl+Z, Ctrl+Y)
 * - State recovery on mount
 * - Undo/redo button state tracking
 * - History size limits
 *
 * @returns object with undo/redo methods and state
 * @doc.type hook
 * @doc.purpose Undo/redo functionality with persistence
 * @doc.layer frontend
 */
export function useUndoRedo() {
  const [workflow, setWorkflow] = useAtom(workflowAtom);
  const [history, setHistory] = useAtom(historyAtom);
  const [historyIndex, setHistoryIndex] = useAtom(historyIndexAtom);
  const [, undo] = useAtom(undoAtom);
  const [, redo] = useAtom(redoAtom);
  const [, saveToHistory] = useAtom(saveToHistoryAtom);

  // Calculate undo/redo availability
  const canUndo = historyIndex > 0;
  const canRedo = historyIndex < history.length - 1;

  /**
   * Performs undo operation.
   *
   * <p>GIVEN: Current workflow state
   * WHEN: undo() is called
   * THEN: Reverts to previous state and persists
   */
  const handleUndo = useCallback(() => {
    if (canUndo) {
      // undo atom expects no args; call directly
      undo();
      // Persist after undo
      setTimeout(() => {
        const newIndex = historyIndex - 1;
        saveHistoryIndex(newIndex);
      }, 0);
    }
  }, [canUndo, undo, historyIndex]);

  /**
   * Performs redo operation.
   *
   * <p>GIVEN: Previous workflow state
   * WHEN: redo() is called
   * THEN: Restores next state and persists
   */
  const handleRedo = useCallback(() => {
    if (canRedo) {
      redo();
      // Persist after redo
      setTimeout(() => {
        const newIndex = historyIndex + 1;
        saveHistoryIndex(newIndex);
      }, 0);
    }
  }, [canRedo, redo, historyIndex]);

  /**
   * Clears all history.
   */
  const clearHistory = useCallback(() => {
    setHistory([]);
    setHistoryIndex(-1);
    setWorkflow(null);
  }, [setHistory, setHistoryIndex, setWorkflow]);

  /**
   * Handles keyboard shortcuts for undo/redo.
   *
   * <p>Shortcuts:
   * - Ctrl+Z (Windows/Linux) or Cmd+Z (Mac): Undo
   * - Ctrl+Y (Windows/Linux) or Cmd+Shift+Z (Mac): Redo
   */
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const isMac = /Mac|iPhone|iPad|iPod/.test(navigator.platform);
      const modifier = isMac ? event.metaKey : event.ctrlKey;

      if (modifier && event.key === 'z' && !event.shiftKey) {
        event.preventDefault();
        handleUndo();
      } else if ((modifier && event.key === 'y') || (isMac && modifier && event.shiftKey && event.key === 'z')) {
        event.preventDefault();
        handleRedo();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [handleUndo, handleRedo]);

  /**
   * Persists workflow state and history to localStorage.
   *
   * <p>Triggered whenever workflow or history changes.
   */
  useEffect(() => {
    if (!isStorageAvailable()) {
      console.warn('localStorage is not available');
      return;
    }

    if (workflow) {
      saveWorkflowState(workflow);
    }

    if (history.length > 0) {
      saveHistory(history);
      saveHistoryIndex(historyIndex);
    }
  }, [workflow, history, historyIndex]);

  /**
   * Recovers workflow state from localStorage on mount.
   *
   * <p>GIVEN: Persisted workflow state in localStorage
   * WHEN: Component mounts
   * THEN: Restores workflow and history
   */
  useEffect(() => {
    if (!isStorageAvailable()) {
      return;
    }

    const savedWorkflow = loadWorkflowState();
    const savedHistory = loadHistory();
    const savedIndex = loadHistoryIndex();

    if (savedWorkflow) {
      setWorkflow(savedWorkflow);
    }

    if (savedHistory.length > 0) {
      setHistory(savedHistory);
      setHistoryIndex(Math.max(0, savedIndex));
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // Only run on mount

  return {
    undo: handleUndo,
    redo: handleRedo,
    canUndo,
    canRedo,
    clearHistory,
    history,
    historyIndex,
    historySize: history.length,
  };
}
