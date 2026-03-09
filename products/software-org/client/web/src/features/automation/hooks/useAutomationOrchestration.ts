/**
 * Automation Orchestration Hook
 *
 * <p><b>Purpose</b><br>
 * Custom hook providing automation feature orchestration, combining store state,
 * API queries, and business logic for workflow management, execution monitoring,
 * and trigger orchestration.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const {
 *   workflows,
 *   executions,
 *   triggers,
 *   isLoading,
 *   selectWorkflow,
 *   createWorkflow,
 *   executeWorkflow,
 *   updateTrigger,
 * } = useAutomationOrchestration();
 * ```
 *
 * @doc.type hook
 * @doc.purpose Automation feature orchestration and state management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

/* eslint-disable @typescript-eslint/no-explicit-any */
import { useCallback, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import * as automationApi from '@/services/api/automationApi';
import {
    automationStateAtom,
    selectWorkflowAtom,
    setActiveExecutionAtom,
    openBuilderAtom,
    closeBuilderAtom,
    setExecutionFilterAtom,
} from '../stores/automation.store';
import type {
    WorkflowExecution,
    WorkflowTrigger,
    WorkflowStats,
} from '@/types/ml-monitoring';

/**
 * Automation orchestration hook interface
 */
export interface UseAutomationOrchestrationReturn {
    workflows: Array<any> | undefined;
    executions: WorkflowExecution[] | undefined;
    triggers: WorkflowTrigger[] | undefined;
    stats: WorkflowStats | undefined;
    isLoading: boolean;
    selectedWorkflowId: string | null;
    activeExecutionId: string | null;
    executionFilter: 'all' | 'running' | 'completed' | 'failed';
    selectWorkflow: (workflowId: string) => void;
    setActiveExecution: (executionId: string | null) => void;
    openBuilder: (workflowId?: string) => void;
    closeBuilder: () => void;
    setFilter: (filter: 'all' | 'running' | 'completed' | 'failed') => void;
    createWorkflow: (definition: any) => Promise<any>;
    updateWorkflow: (id: string, definition: any) => Promise<any>;
    executeWorkflow: (workflowId: string) => Promise<void>;
    cancelExecution: (executionId: string) => Promise<void>;
    addTrigger: (workflowId: string, trigger: WorkflowTrigger) => Promise<void>;
    removeTrigger: (triggerId: string) => Promise<void>;
    updateTrigger: (triggerId: string, enabled: boolean) => Promise<void>;
}

/**
 * Custom hook for automation feature orchestration.
 *
 * @param tenantId - Tenant identifier
 * @returns Automation orchestration state and methods
 */
export function useAutomationOrchestration(tenantId: string): UseAutomationOrchestrationReturn {
    const [automationState] = useAtom(automationStateAtom);
    const [, selectWorkflow] = useAtom(selectWorkflowAtom);
    const [, setActiveExecution] = useAtom(setActiveExecutionAtom);
    const [, openBuilder] = useAtom(openBuilderAtom);
    const [, closeBuilder] = useAtom(closeBuilderAtom);
    const [, setFilter] = useAtom(setExecutionFilterAtom);
    const queryClient = useQueryClient();

    // Fetch workflows
    const { data: workflows, isLoading: workflowsLoading } = useQuery({
        queryKey: ['workflows', tenantId],
        queryFn: () => automationApi.getWorkflows(tenantId),
        staleTime: 30000,
    });

    // Fetch executions for selected workflow
    const { data: executions, isLoading: executionsLoading } = useQuery({
        queryKey: ['workflow-executions', automationState.selectedWorkflowId],
        queryFn: () =>
            automationState.selectedWorkflowId
                ? automationApi.getExecutionHistory(automationState.selectedWorkflowId, 50)
                : Promise.resolve([]),
        enabled: Boolean(automationState.selectedWorkflowId),
        staleTime: 20000,
    });

    // Fetch triggers for selected workflow
    const { data: triggers, isLoading: triggersLoading } = useQuery({
        queryKey: ['workflow-triggers', automationState.selectedWorkflowId],
        queryFn: () =>
            automationState.selectedWorkflowId
                ? automationApi.getWorkflowTriggers(automationState.selectedWorkflowId)
                : Promise.resolve([]),
        enabled: Boolean(automationState.selectedWorkflowId),
        staleTime: 30000,
    });

    // Fetch workflow stats
    const { data: stats, isLoading: statsLoading } = useQuery({
        queryKey: ['workflow-stats', automationState.selectedWorkflowId],
        queryFn: () =>
            automationState.selectedWorkflowId
                ? automationApi.getWorkflowStats(automationState.selectedWorkflowId, {
                    start: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000),
                    end: new Date(),
                })
                : Promise.resolve(null),
        enabled: Boolean(automationState.selectedWorkflowId),
        staleTime: 60000,
    });

    // Create workflow mutation
    const createWorkflowMutation = useMutation({
        mutationFn: (definition: Record<string, unknown>) => automationApi.createWorkflow(tenantId, definition as any),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['workflows', tenantId] });
        },
    });

    // Update workflow mutation
    const updateWorkflowMutation = useMutation({
        mutationFn: ({ id, definition }: { id: string; definition: Record<string, unknown> }) =>
            automationApi.updateWorkflow(id, definition as any),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['workflows', tenantId] });
        },
    });

    // Execute workflow mutation
    const executeWorkflowMutation = useMutation({
        mutationFn: (workflowId: string) => automationApi.executeWorkflow(workflowId, {}),
        onSuccess: () => {
            queryClient.invalidateQueries({
                queryKey: ['workflow-executions', automationState.selectedWorkflowId],
            });
        },
    });

    // Cancel execution mutation
    const cancelExecutionMutation = useMutation({
        mutationFn: (executionId: string) => automationApi.cancelExecution(executionId),
        onSuccess: () => {
            queryClient.invalidateQueries({
                queryKey: ['workflow-executions', automationState.selectedWorkflowId],
            });
        },
    });

    // Add trigger mutation
    const addTriggerMutation = useMutation({
        mutationFn: ({ workflowId, trigger }: { workflowId: string; trigger: WorkflowTrigger }) =>
            automationApi.addWorkflowTrigger(workflowId, {
                ...trigger,
                config: trigger.config ?? {},
            }),
        onSuccess: () => {
            queryClient.invalidateQueries({
                queryKey: ['workflow-triggers', automationState.selectedWorkflowId],
            });
        },
    });

    // Remove trigger mutation
    const removeTriggerMutation = useMutation({
        mutationFn: (triggerId: string) => automationApi.removeWorkflowTrigger(triggerId),
        onSuccess: () => {
            queryClient.invalidateQueries({
                queryKey: ['workflow-triggers', automationState.selectedWorkflowId],
            });
        },
    });

    // Update trigger mutation
    const updateTriggerMutation = useMutation({
        mutationFn: ({ triggerId, enabled }: { triggerId: string; enabled: boolean }) =>
            automationApi.updateWorkflowTrigger(triggerId, { enabled }),
        onSuccess: () => {
            queryClient.invalidateQueries({
                queryKey: ['workflow-triggers', automationState.selectedWorkflowId],
            });
        },
    });

    // Filter executions by status
    const filteredExecutions = useMemo(() => {
        if (!executions) return [];
        if (automationState.executionFilter === 'all') return executions;
        return executions.filter((e) => e.status === automationState.executionFilter);
    }, [executions, automationState.executionFilter]);

    // Memoized handlers
    const handleSelectWorkflow = useCallback(
        (workflowId: string) => {
            selectWorkflow(workflowId);
        },
        [selectWorkflow]
    );

    const handleSetActiveExecution = useCallback(
        (executionId: string | null) => {
            setActiveExecution(executionId);
        },
        [setActiveExecution]
    );

    const handleOpenBuilder = useCallback(
        (workflowId?: string) => {
            openBuilder(workflowId);
        },
        [openBuilder]
    );

    const handleCloseBuilder = useCallback(() => {
        closeBuilder();
    }, [closeBuilder]);

    const handleSetFilter = useCallback(
        (filter: 'all' | 'running' | 'completed' | 'failed') => {
            setFilter(filter);
        },
        [setFilter]
    );

    const handleCreateWorkflow = useCallback(
        (definition: any) => createWorkflowMutation.mutateAsync(definition),
        [createWorkflowMutation]
    );

    const handleUpdateWorkflow = useCallback(
        (id: string, definition: any) =>
            updateWorkflowMutation.mutateAsync({ id, definition }),
        [updateWorkflowMutation]
    );

    const handleExecuteWorkflow = useCallback(
        (workflowId: string) => executeWorkflowMutation.mutateAsync(workflowId).then(() => undefined),
        [executeWorkflowMutation]
    );

    const handleCancelExecution = useCallback(
        (executionId: string) => cancelExecutionMutation.mutateAsync(executionId),
        [cancelExecutionMutation]
    );

    const handleAddTrigger = useCallback(
        (workflowId: string, trigger: WorkflowTrigger) =>
            addTriggerMutation.mutateAsync({ workflowId, trigger }).then(() => undefined),
        [addTriggerMutation]
    );

    const handleRemoveTrigger = useCallback(
        (triggerId: string) => removeTriggerMutation.mutateAsync(triggerId),
        [removeTriggerMutation]
    );

    const handleUpdateTrigger = useCallback(
        (triggerId: string, enabled: boolean) =>
            updateTriggerMutation.mutateAsync({ triggerId, enabled }),
        [updateTriggerMutation]
    );

    return {
        workflows,
        executions: filteredExecutions,
        triggers,
        stats,
        isLoading:
            workflowsLoading ||
            executionsLoading ||
            triggersLoading ||
            statsLoading,
        selectedWorkflowId: automationState.selectedWorkflowId,
        activeExecutionId: automationState.activeExecutionId,
        executionFilter: automationState.executionFilter,
        selectWorkflow: handleSelectWorkflow,
        setActiveExecution: handleSetActiveExecution,
        openBuilder: handleOpenBuilder,
        closeBuilder: handleCloseBuilder,
        setFilter: handleSetFilter,
        createWorkflow: handleCreateWorkflow,
        updateWorkflow: handleUpdateWorkflow,
        executeWorkflow: handleExecuteWorkflow,
        cancelExecution: handleCancelExecution,
        addTrigger: handleAddTrigger,
        removeTrigger: handleRemoveTrigger,
        updateTrigger: handleUpdateTrigger,
    };
}

export default useAutomationOrchestration;
