/**
 * Hook for fetching departments from backend API
 *
 * <p><b>Purpose</b><br>
 * TanStack Query hook that fetches department list from the real backend API.
 * Replaces mock data with actual API calls to /api/v1/departments.
 *
 * <p><b>Features</b><br>
 * - List departments from backend
 * - Get department details with agents and workflows
 * - Get department KPIs
 * - Automatic caching and stale-while-revalidate
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { data: departments } = useDepartmentsApi();
 * const { data: detail } = useDepartmentDetail(deptId);
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose Department API data fetching
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export interface DepartmentSummary {
    id: string;
    name: string;
    type: string;
    agentCount: number;
    status: string;
}

export interface AgentInfo {
    id: string;
    name: string;
    role: string;
    status: string;
}

export interface WorkflowInfo {
    id: string;
    name: string;
    type: string;
    status: string;
}

export interface DepartmentDetail {
    id: string;
    name: string;
    type: string;
    description: string;
    status: string;
    agentCount: number;
    agents: AgentInfo[];
    workflows: WorkflowInfo[];
}

export interface DepartmentKpis {
    departmentId: string;
    velocity: number;
    throughput: number;
    quality: number;
    efficiency: number;
    timestamp: string;
}

/**
 * Fetch all departments
 */
export function useDepartmentsApi(options: { enabled?: boolean } = {}) {
    const { enabled = true } = options;

    return useQuery({
        queryKey: ["departments", "list"],
        queryFn: async (): Promise<DepartmentSummary[]> => {
            const response = await fetch(`${API_BASE_URL}/api/v1/departments`);
            if (!response.ok) {
                throw new Error(`Failed to fetch departments: ${response.statusText}`);
            }
            return response.json();
        },
        staleTime: 1000 * 60 * 5, // 5 minutes
        gcTime: 1000 * 60 * 10, // 10 minutes
        enabled,
        retry: 2,
    });
}

/**
 * Fetch department details
 */
export function useDepartmentDetail(departmentId: string, options: { enabled?: boolean } = {}) {
    const { enabled = true } = options;

    return useQuery({
        queryKey: ["departments", "detail", departmentId],
        queryFn: async (): Promise<DepartmentDetail> => {
            const response = await fetch(`${API_BASE_URL}/api/v1/departments/${departmentId}`);
            if (!response.ok) {
                throw new Error(`Failed to fetch department detail: ${response.statusText}`);
            }
            return response.json();
        },
        staleTime: 1000 * 60 * 5,
        gcTime: 1000 * 60 * 10,
        enabled: enabled && !!departmentId,
        retry: 2,
    });
}

/**
 * Fetch department agents
 */
export function useDepartmentAgents(departmentId: string, options: { enabled?: boolean } = {}) {
    const { enabled = true } = options;

    return useQuery({
        queryKey: ["departments", "agents", departmentId],
        queryFn: async (): Promise<AgentInfo[]> => {
            const response = await fetch(`${API_BASE_URL}/api/v1/departments/${departmentId}/agents`);
            if (!response.ok) {
                throw new Error(`Failed to fetch department agents: ${response.statusText}`);
            }
            return response.json();
        },
        staleTime: 1000 * 60 * 2,
        gcTime: 1000 * 60 * 5,
        enabled: enabled && !!departmentId,
        retry: 2,
    });
}

/**
 * Fetch department KPIs
 */
export function useDepartmentKpis(departmentId: string, options: { enabled?: boolean } = {}) {
    const { enabled = true } = options;

    return useQuery({
        queryKey: ["departments", "kpis", departmentId],
        queryFn: async (): Promise<DepartmentKpis> => {
            const response = await fetch(`${API_BASE_URL}/api/v1/departments/${departmentId}/kpis`);
            if (!response.ok) {
                throw new Error(`Failed to fetch department KPIs: ${response.statusText}`);
            }
            return response.json();
        },
        staleTime: 1000 * 60 * 1, // 1 minute for KPIs
        gcTime: 1000 * 60 * 5,
        enabled: enabled && !!departmentId,
        retry: 2,
    });
}

/**
 * Fetch department workflows
 */
export function useDepartmentWorkflows(departmentId: string, options: { enabled?: boolean } = {}) {
    const { enabled = true } = options;

    return useQuery({
        queryKey: ["departments", "workflows", departmentId],
        queryFn: async (): Promise<WorkflowInfo[]> => {
            const response = await fetch(`${API_BASE_URL}/api/v1/departments/${departmentId}/workflows`);
            if (!response.ok) {
                throw new Error(`Failed to fetch department workflows: ${response.statusText}`);
            }
            return response.json();
        },
        staleTime: 1000 * 60 * 2,
        gcTime: 1000 * 60 * 5,
        enabled: enabled && !!departmentId,
        retry: 2,
    });
}

export default useDepartmentsApi;
