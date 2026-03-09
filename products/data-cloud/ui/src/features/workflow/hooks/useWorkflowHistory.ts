import { useAtom } from 'jotai';
import type { WorkflowState } from '../stores/workflow.store';
import {
  workflowAtom,
  workflowHistoryAtom,
  historyIndexAtom,
} from '../stores/workflow.store';

/**
 * Hook for workflow undo/redo with history management.
 *
 * <p><b>Purpose</b><br>
 * Manages workflow state history with bounded stacks, undo/redo, and snapshot discipline.
 *
 * <p><b>Features</b><br>
 * - Bounded history (max 100 snapshots)
 * - Undo/redo navigation
 * - Automatic history pruning on new changes
 * - Snapshot discipline (immutable updates)
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { undo, redo, canUndo, canRedo, pushSnapshot } = useWorkflowHistory();
 * ```
 *
 * @doc.type hook
 * @doc.purpose Workflow undo/redo management
 * @doc.layer frontend
 * @doc.pattern Custom Hook
 */
export function useWorkflowHistory() {
  const [workflow, setWorkflow] = useAtom(workflowAtom);
  const [history, setHistory] = useAtom(workflowHistoryAtom);
  const [historyIndex, setHistoryIndex] = useAtom(historyIndexAtom);

  const MAX_HISTORY = 100;

  /**
   * Pushes current workflow state to history.
   */
  const pushSnapshot = () => {
    const newHistory = history.slice(0, historyIndex + 1);
    newHistory.push(JSON.parse(JSON.stringify(workflow)));

    if (newHistory.length > MAX_HISTORY) {
      newHistory.shift();
    } else {
      setHistoryIndex(historyIndex + 1);
    }

    setHistory(newHistory);
  };

  /**
   * Undoes to previous state.
   */
  const undo = () => {
    if (historyIndex > 0) {
      const newIndex = historyIndex - 1;
      setWorkflow(JSON.parse(JSON.stringify(history[newIndex])));
      setHistoryIndex(newIndex);
    }
  };

  /**
   * Redoes to next state.
   */
  const redo = () => {
    if (historyIndex < history.length - 1) {
      const newIndex = historyIndex + 1;
      setWorkflow(JSON.parse(JSON.stringify(history[newIndex])));
      setHistoryIndex(newIndex);
    }
  };

  /**
   * Clears history.
   */
  const clearHistory = () => {
    setHistory([]);
    setHistoryIndex(0);
  };

  return {
    undo,
    redo,
    pushSnapshot,
    clearHistory,
    canUndo: historyIndex > 0,
    canRedo: historyIndex < history.length - 1,
    historySize: history.length,
  };
}
