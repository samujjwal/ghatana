/**
 * Synchronized UI Builder Component
 * 
 * Complete UI builder with bidirectional synchronization between
 * visual editor and Monaco code editor, including conflict resolution
 * and real-time collaboration support.
 * 
 * @doc.type component
 * @doc.purpose Synchronized visual and code editing
 * @doc.layer product
 * @doc.pattern Advanced Component
 */

import React, { useCallback, useEffect, useState } from 'react';
import type * as Y from 'yjs';

import { IntegratedUIBuilder } from './IntegratedUIBuilder';
import { useUIBuilderSync } from '../hooks/useBidirectionalSync';
import type { UIComponent } from './IntegratedUIBuilder';

/**
 * Synchronized UI Builder Props
 */
export interface SynchronizedUIBuilderProps {
  userId: string;
  userName: string;
  workspaceRoot: string;
  ydoc: Y.Doc;
  enableCollaboration: boolean;
  enablePreview: boolean;
  initialComponents?: UIComponent[];
  onComponentsChange?: (components: UIComponent[]) => void;
  onCodeChange?: (code: string) => void;
  onSyncStatusChange?: (status: 'syncing' | 'synced' | 'conflict') => void;
}

/**
 * Synchronized UI Builder Component
 */
export const SynchronizedUIBuilder: React.FC<SynchronizedUIBuilderProps> = ({
  userId,
  userName,
  workspaceRoot,
  ydoc,
  enableCollaboration,
  enablePreview,
  initialComponents = [],
  onComponentsChange,
  onCodeChange,
  onSyncStatusChange,
}) => {
  const [components, setComponents] = useState<UIComponent[]>(initialComponents);
  const [code, setCode] = useState('');
  const [syncStatus, setSyncStatus] = useState<'syncing' | 'synced' | 'conflict'>('synced');

  // Setup bidirectional sync
  const sync = useUIBuilderSync({
    debounceMs: 300,
    enableConflictDetection: true,
    enableHistory: true,
    maxHistorySize: 100,
    autoResolveConflicts: !enableCollaboration, // Manual resolution in collaborative mode
  });

  /**
   * Handle components change from visual editor
   */
  const handleComponentsChange = useCallback(
    (newComponents: UIComponent[]) => {
      setComponents(newComponents);
      onComponentsChange?.(newComponents);

      // Sync to code
      setSyncStatus('syncing');
      sync.syncComponentTree(newComponents);
    },
    [sync, onComponentsChange]
  );

  /**
   * Handle code change from editor
   */
  const handleCodeChange = useCallback(
    (newCode: string) => {
      setCode(newCode);
      onCodeChange?.(newCode);

      // Sync to visual
      setSyncStatus('syncing');
      sync.syncCodeChanges(newCode);
    },
    [sync, onCodeChange]
  );

  /**
   * Monitor sync status
   */
  useEffect(() => {
    if (sync.conflictCount > 0) {
      setSyncStatus('conflict');
      onSyncStatusChange?.('conflict');
    } else if (sync.issyncing) {
      setSyncStatus('syncing');
      onSyncStatusChange?.('syncing');
    } else {
      setSyncStatus('synced');
      onSyncStatusChange?.('synced');
    }
  }, [sync.conflictCount, sync.issyncing, onSyncStatusChange]);

  /**
   * Handle conflict resolution
   */
  const handleResolveConflict = useCallback(
    (conflictId: string, preferredSource: 'visual' | 'code') => {
      sync.resolveConflict(conflictId, preferredSource);
      
      if (preferredSource === 'visual') {
        // Apply visual changes to code
        sync.syncComponentTree(components);
      } else {
        // Apply code changes to visual
        sync.syncCodeChanges(code);
      }
    },
    [sync, components, code]
  );

  /**
   * Render conflict resolution UI if needed
   */
  const renderConflictUI = () => {
    if (syncStatus !== 'conflict' || sync.syncHistory.length === 0) {
      return null;
    }

    const lastConflict = sync.syncHistory.find((event) => event.type === 'conflict');
    if (!lastConflict) return null;

    return (
      <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
        <div className="bg-white dark:bg-gray-800 rounded-lg p-6 max-w-md">
          <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-white">
            Sync Conflict Detected
          </h3>
          <p className="text-sm text-gray-600 dark:text-gray-400 mb-6">
            Changes were made in both the visual editor and code editor simultaneously.
            Which version would you like to keep?
          </p>
          <div className="flex gap-3">
            <button
              onClick={() => handleResolveConflict(lastConflict.conflictId || '', 'visual')}
              className="flex-1 px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors"
            >
              Keep Visual
            </button>
            <button
              onClick={() => handleResolveConflict(lastConflict.conflictId || '', 'code')}
              className="flex-1 px-4 py-2 bg-gray-600 text-white rounded hover:bg-gray-700 transition-colors"
            >
              Keep Code
            </button>
          </div>
        </div>
      </div>
    );
  };

  return (
    <div className="synchronized-ui-builder w-full h-full flex flex-col">
      {/* Sync Status Indicator */}
      <div className="flex items-center justify-between px-4 py-2 bg-gray-100 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center gap-2">
          <div
            className={`w-2 h-2 rounded-full ${
              syncStatus === 'synced'
                ? 'bg-green-500'
                : syncStatus === 'syncing'
                ? 'bg-yellow-500'
                : 'bg-red-500'
            }`}
          />
          <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
            {syncStatus === 'synced'
              ? 'Synced'
              : syncStatus === 'syncing'
              ? 'Syncing...'
              : 'Conflict'}
          </span>
        </div>
        <div className="text-xs text-gray-500 dark:text-gray-400">
          Last sync: {sync.lastSyncTime ? new Date(sync.lastSyncTime).toLocaleTimeString() : 'Never'}
        </div>
      </div>

      {/* Main Editor */}
      <div className="flex-1 overflow-hidden">
        <IntegratedUIBuilder
          config={{
            userId,
            userName,
            workspaceRoot,
            ydoc,
            enableCollaboration,
            enablePreview,
            componentLibrary: new Map(),
          }}
          initialComponents={components}
          onComponentsChange={handleComponentsChange}
          onCodeChange={handleCodeChange}
        />
      </div>

      {/* Conflict Resolution UI */}
      {renderConflictUI()}
    </div>
  );
};

export default SynchronizedUIBuilder;
