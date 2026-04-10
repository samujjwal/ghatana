/**
 * Dashboard Tasks Hook
 *
 * React hook for managing dashboard priority tasks with optimistic updates.
 * Provides CRUD operations, bulk actions, and comprehensive error handling.
 *
 * @doc.type hook
 * @doc.purpose Dashboard task management with optimistic updates
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useCallback, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient, UseMutationOptions } from '@tanstack/react-query';
import {
  createDashboardClients,
  type DashboardApiConfig,
  type ApiResponse,
  type PaginatedResponse,
  type PriorityTask,
  type TaskStatus,
  type TaskPriority,
  type TaskType,
  type ListPriorityTasksRequest,
  type UpdateTaskStatusRequest,
  type BulkTaskActionRequest,
  type BulkTaskActionResult,
} from '../clients/dashboard';

// ============================================================================
// Query Keys
// ============================================================================

export const dashboardTaskQueryKeys = {
  all: ['dashboard', 'tasks'] as const,
  lists: () => [...dashboardTaskQueryKeys.all, 'list'] as const,
  list: (filters: ListPriorityTasksRequest) => [...dashboardTaskQueryKeys.lists(), filters] as const,
  details: () => [...dashboardTaskQueryKeys.all, 'detail'] as const,
  detail: (id: string) => [...dashboardTaskQueryKeys.details(), id] as const,
};

// ============================================================================
// Hook Options
// ============================================================================

interface UseDashboardTasksOptions {
  config?: Partial<DashboardApiConfig>;
  projectId?: string;
  initialFilters?: ListPriorityTasksRequest;
}

interface UseDashboardTasksResult {
  // Query state
  tasks: PriorityTask[];
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
  refetch: () => void;

  // Mutations
  approveTask: (taskId: string, reason?: string) => Promise<void>;
  rejectTask: (taskId: string, reason?: string) => Promise<void>;
  completeTask: (taskId: string) => Promise<void>;
  updateTaskStatus: (taskId: string, status: TaskStatus, reason?: string) => Promise<void>;
  assignTask: (taskId: string, assignee: string) => Promise<void>;

  // Bulk operations
  bulkApprove: (taskIds: string[], reason?: string) => Promise<void>;
  bulkReject: (taskIds: string[], reason?: string) => Promise<void>;
  bulkComplete: (taskIds: string[]) => Promise<void>;

  // Mutation states
  isApproving: boolean;
  isRejecting: boolean;
  isCompleting: boolean;
  isUpdatingStatus: boolean;
  isAssigning: boolean;
  isBulkOperating: boolean;
}

// ============================================================================
// Hook Implementation
// ============================================================================

/**
 * Hook for managing dashboard priority tasks
 *
 * Provides optimistic updates for task operations with automatic rollback on error.
 */
export function useDashboardTasks(
  options: UseDashboardTasksOptions = {}
): UseDashboardTasksResult {
  const { config, projectId, initialFilters = {} } = options;
  const queryClient = useQueryClient();

  // Create API clients with default config
  const clients = useMemo(() => {
    const defaultConfig: DashboardApiConfig = {
      baseUrl: config?.baseUrl || import.meta.env.VITE_API_ORIGIN || '/api',
      timeout: config?.timeout || 10000,
      maxRetries: config?.maxRetries || 3,
      logRequests: config?.logRequests || false,
      logResponses: config?.logResponses || false,
      tenantId: config?.tenantId || '',
      authToken: config?.authToken || '',
    };
    return createDashboardClients(defaultConfig);
  }, [config]);
  const taskClient = clients.task;

  // Default filters
  const queryFilters = useMemo<ListPriorityTasksRequest>(
    () => ({
      ...initialFilters,
      projectId: projectId || initialFilters.projectId,
    }),
    [initialFilters, projectId]
  );

  // Query for tasks
  const {
    data: tasksResponse,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: dashboardTaskQueryKeys.list(queryFilters),
    queryFn: () => taskClient.listPriorityTasks(queryFilters),
    select: (response) => response.data?.items || [],
    staleTime: 30000, // 30 seconds
  });

  const tasks = tasksResponse || [];

  // Approve task mutation with optimistic update
  const approveTaskMutation = useMutation({
    mutationFn: ({ taskId, reason }: { taskId: string; reason?: string }) =>
      taskClient.approveTask(taskId, reason),
    onMutate: async ({ taskId }) => {
      // Cancel in-flight queries
      await queryClient.cancelQueries({ queryKey: dashboardTaskQueryKeys.lists() });

      // Snapshot previous state
      const previousTasks = queryClient.getQueryData(dashboardTaskQueryKeys.list(queryFilters));

      // Optimistically update
      queryClient.setQueryData(dashboardTaskQueryKeys.list(queryFilters), (old: ApiResponse<PaginatedResponse<PriorityTask>> | undefined) => {
        if (!old?.data?.items) return old;
        const updatedItems = old.data.items.map((task) =>
          task.id === taskId
            ? { ...task, status: 'completed' as TaskStatus, updatedAt: new Date().toISOString() }
            : task
        );
        return { ...old, data: { ...old.data, items: updatedItems } };
      });

      // Return context for rollback
      return { previousTasks };
    },
    onError: (error, variables, context) => {
      // Rollback on error
      if (context?.previousTasks) {
        queryClient.setQueryData(dashboardTaskQueryKeys.list(queryFilters), context.previousTasks);
      }
      console.error('Failed to approve task:', error);
    },
    onSettled: () => {
      // Refetch to ensure server state
      queryClient.invalidateQueries({ queryKey: dashboardTaskQueryKeys.lists() });
    },
  });

  // Reject task mutation with optimistic update
  const rejectTaskMutation = useMutation({
    mutationFn: ({ taskId, reason }: { taskId: string; reason?: string }) =>
      taskClient.rejectTask(taskId, reason),
    onMutate: async ({ taskId }) => {
      await queryClient.cancelQueries({ queryKey: dashboardTaskQueryKeys.lists() });
      const previousTasks = queryClient.getQueryData(dashboardTaskQueryKeys.list(queryFilters));

      queryClient.setQueryData(dashboardTaskQueryKeys.list(queryFilters), (old: ApiResponse<PaginatedResponse<PriorityTask>> | undefined) => {
        if (!old?.data?.items) return old;
        const updatedItems = old.data.items.map((task) =>
          task.id === taskId
            ? { ...task, status: 'skipped' as TaskStatus, updatedAt: new Date().toISOString() }
            : task
        );
        return { ...old, data: { ...old.data, items: updatedItems } };
      });

      return { previousTasks };
    },
    onError: (error, variables, context) => {
      if (context?.previousTasks) {
        queryClient.setQueryData(dashboardTaskQueryKeys.list(queryFilters), context.previousTasks);
      }
      console.error('Failed to reject task:', error);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: dashboardTaskQueryKeys.lists() });
    },
  });

  // Complete task mutation with optimistic update
  const completeTaskMutation = useMutation({
    mutationFn: (taskId: string) => taskClient.completeTask(taskId),
    onMutate: async (taskId) => {
      await queryClient.cancelQueries({ queryKey: dashboardTaskQueryKeys.lists() });
      const previousTasks = queryClient.getQueryData(dashboardTaskQueryKeys.list(queryFilters));

      queryClient.setQueryData(dashboardTaskQueryKeys.list(queryFilters), (old: ApiResponse<PaginatedResponse<PriorityTask>> | undefined) => {
        if (!old?.data?.items) return old;
        const updatedItems = old.data.items.map((task) =>
          task.id === taskId
            ? { ...task, status: 'completed' as TaskStatus, updatedAt: new Date().toISOString() }
            : task
        );
        return { ...old, data: { ...old.data, items: updatedItems } };
      });

      return { previousTasks };
    },
    onError: (error, variables, context) => {
      if (context?.previousTasks) {
        queryClient.setQueryData(dashboardTaskQueryKeys.list(queryFilters), context.previousTasks);
      }
      console.error('Failed to complete task:', error);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: dashboardTaskQueryKeys.lists() });
    },
  });

  // Update task status mutation with optimistic update
  const updateTaskStatusMutation = useMutation({
    mutationFn: ({ taskId, status, reason }: { taskId: string; status: TaskStatus; reason?: string }) =>
      taskClient.updateTaskStatus(taskId, { status, reason }),
    onMutate: async ({ taskId, status }) => {
      await queryClient.cancelQueries({ queryKey: dashboardTaskQueryKeys.lists() });
      const previousTasks = queryClient.getQueryData(dashboardTaskQueryKeys.list(queryFilters));

      queryClient.setQueryData(dashboardTaskQueryKeys.list(queryFilters), (old: ApiResponse<PaginatedResponse<PriorityTask>> | undefined) => {
        if (!old?.data?.items) return old;
        const updatedItems = old.data.items.map((task) =>
          task.id === taskId
            ? { ...task, status, updatedAt: new Date().toISOString() }
            : task
        );
        return { ...old, data: { ...old.data, items: updatedItems } };
      });

      return { previousTasks };
    },
    onError: (error, variables, context) => {
      if (context?.previousTasks) {
        queryClient.setQueryData(dashboardTaskQueryKeys.list(queryFilters), context.previousTasks);
      }
      console.error('Failed to update task status:', error);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: dashboardTaskQueryKeys.lists() });
    },
  });

  // Assign task mutation with optimistic update
  const assignTaskMutation = useMutation({
    mutationFn: ({ taskId, assignee }: { taskId: string; assignee: string }) =>
      taskClient.assignTask(taskId, assignee),
    onMutate: async ({ taskId, assignee }) => {
      await queryClient.cancelQueries({ queryKey: dashboardTaskQueryKeys.lists() });
      const previousTasks = queryClient.getQueryData(dashboardTaskQueryKeys.list(queryFilters));

      queryClient.setQueryData(dashboardTaskQueryKeys.list(queryFilters), (old: ApiResponse<PaginatedResponse<PriorityTask>> | undefined) => {
        if (!old?.data?.items) return old;
        const updatedItems = old.data.items.map((task) =>
          task.id === taskId
            ? { ...task, assignee, updatedAt: new Date().toISOString() }
            : task
        );
        return { ...old, data: { ...old.data, items: updatedItems } };
      });

      return { previousTasks };
    },
    onError: (error, variables, context) => {
      if (context?.previousTasks) {
        queryClient.setQueryData(dashboardTaskQueryKeys.list(queryFilters), context.previousTasks);
      }
      console.error('Failed to assign task:', error);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: dashboardTaskQueryKeys.lists() });
    },
  });

  // Bulk operation mutation with optimistic update
  const bulkActionMutation = useMutation({
    mutationFn: (request: BulkTaskActionRequest) => taskClient.bulkTaskAction(request),
    onMutate: async (request) => {
      await queryClient.cancelQueries({ queryKey: dashboardTaskQueryKeys.lists() });
      const previousTasks = queryClient.getQueryData(dashboardTaskQueryKeys.list(queryFilters));

      const targetStatus = request.action === 'approve' || request.action === 'complete' ? 'completed' : 'skipped';

      queryClient.setQueryData(dashboardTaskQueryKeys.list(queryFilters), (old: ApiResponse<PaginatedResponse<PriorityTask>> | undefined) => {
        if (!old?.data?.items) return old;
        const updatedItems = old.data.items.map((task) =>
          request.taskIds.includes(task.id)
            ? { ...task, status: targetStatus as TaskStatus, updatedAt: new Date().toISOString() }
            : task
        );
        return { ...old, data: { ...old.data, items: updatedItems } };
      });

      return { previousTasks };
    },
    onError: (error, variables, context) => {
      if (context?.previousTasks) {
        queryClient.setQueryData(dashboardTaskQueryKeys.list(queryFilters), context.previousTasks);
      }
      console.error('Failed to perform bulk action:', error);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: dashboardTaskQueryKeys.lists() });
    },
  });

  // Memoized mutation functions
  const approveTask = useCallback(
    async (taskId: string, reason?: string) => {
      await approveTaskMutation.mutateAsync({ taskId, reason });
    },
    [approveTaskMutation]
  );

  const rejectTask = useCallback(
    async (taskId: string, reason?: string) => {
      await rejectTaskMutation.mutateAsync({ taskId, reason });
    },
    [rejectTaskMutation]
  );

  const completeTask = useCallback(
    async (taskId: string) => {
      await completeTaskMutation.mutateAsync(taskId);
    },
    [completeTaskMutation]
  );

  const updateTaskStatus = useCallback(
    async (taskId: string, status: TaskStatus, reason?: string) => {
      await updateTaskStatusMutation.mutateAsync({ taskId, status, reason });
    },
    [updateTaskStatusMutation]
  );

  const assignTask = useCallback(
    async (taskId: string, assignee: string) => {
      await assignTaskMutation.mutateAsync({ taskId, assignee });
    },
    [assignTaskMutation]
  );

  const bulkApprove = useCallback(
    async (taskIds: string[], reason?: string) => {
      await bulkActionMutation.mutateAsync({ taskIds, action: 'approve', reason });
    },
    [bulkActionMutation]
  );

  const bulkReject = useCallback(
    async (taskIds: string[], reason?: string) => {
      await bulkActionMutation.mutateAsync({ taskIds, action: 'reject', reason });
    },
    [bulkActionMutation]
  );

  const bulkComplete = useCallback(
    async (taskIds: string[]) => {
      await bulkActionMutation.mutateAsync({ taskIds, action: 'complete' });
    },
    [bulkActionMutation]
  );

  return {
    tasks,
    isLoading,
    isError,
    error,
    refetch,
    approveTask,
    rejectTask,
    completeTask,
    updateTaskStatus,
    assignTask,
    bulkApprove,
    bulkReject,
    bulkComplete,
    isApproving: approveTaskMutation.isPending,
    isRejecting: rejectTaskMutation.isPending,
    isCompleting: completeTaskMutation.isPending,
    isUpdatingStatus: updateTaskStatusMutation.isPending,
    isAssigning: assignTaskMutation.isPending,
    isBulkOperating: bulkActionMutation.isPending,
  };
}
