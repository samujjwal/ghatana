/**
 * Configuration Data Hooks
 *
 * React Query hooks for fetching personas, domains, tasks, templates, and workflows
 * from the backend API. Replaces hardcoded mock data with real backend calls.
 *
 * @doc.type hook
 * @doc.purpose Fetch config data from backend
 * @doc.layer product
 * @doc.pattern Data Fetching Hook
 */

import { useQuery, type UseQueryResult } from '@tanstack/react-query';

/**
 * API Configuration
 */
const apiOrigin = import.meta.env.VITE_API_ORIGIN;
const API_BASE_URL = import.meta.env.DEV
  ? typeof apiOrigin === 'string' && apiOrigin.length > 0
    ? apiOrigin
    : 'http://localhost:7002'
  : '';

function getResponseData<T>(payload: unknown): T {
  if (typeof payload === 'object' && payload !== null && 'data' in payload) {
    return (payload as { data: T }).data;
  }

  return payload as T;
}

/**
 * Generic REST API fetch helper
 */
async function fetchConfig<T = unknown>(endpoint: string): Promise<T> {
  const response = await fetch(`${API_BASE_URL}/api/config/${endpoint}`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`API request failed: ${response.statusText}`);
  }

  const json: unknown = await response.json();
  return getResponseData<T>(json);
}

/**
 * Persona configuration
 */
export interface PersonaConfig {
  id: string;
  label: string;
  description: string;
  icon: string;
  color: string;
  focusAreas: string[];
}

/**
 * Task domain configuration
 */
export interface TaskDomain {
  id: string;
  name: string;
  description?: string;
  primary_personas: string[];
  secondary_personas: string[];
}

/**
 * Task template configuration
 */
export interface TaskTemplate {
  id: string;
  name: string;
  description: string;
  domain: string;
  steps: Array<{
    id: string;
    title: string;
    description: string;
  }>;
  estimatedTime?: number;
}

/**
 * Task data (when backend is ready)
 */
export interface TaskData {
  id: string;
  title: string;
  status: 'todo' | 'in-progress' | 'blocked' | 'done';
  priority: 'high' | 'medium' | 'low';
  assignee:
    | string
    | {
        name: string;
        avatar?: string;
        initials: string;
      };
  phase: string;
  dueDate: string;
  description?: string;
  team?: string;
}

/**
 * Workflow configuration
 */
export interface WorkflowConfig {
  id: string;
  name: string;
  description: string;
  stages: string[];
  automations?: Record<string, unknown>;
}

/**
 * Query keys for React Query
 */
export const configQueryKeys = {
  personas: ['config', 'personas'] as const,
  persona: (id: string) => ['config', 'personas', id] as const,
  domains: ['config', 'domains'] as const,
  domain: (id: string) => ['config', 'domains', id] as const,
  templates: ['config', 'templates'] as const,
  template: (id: string) => ['config', 'templates', id] as const,
  workflows: ['config', 'workflows'] as const,
  workflow: (id: string) => ['config', 'workflows', id] as const,
  tasks: ['config', 'tasks'] as const,
  task: (id: string) => ['config', 'tasks', id] as const,
};

/**
 * Hook to fetch all personas
 */
export function usePersonas(): UseQueryResult<PersonaConfig[], unknown> {
  return useQuery({
    queryKey: configQueryKeys.personas,
    queryFn: () => fetchConfig<PersonaConfig[]>('personas'),
    staleTime: 10 * 60 * 1000, // 10 minutes
  });
}

/**
 * Hook to fetch a single persona by ID
 */
export function usePersona(id: string): UseQueryResult<PersonaConfig, unknown> {
  return useQuery({
    queryKey: configQueryKeys.persona(id),
    queryFn: () => fetchConfig<PersonaConfig>(`personas/${id}`),
    enabled: !!id,
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * Hook to fetch all task domains
 */
export function useDomains(): UseQueryResult<TaskDomain[], unknown> {
  return useQuery({
    queryKey: configQueryKeys.domains,
    queryFn: () => fetchConfig<TaskDomain[]>('domains'),
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * Hook to fetch a single domain by ID
 */
export function useDomain(id: string): UseQueryResult<TaskDomain, unknown> {
  return useQuery({
    queryKey: configQueryKeys.domain(id),
    queryFn: () => fetchConfig<TaskDomain>(`domains/${id}`),
    enabled: !!id,
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * Hook to fetch all task templates
 */
export function useTemplates(): UseQueryResult<TaskTemplate[], unknown> {
  return useQuery({
    queryKey: configQueryKeys.templates,
    queryFn: () => fetchConfig<TaskTemplate[]>('templates'),
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * Hook to fetch a single template by ID
 */
export function useTemplate(id: string): UseQueryResult<TaskTemplate, unknown> {
  return useQuery({
    queryKey: configQueryKeys.template(id),
    queryFn: () => fetchConfig<TaskTemplate>(`templates/${id}`),
    enabled: !!id,
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * Hook to fetch all workflows
 */
export function useWorkflows(): UseQueryResult<WorkflowConfig[], unknown> {
  return useQuery({
    queryKey: configQueryKeys.workflows,
    queryFn: () => fetchConfig<WorkflowConfig[]>('workflows'),
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * Hook to fetch a single workflow by ID
 */
export function useWorkflow(id: string): UseQueryResult<WorkflowConfig, unknown> {
  return useQuery({
    queryKey: configQueryKeys.workflow(id),
    queryFn: () => fetchConfig<WorkflowConfig>(`workflows/${id}`),
    enabled: !!id,
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * Hook to fetch all tasks (when backend is ready)
 * For now, returns empty array - components should provide their own mock data
 */
export function useTasks(filters?: {
  status?: string;
  assignee?: string;
  phase?: string;
}): UseQueryResult<TaskData[], unknown> {
  return useQuery({
    queryKey: [...configQueryKeys.tasks, filters],
    queryFn: async () => {
      try {
        const queryParams = new URLSearchParams();
        if (filters?.status) queryParams.set('status', filters.status);
        if (filters?.assignee) queryParams.set('assignee', filters.assignee);
        if (filters?.phase) queryParams.set('phase', filters.phase);

        const endpoint =
          queryParams.size > 0 ? `tasks?${queryParams.toString()}` : 'tasks';
        return await fetchConfig<TaskData[]>(endpoint);
      } catch (error) {
        // Backend not ready - return empty array
        // Components should handle with fallback to mock data
        console.warn('Tasks API not available, using component mock data');
        return [];
      }
    },
    staleTime: 2 * 60 * 1000, // Tasks change more frequently - 2 min cache
  });
}

/**
 * Hook to fetch a single task by ID
 */
export function useTask(id: string): UseQueryResult<TaskData | null, unknown> {
  return useQuery({
    queryKey: configQueryKeys.task(id),
    queryFn: async () => {
      try {
        return await fetchConfig<TaskData>(`tasks/${id}`);
      } catch (error) {
        console.warn(
          `Task ${id} not found in backend, using component mock data`
        );
        return null;
      }
    },
    enabled: !!id,
    staleTime: 2 * 60 * 1000,
  });
}
