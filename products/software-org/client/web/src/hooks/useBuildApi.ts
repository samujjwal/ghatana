/**
 * Build API Hooks
 *
 * React Query hooks for Build API endpoints (Workflows, Agents, Simulator).
 * Covers Build journeys B1-B3 from SOFTWARE_ORG_BUILD_IMPLEMENTATION_PLAN.md.
 *
 * @doc.type hooks
 * @doc.purpose Build API data fetching and mutations
 * @doc.layer product
 * @doc.pattern React Query
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/services/api';

// =============================================================================
// Types (matching backend types/build.ts)
// =============================================================================

export interface WorkflowTrigger {
    type: string;
    event?: string;
    schedule?: string;
    conditions?: Array<Record<string, unknown>>;
}

export interface WorkflowStep {
    id: string;
    name: string;
    type: string;
    description?: string;
    config?: Record<string, unknown>;
    timeout?: string;
}

export interface WorkflowResponse {
    id: string;
    tenantId: string;
    name: string;
    slug: string;
    description: string | null;
    status: string;
    ownerTeamId: string | null;
    trigger: WorkflowTrigger;
    steps: WorkflowStep[];
    serviceIds: string[];
    policyIds: string[];
    createdAt: string;
    updatedAt: string;
}

export interface WorkflowCreateBody {
    tenantId: string;
    name: string;
    slug: string;
    description?: string;
    ownerTeamId?: string;
    trigger: WorkflowTrigger;
    steps: WorkflowStep[];
}

export interface WorkflowUpdateBody {
    name?: string;
    description?: string;
    ownerTeamId?: string;
    trigger?: WorkflowTrigger;
    steps?: WorkflowStep[];
    serviceIds?: string[];
    policyIds?: string[];
}

export interface AgentResponse {
    id: string;
    tenantId: string;
    name: string;
    slug: string;
    description: string | null;
    type: string;
    status: string;
    ownerTeamId: string | null;
    personaId: string | null;
    tools: string[];
    guardrails: Record<string, unknown>;
    serviceIds: string[];
    version?: string;
    services?: Array<{id: string; name: string}>;
    createdAt: string;
    updatedAt: string;
}

export interface AgentCreateBody {
    tenantId: string;
    name: string;
    slug: string;
    description?: string;
    type: string;
    ownerTeamId?: string;
    personaId?: string;
    tools: string[];
    guardrails: Record<string, unknown>;
    serviceIds?: string[];
}

export interface AgentUpdateBody {
    name?: string;
    slug?: string;
    description?: string;
    type?: string;
    ownerTeamId?: string;
    personaId?: string;
    tools?: string[];
    guardrails?: Record<string, unknown>;
    serviceIds?: string[];
}

export interface SimulateRequest {
    workflowId?: string;
    agentId?: string;
    tenantId: string;
    environment: string;
    eventPayload: Record<string, unknown>;
}

export interface SimulateResponse {
    simulationId: string;
    status: 'success' | 'failure';
    trace: {
        step: string;
        timestamp: string;
        action: string;
        result: Record<string, unknown>;
    }[];
    policyBlocks: {
        policyId: string;
        policyName: string;
        reason: string;
    }[];
    duration: number;
}

// =============================================================================
// Workflow Hooks
// =============================================================================

/**
 * Fetch workflows for a tenant
 */
export function useWorkflows(tenantId: string, status?: string) {
    return useQuery({
        queryKey: ['build', 'workflows', tenantId, status],
        queryFn: async () => {
            const params = new URLSearchParams({ tenantId });
            if (status) params.append('status', status);
            
            const response = await apiClient.get(`/build/workflows?${params}`);
            return response.data as { data: WorkflowResponse[]; pagination: { page: number; pageSize: number; total: number } };
        },
        enabled: !!tenantId,
    });
}

/**
 * Fetch single workflow by ID
 */
export function useWorkflow(workflowId: string, tenantId: string) {
    return useQuery({
        queryKey: ['build', 'workflows', workflowId],
        queryFn: async () => {
            const response = await apiClient.get(
                `/build/workflows/${workflowId}?tenantId=${tenantId}`
            );
            return response.data as WorkflowResponse;
        },
        enabled: !!workflowId && !!tenantId,
    });
}

/**
 * Create new workflow
 */
export function useCreateWorkflow() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (data: WorkflowCreateBody) => {
            const response = await apiClient.post('/build/workflows', data);
            return response.data as WorkflowResponse;
        },
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: ['build', 'workflows', variables.tenantId] });
        },
    });
}

/**
 * Update workflow
 */
export function useUpdateWorkflow(workflowId: string, tenantId: string) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (data: WorkflowUpdateBody) => {
            const response = await apiClient.put(
                `/build/workflows/${workflowId}?tenantId=${tenantId}`,
                data
            );
            return response.data as WorkflowResponse;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['build', 'workflows', workflowId] });
            queryClient.invalidateQueries({ queryKey: ['build', 'workflows', tenantId] });
        },
    });
}

/**
 * Activate workflow
 */
export function useActivateWorkflow(workflowId: string, tenantId: string) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async () => {
            const response = await apiClient.post(
                `/build/workflows/${workflowId}/activate?tenantId=${tenantId}`
            );
            return response.data;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['build', 'workflows', workflowId] });
            queryClient.invalidateQueries({ queryKey: ['build', 'workflows', tenantId] });
        },
    });
}

// =============================================================================
// Agent Hooks
// =============================================================================

/**
 * Fetch agents for a tenant
 */
export function useAgents(tenantId: string, status?: string) {
    return useQuery({
        queryKey: ['build', 'agents', tenantId, status],
        queryFn: async () => {
            const params = new URLSearchParams({ tenantId });
            if (status) params.append('status', status);
            
            const response = await apiClient.get(`/build/agents?${params}`);
            return response.data as { data: AgentResponse[]; pagination: { page: number; pageSize: number; total: number } };
        },
        enabled: !!tenantId,
    });
}

/**
 * Fetch single agent by ID
 */
export function useAgent(agentId: string, tenantId: string) {
    return useQuery({
        queryKey: ['build', 'agents', agentId],
        queryFn: async () => {
            const response = await apiClient.get(
                `/build/agents/${agentId}?tenantId=${tenantId}`
            );
            return response.data as AgentResponse;
        },
        enabled: !!agentId && !!tenantId,
    });
}

/**
 * Create new agent
 */
export function useCreateAgent() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (data: AgentCreateBody) => {
            const response = await apiClient.post('/build/agents', data);
            return response.data as AgentResponse;
        },
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: ['build', 'agents', variables.tenantId] });
        },
    });
}

/**
 * Update agent
 */
export function useUpdateAgent(agentId: string, tenantId: string) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (data: AgentUpdateBody) => {
            const response = await apiClient.put(
                `/build/agents/${agentId}?tenantId=${tenantId}`,
                data
            );
            return response.data as AgentResponse;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['build', 'agents', agentId] });
            queryClient.invalidateQueries({ queryKey: ['build', 'agents', tenantId] });
        },
    });
}

/**
 * Activate agent
 */
export function useActivateAgent(agentId: string, tenantId: string) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async () => {
            const response = await apiClient.post(
                `/build/agents/${agentId}/activate?tenantId=${tenantId}`
            );
            return response.data;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['build', 'agents', agentId] });
            queryClient.invalidateQueries({ queryKey: ['build', 'agents', tenantId] });
        },
    });
}

// =============================================================================
// Simulator Hooks
// =============================================================================

/**
 * Run simulation
 */
export function useRunSimulation() {
    return useMutation({
        mutationFn: async (data: SimulateRequest) => {
            const response = await apiClient.post('/build/simulator/run', data);
            return response.data as SimulateResponse;
        },
    });
}
