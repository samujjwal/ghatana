/**
 * Organization & Structure API Hooks
 *
 * React Query hooks for organization structure, departments, agents,
 * and the genesis wizard.
 *
 * @doc.type hooks
 * @doc.purpose Organization API integration
 * @doc.layer product
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/services/api';

// =============================================================================
// Types
// =============================================================================

export interface Department {
    id: string;
    organizationId: string;
    name: string;
    type: string;
    description: string | null;
    status: string;
    configuration: Record<string, unknown>;
    createdAt: string;
    updatedAt: string;
    agents?: Agent[];
    teams?: Team[];
    _count?: {
        agents: number;
        teams: number;
        workflows: number;
    };
}

export interface Agent {
    id: string;
    organizationId: string;
    departmentId: string;
    name: string;
    role: string;
    status: 'ONLINE' | 'OFFLINE' | 'BUSY';
    capabilities: string[];
    configuration: Record<string, unknown>;
    department?: Department;
    createdAt: string;
    updatedAt: string;
}

export interface Team {
    id: string;
    tenantId: string;
    departmentId: string;
    name: string;
    slug: string;
    description: string | null;
    status: string;
    createdAt: string;
    updatedAt: string;
}

export interface OrgStructure {
    departments: Department[];
    totalAgents: number;
    totalTeams: number;
    totalWorkflows: number;
}

export interface GenesisRequest {
    name: string;
    vision: string;
    template: 'startup' | 'scaleup' | 'enterprise' | 'custom';
    departments?: string[];
    agentCount?: number;
    options?: {
        enableCompliance?: boolean;
        enableSecurity?: boolean;
        enableAI?: boolean;
    };
}

export interface GeneratedOrg {
    id: string;
    name: string;
    namespace: string;
    vision: string;
    departments: Array<{
        name: string;
        type: string;
        agents: Array<{ name: string; role: string }>;
    }>;
    norms: string[];
    estimatedAgentCount: number;
}

// =============================================================================
// Query Keys
// =============================================================================

export const orgQueryKeys = {
    all: ['organization'] as const,
    structure: () => [...orgQueryKeys.all, 'structure'] as const,
    departments: () => [...orgQueryKeys.all, 'departments'] as const,
    department: (id: string) => [...orgQueryKeys.all, 'department', id] as const,
    agents: () => [...orgQueryKeys.all, 'agents'] as const,
    agentsByDept: (deptId: string) => [...orgQueryKeys.all, 'agents', 'department', deptId] as const,
    agent: (id: string) => [...orgQueryKeys.all, 'agent', id] as const,
    teams: () => [...orgQueryKeys.all, 'teams'] as const,
    graph: () => [...orgQueryKeys.all, 'graph'] as const,
};

// =============================================================================
// Hooks
// =============================================================================

/**
 * Fetch organization structure (departments with agents)
 */
export function useOrgStructure(tenantId?: string) {
    return useQuery({
        queryKey: [...orgQueryKeys.structure(), tenantId],
        queryFn: async () => {
            const params = new URLSearchParams();
            if (tenantId) params.append('tenantId', tenantId);
            const response = await apiClient.get(`/org/structure?${params}`);
            return response.data as OrgStructure;
        },
        enabled: !!tenantId,
    });
}

/**
 * Fetch all departments
 */
export function useDepartments(tenantId?: string) {
    return useQuery({
        queryKey: [...orgQueryKeys.departments(), tenantId],
        queryFn: async () => {
            const params = new URLSearchParams();
            if (tenantId) params.append('tenantId', tenantId);
            const response = await apiClient.get(`/org/departments?${params}`);
            return (response.data?.data || response.data || []) as Department[];
        },
        enabled: !!tenantId,
    });
}

/**
 * Fetch single department with agents
 */
export function useDepartment(id: string) {
    return useQuery({
        queryKey: orgQueryKeys.department(id),
        queryFn: async () => {
            const response = await apiClient.get(`/org/departments/${id}`);
            return (response.data?.data || response.data) as Department;
        },
        enabled: !!id,
    });
}

/**
 * Fetch all agents
 */
export function useAgents(tenantId?: string, departmentId?: string) {
    return useQuery({
        queryKey: departmentId ? orgQueryKeys.agentsByDept(departmentId) : [...orgQueryKeys.agents(), tenantId],
        queryFn: async () => {
            const params = new URLSearchParams();
            if (tenantId) params.append('tenantId', tenantId);
            if (departmentId) params.append('departmentId', departmentId);
            const response = await apiClient.get(`/config/agents?${params}`);
            return (response.data?.data || response.data || []) as Agent[];
        },
        enabled: !!tenantId,
    });
}

/**
 * Fetch organization graph for visualization
 */
export function useOrgGraph(tenantId?: string) {
    return useQuery({
        queryKey: [...orgQueryKeys.graph(), tenantId],
        queryFn: async () => {
            const params = new URLSearchParams();
            if (tenantId) params.append('tenantId', tenantId);
            const response = await apiClient.get(`/org/graph?${params}`);
            return response.data;
        },
        enabled: !!tenantId,
    });
}

/**
 * Genesis - Generate organization structure with AI
 */
export function useGenerateOrganization() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (request: GenesisRequest) => {
            const response = await apiClient.post('/org/genesis/generate', request);
            return response.data as GeneratedOrg;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.all });
        },
    });
}

/**
 * Genesis - Materialize (create) the generated organization
 */
export function useMaterializeOrganization() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (org: GeneratedOrg) => {
            const response = await apiClient.post('/org/genesis/materialize', org);
            return response.data;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.all });
        },
    });
}

/**
 * Add agent to department
 */
export function useAddAgentToDepartment() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({ departmentId, agent }: { departmentId: string; agent: Partial<Agent> }) => {
            const response = await apiClient.post(`/org/departments/${departmentId}/agents`, agent);
            return response.data as Agent;
        },
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.department(variables.departmentId) });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.agents() });
        },
    });
}

/**
 * Move agent between departments
 */
export function useMoveAgent() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({ agentId, fromDeptId, toDeptId }: { agentId: string; fromDeptId: string; toDeptId: string }) => {
            const response = await apiClient.post('/org/hierarchy/move', {
                nodeId: agentId,
                nodeType: 'agent',
                fromParentId: fromDeptId,
                toParentId: toDeptId,
            });
            return response.data;
        },
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.department(variables.fromDeptId) });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.department(variables.toDeptId) });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.agents() });
        },
    });
}

/**
 * Create new department
 */
export function useCreateDepartment() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (dept: Partial<Department>) => {
            const response = await apiClient.post('/org/departments', dept);
            return response.data as Department;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.departments() });
            queryClient.invalidateQueries({ queryKey: orgQueryKeys.structure() });
        },
    });
}
