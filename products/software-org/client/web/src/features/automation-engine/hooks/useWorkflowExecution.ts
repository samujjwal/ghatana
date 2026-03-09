import { useState, useCallback } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as automationApi from '@/services/api/automationApi';

interface WorkflowExecutionState {
    executionId: string | null;
    status: 'idle' | 'running' | 'completed' | 'failed' | 'cancelled';
    progress: number;
    error: string | null;
}

/**
 * Custom hook for managing workflow execution lifecycle.
 *
 * Provides:
 * - Execute workflow with mutation handling
 * - Poll execution status
 * - Execution history fetching
 * - Error handling and retry logic
 * - Progress tracking
 *
 * @param workflowId - Workflow ID to execute
 * @returns Object with execution state and utilities
 *
 * @example
 * const { execute, status, progress, history } = useWorkflowExecution(workflowId);
 * const handleExecute = async () => {
 *   const result = await execute({ param1: 'value' });
 * };
 */
export function useWorkflowExecution(workflowId: string | null) {
    const queryClient = useQueryClient();
    const [executionState, setExecutionState] = useState<WorkflowExecutionState>({
        executionId: null,
        status: 'idle',
        progress: 0,
        error: null,
    });

    // Poll execution status
    const { data: executionDetails, isLoading: isLoadingDetails } = useQuery({
        queryKey: ['automation', 'execution', executionState.executionId],
        queryFn: async () => {
            if (!executionState.executionId) return null;
            try {
                const details = await automationApi.getExecutionDetails(
                    executionState.executionId
                );
                const executionError = (details as unknown as { error?: unknown }).error;
                setExecutionState((prev) => ({
                    ...prev,
                    status: details.status as any,
                    progress: details.progress || 0,
                    error: typeof executionError === 'string' ? executionError : null,
                }));
                return details;
            } catch (error) {
                setExecutionState((prev) => ({
                    ...prev,
                    error: error instanceof Error ? error.message : 'Unknown error',
                }));
                throw error;
            }
        },
        enabled: !!executionState.executionId,
        refetchInterval: () => {
            // Stop polling when execution completes or fails
            if (
                executionState.status === 'completed' ||
                executionState.status === 'failed' ||
                executionState.status === 'cancelled'
            ) {
                return false;
            }
            return 2000; // Poll every 2 seconds
        },
        staleTime: 0,
    });

    // Fetch execution history
    const historyQuery = useQuery({
        queryKey: ['automation', 'execution-history', workflowId],
        queryFn: async () => {
            if (!workflowId) return [];
            try {
                const history = await automationApi.getExecutionHistory(workflowId);
                return history;
            } catch (error) {
                console.error('Failed to fetch execution history:', error);
                return [];
            }
        },
        enabled: !!workflowId,
        staleTime: 5 * 60 * 1000, // 5 minutes
        gcTime: 10 * 60 * 1000, // 10 minutes
    });

    // Execute workflow mutation
    const executeMutation = useMutation({
        mutationFn: async (params: Record<string, any> = {}) => {
            if (!workflowId) throw new Error('Workflow ID is required');
            try {
                const execution = await automationApi.executeWorkflow(workflowId, params);
                return execution;
            } catch (error) {
                const errMsg = error instanceof Error ? error.message : 'Execution failed';
                setExecutionState((prev) => ({
                    ...prev,
                    status: 'failed',
                    error: errMsg,
                }));
                throw error;
            }
        },
        onSuccess: (data) => {
            setExecutionState((prev) => ({
                ...prev,
                executionId: data.id,
                status: 'running',
                progress: 0,
                error: null,
            }));
            // Invalidate history to refresh
            queryClient.invalidateQueries({
                queryKey: ['automation', 'execution-history', workflowId],
            });
        },
    });

    /**
     * Execute the workflow
     *
     * @param params - Execution parameters
     * @returns Promise with execution result
     */
    const execute = useCallback(
        async (params: Record<string, any> = {}) => {
            try {
                const result = await executeMutation.mutateAsync(params);
                return result;
            } catch (error) {
                throw error;
            }
        },
        [executeMutation]
    );

    /**
     * Cancel current execution
     */
    const cancel = useCallback(async () => {
        if (!executionState.executionId) return;
        try {
            await automationApi.cancelExecution(executionState.executionId);
            setExecutionState((prev) => ({
                ...prev,
                status: 'cancelled',
            }));
            queryClient.invalidateQueries({
                queryKey: ['automation', 'execution-history', workflowId],
            });
        } catch (error) {
            console.error('Failed to cancel execution:', error);
            throw error;
        }
    }, [executionState.executionId, workflowId, queryClient]);

    /**
     * Refetch history
     */
    const refetchHistory = useCallback(() => {
        return historyQuery.refetch();
    }, [historyQuery]);

    return {
        // Execution state
        executionId: executionState.executionId,
        status: executionState.status,
        progress: executionState.progress,
        error: executionState.error,

        // Execution details
        executionDetails,
        isLoadingDetails,

        // History
        history: historyQuery.data || [],
        isLoadingHistory: historyQuery.isLoading,
        isFetchingHistory: historyQuery.isFetching,

        // Mutations
        isExecuting: executeMutation.isPending,
        execute,
        cancel,
        refetchHistory,
    };
}
