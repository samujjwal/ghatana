/**
 * useWorkspaceData Hook
 *
 * Provides workspace and project data fetching with TanStack Query.
 * Integrates with Jotai atoms for local state management.
 * Includes AI-powered features for smart suggestions and insights.
 *
 * @doc.type hook
 * @doc.purpose Workspace data fetching and caching
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useCallback, useEffect, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtom, useSetAtom, useAtomValue } from 'jotai';
import {
  currentWorkspaceIdAtom,
  workspaceAtom,
  setCurrentWorkspaceAtom,
  setProjectsAtom,
  addOwnedProjectAtom,
  type Workspace,
  type Project,
  type ProjectWithOwnership,
} from '@/state/atoms/workspaceAtom';

// ============================================================================
// API Functions
// ============================================================================

// All API requests go through a single gateway on port 7002
// The gateway internally routes to appropriate backend services
const API_BASE = import.meta.env.DEV
  ? `${import.meta.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}/api`
  : '/api';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

async function parseJsonResponse(res: Response): Promise<unknown> {
  const contentType = res.headers.get('content-type') ?? '';
  const rawBody = await res.text();

  if (!res.ok) {
    // Better error messages for different status codes
    if (res.status === 503) {
      throw new Error(
        'Database service unavailable. Please ensure the backend services are running.'
      );
    }
    if (res.status >= 500) {
      const snippet = rawBody.slice(0, 300);
      throw new Error(`Server error (${res.status}): ${snippet}`);
    }
    const snippet = rawBody.slice(0, 300);
    throw new Error(`HTTP ${res.status} ${res.statusText}: ${snippet}`);
  }

  // If Vite proxy isn't applied, we can get HTML back (SPA fallback). Make that failure obvious.
  if (!contentType.includes('application/json')) {
    const snippet = rawBody.slice(0, 300);
    throw new Error(`Expected JSON but got '${contentType}': ${snippet}`);
  }

  try {
    return JSON.parse(rawBody) as unknown;
  } catch {
    const snippet = rawBody.slice(0, 300);
    throw new Error(`Invalid JSON response: ${snippet}`);
  }
}

async function fetchWorkspaces(): Promise<Workspace[]> {
  try {
    const res = await fetch(`${API_BASE}/workspaces`);

    const data = await parseJsonResponse(res);

    // Accept either the raw array or the wrapped form { workspaces: [...] }
    if (Array.isArray(data)) return data as Workspace[];
    if (isRecord(data) && Array.isArray(data.workspaces))
      return data.workspaces as Workspace[];

    throw new Error('Unexpected response shape for workspaces');
  } catch (error) {
    // Fallback to mock data when backend is not available
    console.warn(
      'Backend API not available, using fallback workspace data:',
      error
    );
    return [
      {
        id: 'demo-workspace-1',
        name: 'Demo Workspace',
        description: 'A sample workspace for demonstrating the IDE',
        isDefault: true,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        aiSummary: 'Demo workspace for testing IDE features',
        aiTags: ['demo', 'testing', 'ide'],
      },
      {
        id: 'demo-workspace-2',
        name: 'Development Workspace',
        description: 'Workspace for active development projects',
        isDefault: false,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        aiSummary: 'Active development workspace',
        aiTags: ['development', 'active', 'coding'],
      },
    ];
  }
}

async function fetchWorkspace(
  workspaceId: string
): Promise<
  Workspace & { ownedProjects: Project[]; includedProjects: Project[] }
> {
  try {
    const res = await fetch(`${API_BASE}/workspaces/${workspaceId}`);

    const data = await parseJsonResponse(res);

    // Accept either raw workspace or wrapped form { workspace: {...} }
    if (isRecord(data) && isRecord(data.workspace)) {
      return data.workspace as unknown as Workspace & {
        ownedProjects: Project[];
        includedProjects: Project[];
      };
    }
    return data as Workspace & {
      ownedProjects: Project[];
      includedProjects: Project[];
    };
  } catch (error) {
    // If the backend explicitly returned 404, surface the error so the
    // workspace selection logic can pick a different workspace (prevents
    // silently using a fallback that hides configuration issues).
    if (error instanceof Error && error.message.includes('HTTP 404')) {
      throw error;
    }

    // Fallback to mock data when backend is not available
    console.warn(
      'Backend API not available, using fallback workspace detail data:',
      error
    );
    return {
      id: workspaceId,
      name:
        workspaceId === 'demo-workspace-1'
          ? 'Demo Workspace'
          : 'Development Workspace',
      description:
        workspaceId === 'demo-workspace-1'
          ? 'A sample workspace for demonstrating the IDE'
          : 'Workspace for active development projects',
      isDefault: workspaceId === 'demo-workspace-1',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      aiSummary:
        workspaceId === 'demo-workspace-1'
          ? 'Demo workspace for testing IDE features'
          : 'Active development workspace',
      aiTags:
        workspaceId === 'demo-workspace-1'
          ? ['demo', 'testing', 'ide']
          : ['development', 'active', 'coding'],
      ownedProjects: [
        {
          id: 'demo-project-1',
          name: 'Demo Project',
          description: 'A sample project for demonstration',
          type: 'webapp',
          workspaceId: workspaceId,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          aiNextActions: ['Add more components', 'Implement authentication'],
          aiHealthScore: 85,
        },
      ],
      includedProjects: [],
    };
  }
}

async function fetchProjects(
  workspaceId: string
): Promise<{ owned: Project[]; included: Project[] }> {
  try {
    const res = await fetch(`${API_BASE}/projects?workspaceId=${workspaceId}`);
    return (await parseJsonResponse(res)) as {
      owned: Project[];
      included: Project[];
    };
  } catch (error) {
    // Fallback to mock data when backend is not available
    console.warn(
      'Backend API not available, using fallback projects data:',
      error
    );
    return {
      owned: [
        {
          id: 'demo-project-1',
          name: 'Demo Project',
          description: 'A sample project for demonstration',
          type: 'webapp',
          workspaceId: workspaceId,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          aiNextActions: ['Add more components', 'Implement authentication'],
          aiHealthScore: 85,
        },
        {
          id: 'demo-project-2',
          name: 'UI Components',
          description: 'Reusable UI component library',
          type: 'library',
          workspaceId: workspaceId,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          aiNextActions: ['Add theme support', 'Improve documentation'],
          aiHealthScore: 92,
        },
      ],
      included: [
        {
          id: 'demo-project-3',
          name: 'Shared Utils',
          description: 'Common utility functions',
          type: 'library',
          workspaceId: 'other-workspace',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          aiNextActions: ['Add tests', 'Optimize performance'],
          aiHealthScore: 78,
        },
      ],
    };
  }
}

async function createWorkspaceApi(data: {
  name: string;
  description?: string;
  createDefaultProject?: boolean;
}): Promise<Workspace> {
  const res = await fetch(`${API_BASE}/workspaces`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });

  const payload = await parseJsonResponse(res);
  if (isRecord(payload) && isRecord(payload.workspace))
    return payload.workspace as unknown as Workspace;
  return payload as Workspace;
}

async function createProjectApi(data: {
  name: string;
  description?: string;
  type: string;
  ownerWorkspaceId: string;
}): Promise<Project> {
  const res = await fetch(`${API_BASE}/projects`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name: data.name,
      description: data.description,
      type: data.type,
      workspaceId: data.ownerWorkspaceId,
    }),
  });

  const payload = await parseJsonResponse(res);
  if (isRecord(payload) && isRecord(payload.project))
    return payload.project as unknown as Project;
  return payload as Project;
}

async function includeProjectApi(data: {
  projectId: string;
  workspaceId: string;
}): Promise<void> {
  const res = await fetch(`${API_BASE}/projects/include`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  if (!res.ok) throw new Error('Failed to include project');
}

async function suggestWorkspaceName(): Promise<string> {
  const res = await fetch(`${API_BASE}/workspaces/suggest-name`);
  const data = await parseJsonResponse(res);
  if (isRecord(data) && typeof data.suggestion === 'string')
    return data.suggestion;
  if (isRecord(data) && typeof data.name === 'string') return data.name;
  throw new Error('Unexpected response shape for workspace name suggestion');
}

async function suggestProjectName(
  workspaceId: string,
  projectType?: string
): Promise<string> {
  const params = new URLSearchParams({ workspaceId });
  if (projectType) params.append('type', projectType);
  const res = await fetch(`${API_BASE}/projects/suggest-name?${params}`);
  const data = await parseJsonResponse(res);
  if (isRecord(data) && typeof data.suggestion === 'string')
    return data.suggestion;
  if (isRecord(data) && typeof data.name === 'string') return data.name;
  throw new Error('Unexpected response shape for project name suggestion');
}

async function refreshWorkspaceAI(
  workspaceId: string
): Promise<{ aiSummary: string; aiTags: string[] }> {
  const res = await fetch(`${API_BASE}/workspaces/${workspaceId}/refresh-ai`, {
    method: 'POST',
  });

  const data = await parseJsonResponse(res);
  if (isRecord(data) && isRecord(data.workspace)) {
    const workspace = data.workspace;
    return {
      aiSummary:
        typeof workspace.aiSummary === 'string' ? workspace.aiSummary : '',
      aiTags: Array.isArray(workspace.aiTags)
        ? (workspace.aiTags as string[])
        : [],
    };
  }
  if (
    isRecord(data) &&
    typeof data.aiSummary === 'string' &&
    Array.isArray(data.aiTags)
  ) {
    return { aiSummary: data.aiSummary, aiTags: data.aiTags as string[] };
  }
  throw new Error('Unexpected response shape for workspace AI refresh');
}

async function refreshProjectAI(
  projectId: string
): Promise<{ aiNextActions: string[]; aiHealthScore: number }> {
  const res = await fetch(`${API_BASE}/projects/${projectId}/refresh-ai`, {
    method: 'POST',
  });

  const data = await parseJsonResponse(res);
  if (isRecord(data) && isRecord(data.project)) {
    const project = data.project;
    return {
      aiNextActions: Array.isArray(project.aiNextActions)
        ? (project.aiNextActions as string[])
        : [],
      aiHealthScore:
        typeof project.aiHealthScore === 'number' ? project.aiHealthScore : 0,
    };
  }
  if (
    isRecord(data) &&
    Array.isArray(data.aiNextActions) &&
    typeof data.aiHealthScore === 'number'
  ) {
    return {
      aiNextActions: data.aiNextActions as string[],
      aiHealthScore: data.aiHealthScore,
    };
  }
  throw new Error('Unexpected response shape for project AI refresh');
}

async function fetchAvailableForInclusion(
  workspaceId: string
): Promise<Project[]> {
  try {
    const res = await fetch(
      `${API_BASE}/projects/available-for-inclusion?workspaceId=${workspaceId}`
    );
    const data = await parseJsonResponse(res);
    if (Array.isArray(data)) return data as Project[];
    if (isRecord(data) && Array.isArray(data.projects))
      return data.projects as Project[];
    throw new Error('Unexpected response shape for available projects');
  } catch (error) {
    // Fallback to mock data when backend is not available
    console.warn(
      'Backend API not available, using fallback available projects data:',
      error
    );
    return [
      {
        id: 'available-project-1',
        name: 'Analytics Engine',
        description: 'Real-time analytics and reporting system',
        type: 'microservice',
        workspaceId: 'shared-workspace',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        aiNextActions: ['Add dashboards', 'Improve performance'],
        aiHealthScore: 88,
      },
      {
        id: 'available-project-2',
        name: 'Design System',
        description: 'Component library and design tokens',
        type: 'library',
        workspaceId: 'shared-workspace',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        aiNextActions: ['Add more components', 'Create documentation'],
        aiHealthScore: 95,
      },
      {
        id: 'available-project-3',
        name: 'Mobile App',
        description: 'Cross-platform mobile application',
        type: 'mobile',
        workspaceId: 'shared-workspace',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        aiNextActions: ['Add offline support', 'Improve UI'],
        aiHealthScore: 72,
      },
    ];
  }
}

// ============================================================================
// Query Keys
// ============================================================================

export const workspaceKeys = {
  all: ['workspaces'] as const,
  lists: () => [...workspaceKeys.all, 'list'] as const,
  list: (filters: Record<string, unknown>) =>
    [...workspaceKeys.lists(), filters] as const,
  details: () => [...workspaceKeys.all, 'detail'] as const,
  detail: (id: string) => [...workspaceKeys.details(), id] as const,
};

export const projectKeys = {
  all: ['projects'] as const,
  lists: () => [...projectKeys.all, 'list'] as const,
  list: (workspaceId: string) => [...projectKeys.lists(), workspaceId] as const,
  details: () => [...projectKeys.all, 'detail'] as const,
  detail: (id: string) => [...projectKeys.details(), id] as const,
  available: (workspaceId: string) =>
    [...projectKeys.all, 'available', workspaceId] as const,
};

// ============================================================================
// Hooks
// ============================================================================

/**
 * Hook to fetch and manage all workspaces for the current user
 */
export function useWorkspaces() {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: workspaceKeys.lists(),
    queryFn: fetchWorkspaces,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

  const invalidate = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: workspaceKeys.all });
  }, [queryClient]);

  return useMemo(() => ({ ...query, invalidate }), [query, invalidate]);
}

/**
 * Hook to fetch and manage a single workspace with its projects
 */
export function useWorkspace(workspaceId: string | null) {
  const setWorkspaceState = useSetAtom(setCurrentWorkspaceAtom);
  const setProjects = useSetAtom(setProjectsAtom);

  const query = useQuery({
    queryKey: workspaceKeys.detail(workspaceId ?? ''),
    queryFn: () => {
      if (!workspaceId) throw new Error('No workspace ID provided');
      return fetchWorkspace(workspaceId);
    },
    enabled: !!workspaceId,
    staleTime: 2 * 60 * 1000, // 2 minutes
    retry: (failureCount, error: unknown) => {
      // Do not retry on 404 errors as they are likely permanent for that ID
      // This prevents rapid infinite loops when a workspace ID is stale/invalid
      if (error?.message?.includes('HTTP 404')) return false;
      return failureCount < 3;
    },
  });

  // Sync to Jotai state when data changes
  useEffect(() => {
    if (query.data) {
      setWorkspaceState(query.data);
      // Transform Project to ProjectWithOwnership by adding isOwned flag
      setProjects({
        owned: query.data.ownedProjects.map((p: Project) => ({
          ...p,
          isOwned: true,
        })),
        included: query.data.includedProjects.map((p: Project) => ({
          ...p,
          isOwned: false,
        })),
      });
    }
  }, [query.data, setWorkspaceState, setProjects]);

  return query;
}

/**
 * Hook to fetch projects for a workspace
 */
export function useProjects(workspaceId: string | null) {
  const setProjects = useSetAtom(setProjectsAtom);

  const query = useQuery({
    queryKey: projectKeys.list(workspaceId ?? ''),
    queryFn: () => fetchProjects(workspaceId!),
    enabled: !!workspaceId,
    staleTime: 2 * 60 * 1000,
  });

  useEffect(() => {
    if (query.data) {
      // Transform to add isOwned flag
      setProjects({
        owned: query.data.owned.map((p: Project) => ({ ...p, isOwned: true })),
        included: query.data.included.map((p: Project) => ({
          ...p,
          isOwned: false,
        })),
      });
    }
  }, [query.data, setProjects]);

  return query;
}

/**
 * Hook to fetch projects available for inclusion
 */
export function useAvailableProjects(workspaceId: string | null) {
  return useQuery({
    queryKey: projectKeys.available(workspaceId ?? ''),
    queryFn: () => fetchAvailableForInclusion(workspaceId!),
    enabled: !!workspaceId,
    staleTime: 1 * 60 * 1000,
  });
}

/**
 * Hook to create a new workspace
 */
export function useCreateWorkspace() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createWorkspaceApi,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: workspaceKeys.all });
    },
  });
}

/**
 * Hook to create a new project
 */
export function useCreateProject() {
  const queryClient = useQueryClient();
  const addOwned = useSetAtom(addOwnedProjectAtom);

  return useMutation({
    mutationFn: createProjectApi,
    onSuccess: (project, variables) => {
      // Transform to ProjectWithOwnership
      addOwned({ ...project, isOwned: true } as ProjectWithOwnership);
      queryClient.invalidateQueries({
        queryKey: projectKeys.list(variables.ownerWorkspaceId),
      });
      queryClient.invalidateQueries({
        queryKey: workspaceKeys.detail(variables.ownerWorkspaceId),
      });
    },
  });
}

/**
 * Hook to include a project in a workspace (read-only)
 */
export function useIncludeProject() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: includeProjectApi,
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({
        queryKey: projectKeys.list(variables.workspaceId),
      });
      queryClient.invalidateQueries({
        queryKey: projectKeys.available(variables.workspaceId),
      });
      queryClient.invalidateQueries({
        queryKey: workspaceKeys.detail(variables.workspaceId),
      });
    },
  });
}

/**
 * Hook to get AI name suggestions
 */
export function useNameSuggestions() {
  const suggestWorkspace = useCallback(async () => {
    try {
      return await suggestWorkspaceName();
    } catch {
      // Fallback suggestions
      const suggestions = [
        'Innovation Lab',
        'Project Alpha',
        'Creative Studio',
        'Growth Hub',
        'Digital Forge',
      ];
      return suggestions[Math.floor(Math.random() * suggestions.length)];
    }
  }, []);

  const suggestProject = useCallback(
    async (workspaceId: string, type?: string) => {
      try {
        return await suggestProjectName(workspaceId, type);
      } catch {
        const typeDefaults: Record<string, string[]> = {
          webapp: ['Web Portal', 'Dashboard', 'Client App'],
          api: ['Core API', 'Gateway Service', 'Data API'],
          mobile: ['Mobile App', 'Companion App', 'Field App'],
          library: ['Core Utils', 'Shared Components', 'Common Lib'],
          microservice: [
            'Auth Service',
            'Notification Service',
            'Analytics Engine',
          ],
        };
        const options = typeDefaults[type ?? 'webapp'] ?? typeDefaults.webapp;
        return options[Math.floor(Math.random() * options.length)];
      }
    },
    []
  );

  return { suggestWorkspace, suggestProject };
}

/**
 * Hook to refresh AI insights for workspace or project
 */
export function useRefreshAI() {
  const queryClient = useQueryClient();

  const refreshWorkspace = useMutation({
    mutationFn: refreshWorkspaceAI,
    onSuccess: (_, workspaceId) => {
      queryClient.invalidateQueries({
        queryKey: workspaceKeys.detail(workspaceId),
      });
    },
  });

  const refreshProject = useMutation({
    mutationFn: refreshProjectAI,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: projectKeys.all });
    },
  });

  return { refreshWorkspace, refreshProject };
}

/**
 * Main hook that combines workspace selection with data fetching
 */
export function useWorkspaceContext() {
  const [currentWorkspaceId, setCurrentWorkspaceId] = useAtom(
    currentWorkspaceIdAtom
  );
  const workspaceState = useAtomValue(workspaceAtom);

  const workspacesQuery = useWorkspaces();
  const workspaceQuery = useWorkspace(currentWorkspaceId);

  // Auto-select workspace on first load or if current workspace no longer exists
  useEffect(() => {
    if (!workspacesQuery.data?.length) return;

    // Check if current workspace ID exists in the list
    const workspaceExists = workspacesQuery.data.some(
      (w) => w.id === currentWorkspaceId
    );

    if (!currentWorkspaceId || !workspaceExists) {
      // Auto-select default workspace or first workspace
      const defaultWorkspace = workspacesQuery.data.find((w) => w.isDefault);
      const selectedWorkspace = defaultWorkspace ?? workspacesQuery.data[0];
      setCurrentWorkspaceId(selectedWorkspace.id);
    }
  }, [workspacesQuery.data, currentWorkspaceId, setCurrentWorkspaceId]);

  const switchWorkspace = useCallback(
    (workspaceId: string) => {
      setCurrentWorkspaceId(workspaceId);
    },
    [setCurrentWorkspaceId]
  );

  // Don't bubble up workspace detail errors (like 404s) if we have a valid list of workspaces
  // This allows the auto-recovery logic (useEffect above) to switch to a valid workspace
  // without blocking the entire UI with an error screen.
  const combinedError =
    workspacesQuery.error ||
    (workspacesQuery.data?.length ? null : workspaceQuery.error);

  // If the currently selected workspace failed (e.g. 404), and we have a valid
  // list of workspaces from the list query, auto-switch to a DIFFERENT
  // available workspace to recover the UI automatically.
  useEffect(() => {
    if (workspaceQuery.isError && workspacesQuery.data?.length) {
      // Find a candidate that is NOT the current failing one
      const defaultWorkspace = workspacesQuery.data.find((w) => w.isDefault);
      const candidate =
        defaultWorkspace && defaultWorkspace.id !== currentWorkspaceId
          ? defaultWorkspace
          : workspacesQuery.data.find((w) => w.id !== currentWorkspaceId);

      if (candidate && candidate.id !== currentWorkspaceId) {
        console.warn(
          'Selected workspace failed to load, switching to alternative:',
          candidate.id,
          workspaceQuery.error
        );
        setCurrentWorkspaceId(candidate.id);
      } else if (
        workspaceQuery.error instanceof Error &&
        workspaceQuery.error.message.includes('404')
      ) {
        // If we are stuck with only one workspace and it 404s, the list is likely stale
        // Invalidate the list to see if it's actually gone from the server
        console.error(
          'Workspace 404ed and no alternatives found. Refetching workspace list.'
        );
        workspacesQuery.refetch();
      }
    }
  }, [
    workspaceQuery.isError,
    workspaceQuery.error,
    workspacesQuery.data,
    currentWorkspaceId,
    setCurrentWorkspaceId,
    workspacesQuery.refetch, // Depend on refetch function which is stable from useQuery
  ]);

  return {
    workspaces: workspacesQuery.data ?? [],
    currentWorkspace: workspaceState.currentWorkspace,
    currentWorkspaceId,
    ownedProjects: workspaceState.ownedProjects,
    includedProjects: workspaceState.includedProjects,
    isLoading: workspacesQuery.isLoading,
    error: combinedError,
    switchWorkspace,
    refetch: () => {
      workspacesQuery.refetch();
      workspaceQuery.refetch();
    },
  };
}
