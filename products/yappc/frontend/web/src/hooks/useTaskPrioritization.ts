/**
 * Task Prioritization Hook
 *
 * React hook for AI-powered task prioritization.
 * Provides intelligent task ordering based on multiple factors.
 *
 * @doc.type hook
 * @doc.purpose AI-powered task prioritization
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  prioritizeTasks,
  getTaskDependencies,
  getTaskDependents,
  canStartTask,
  type Task,
  type PrioritizedTask,
  type PrioritizationRequest,
} from '../services/ai/TaskPrioritizationService';

// ============================================================================
// Types
// ============================================================================

export interface UseTaskPrioritizationOptions {
  tasks: Task[];
  userId?: string;
  context?: PrioritizationRequest['context'];
  enabled?: boolean;
  autoRefresh?: boolean;
}

export interface UseTaskPrioritizationResult {
  prioritizedTasks: PrioritizedTask[];
  isLoading: boolean;
  error: Error | null;
  refresh: () => void;
  getTaskDependencies: () => Map<string, string[]>;
  getTaskDependents: (taskId: string) => Task[];
  canStartTask: (taskId: string) => boolean;
  getTaskScore: (taskId: string) => number;
  getTaskFactors: (taskId: string) => PrioritizedTask['factors'] | null;
  getTaskReasoning: (taskId: string) => string | null;
}

// ============================================================================
// Hook Implementation
// ============================================================================

export function useTaskPrioritization({
  tasks,
  userId,
  context,
  enabled = true,
  autoRefresh = false,
}: UseTaskPrioritizationOptions): UseTaskPrioritizationResult {
  const queryClient = useQueryClient();

  // Query for prioritized tasks
  const {
    data: prioritizationResponse,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['task-prioritization', tasks, userId, context],
    queryFn: () =>
      prioritizeTasks({
        tasks,
        userId,
        context,
      } as PrioritizationRequest),
    enabled: enabled && tasks.length > 0,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

  const prioritizedTasks = prioritizationResponse?.prioritizedTasks || [];

  // Get task dependencies graph
  const getDependencies = useCallback(() => {
    return getTaskDependencies(tasks);
  }, [tasks]);

  // Get tasks that depend on a given task
  const getDependents = useCallback((taskId: string) => {
    return getTaskDependents(taskId, tasks);
  }, [tasks]);

  // Check if a task can be started
  const checkCanStart = useCallback((taskId: string) => {
    const task = tasks.find(t => t.id === taskId);
    if (!task) return false;
    return canStartTask(task, tasks);
  }, [tasks]);

  // Get task score
  const getTaskScore = useCallback((taskId: string): number => {
    const prioritized = prioritizedTasks.find(t => t.id === taskId);
    return prioritized?.score || 0;
  }, [prioritizedTasks]);

  // Get task factors
  const getTaskFactors = useCallback((taskId: string) => {
    const prioritized = prioritizedTasks.find(t => t.id === taskId);
    return prioritized?.factors || null;
  }, [prioritizedTasks]);

  // Get task reasoning
  const getTaskReasoning = useCallback((taskId: string) => {
    const prioritized = prioritizedTasks.find(t => t.id === taskId);
    return prioritized?.reasoning || null;
  }, [prioritizedTasks]);

  return {
    prioritizedTasks,
    isLoading,
    error,
    refresh: refetch,
    getTaskDependencies: getDependencies,
    getTaskDependents: getDependents,
    canStartTask: checkCanStart,
    getTaskScore,
    getTaskFactors,
    getTaskReasoning,
  };
}
