/**
 * usePendingTasks Hook
 *
 * <p><b>Purpose</b><br>
 * React Query hook for fetching and auto-refreshing user's pending tasks.
 * Polls every 30 seconds and syncs with Jotai atom for global state.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { tasks, isLoading, error, refetch } = usePendingTasks();
 *
 * if (isLoading) return <Spinner />;
 * if (error) return <Error>{error.message}</Error>;
 * return <TaskList tasks={tasks} />;
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose Fetch and sync pending tasks with polling
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { pendingTasksAtom, type PendingTasks, userProfileAtom, userRoleAtom } from '@/state/jotai/atoms';
import { api } from '@/services/personaApi';

/**
 * Hook options
 */
export interface UsePendingTasksOptions {
    /**
     * Polling interval in milliseconds (default: 30000 = 30 seconds)
     */
    refetchInterval?: number;

    /**
     * Enable/disable automatic polling (default: true)
     */
    enabled?: boolean;

    /**
     * Retry failed requests (default: 3)
     */
    retry?: number;
}

/**
 * Hook return type
 */
export interface UsePendingTasksResult {
    /**
     * Current pending tasks (from Jotai atom)
     */
    tasks: PendingTasks;

    /**
     * Loading state (first fetch)
     */
    isLoading: boolean;

    /**
     * Error if fetch failed
     */
    error: Error | null;

    /**
     * Refetch function for manual refresh
     */
    refetch: () => void;

    /**
     * True if background refetch is happening
     */
    isFetching: boolean;

    /**
     * Timestamp of last successful fetch
     */
    dataUpdatedAt: number;
}

/**
 * Fetches and syncs user's pending tasks with Jotai atom.
 *
 * Features:
 * - Auto-polling every 30 seconds (configurable)
 * - Automatic retry on failure (3 attempts)
 * - Syncs with Jotai atom for global state
 * - Optimistic updates on task completion
 *
 * @param options - Hook configuration options
 * @returns UsePendingTasksResult - Tasks, loading state, error, refetch
 */
export function usePendingTasks(options: UsePendingTasksOptions = {}): UsePendingTasksResult {
    const {
        refetchInterval = 30000, // 30 seconds
        enabled = true,
        retry = 3,
    } = options;

    const [tasks, setTasks] = useAtom(pendingTasksAtom);
    const [userProfile] = useAtom(userProfileAtom);
    const [userRoleAtomValue] = useAtom(userRoleAtom);
    const role = userProfile?.role || userRoleAtomValue;

    const {
        data,
        error,
        isLoading,
        isFetching,
        refetch,
        dataUpdatedAt,
    } = useQuery({
        queryKey: ['pendingTasks', role],
        queryFn: () => api.getPendingTasks(),
        refetchInterval,
        enabled,
        retry,
        staleTime: 10000, // Consider data stale after 10 seconds
    });

    // Sync React Query data with Jotai atom
    useEffect(() => {
        if (data) {
            setTasks(data);
        }
    }, [data, setTasks]);

    return {
        tasks,
        isLoading,
        error: error as Error | null,
        refetch,
        isFetching,
        dataUpdatedAt,
    };
}

/**
 * Returns total count of all pending tasks.
 *
 * @param tasks - PendingTasks object
 * @returns Total task count
 */
export function getTotalPendingTasks(tasks: PendingTasks): number {
    return (
        tasks.hitlApprovals +
        tasks.securityAlerts +
        tasks.failedWorkflows +
        tasks.modelAlerts
    );
}

/**
 * Hook for optimistically updating task count after completion.
 *
 * @param taskType - Type of task completed
 */
export function useOptimisticTaskUpdate() {
    const [, setTasks] = useAtom(pendingTasksAtom);

    return (taskType: keyof PendingTasks) => {
        setTasks((prev) => ({
            ...prev,
            [taskType]: Math.max(0, prev[taskType] - 1),
        }));
    };
}
