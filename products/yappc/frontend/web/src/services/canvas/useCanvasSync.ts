/**
 * useCanvasSync Hook
 *
 * React hook bridging CanvasSyncService state machine into component lifecycle.
 * Provides real-time sync status, history, and transition controls.
 *
 * @doc.type hook
 * @doc.purpose React integration for CanvasSyncService
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import type { CanvasSyncSnapshot, CanvasSyncState } from './CanvasSyncService';
import { getCanvasSyncService } from './CanvasSyncService';

export interface UseCanvasSyncResult {
  state: CanvasSyncState;
  snapshot: CanvasSyncSnapshot;
  startSync: () => void;
  markLocalChange: () => void;
  syncSucceeded: (remoteVersion?: number) => void;
  syncFailed: (errorMessage?: string) => void;
  reportConflict: (message?: string) => void;
  markStale: (reason?: string) => void;
  resolveConflict: (resolution: 'local' | 'remote') => void;
  reset: () => void;
}

export function useCanvasSync(projectId: string): UseCanvasSyncResult {
  const serviceRef = useRef(getCanvasSyncService(projectId));
  const [snapshot, setSnapshot] = useState<CanvasSyncSnapshot>(
    serviceRef.current.getSnapshot()
  );

  useEffect(() => {
    const service = serviceRef.current;
    setSnapshot(service.getSnapshot());
    const unsubscribe = service.subscribe((next) => {
      setSnapshot(next);
    });
    return () => {
      unsubscribe();
    };
  }, [projectId]);

  const startSync = useCallback(() => serviceRef.current.startSync(), []);
  const markLocalChange = useCallback(() => serviceRef.current.markLocalChange(), []);
  const syncSucceeded = useCallback(
    (remoteVersion?: number) => serviceRef.current.syncSucceeded(remoteVersion),
    []
  );
  const syncFailed = useCallback(
    (errorMessage?: string) => serviceRef.current.syncFailed(errorMessage),
    []
  );
  const reportConflict = useCallback(
    (message?: string) => serviceRef.current.reportConflict(message),
    []
  );
  const markStale = useCallback(
    (reason?: string) => serviceRef.current.markStale(reason),
    []
  );
  const resolveConflict = useCallback(
    (resolution: 'local' | 'remote') => serviceRef.current.resolveConflict(resolution),
    []
  );
  const reset = useCallback(() => serviceRef.current.reset(), []);

  return {
    state: snapshot.state,
    snapshot,
    startSync,
    markLocalChange,
    syncSucceeded,
    syncFailed,
    reportConflict,
    markStale,
    resolveConflict,
    reset,
  };
}
