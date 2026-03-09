import React from 'react';
import { useUndoRedo } from '@/hooks/useUndoRedo';

/**
 * Undo/Redo controls component.
 *
 * <p><b>Purpose</b><br>
 * Displays undo/redo buttons with keyboard shortcut hints.
 * Provides visual feedback for undo/redo availability.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { UndoRedoControls } from '@/components/UndoRedoControls';
 *
 * function WorkflowEditor() {
 *   return (
 *     <div>
 *       <UndoRedoControls />
 *     </div>
 *   );
 * }
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Undo/Redo buttons with disabled state
 * - Keyboard shortcut indicators
 * - History count display
 * - Tooltips on hover
 * - Responsive design
 *
 * @doc.type component
 * @doc.purpose Undo/Redo UI controls
 * @doc.layer frontend
 */
export function UndoRedoControls() {
  const { undo, redo, canUndo, canRedo, history, historyIndex } = useUndoRedo();
  const isMac = /Mac|iPhone|iPad|iPod/.test(navigator.platform);
  const undoShortcut = isMac ? '⌘Z' : 'Ctrl+Z';
  const redoShortcut = isMac ? '⌘⇧Z' : 'Ctrl+Y';

  return (
    <div className="flex items-center gap-2 px-3 py-2 border-r border-gray-200 dark:border-gray-700">
      {/* Undo Button */}
      <button
        onClick={undo}
        disabled={!canUndo}
        className={`
          flex items-center gap-1 px-3 py-1.5 rounded-md
          transition-colors duration-200
          ${
            canUndo
              ? 'bg-gray-100 hover:bg-gray-200 dark:bg-gray-700 dark:hover:bg-gray-600 text-gray-900 dark:text-gray-100 cursor-pointer'
              : 'bg-gray-50 dark:bg-gray-800 text-gray-400 dark:text-gray-600 cursor-not-allowed'
          }
        `}
        title={`Undo (${undoShortcut})`}
        aria-label="Undo"
      >
        <svg
          className="w-4 h-4"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
          />
        </svg>
        <span className="text-sm font-medium hidden sm:inline">Undo</span>
        <span className="text-xs text-gray-500 dark:text-gray-400 hidden md:inline">
          {undoShortcut}
        </span>
      </button>

      {/* Redo Button */}
      <button
        onClick={redo}
        disabled={!canRedo}
        className={`
          flex items-center gap-1 px-3 py-1.5 rounded-md
          transition-colors duration-200
          ${
            canRedo
              ? 'bg-gray-100 hover:bg-gray-200 dark:bg-gray-700 dark:hover:bg-gray-600 text-gray-900 dark:text-gray-100 cursor-pointer'
              : 'bg-gray-50 dark:bg-gray-800 text-gray-400 dark:text-gray-600 cursor-not-allowed'
          }
        `}
        title={`Redo (${redoShortcut})`}
        aria-label="Redo"
      >
        <svg
          className="w-4 h-4"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M15 19v-6a2 2 0 012-2h2a2 2 0 012 2v6a2 2 0 01-2 2h-2a2 2 0 01-2-2zm0 0V9a2 2 0 01-2-2H9a2 2 0 01-2 2v10m6 0a2 2 0 01-2 2H9a2 2 0 01-2-2m0 0V5a2 2 0 01-2-2h2a2 2 0 012 2v14a2 2 0 002 2h2a2 2 0 002-2z"
          />
        </svg>
        <span className="text-sm font-medium hidden sm:inline">Redo</span>
        <span className="text-xs text-gray-500 dark:text-gray-400 hidden md:inline">
          {redoShortcut}
        </span>
      </button>

      {/* History Counter */}
      <div className="flex items-center gap-1 px-2 py-1 ml-2 text-xs text-gray-600 dark:text-gray-400 bg-gray-50 dark:bg-gray-800 rounded">
        <span className="font-medium">{historyIndex + 1}</span>
        <span>/</span>
        <span className="font-medium">{history.length}</span>
      </div>
    </div>
  );
}

export default UndoRedoControls;
