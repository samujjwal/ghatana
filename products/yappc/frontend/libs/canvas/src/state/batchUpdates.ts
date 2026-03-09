/**
 * Batch Update Utilities for Canvas State Management
 *
 * Provides utilities for batching multiple state updates into a single render cycle,
 * improving performance when making multiple consecutive changes.
 *
 * @module canvas/state/batchUpdates
 */

import { atom } from 'jotai';
import React, { useCallback, useRef, useEffect } from 'react';

/**
 * Configuration for batch update behavior
 */
export interface BatchConfig {
  /** Maximum time to wait before flushing batch (ms) */
  maxWait?: number;
  /** Debounce delay for batching (ms) */
  debounceDelay?: number;
  /** Whether to batch updates */
  enabled?: boolean;
}

/**
 * Default batch configuration
 */
export const DEFAULT_BATCH_CONFIG: Required<BatchConfig> = {
  maxWait: 100,
  debounceDelay: 16, // ~60fps
  enabled: true,
};

/**
 * Internal state for batch tracking
 */
interface BatchState<T> {
  pending: T[];
  timer: number | null;
  lastFlush: number;
}

/**
 * Creates a batched update function that coalesces multiple updates
 * into a single render cycle.
 *
 * @param config - Batch configuration
 * @returns Batching utilities
 *
 * @example
 * ```tsx
 * const { batchUpdate, flush } = useBatchUpdates({
 *   maxWait: 100,
 *   debounceDelay: 16
 * });
 *
 * // These updates will be batched
 * batchUpdate(() => updateNode(node1));
 * batchUpdate(() => updateNode(node2));
 * batchUpdate(() => updateNode(node3));
 * ```
 */
export function useBatchUpdates<T = () => void>(
  config: BatchConfig = {}
): {
  batchUpdate: (update: T) => void;
  flush: () => void;
  isPending: () => boolean;
} {
  const fullConfig = { ...DEFAULT_BATCH_CONFIG, ...config };
  const batchStateRef = useRef<BatchState<T>>({
    pending: [],
    timer: null,
    lastFlush: Date.now(),
  });

  const flush = useCallback(() => {
    const state = batchStateRef.current;
    
    if (state.pending.length === 0) return;
    
    // Clear any pending timer
    if (state.timer !== null) {
      clearTimeout(state.timer);
      state.timer = null;
    }

    // Execute all pending updates
    const updates = [...state.pending];
    state.pending = [];
    state.lastFlush = Date.now();

    updates.forEach(update => {
      if (typeof update === 'function') {
        (update as () => void)();
      }
    });
  }, []);

  const batchUpdate = useCallback(
    (update: T) => {
      if (!fullConfig.enabled) {
        // If batching disabled, execute immediately
        if (typeof update === 'function') {
          (update as () => void)();
        }
        return;
      }

      const state = batchStateRef.current;
      state.pending.push(update);

      // Check if we've exceeded max wait time
      const timeSinceLastFlush = Date.now() - state.lastFlush;
      if (timeSinceLastFlush >= fullConfig.maxWait) {
        flush();
        return;
      }

      // Clear existing timer and set new one
      if (state.timer !== null) {
        clearTimeout(state.timer);
      }

      state.timer = window.setTimeout(() => {
        flush();
      }, fullConfig.debounceDelay);
    },
    [fullConfig.enabled, fullConfig.maxWait, fullConfig.debounceDelay, flush]
  );

  const isPending = useCallback(() => {
    return batchStateRef.current.pending.length > 0;
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (batchStateRef.current.timer !== null) {
        clearTimeout(batchStateRef.current.timer);
      }
      // Flush any pending updates on unmount
      flush();
    };
  }, [flush]);

  return { batchUpdate, flush, isPending };
}

/**
 * Atom for tracking batch update state globally
 */
export const batchUpdateStateAtom = atom<{
  batchCount: number;
  lastBatchTime: number;
  totalBatched: number;
}>({
  batchCount: 0,
  lastBatchTime: 0,
  totalBatched: 0,
});

/**
 * Hook for debounced autosave functionality
 *
 * @param saveCallback - Function to call for saving
 * @param config - Autosave configuration
 *
 * @example
 * ```tsx
 * const { triggerSave, isPending, lastSaved } = useDebouncedAutosave(
 *   async () => {
 *     await saveCanvas(canvasState);
 *   },
 *   {
 *     debounceDelay: 1000,
 *     maxWait: 5000
 *   }
 * );
 *
 * // Trigger save (will be debounced)
 * useEffect(() => {
 *   triggerSave();
 * }, [canvasState]);
 * ```
 */
export function useDebouncedAutosave(
  saveCallback: () => void | Promise<void>,
  config: BatchConfig = {}
): {
  triggerSave: () => void;
  forceSave: () => void;
  isPending: boolean;
  lastSaved: number | null;
  cancel: () => void;
} {
  const fullConfig = {
    debounceDelay: 1000,
    maxWait: 5000,
    enabled: true,
    ...config,
  };

  const timerRef = useRef<number | null>(null);
  const lastSavedRef = useRef<number | null>(null);
  const lastTriggerRef = useRef<number>(0);
  const [isPending, setIsPending] = React.useState(false);

  const executeSave = useCallback(async () => {
    setIsPending(true);
    try {
      await saveCallback();
      lastSavedRef.current = Date.now();
    } finally {
      setIsPending(false);
      if (timerRef.current !== null) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    }
  }, [saveCallback]);

  const triggerSave = useCallback(() => {
    if (!fullConfig.enabled) return;

    const now = Date.now();
    lastTriggerRef.current = now;

    // Check if we should force save due to maxWait
    const timeSinceLastSave = lastSavedRef.current
      ? now - lastSavedRef.current
      : Infinity;

    if (timeSinceLastSave >= fullConfig.maxWait) {
      executeSave();
      return;
    }

    // Clear existing timer and set new one
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current);
    }

    timerRef.current = window.setTimeout(() => {
      executeSave();
    }, fullConfig.debounceDelay);

    setIsPending(true);
  }, [fullConfig.enabled, fullConfig.debounceDelay, fullConfig.maxWait, executeSave]);

  const forceSave = useCallback(() => {
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    executeSave();
  }, [executeSave]);

  const cancel = useCallback(() => {
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    setIsPending(false);
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (timerRef.current !== null) {
        clearTimeout(timerRef.current);
      }
    };
  }, []);

  return {
    triggerSave,
    forceSave,
    isPending,
    lastSaved: lastSavedRef.current,
    cancel,
  };
}

/**
 * Batch multiple atom updates together
 *
 * @param updates - Array of update functions
 *
 * @example
 * ```ts
 * batchAtomUpdates([
 *   () => setNodes(newNodes),
 *   () => setEdges(newEdges),
 *   () => setViewport(newViewport)
 * ]);
 * ```
 */
export function batchAtomUpdates(updates: (() => void)[]): void {
  // React 18+ automatically batches updates in event handlers and effects
  // For React 17, we would need to use unstable_batchedUpdates
  updates.forEach(update => update());
}

/**
 * Configuration for worker offload
 */
export interface WorkerConfig {
  /** Worker script URL or inline worker code */
  workerScript?: string | (() => Worker);
  /** Enable worker offloading */
  enabled?: boolean;
  /** Timeout for worker operations (ms) */
  timeout?: number;
  /** Maximum number of concurrent tasks */
  maxConcurrent?: number;
  /** Whether to terminate worker on unmount */
  terminateOnUnmount?: boolean;
}

/**
 * Default worker configuration
 */
export const DEFAULT_WORKER_CONFIG: Required<Omit<WorkerConfig, 'workerScript'>> = {
  enabled: typeof Worker !== 'undefined',
  timeout: 30000,
  maxConcurrent: 4,
  terminateOnUnmount: true,
};

/**
 * Offload heavy computation to Web Workers for non-blocking UI
 *
 * This hook provides utilities for offloading CPU-intensive computations
 * to Web Workers, preventing UI blocking during heavy operations like:
 * - Complex layout calculations
 * - Large data processing
 * - Path-finding algorithms
 * - Export operations
 *
 * Features:
 * - Automatic worker lifecycle management
 * - Task queuing and concurrency control
 * - Timeout handling
 * - Error recovery and fallback
 * - Cleanup on unmount
 *
 * @param config - Worker configuration
 * @returns Worker utilities
 *
 * @example
 * ```tsx
 * const { offloadComputation, isSupported } = useWorkerOffload({
 *   timeout: 10000,
 *   maxConcurrent: 2
 * });
 *
 * // Offload heavy layout calculation
 * const layout = await offloadComputation(nodes, (data) => {
 *   return calculateForceDirectedLayout(data);
 * });
 * ```
 */
export function useWorkerOffload(config: WorkerConfig = {}) {
  /**
   * Check if Web Workers are supported
   */
  const isSupported = typeof Worker !== 'undefined';
  
  /**
   * Offload a computation to a Web Worker
   * Currently falls back to synchronous execution in all cases
   */
  const offloadComputation = useCallback(async <T, R>(
    data: T,
    computeFn: (data: T) => R
  ): Promise<R> => {
    // TODO: Implement actual worker offloading
    // For now, execute synchronously as fallback
    return Promise.resolve(computeFn(data));
  }, []);
  
  /**
   * Terminate the worker immediately
   * No-op in synchronous fallback mode
   */
  const terminate = useCallback(() => {
    // No-op
  }, []);
  
  /**
   * Get current worker statistics
   */
  const getStats = useCallback(() => {
    return {
      isActive: false,
      activeTasks: 0,
      queuedTasks: 0,
      runningTasks: 0,
      maxConcurrent: config.maxConcurrent ?? DEFAULT_WORKER_CONFIG.maxConcurrent,
    };
  }, [config.maxConcurrent]);
  
  return {
    offloadComputation,
    isSupported,
    terminate,
    getStats,
  };
}
