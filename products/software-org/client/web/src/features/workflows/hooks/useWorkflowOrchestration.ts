/**
 * Workflow Orchestration Hook
 *
 * <p><b>Purpose</b><br>
 * Custom hook providing workflow feature orchestration, combining store state,
 * API queries, and business logic for workflow management, execution tracking,
 * and node detail inspection.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const {
 *   workflows,
 *   executions,
 *   nodeDetails,
 *   isLoading,
 *   selectWorkflow,
 *   selectNode,
 *   setPlaybackSpeed,
 * } = useWorkflowOrchestration(tenantId);
 * ```
 *
 * @doc.type hook
 * @doc.purpose Workflow feature orchestration and state management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

/* eslint-disable @typescript-eslint/no-explicit-any */
import { useCallback, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { workflowApi } from '@/services/api/workflowApi';
import {
    selectedWorkflowIdAtom,
    selectedNodeIdAtom,
    playbackSpeedAtom,
} from '../stores/workflows.store';
import type { WorkflowNode } from '@/features/workflows/components/WorkflowNodeDetail';

/**
 * Workflow orchestration hook interface
 */
export interface UseWorkflowOrchestrationReturn {
    workflows: Array<any> | undefined;
    executions: Array<any> | undefined;
    nodeDetails: any | undefined;
    workflowNodes: WorkflowNode[] | undefined;
    isLoading: boolean;
    selectedWorkflowId: string | null;
    selectedNodeId: string | null;
    playbackSpeed: number;
    selectWorkflow: (workflowId: string) => void;
    selectNode: (nodeId: string | null) => void;
    setPlaybackSpeed: (speed: number) => void;
}

/**
 * Custom hook for workflow feature orchestration.
 *
 * @param tenantId - Tenant identifier
 * @returns Workflow orchestration state and methods
 */
export function useWorkflowOrchestration(tenantId: string): UseWorkflowOrchestrationReturn {
    const [selectedWorkflowId, setSelectedWorkflowId] = useAtom(selectedWorkflowIdAtom);
    const [selectedNodeId, setSelectedNodeId] = useAtom(selectedNodeIdAtom);
    const [playbackSpeed, setPlaybackSpeed] = useAtom(playbackSpeedAtom);

    // Fetch workflows
    const { data: workflows, isLoading: workflowsLoading } = useQuery({
        queryKey: ['workflows', tenantId],
        queryFn: () => workflowApi.getWorkflows(tenantId || 'default'),
        staleTime: 30 * 1000,
        enabled: !!tenantId,
    });

    // Fetch workflow nodes for selected workflow
    const { data: workflowNodes, isLoading: nodesLoading } = useQuery({
        queryKey: ['workflowNodes', selectedWorkflowId, tenantId],
        queryFn: () =>
            selectedWorkflowId
                ? workflowApi.getWorkflowNodes(selectedWorkflowId, tenantId || 'default')
                : Promise.resolve(null),
        staleTime: 30 * 1000,
        enabled: !!selectedWorkflowId && !!tenantId,
    });

    // Fetch executions for selected workflow
    const { data: executions, isLoading: executionsLoading } = useQuery({
        queryKey: ['workflowExecutions', selectedWorkflowId, tenantId],
        queryFn: () =>
            selectedWorkflowId
                ? workflowApi.getWorkflowExecutions(selectedWorkflowId, tenantId || 'default', 10)
                : Promise.resolve(null),
        staleTime: 10 * 1000,
        refetchInterval: 5 * 1000,
        enabled: !!selectedWorkflowId && !!tenantId,
    });

    // Fetch node details
    const { data: nodeDetails, isLoading: nodeDetailsLoading } = useQuery({
        queryKey: ['nodeDetails', selectedWorkflowId, selectedNodeId, tenantId],
        queryFn: () =>
            selectedWorkflowId && selectedNodeId
                ? workflowApi.getNodeDetails(selectedWorkflowId, selectedNodeId, tenantId || 'default')
                : Promise.resolve(null),
        staleTime: 30 * 1000,
        enabled: !!selectedWorkflowId && !!selectedNodeId && !!tenantId,
    });

    // Combined loading state
    const isLoading = useMemo(
        () => workflowsLoading || nodesLoading || executionsLoading || nodeDetailsLoading,
        [workflowsLoading, nodesLoading, executionsLoading, nodeDetailsLoading]
    );

    // Handler callbacks
    const selectWorkflow = useCallback(
        (workflowId: string) => {
            setSelectedWorkflowId(workflowId);
            setSelectedNodeId(null); // Reset node selection
        },
        [setSelectedWorkflowId, setSelectedNodeId]
    );

    const selectNode = useCallback(
        (nodeId: string | null) => {
            setSelectedNodeId(nodeId);
        },
        [setSelectedNodeId]
    );

    const handleSetPlaybackSpeed = useCallback(
        (speed: number) => {
            setPlaybackSpeed(speed);
        },
        [setPlaybackSpeed]
    );

    return {
        workflows,
        executions: executions || [],
        nodeDetails,
        workflowNodes: workflowNodes?.nodes || [],
        isLoading,
        selectedWorkflowId,
        selectedNodeId,
        playbackSpeed,
        selectWorkflow,
        selectNode,
        setPlaybackSpeed: handleSetPlaybackSpeed,
    };
}
