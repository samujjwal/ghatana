/**
 * @ghatana/yappc-ide - Bulk Operations Toolbar Component
 * 
 * Toolbar for bulk file operations with progress indicators.
 * 
 * @doc.type component
 * @doc.purpose Bulk operations UI for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback } from 'react';
import { useAdvancedFileOperations } from '../hooks/useAdvancedFileOperations';
import { InteractiveButton } from './MicroInteractions';
import type { BulkOperation } from '../hooks/useAdvancedFileOperations';

/**
 * Bulk Operations Toolbar Props
 */
export interface BulkOperationsToolbarProps {
  className?: string;
  onOperationComplete?: (operation: BulkOperation) => void;
  onOperationError?: (error: Error) => void;
}

/**
 * Bulk Operations Toolbar Component
 */
export const BulkOperationsToolbar: React.FC<BulkOperationsToolbarProps> = ({
  className = '',
  onOperationComplete,
  onOperationError,
}) => {
  const {
    selectedFiles,
    selectionCount,
    clearSelection,
    bulkRename,
    bulkMove,
    bulkDelete,
  } = useAdvancedFileOperations();

  const [showRenameDialog, setShowRenameDialog] = useState(false);
  const [showMoveDialog, setShowMoveDialog] = useState(false);
  const [renamePattern, setRenamePattern] = useState('');
  const [moveDestination, setMoveDestination] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);

  const handleBulkRename = useCallback(async () => {
    if (!renamePattern.trim()) return;

    setIsProcessing(true);
    try {
      // Build operation for reporting, but bulkRename expects a pattern string
      const operation: BulkOperation = {
        type: 'rename',
        fileIds: selectedFiles.map(f => f.id),
        options: { pattern: renamePattern },
      };

      await bulkRename(renamePattern);
      onOperationComplete?.(operation);
      setShowRenameDialog(false);
      setRenamePattern('');
      clearSelection();
    } catch (error) {
      onOperationError?.(error as Error);
    } finally {
      setIsProcessing(false);
    }
  }, [selectedFiles, renamePattern, bulkRename, onOperationComplete, onOperationError, clearSelection]);

  const handleBulkMove = useCallback(async () => {
    if (!moveDestination.trim()) return;

    setIsProcessing(true);
    try {
      const operation: BulkOperation = {
        type: 'move',
        fileIds: selectedFiles.map(f => f.id),
        destination: moveDestination,
      };

      await bulkMove(moveDestination);
      onOperationComplete?.(operation);
      setShowMoveDialog(false);
      setMoveDestination('');
      clearSelection();
    } catch (error) {
      onOperationError?.(error as Error);
    } finally {
      setIsProcessing(false);
    }
  }, [selectedFiles, moveDestination, bulkMove, onOperationComplete, onOperationError, clearSelection]);

  const handleBulkDelete = useCallback(async () => {
    const confirmed = window.confirm(`Are you sure you want to delete ${selectionCount} selected file(s)?`);
    if (!confirmed) return;

    setIsProcessing(true);
    try {
      const operation: BulkOperation = {
        type: 'delete',
        fileIds: selectedFiles.map(f => f.id),
      };

      await bulkDelete();
      onOperationComplete?.(operation);
      clearSelection();
    } catch (error) {
      onOperationError?.(error as Error);
    } finally {
      setIsProcessing(false);
    }
  }, [selectedFiles, selectionCount, bulkDelete, onOperationComplete, onOperationError, clearSelection]);

  if (selectionCount === 0) {
    return null;
  }

  return (
    <div className={`border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 p-2 ${className}`}>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
            {selectionCount} selected
          </span>
          <InteractiveButton
            variant="ghost"
            size="sm"
            onClick={clearSelection}
            className="text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
          >
            Clear
          </InteractiveButton>
        </div>

        <div className="flex items-center gap-1">
          <InteractiveButton
            variant="secondary"
            size="sm"
            onClick={() => setShowRenameDialog(true)}
            disabled={isProcessing}
          >
            Rename
          </InteractiveButton>

          <InteractiveButton
            variant="secondary"
            size="sm"
            onClick={() => setShowMoveDialog(true)}
            disabled={isProcessing}
          >
            Move
          </InteractiveButton>

          <InteractiveButton
            variant="secondary"
            size="sm"
            onClick={handleBulkDelete}
            disabled={isProcessing}
          >
            Delete
          </InteractiveButton>
        </div>
      </div>

      {/* Rename Dialog */}
      {showRenameDialog && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white dark:bg-gray-900 rounded-lg p-6 w-96 max-w-full">
            <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-gray-100">
              Bulk Rename {selectionCount} Files
            </h3>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  Rename Pattern
                </label>
                <input
                  type="text"
                  value={renamePattern}
                  onChange={(e) => setRenamePattern(e.target.value)}
                  placeholder="e.g., {name}_backup{ext}"
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
                />
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                  Use {'{name}'}, {'{ext}'}, {'{index}'} for placeholders
                </p>
              </div>
              <div className="flex justify-end gap-2">
                <InteractiveButton
                  variant="ghost"
                  onClick={() => {
                    setShowRenameDialog(false);
                    setRenamePattern('');
                  }}
                  disabled={isProcessing}
                >
                  Cancel
                </InteractiveButton>
                <InteractiveButton
                  variant="primary"
                  onClick={handleBulkRename}
                  disabled={isProcessing || !renamePattern.trim()}
                >
                  {isProcessing ? 'Renaming...' : 'Rename'}
                </InteractiveButton>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Move Dialog */}
      {showMoveDialog && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white dark:bg-gray-900 rounded-lg p-6 w-96 max-w-full">
            <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-gray-100">
              Move {selectionCount} Files
            </h3>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  Destination Path
                </label>
                <input
                  type="text"
                  value={moveDestination}
                  onChange={(e) => setMoveDestination(e.target.value)}
                  placeholder="/path/to/destination"
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
                />
              </div>
              <div className="flex justify-end gap-2">
                <InteractiveButton
                  variant="ghost"
                  onClick={() => {
                    setShowMoveDialog(false);
                    setMoveDestination('');
                  }}
                  disabled={isProcessing}
                >
                  Cancel
                </InteractiveButton>
                <InteractiveButton
                  variant="primary"
                  onClick={handleBulkMove}
                  disabled={isProcessing || !moveDestination.trim()}
                >
                  {isProcessing ? 'Moving...' : 'Move'}
                </InteractiveButton>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default BulkOperationsToolbar;
