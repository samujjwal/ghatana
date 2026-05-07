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
import { workspaceQueryKeys, projectQueryKeys } from '../lib/query-keys';
import { useAtom, useSetAtom, useAtomValue } from 'jotai';
import type {
  CreateProjectRequestContract,
  CreateWorkspaceRequestContract,
  NameSuggestionResponseContract,
  ProjectResponseContract,
  ProjectSetupSuggestionContract,
  ProjectTypeContract,
  WorkspaceDetailResponseContract,
  WorkspaceListResponseContract,
  WorkspaceResponseContract,
} from '@/contracts/workspace-project';
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
import {
  deriveCapabilities,
  normalizeWorkspaceRole,
  projectCanEdit,
  workspaceIsOwner,
} from '@/services/workspace/accessControl';
import { isLifecyclePhase } from '@/shared/types/lifecycle';
import { yappcApi, type ProjectDashboardActionsResponse } from '@/lib/api';

export type ProjectSetupSuggestion = ProjectSetupSuggestionContract;
export type CreateWorkspaceRequest = CreateWorkspaceRequestContract & {
  /** Optional persona selections carried during onboarding. */
  personaSelections?: string[];
  /** Optional default project to create alongside the workspace. */
  defaultProject?: { name: string; type: string };
};

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

function normalizeWorkspaceAccess(workspace: Workspace): Workspace {
  const isOwner = workspaceIsOwner(workspace);
  const role = normalizeWorkspaceRole(workspace.role, isOwner);
  return {
    ...workspace,
    role,
    isOwner,
    capabilities: deriveCapabilities(workspace),
  };
}

const PROJECT_TYPES = ['UI', 'BACKEND', 'MOBILE', 'DESKTOP', 'FULL_STACK'] as const;
const PROJECT_STATUSES = ['DRAFT', 'ACTIVE', 'COMPLETED', 'ARCHIVED'] as const;

function isProjectType(value: unknown): value is Project['type'] {
  return typeof value === 'string' && (PROJECT_TYPES as readonly string[]).includes(value);
}

function isProjectStatus(value: unknown): value is Project['status'] {
  return typeof value === 'string' && (PROJECT_STATUSES as readonly string[]).includes(value);
}

function normalizeStringArray(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
    : [];
}

function normalizeIsoTimestamp(value: unknown, fallback: string): string {
  return typeof value === 'string' && !Number.isNaN(Date.parse(value)) ? value : fallback;
}

export function normalizeProjectAccess(project: Project, isOwnedFallback: boolean): ProjectWithOwnership {
  const rawProject = project as Project & Record<string, unknown>;
  const createdAt = normalizeIsoTimestamp(rawProject.createdAt, new Date(0).toISOString());
  const updatedAt = normalizeIsoTimestamp(rawProject.updatedAt, createdAt);
  const isOwned = project.isOwned ?? isOwnedFallback;
  const role = normalizeWorkspaceRole(project.role);
  const capabilities = deriveCapabilities(project, {
    read: true,
    create: false,
    update: false,
    delete: false,
    include: false,
    comment: true,
  });
  const normalized: ProjectWithOwnership = {
    ...project,
    type: isProjectType(rawProject.type) ? rawProject.type : 'FULL_STACK',
    status: isProjectStatus(rawProject.status) ? rawProject.status : 'DRAFT',
    lifecyclePhase:
      typeof rawProject.lifecyclePhase === 'string' && isLifecyclePhase(rawProject.lifecyclePhase)
        ? rawProject.lifecyclePhase
        : 'INTENT',
    aiNextActions: normalizeStringArray(rawProject.aiNextActions).slice(0, 10),
    aiHealthScore:
      typeof rawProject.aiHealthScore === 'number' && Number.isFinite(rawProject.aiHealthScore)
        ? Math.max(0, Math.min(100, rawProject.aiHealthScore))
        : undefined,
    createdAt,
    updatedAt,
    role,
    isOwned,
    isIncluded: project.isIncluded ?? !isOwned,
    readOnly: project.readOnly ?? !capabilities.update,
    capabilities,
  };

  return {
    ...normalized,
    readOnly: normalized.readOnly || !projectCanEdit(normalized),
  };
}

async function fetchWorkspaces(): Promise<Workspace[]> {
  try {
    const res = await fetch(`${API_BASE}/workspaces`);

    const data = await parseJsonResponse(res);

    // Accept either the raw array or the wrapped form { workspaces: [...] }
    if (Array.isArray(data)) return (data as Workspace[]).map(normalizeWorkspaceAccess);
    if (isRecord(data) && Array.isArray(data.workspaces)) {
      const response = data as WorkspaceListResponseContract;
      return (response.workspaces as Workspace[]).map(normalizeWorkspaceAccess);
    }

    throw new Error('Unexpected response shape for workspaces');
  } catch (error) {
    // Surface real backend failures to the UI; do not silently fall back.
    throw error;
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
      const response = data as WorkspaceDetailResponseContract;
      const workspace = response.workspace as unknown as Workspace & {
        ownedProjects?: Project[];
        includedProjects?: Project[];
      };
      return {
        ...normalizeWorkspaceAccess(workspace),
        ownedProjects: (workspace.ownedProjects ?? []).map((project) => normalizeProjectAccess(project, true)),
        includedProjects: (workspace.includedProjects ?? []).map((project) => normalizeProjectAccess(project, false)),
      };
    }
    const workspace = data as Workspace & {
      ownedProjects?: Project[];
      includedProjects?: Project[];
    };
    return {
      ...normalizeWorkspaceAccess(workspace),
      ownedProjects: (workspace.ownedProjects ?? []).map((project) => normalizeProjectAccess(project, true)),
      includedProjects: (workspace.includedProjects ?? []).map((project) => normalizeProjectAccess(project, false)),
    };
  } catch (error) {
    // If the backend explicitly returned 404, surface the error so the
    // workspace selection logic can pick a different workspace (prevents
    // silently using a fallback that hides configuration issues).
    if (error instanceof Error && error.message.includes('HTTP 404')) {
      throw error;
    }

    throw error;
  }
}

async function fetchProjects(
  workspaceId: string
): Promise<{ owned: Project[]; included: Project[] }> {
  const payload = await yappcApi.projects.list(workspaceId);
  if (Array.isArray(payload)) {
    return {
      owned: payload.map((project) => normalizeProjectAccess(project as unknown as Project, true)),
      included: [],
    };
  }

  return {
    owned: (Array.isArray(payload.owned) ? payload.owned : []).map((project) =>
      normalizeProjectAccess(project as unknown as Project, true)
    ),
    included: (Array.isArray(payload.included) ? payload.included : []).map((project) =>
      normalizeProjectAccess(project as unknown as Project, false)
    ),
  };
}

function emptyDashboardActions(workspaceId: string): ProjectDashboardActionsResponse {
  return {
    workspaceId,
    blockedWork: [],
    reviewRequired: [],
    safeToContinue: [],
    generatedAt: new Date(0).toISOString(),
  };
}

async function fetchDashboardActions(workspaceId: string): Promise<ProjectDashboardActionsResponse> {
  return yappcApi.projects.dashboardActions(workspaceId);
}

async function createWorkspaceApi(
  data: CreateWorkspaceRequest
): Promise<Workspace> {
  const res = await fetch(`${API_BASE}/workspaces`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });

  const payload = await parseJsonResponse(res);
  if (isRecord(payload) && isRecord(payload.workspace)) {
    const response = payload as WorkspaceResponseContract;
    return response.workspace as Workspace;
  }
  return payload as Workspace;
}

async function createProjectApi(data: {
  name: string;
  description?: string;
  type: ProjectTypeContract;
  ownerWorkspaceId: string;
}): Promise<Project> {
  const request: CreateProjectRequestContract = {
    name: data.name,
    description: data.description,
    type: data.type,
    workspaceId: data.ownerWorkspaceId,
  };

  const res = await fetch(`${API_BASE}/projects`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  const payload = await parseJsonResponse(res);
  if (isRecord(payload) && isRecord(payload.project)) {
    const response = payload as ProjectResponseContract;
    return response.project as Project;
  }
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
  if (isRecord(data) && typeof data.suggestion === 'string') {
    const response = data as NameSuggestionResponseContract;
    return response.suggestion;
  }
  if (isRecord(data) && typeof data.name === 'string') return data.name;
  throw new Error('Unexpected response shape for workspace name suggestion');
}

async function suggestProjectName(
  workspaceId: string,
  projectType?: ProjectTypeContract
): Promise<string> {
  const params = new URLSearchParams({ workspaceId });
  if (projectType) params.append('type', projectType);
  const res = await fetch(`${API_BASE}/projects/suggest-name?${params}`);
  const data = await parseJsonResponse(res);
  if (isRecord(data) && typeof data.suggestion === 'string') {
    const response = data as NameSuggestionResponseContract;
    return response.suggestion;
  }
  if (isRecord(data) && typeof data.name === 'string') return data.name;
  throw new Error('Unexpected response shape for project name suggestion');
}

async function suggestProjectSetup(data: {
  workspaceId: string;
  description?: string;
  preferredType?: ProjectTypeContract;
}): Promise<ProjectSetupSuggestion> {
  const res = await fetch(`${API_BASE}/projects/setup-suggestion`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });

  const payload = await parseJsonResponse(res);
  if (
    isRecord(payload) &&
    typeof payload.suggestion === 'string' &&
    typeof payload.inferredType === 'string' &&
    typeof payload.rationale === 'string' &&
    typeof payload.summary === 'string' &&
    Array.isArray(payload.recommendations) &&
    Array.isArray(payload.relatedProjects)
  ) {
    return payload as ProjectSetupSuggestion;
  }

  throw new Error('Unexpected response shape for project setup suggestion');
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
    throw error;
  }
}

// ============================================================================
// Query Keys — delegated to centralized lib/query-keys.ts
// ============================================================================

export { workspaceQueryKeys, projectQueryKeys } from '../lib/query-keys';

/**
 * @deprecated Use workspaceQueryKeys from lib/query-keys.ts. Will be removed in v3.0.
 */
export const workspaceKeys = workspaceQueryKeys;

/**
 * @deprecated Use projectQueryKeys from lib/query-keys.ts. Will be removed in v3.0.
 */
export const projectKeys = projectQueryKeys;

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
      if (error instanceof Error && error.message.includes('HTTP 404')) return false;
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

export function useProjectDashboardActions(workspaceId: string | null) {
  return useQuery({
    queryKey: projectKeys.dashboardActions(workspaceId ?? ''),
    queryFn: () => fetchDashboardActions(workspaceId!),
    enabled: !!workspaceId,
    staleTime: 60 * 1000,
  });
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
    onMutate: async (newWorkspace) => {
      // Cancel in-flight queries
      await queryClient.cancelQueries({ queryKey: workspaceKeys.all });

      // Snapshot previous value
      const previousWorkspaces = queryClient.getQueryData(workspaceKeys.lists());

      // Optimistically add new workspace
      queryClient.setQueryData(workspaceKeys.lists(), (old: Workspace[] = []) => [
        ...old,
        {
          id: 'temp-' + Date.now(),
          name: newWorkspace.name,
          description: newWorkspace.description,
          ownerId: '',
          isDefault: false,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          aiSummary: '',
          aiTags: [],
        } as Workspace,
      ]);

      return { previousWorkspaces };
    },
    onError: (err, _, context) => {
      // Rollback on error
      queryClient.setQueryData(workspaceKeys.lists(), context?.previousWorkspaces);
    },
    onSettled: () => {
      // Refetch to ensure server state
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
    onMutate: async (variables) => {
      const { ownerWorkspaceId } = variables;
      
      // Cancel in-flight queries
      await queryClient.cancelQueries({ queryKey: projectKeys.list(ownerWorkspaceId) });

      // Snapshot previous value
      const previousProjects = queryClient.getQueryData(projectKeys.list(ownerWorkspaceId));

      // Optimistically add new project
      const tempProject: Project = {
        id: 'temp-' + Date.now(),
        name: variables.name,
        description: variables.description,
        type: variables.type,
        status: 'DRAFT',
        lifecyclePhase: 'INTENT',
        ownerWorkspaceId,
        isDefault: false,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        aiNextActions: [],
        aiHealthScore: 0,
      };

      queryClient.setQueryData(projectKeys.list(ownerWorkspaceId), (old: { owned?: Project[] } = {}) => ({
        ...old,
        owned: [...(old.owned || []), tempProject],
      }));

      return { previousProjects, tempProject };
    },
    onSuccess: (project, variables) => {
      // Transform to ProjectWithOwnership and update atom
      addOwned({ ...project, isOwned: true } as ProjectWithOwnership);
    },
    onError: (err, _, context) => {
      // Rollback on error
      if (context?.previousProjects) {
        queryClient.setQueryData(projectKeys.list(context.tempProject.ownerWorkspaceId), context.previousProjects);
      }
    },
    onSettled: (_, __, variables) => {
      // Refetch to ensure server state
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
    onMutate: async (variables) => {
      const { workspaceId, projectId } = variables;

      // Cancel in-flight queries
      await queryClient.cancelQueries({ queryKey: projectKeys.list(workspaceId) });
      await queryClient.cancelQueries({ queryKey: projectKeys.available(workspaceId) });

      // Snapshot previous values
      const previousProjects = queryClient.getQueryData(projectKeys.list(workspaceId));
      const previousAvailable = queryClient.getQueryData(projectKeys.available(workspaceId));

      // Optimistically move project from available to included
      queryClient.setQueryData(projectKeys.available(workspaceId), (old: Project[] = []) =>
        old.filter(p => p.id !== projectId)
      );

      // Note: We don't optimistically add to included since we don't have the full project data
      // The refetch will handle this

      return { previousProjects, previousAvailable };
    },
    onError: (err, variables, context) => {
      // Rollback on error
      if (context?.previousAvailable) {
        queryClient.setQueryData(projectKeys.available(variables.workspaceId), context.previousAvailable);
      }
    },
    onSettled: (_data, _error, variables) => {
      if (!variables) {
        return;
      }
      // Refetch to ensure server state
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
    async (workspaceId: string, type?: ProjectTypeContract) => {
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

  const suggestProjectSetupWithFallback = useCallback(
    async (workspaceId: string, description?: string, preferredType?: ProjectTypeContract) => {
      try {
        return await suggestProjectSetup({ workspaceId, description, preferredType });
      } catch {
        return {
          suggestion: await suggestProject(workspaceId, preferredType),
          inferredType: (preferredType ?? 'FULL_STACK') as Project['type'],
          rationale:
            'The server-side setup suggestion is unavailable, so the dialog is using the currently selected project type.',
          summary: 'Fallback suggestion generated from the current workspace context.',
          recommendations: [
            'Add a richer project description to improve the initial setup suggestion.',
          ],
          relatedProjects: [],
        } satisfies ProjectSetupSuggestion;
      }
    },
    [suggestProject]
  );

  return { suggestWorkspace, suggestProject, suggestProjectSetup: suggestProjectSetupWithFallback };
}

/**
 * Hook to refresh AI insights for workspace or project
 */
export function useRefreshAI() {
  const queryClient = useQueryClient();

  const refreshWorkspace = useMutation({
    mutationFn: refreshWorkspaceAI,
    onMutate: async (workspaceId) => {
      await queryClient.cancelQueries({ queryKey: workspaceKeys.detail(workspaceId) });
      const previousWorkspace = queryClient.getQueryData<Workspace>(workspaceKeys.detail(workspaceId));
      return { previousWorkspace };
    },
    onError: (err, _, context) => {
      queryClient.setQueryData(workspaceKeys.detail(context?.previousWorkspace?.id || ''), context?.previousWorkspace);
    },
    onSettled: (_data, _error, workspaceId) => {
      if (!workspaceId) {
        return;
      }
      queryClient.invalidateQueries({ queryKey: workspaceKeys.detail(workspaceId) });
    },
  });

  const refreshProject = useMutation({
    mutationFn: refreshProjectAI,
    onMutate: async (projectId) => {
      await queryClient.cancelQueries({ queryKey: projectKeys.detail(projectId) });
      const previousProject = queryClient.getQueryData<Project>(projectKeys.detail(projectId));
      return { previousProject };
    },
    onError: (err, _, context) => {
      queryClient.setQueryData(projectKeys.detail(context?.previousProject?.id || ''), context?.previousProject);
    },
    onSettled: () => {
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
  const dashboardActionsQuery = useProjectDashboardActions(currentWorkspaceId);

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
    dashboardActions: dashboardActionsQuery.data ?? (currentWorkspaceId ? emptyDashboardActions(currentWorkspaceId) : null),
    dashboardActionsLoading: dashboardActionsQuery.isLoading,
    dashboardActionsError: dashboardActionsQuery.error,
    isLoading: workspacesQuery.isLoading,
    error: combinedError,
    switchWorkspace,
    refetch: () => {
      workspacesQuery.refetch();
      workspaceQuery.refetch();
      dashboardActionsQuery.refetch();
    },
  };
}
