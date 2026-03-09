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

import { useQuery } from '@tanstack/react-query';

/**
 * API Configuration
 */
const API_BASE_URL = import.meta.env.DEV
  ? `${import.meta.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}`
  : '';

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

  const json = await response.json();
  return json.data || json;
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
export function usePersonas() {
  return useQuery({
    queryKey: configQueryKeys.personas,
    queryFn: () => fetchConfig<PersonaConfig[]>('personas'),
    staleTime: 10 * 60 * 1000, // 10 minutes
  });
}

/**
 * Hook to fetch a single persona by ID
 */
export function usePersona(id: string) {
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
export function useDomains() {
  return useQuery({
    queryKey: configQueryKeys.domains,
    queryFn: async () => {
      const data = await graphql<{ domains: TaskDomain[] }>(`
        query GetDomains {
          domains {
            id
            name
            description
            primary_personas
            secondary_personas
          }
        }
      `);
      return data.domains;
    },
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * Hook to fetch a single domain by ID
 */
export function useDomain(id: string) {
  return useQuery({
    queryKey: configQueryKeys.domain(id),
    queryFn: async () => {
      const data = await graphql<{ domain: TaskDomain }>(
        `
          query GetDomain($id: ID!) {
            domain(id: $id) {
              id
              name
              description
              primary_personas
              secondary_personas
            }
          }
        `,
        { id }
      );
      return data.domain;
    },
    enabled: !!id,
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * Hook to fetch all task templates
 */
export function useTemplates() {
  return useQuery({
    queryKey: configQueryKeys.templates,
    queryFn: async () => {
      const data = await graphql<{ templates: TaskTemplate[] }>(`
        query GetTemplates {
          templates {
            id
            name
            description
            domain
            steps {
              id
              title
              description
            }
            estimatedTime
          }
        }
      `);
      return data.templates;
    },
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * Hook to fetch a single template by ID
 */
export function useTemplate(id: string) {
  return useQuery({
    queryKey: configQueryKeys.template(id),
    queryFn: async () => {
      const data = await graphql<{ template: TaskTemplate }>(
        `
          query GetTemplate($id: ID!) {
            template(id: $id) {
              id
              name
              description
              domain
              steps {
                id
                title
                description
              }
              estimatedTime
            }
          }
        `,
        { id }
      );
      return data.template;
    },
    enabled: !!id,
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * Hook to fetch all workflows
 */
export function useWorkflows() {
  return useQuery({
    queryKey: configQueryKeys.workflows,
    queryFn: async () => {
      const data = await graphql<{ workflows: WorkflowConfig[] }>(`
        query GetWorkflows {
          workflows {
            id
            name
            description
            stages
            automations
          }
        }
      `);
      return data.workflows;
    },
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * Hook to fetch a single workflow by ID
 */
export function useWorkflow(id: string) {
  return useQuery({
    queryKey: configQueryKeys.workflow(id),
    queryFn: async () => {
      const data = await graphql<{ workflow: WorkflowConfig }>(
        `
          query GetWorkflow($id: ID!) {
            workflow(id: $id) {
              id
              name
              description
              stages
              automations
            }
          }
        `,
        { id }
      );
      return data.workflow;
    },
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
}) {
  return useQuery({
    queryKey: [...configQueryKeys.tasks, filters],
    queryFn: async () => {
      try {
        const data = await graphql<{ tasks: TaskData[] }>(
          `
            query GetTasks($filters: TaskFiltersInput) {
              tasks(filters: $filters) {
                id
                title
                status
                priority
                assignee
                phase
                dueDate
                description
                team
              }
            }
          `,
          { filters }
        );
        return data.tasks;
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
export function useTask(id: string) {
  return useQuery({
    queryKey: configQueryKeys.task(id),
    queryFn: async () => {
      try {
        const data = await graphql<{ task: TaskData }>(
          `
            query GetTask($id: ID!) {
              task(id: $id) {
                id
                title
                status
                priority
                assignee
                phase
                dueDate
                description
                team
              }
            }
          `,
          { id }
        );
        return data.task;
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
