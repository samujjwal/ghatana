/**
 * useBidirectionalSync Hook
 * 
 * React hook for managing bidirectional synchronization between
 * visual UI builder and Monaco code editor.
 * 
 * Features:
 * - 🔄 Automatic sync management
 * - 🎯 Conflict detection and resolution
 * - 📊 Sync history tracking
 * - ⚡ Debounced updates
 * - 👥 Collaborative support
 * 
 * @doc.type hook
 * @doc.purpose Bidirectional sync React hook
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useEffect, useRef, useCallback, useState } from 'react';
import { BidirectionalSyncCoordinator, type SyncEvent, type SyncConfig } from '../sync/BidirectionalSyncCoordinator';

/**
 * Sync state
 */
export interface SyncState {
  issyncing: boolean;
  lastSyncTime: number;
  syncHistory: SyncEvent[];
  conflictCount: number;
  resolvedConflictCount: number;
}

/**
 * useBidirectionalSync hook
 */
export function useBidirectionalSync(config?: Partial<SyncConfig>) {
  const coordinatorRef = useRef<BidirectionalSyncCoordinator | null>(null);
  const [syncState, setSyncState] = useState<SyncState>({
    issyncing: false,
    lastSyncTime: 0,
    syncHistory: [],
    conflictCount: 0,
    resolvedConflictCount: 0,
  });

  // Initialize coordinator
  useEffect(() => {
    coordinatorRef.current = new BidirectionalSyncCoordinator(config);

    // Setup event listeners
    const unsubscribeVisualToCode = coordinatorRef.current.on('visual-to-code', (event) => {
      setSyncState((prev) => ({
        ...prev,
        lastSyncTime: event.timestamp,
        syncHistory: [...prev.syncHistory, event].slice(-50), // Keep last 50
      }));
    });

    const unsubscribeCodeToVisual = coordinatorRef.current.on('code-to-visual', (event) => {
      setSyncState((prev) => ({
        ...prev,
        lastSyncTime: event.timestamp,
        syncHistory: [...prev.syncHistory, event].slice(-50),
      }));
    });

    const unsubscribeConflict = coordinatorRef.current.on('conflict', (event) => {
      setSyncState((prev) => ({
        ...prev,
        conflictCount: prev.conflictCount + 1,
        syncHistory: [...prev.syncHistory, event].slice(-50),
      }));
    });

    const unsubscribeResolved = coordinatorRef.current.on('resolved', (event) => {
      setSyncState((prev) => ({
        ...prev,
        resolvedConflictCount: prev.resolvedConflictCount + 1,
        syncHistory: [...prev.syncHistory, event].slice(-50),
      }));
    });

    return () => {
      unsubscribeVisualToCode();
      unsubscribeCodeToVisual();
      unsubscribeConflict();
      unsubscribeResolved();
      coordinatorRef.current?.dispose();
    };
  }, [config]);

  /**
   * Sync visual changes to code
   */
  const syncVisualToCode = useCallback((visualData: unknown, debounce = true) => {
    coordinatorRef.current?.syncVisualToCode(visualData, debounce);
  }, []);

  /**
   * Sync code changes to visual
   */
  const syncCodeToVisual = useCallback((codeData: unknown, debounce = true) => {
    coordinatorRef.current?.syncCodeToVisual(codeData, debounce);
  }, []);

  /**
   * Resolve conflict
   */
  const resolveConflict = useCallback((conflictId: string, preferredSource: 'visual' | 'code') => {
    coordinatorRef.current?.resolveConflict(conflictId, preferredSource);
  }, []);

  /**
   * Get sync history
   */
  const getHistory = useCallback(() => {
    return coordinatorRef.current?.getHistory() || [];
  }, []);

  /**
   * Clear sync history
   */
  const clearHistory = useCallback(() => {
    coordinatorRef.current?.clearHistory();
    setSyncState((prev) => ({
      ...prev,
      syncHistory: [],
    }));
  }, []);

  return {
    // State
    ...syncState,

    // Actions
    syncVisualToCode,
    syncCodeToVisual,
    resolveConflict,
    getHistory,
    clearHistory,

    // Coordinator access
    coordinator: coordinatorRef.current,
  };
}

/**
 * Enhanced UI Builder with bidirectional sync
 */
export function useUIBuilderSync(config?: Partial<SyncConfig>) {
  const sync = useBidirectionalSync(config);

  /**
   * Sync component tree changes
   */
  const syncComponentTree = useCallback(
    (components: unknown) => {
      sync.syncVisualToCode(components, true);
    },
    [sync]
  );

  /**
   * Sync code changes
   */
  const syncCodeChanges = useCallback(
    (code: string) => {
      sync.syncCodeToVisual(code, true);
    },
    [sync]
  );

  /**
   * Sync property changes
   */
  const syncPropertyChange = useCallback(
    (componentId: string, property: string, value: unknown) => {
      sync.syncVisualToCode(
        {
          type: 'property-change',
          componentId,
          property,
          value,
        },
        true
      );
    },
    [sync]
  );

  /**
   * Sync style changes
   */
  const syncStyleChange = useCallback(
    (componentId: string, styles: Record<string, string>) => {
      sync.syncVisualToCode(
        {
          type: 'style-change',
          componentId,
          styles,
        },
        true
      );
    },
    [sync]
  );

  return {
    ...sync,
    syncComponentTree,
    syncCodeChanges,
    syncPropertyChange,
    syncStyleChange,
  };
}
