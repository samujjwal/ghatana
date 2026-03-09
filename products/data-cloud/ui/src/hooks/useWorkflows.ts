/**
 * Workflows Hooks
 * 
 * TanStack Query hooks for workflow management.
 * Provides data fetching, caching, and mutation capabilities.
 * 
 * @doc.type hook
 * @doc.purpose Workflow data management hooks
 * @doc.layer frontend
 * @doc.pattern Custom Hook
 */

import { useQuery, useMutation, useQueryClient, UseQueryOptions } from '@tanstack/react-query';
import {
    workflowsApi,
    Workflow,
    WorkflowExecution,
    CreateWorkflowDto,
    UpdateWorkflowDto,
    WorkflowQueryParams,
    PaginatedResponse,
} from '../lib/api';

/**
 * Query keys for workflows
 */
export const workflowKeys = {
    all: ['workflows'] as const,
    lists: () => [...workflowKeys.all, 'list'] as const,
    list: (params?: WorkflowQueryParams) => [...workflowKeys.lists(), params] as const,
    details: () => [...workflowKeys.all, 'detail'] as const,
    detail: (id: string) => [...workflowKeys.details(), id] as const,
    executions: (id: string) => [...workflowKeys.detail(id), 'executions'] as const,
    execution: (workflowId: string, executionId: string) =>
        [...workflowKeys.executions(workflowId), executionId] as const,
};

/**
 * Hook to fetch all workflows
 * 
 * @example
 * ```tsx
 * const { data, isLoading } = useWorkflows({ status: 'active' });
 * ```
 */
export function useWorkflows(
    params?: WorkflowQueryParams,
    options?: Omit<UseQueryOptions<PaginatedResponse<Workflow>>, 'queryKey' | 'queryFn'>
) {
    return useQuery({
        queryKey: workflowKeys.list(params),
        queryFn: () => workflowsApi.list(params),
        ...options,
    });
}

/**
 * Hook to fetch a single workflow
 * 
 * @example
 * ```tsx
 * const { data: workflow, isLoading } = useWorkflow('workflow-id');
 * ```
 */
export function useWorkflow(
    id: string,
    options?: Omit<UseQueryOptions<Workflow>, 'queryKey' | 'queryFn'>
) {
    return useQuery({
        queryKey: workflowKeys.detail(id),
        queryFn: () => workflowsApi.get(id),
        enabled: !!id,
        ...options,
    });
}

/**
 * Hook to fetch workflow executions
 */
export function useWorkflowExecutions(
    workflowId: string,
    params?: { page?: number; pageSize?: number; status?: WorkflowExecution['status'] },
    options?: Omit<UseQueryOptions<PaginatedResponse<WorkflowExecution>>, 'queryKey' | 'queryFn'>
) {
    return useQuery({
        queryKey: [...workflowKeys.executions(workflowId), params],
        queryFn: () => workflowsApi.getExecutions(workflowId, params),
        enabled: !!workflowId,
        ...options,
    });
}

/**
 * Hook to fetch a single execution
 */
export function useWorkflowExecution(
    workflowId: string,
    executionId: string,
    options?: Omit<UseQueryOptions<WorkflowExecution>, 'queryKey' | 'queryFn'>
) {
    return useQuery({
        queryKey: workflowKeys.execution(workflowId, executionId),
        queryFn: () => workflowsApi.getExecution(workflowId, executionId),
        enabled: !!workflowId && !!executionId,
        ...options,
    });
}

/**
 * Hook to create a workflow
 * 
 * @example
 * ```tsx
 * const { mutate: createWorkflow, isPending } = useCreateWorkflow();
 * createWorkflow({ name: 'New Workflow', nodes: [], edges: [] });
 * ```
 */
export function useCreateWorkflow() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (data: CreateWorkflowDto) => workflowsApi.create(data),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: workflowKeys.lists() });
        },
    });
}

/**
 * Hook to update a workflow
 */
export function useUpdateWorkflow() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ id, data }: { id: string; data: UpdateWorkflowDto }) =>
            workflowsApi.update(id, data),
        onSuccess: (updatedWorkflow) => {
            queryClient.setQueryData(
                workflowKeys.detail(updatedWorkflow.id),
                updatedWorkflow
            );
            queryClient.invalidateQueries({ queryKey: workflowKeys.lists() });
        },
    });
}

/**
 * Hook to delete a workflow
 */
export function useDeleteWorkflow() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (id: string) => workflowsApi.delete(id),
        onSuccess: (_, deletedId) => {
            queryClient.removeQueries({ queryKey: workflowKeys.detail(deletedId) });
            queryClient.invalidateQueries({ queryKey: workflowKeys.lists() });
        },
    });
}

/**
 * Hook to execute a workflow
 * 
 * @example
 * ```tsx
 * const { mutate: executeWorkflow } = useExecuteWorkflow();
 * executeWorkflow({ id: 'workflow-id', params: { input: 'value' } });
 * ```
 */
export function useExecuteWorkflow() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ id, params }: { id: string; params?: Record<string, unknown> }) =>
            workflowsApi.execute(id, params),
        onSuccess: (execution) => {
            // Invalidate executions list
            queryClient.invalidateQueries({
                queryKey: workflowKeys.executions(execution.workflowId)
            });
        },
    });
}

/**
 * Hook to cancel a workflow execution
 */
export function useCancelExecution() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ workflowId, executionId }: { workflowId: string; executionId: string }) =>
            workflowsApi.cancelExecution(workflowId, executionId),
        onSuccess: (execution) => {
            queryClient.setQueryData(
                workflowKeys.execution(execution.workflowId, execution.id),
                execution
            );
            queryClient.invalidateQueries({
                queryKey: workflowKeys.executions(execution.workflowId),
            });
        },
    });
}

/**
 * Hook to activate a workflow
 */
export function useActivateWorkflow() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (id: string) => workflowsApi.activate(id),
        onSuccess: (updatedWorkflow) => {
            queryClient.setQueryData(
                workflowKeys.detail(updatedWorkflow.id),
                updatedWorkflow
            );
            queryClient.invalidateQueries({ queryKey: workflowKeys.lists() });
        },
    });
}

/**
 * Hook to deactivate a workflow
 */
export function useDeactivateWorkflow() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (id: string) => workflowsApi.deactivate(id),
        onSuccess: (updatedWorkflow) => {
            queryClient.setQueryData(
                workflowKeys.detail(updatedWorkflow.id),
                updatedWorkflow
            );
            queryClient.invalidateQueries({ queryKey: workflowKeys.lists() });
        },
    });
}
