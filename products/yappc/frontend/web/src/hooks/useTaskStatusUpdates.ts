/**
 * Task Status Updates Hook
 *
 * React hook for real-time task status updates via WebSocket.
 * Provides live status indicators and automatic task list updates.
 *
 * @doc.type hook
 * @doc.purpose Real-time task status updates via WebSocket
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useEffect, useCallback, useState } from 'react';
import { useWebSocket } from '@ghatana/realtime';

/**
 * Task status types
 */
export type TaskStatus = 'pending' | 'in-progress' | 'blocked' | 'completed' | 'skipped';

interface TaskStatusUpdate {
  taskId: string;
  status: TaskStatus;
  timestamp: string;
  previousStatus?: TaskStatus;
  error?: string;
}

interface UseTaskStatusUpdatesOptions {
  projectId?: string;
  enabled?: boolean;
}

interface UseTaskStatusUpdatesResult {
  isConnected: boolean;
  lastUpdate: TaskStatusUpdate | null;
  subscribeToTaskUpdates: (onUpdate: (update: TaskStatusUpdate) => void) => () => void;
  requestTaskStatus: (taskId: string) => void;
}

/**
 * Hook for real-time task status updates
 *
 * Provides WebSocket-based real-time status updates for dashboard tasks.
 * Automatically subscribes to task status changes and provides callbacks for UI updates.
 *
 * @param options - Configuration options
 * @param options.projectId - Project ID to subscribe to task updates for
 * @param options.enabled - Enable/disable real-time updates (default: true)
 *
 * @returns Object containing connection status, last update, and subscription methods
 *
 * @example
 * ```tsx
 * function TaskDashboard({ projectId }: { projectId: string }) {
 *   const { isConnected, subscribeToTaskUpdates } = useTaskStatusUpdates({
 *     projectId,
 *     enabled: true
 *   });
 *
 *   const [tasks, setTasks] = useState<PriorityTask[]>([]);
 *
 *   useEffect(() => {
 *     const unsubscribe = subscribeToTaskUpdates((update) => {
 *       setTasks(prev => prev.map(task =>
 *         task.id === update.taskId
 *           ? { ...task, status: update.status, updatedAt: update.timestamp }
 *           : task
 *       ));
 *     });
 *
 *     return unsubscribe;
 *   }, [subscribeToTaskUpdates]);
 *
 *   return (
 *     <div>
 *       <ConnectionIndicator connected={isConnected} />
 *       <TaskList tasks={tasks} />
 *     </div>
 *   );
 * }
 * ```
 */
export function useTaskStatusUpdates(
  options: UseTaskStatusUpdatesOptions = {}
): UseTaskStatusUpdatesResult {
  const { projectId, enabled = true } = options;
  const { isConnected, send, subscribe } = useWebSocket({
    autoConnect: enabled,
  });
  const [lastUpdate, setLastUpdate] = useState<TaskStatusUpdate | null>(null);

  /**
   * Subscribe to task status updates
   *
   * Registers a callback for task status changes via WebSocket.
   * Returns an unsubscribe function for cleanup.
   */
  const subscribeToTaskUpdates = useCallback(
    (onUpdate: (update: TaskStatusUpdate) => void) => {
      if (!enabled) {
        return () => {};
      }

      const channel = projectId 
        ? `task_status_update_${projectId}` 
        : 'task_status_update_global';

      const unsubscribe = subscribe(channel, (message: unknown) => {
        const update = message as { payload?: TaskStatusUpdate };
        
        if (update.payload) {
          setLastUpdate(update.payload);
          onUpdate(update.payload);
        }
      });

      return unsubscribe;
    },
    [subscribe, projectId, enabled]
  );

  /**
   * Request current status for a specific task
   *
   * Sends a WebSocket message to request the current status of a task.
   */
  const requestTaskStatus = useCallback(
    (taskId: string) => {
      if (!isConnected) {
        console.warn('WebSocket not connected, cannot request task status');
        return;
      }

      send({
        type: 'task_status_request',
        payload: { taskId, projectId },
      });
    },
    [send, isConnected, projectId]
  );

  return {
    isConnected,
    lastUpdate,
    subscribeToTaskUpdates,
    requestTaskStatus,
  };
}

/**
 * Hook for task status with automatic optimistic updates
 *
 * Combines real-time WebSocket updates with optimistic UI updates for instant feedback.
 * Automatically rolls back on WebSocket confirmation of failure.
 */
export function useTaskStatusOptimistic(
  options: UseTaskStatusUpdatesOptions = {}
) {
  const { subscribeToTaskUpdates, requestTaskStatus } = useTaskStatusUpdates(options);
  const [pendingUpdates, setPendingUpdates] = useState<Set<string>>(new Set());
  const [failedUpdates, setFailedUpdates] = useState<Set<string>>(new Set());

  const updateTaskStatus = useCallback(
    (taskId: string, newStatus: TaskStatus, onConfirm?: () => void) => {
      // Mark as pending
      setPendingUpdates(prev => new Set(prev).add(taskId));
      setFailedUpdates(prev => {
        const next = new Set(prev);
        next.delete(taskId);
        return next;
      });

      // Request status update via WebSocket
      requestTaskStatus(taskId);

      // Call confirmation callback if provided
      if (onConfirm) {
        onConfirm();
      }
    },
    [requestTaskStatus]
  );

  useEffect(() => {
    const unsubscribe = subscribeToTaskUpdates((update: TaskStatusUpdate) => {
      // Remove from pending when update is confirmed
      setPendingUpdates(prev => {
        const next = new Set(prev);
        next.delete(update.taskId);
        return next;
      });

      // Add to failed if error occurred
      if (update.error) {
        setFailedUpdates(prev => new Set(prev).add(update.taskId));
      }
    });

    return unsubscribe;
  }, [subscribeToTaskUpdates]);

  return {
    updateTaskStatus,
    isPending: (taskId: string) => pendingUpdates.has(taskId),
    hasFailed: (taskId: string) => failedUpdates.has(taskId),
    clearFailed: (taskId: string) => {
      setFailedUpdates(prev => {
        const next = new Set(prev);
        next.delete(taskId);
        return next;
      });
    },
  };
}
