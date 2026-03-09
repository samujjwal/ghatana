/**
 * Workflow toolbar component.
 *
 * <p><b>Purpose</b><br>
 * Provides workflow controls (save, execute, undo, redo, zoom).
 * Displays workflow status and quick actions.
 *
 * <p><b>Architecture</b><br>
 * - Workflow operations
 * - State management integration
 * - Status display
 * - Keyboard shortcuts
 *
 * @doc.type component
 * @doc.purpose Workflow editor toolbar
 * @doc.layer frontend
 * @doc.pattern React Component
 */

import React, { useCallback, useState } from 'react';
import { useAtom, useSetAtom } from 'jotai';
import {
  workflowAtom,
  canUndoAtom,
  canRedoAtom,
  undoAtom,
  redoAtom,
} from '../stores/workflow.store';
import { useWorkflow } from '../hooks/useWorkflow';
import { useWorkflowExecution } from '../hooks/useWorkflowExecution';

/**
 * WorkflowToolbar component props.
 *
 * @doc.type interface
 */
export interface WorkflowToolbarProps {
  workflowId?: string;
  collectionId?: string;
  onExecutionStart?: (executionId: string) => void;
  readOnly?: boolean;
}

/**
 * WorkflowToolbar component.
 *
 * Provides workflow controls and status display.
 *
 * @param props component props
 * @returns JSX element
 *
 * @doc.type function
 */
export const WorkflowToolbar: React.FC<WorkflowToolbarProps> = ({
  workflowId,
  collectionId,
  onExecutionStart,
  readOnly = false,
}) => {
  const [workflow] = useAtom(workflowAtom);
  const [canUndo] = useAtom(canUndoAtom);
  const [canRedo] = useAtom(canRedoAtom);
  const undo = useSetAtom(undoAtom);
  const redo = useSetAtom(redoAtom);

  const { saveWorkflow, loading: saving, error: saveError } = useWorkflow();
  const { executeWorkflow, loading: executing, error: executeError } = useWorkflowExecution();

  const [showSaveConfirm, setShowSaveConfirm] = useState(false);
  const [showExecuteConfirm, setShowExecuteConfirm] = useState(false);
  void collectionId;

  /**
   * Handles save.
   */
  const handleSave = useCallback(async () => {
    try {
      await saveWorkflow();
      setShowSaveConfirm(false);
    } catch (error) {
      console.error('Save failed:', error);
    }
  }, [saveWorkflow]);

  /**
   * Handles execute.
   */
  const handleExecute = useCallback(async () => {
    if (!workflowId) {
      console.error('No workflow ID');
      return;
    }

    try {
      const executionId = await executeWorkflow(workflowId);
      onExecutionStart?.(executionId);
      setShowExecuteConfirm(false);
    } catch (error) {
      console.error('Execution failed:', error);
    }
  }, [workflowId, executeWorkflow, onExecutionStart]);

  /**
   * Handles keyboard shortcuts.
   */
  React.useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        handleSave();
      }
      if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) {
        e.preventDefault();
        undo();
      }
      if ((e.ctrlKey || e.metaKey) && (e.key === 'y' || (e.key === 'z' && e.shiftKey))) {
        e.preventDefault();
        redo();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [handleSave, undo, redo]);

  return (
    <div className="flex items-center justify-between px-4 py-3 bg-white border-b border-gray-200">
      {/* Left: Workflow info */}
      <div className="flex items-center gap-4">
        <div>
          <h2 className="font-semibold text-gray-900">{workflow.name || 'Untitled'}</h2>
          <p className="text-xs text-gray-500">
            {workflow.nodes.length} nodes • {workflow.edges.length} edges
          </p>
        </div>
      </div>

      {/* Center: Status */}
      <div className="flex items-center gap-2">
        {workflow.isDirty && (
          <span className="text-xs text-amber-600 font-medium">Unsaved changes</span>
        )}
        {saveError && <span className="text-xs text-red-600">{saveError}</span>}
        {executeError && <span className="text-xs text-red-600">{executeError}</span>}
      </div>

      {/* Right: Controls */}
      <div className="flex items-center gap-2">
        {/* Undo/Redo */}
        <button
          onClick={() => undo()}
          disabled={!canUndo || readOnly}
          title="Undo (Ctrl+Z)"
          className="p-2 text-gray-600 hover:bg-gray-100 rounded disabled:opacity-50 disabled:cursor-not-allowed"
        >
          ↶
        </button>
        <button
          onClick={() => redo()}
          disabled={!canRedo || readOnly}
          title="Redo (Ctrl+Y)"
          className="p-2 text-gray-600 hover:bg-gray-100 rounded disabled:opacity-50 disabled:cursor-not-allowed"
        >
          ↷
        </button>

        <div className="w-px h-6 bg-gray-200" />

        {/* Save */}
        <button
          onClick={() => setShowSaveConfirm(true)}
          disabled={!workflow.isDirty || saving || readOnly}
          title="Save (Ctrl+S)"
          className="px-3 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {saving ? 'Saving...' : 'Save'}
        </button>

        {/* Execute */}
        <button
          onClick={() => setShowExecuteConfirm(true)}
          disabled={!workflowId || executing || readOnly}
          title="Execute workflow"
          className="px-3 py-2 text-sm font-medium text-white bg-blue-500 hover:bg-blue-600 rounded disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {executing ? 'Executing...' : 'Execute'}
        </button>
      </div>

      {/* Save Confirmation Dialog */}
      {showSaveConfirm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 max-w-sm">
            <h3 className="font-semibold text-gray-900 mb-2">Save Workflow?</h3>
            <p className="text-sm text-gray-600 mb-4">
              Save changes to "{workflow.name || 'Untitled'}"?
            </p>
            <div className="flex gap-2 justify-end">
              <button
                onClick={() => setShowSaveConfirm(false)}
                className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded"
              >
                Cancel
              </button>
              <button
                onClick={handleSave}
                className="px-4 py-2 text-sm font-medium text-white bg-blue-500 hover:bg-blue-600 rounded"
              >
                Save
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Execute Confirmation Dialog */}
      {showExecuteConfirm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 max-w-sm">
            <h3 className="font-semibold text-gray-900 mb-2">Execute Workflow?</h3>
            <p className="text-sm text-gray-600 mb-4">
              Execute "{workflow.name || 'Untitled'}"?
            </p>
            <div className="flex gap-2 justify-end">
              <button
                onClick={() => setShowExecuteConfirm(false)}
                className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded"
              >
                Cancel
              </button>
              <button
                onClick={handleExecute}
                className="px-4 py-2 text-sm font-medium text-white bg-blue-500 hover:bg-blue-600 rounded"
              >
                Execute
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default WorkflowToolbar;
