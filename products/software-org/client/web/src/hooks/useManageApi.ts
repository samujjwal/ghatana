/**
 * Manage API Hooks
 *
 * React Query hooks for the MANAGE section:
 * - Norms (rules/policies)
 * - Budget planning
 * - Agent marketplace
 *
 * @doc.type hooks
 * @doc.purpose Manage API integration
 * @doc.layer product
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/services/api';

// =============================================================================
// Types - Norms
// =============================================================================

export interface Norm {
    id: string;
    tenantId?: string;
    name: string;
    description: string;
    category: 'quality' | 'security' | 'compliance' | 'operational' | 'custom';
    type: 'policy' | 'guideline' | 'standard' | 'constraint';
    rule: string;
    severity: 'info' | 'warning' | 'error' | 'critical';
    enabled: boolean;
    enforcementLevel: 'advisory' | 'soft' | 'hard';
    scope: string[];
    conditions?: Record<string, unknown>;
    actions?: Record<string, unknown>;
    metadata?: Record<string, unknown>;
    createdAt: string;
    updatedAt: string;
}

export interface CreateNormRequest {
    name: string;
    description: string;
    category: Norm['category'];
    type: Norm['type'];
    rule: string;
    severity?: Norm['severity'];
    enabled?: boolean;
    enforcementLevel?: Norm['enforcementLevel'];
    scope?: string[];
}

// =============================================================================
// Types - Budget
// =============================================================================

export interface BudgetCategory {
    id: string;
    name: string;
    allocated: number;
    spent: number;
    color: string;
}

export interface DepartmentBudget {
    id: string;
    departmentId: string;
    departmentName: string;
    year: number;
    quarter?: string;
    allocated: number;
    spent: number;
    proposedAllocated: number;
    categories: Record<string, number>;
    justification: string;
    status: 'draft' | 'pending' | 'approved' | 'rejected';
    createdAt: string;
    updatedAt: string;
}

export interface BudgetPlan {
    year: number;
    quarter?: string;
    totalBudget: number;
    totalSpent: number;
    totalBudgetLimit: number;
    budgets: DepartmentBudget[];
    status: 'draft' | 'pending' | 'approved';
}

export interface BudgetUpdateRequest {
    budgets: Array<{
        departmentId: string;
        proposedAllocated: number;
        categories: Record<string, number>;
        justification: string;
    }>;
    status?: string;
}

// =============================================================================
// Types - Agent Marketplace
// =============================================================================

export interface AgentTemplate {
    id: string;
    name: string;
    description: string;
    category: 'engineering' | 'qa' | 'devops' | 'security' | 'data' | 'management';
    role: string;
    icon: string;
    capabilities: string[];
    skills: string[];
    defaultConfig: Record<string, unknown>;
    pricing: {
        type: 'free' | 'standard' | 'premium';
        creditsPerMonth?: number;
    };
    popularity: number;
    rating: number;
    reviewCount: number;
    isNew?: boolean;
    isFeatured?: boolean;
    createdAt: string;
}

export interface DeployAgentRequest {
    templateId: string;
    name: string;
    departmentId: string;
    configuration?: Record<string, unknown>;
}

// =============================================================================
// Query Keys
// =============================================================================

export const manageQueryKeys = {
    norms: {
        all: ['norms'] as const,
        list: (tenantId?: string) => [...manageQueryKeys.norms.all, 'list', tenantId] as const,
        detail: (id: string) => [...manageQueryKeys.norms.all, 'detail', id] as const,
        byCategory: (category: string) => [...manageQueryKeys.norms.all, 'category', category] as const,
    },
    budget: {
        all: ['budget'] as const,
        plan: (year: number, quarter?: string) => [...manageQueryKeys.budget.all, 'plan', year, quarter] as const,
        departments: (year: number) => [...manageQueryKeys.budget.all, 'departments', year] as const,
        history: () => [...manageQueryKeys.budget.all, 'history'] as const,
    },
    agents: {
        all: ['agent-marketplace'] as const,
        templates: () => [...manageQueryKeys.agents.all, 'templates'] as const,
        template: (id: string) => [...manageQueryKeys.agents.all, 'template', id] as const,
        deployed: (tenantId?: string) => [...manageQueryKeys.agents.all, 'deployed', tenantId] as const,
    },
};

// =============================================================================
// Norms Hooks
// =============================================================================

/**
 * Fetch all norms
 */
export function useNorms(tenantId?: string, category?: string) {
    return useQuery({
        queryKey: category 
            ? manageQueryKeys.norms.byCategory(category)
            : manageQueryKeys.norms.list(tenantId),
        queryFn: async () => {
            const params = new URLSearchParams();
            if (tenantId) params.append('tenantId', tenantId);
            if (category) params.append('category', category);
            const response = await apiClient.get(`/norms?${params}`);
            return (response.data?.data || response.data || []) as Norm[];
        },
    });
}

/**
 * Fetch single norm
 */
export function useNorm(id: string) {
    return useQuery({
        queryKey: manageQueryKeys.norms.detail(id),
        queryFn: async () => {
            const response = await apiClient.get(`/norms/${id}`);
            return (response.data?.data || response.data) as Norm;
        },
        enabled: !!id,
    });
}

/**
 * Create new norm
 */
export function useCreateNorm() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (norm: CreateNormRequest) => {
            const response = await apiClient.post('/norms', norm);
            return response.data as Norm;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: manageQueryKeys.norms.all });
        },
    });
}

/**
 * Update norm
 */
export function useUpdateNorm() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({ id, data }: { id: string; data: Partial<Norm> }) => {
            const response = await apiClient.put(`/norms/${id}`, data);
            return response.data as Norm;
        },
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: manageQueryKeys.norms.detail(variables.id) });
            queryClient.invalidateQueries({ queryKey: manageQueryKeys.norms.all });
        },
    });
}

/**
 * Delete norm
 */
export function useDeleteNorm() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (id: string) => {
            await apiClient.delete(`/norms/${id}`);
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: manageQueryKeys.norms.all });
        },
    });
}

/**
 * Toggle norm enabled/disabled
 */
export function useToggleNorm() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({ id, enabled }: { id: string; enabled: boolean }) => {
            const response = await apiClient.patch(`/norms/${id}/toggle`, { enabled });
            return response.data as Norm;
        },
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: manageQueryKeys.norms.detail(variables.id) });
            queryClient.invalidateQueries({ queryKey: manageQueryKeys.norms.all });
        },
    });
}

// =============================================================================
// Budget Hooks
// =============================================================================

/**
 * Fetch budget plan for a year/quarter
 */
export function useBudgetPlan(year: number, quarter?: string, tenantId?: string) {
    return useQuery({
        queryKey: [...manageQueryKeys.budget.plan(year, quarter), tenantId],
        queryFn: async () => {
            const params = new URLSearchParams();
            params.append('year', year.toString());
            if (quarter) params.append('quarter', quarter);
            if (tenantId) params.append('tenantId', tenantId);
            const response = await apiClient.get(`/budgets?${params}`);
            return (response.data?.data || response.data) as BudgetPlan;
        },
    });
}

/**
 * Update budget allocations
 */
export function useUpdateBudget() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({ year, quarter, data }: { year: number; quarter?: string; data: BudgetUpdateRequest }) => {
            const response = await apiClient.put(`/budgets/${year}${quarter ? `?quarter=${quarter}` : ''}`, data);
            return response.data as BudgetPlan;
        },
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: manageQueryKeys.budget.plan(variables.year, variables.quarter) });
        },
    });
}

/**
 * Submit budget for approval
 */
export function useSubmitBudget() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({ year, quarter }: { year: number; quarter?: string }) => {
            const response = await apiClient.post(`/budgets/${year}/submit${quarter ? `?quarter=${quarter}` : ''}`);
            return response.data;
        },
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: manageQueryKeys.budget.plan(variables.year, variables.quarter) });
        },
    });
}

// =============================================================================
// Agent Marketplace Hooks
// =============================================================================

/**
 * Fetch available agent templates
 */
export function useAgentTemplates(category?: string) {
    return useQuery({
        queryKey: [...manageQueryKeys.agents.templates(), category],
        queryFn: async () => {
            const params = new URLSearchParams();
            if (category) params.append('category', category);
            const response = await apiClient.get(`/agents/marketplace?${params}`);
            return (response.data?.data || response.data || []) as AgentTemplate[];
        },
    });
}

/**
 * Fetch single agent template
 */
export function useAgentTemplate(id: string) {
    return useQuery({
        queryKey: manageQueryKeys.agents.template(id),
        queryFn: async () => {
            const response = await apiClient.get(`/agents/marketplace/${id}`);
            return (response.data?.data || response.data) as AgentTemplate;
        },
        enabled: !!id,
    });
}

/**
 * Deploy agent from template
 */
export function useDeployAgent() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (request: DeployAgentRequest) => {
            const response = await apiClient.post('/agents/deploy', request);
            return response.data;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: manageQueryKeys.agents.deployed() });
        },
    });
}

/**
 * Fetch deployed agents
 */
export function useDeployedAgents(tenantId?: string) {
    return useQuery({
        queryKey: manageQueryKeys.agents.deployed(tenantId),
        queryFn: async () => {
            const params = new URLSearchParams();
            if (tenantId) params.append('tenantId', tenantId);
            const response = await apiClient.get(`/agents/deployed?${params}`);
            return (response.data?.data || response.data || []) as AgentTemplate[];
        },
        enabled: !!tenantId,
    });
}
