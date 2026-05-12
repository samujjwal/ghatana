/**
 * Legacy Client Adapter Module
 *
 * Legacy fetch-based implementations for endpoints not yet migrated to generated client.
 * These implementations will be migrated to the generated client as part of Phase 1.2.
 *
 * @doc.type module
 * @doc.purpose Legacy fetch-based client implementations
 * @doc.layer product
 * @doc.pattern Legacy Adapter
 */

import { parseJsonResponse, readErrorResponse } from '@/lib/http';
import { authService } from '@/services/auth/AuthService';
import { handleError } from './errorMapper';
import { addScopeToRequest, type ScopedRequestOptions } from './scopeHeaders';

// ============================================================================
// Internal Fetch Helpers
// ============================================================================

async function get<T>(path: string, context: string): Promise<T> {
  const response = await fetch(path, {
    method: 'GET',
    headers: { Accept: 'application/json' },
    credentials: 'same-origin',
  });
  if (!response.ok) return handleError(response, context);
  return parseJsonResponse<T>(response, context);
}

async function getWithHeaders<T>(
  path: string,
  context: string,
  headers: Readonly<Record<string, string>>,
): Promise<T> {
  const response = await fetch(path, {
    method: 'GET',
    headers: { Accept: 'application/json', ...headers },
    credentials: 'same-origin',
  });
  if (!response.ok) return handleError(response, context);
  return parseJsonResponse<T>(response, context);
}

async function getResponse(path: string, context: string): Promise<Response> {
  const response = await fetch(path, {
    method: 'GET',
    headers: { Accept: 'application/json' },
    credentials: 'same-origin',
  });
  if (!response.ok) return handleError(response, context);
  return response;
}

async function post<TBody, TResult>(path: string, body: TBody, context: string): Promise<TResult> {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });
  if (!response.ok) return handleError(response, context);
  if (response.status === 204) return undefined as unknown as TResult;
  return parseJsonResponse<TResult>(response, context);
}

async function postPossiblyEmpty<TBody, TResult>(path: string, body: TBody, context: string): Promise<TResult> {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });
  if (!response.ok) return handleError(response, context);
  if (response.status === 204) return undefined as unknown as TResult;
  return parsePossiblyEmptyJsonResponse<TResult>(response, context);
}

async function postWithHeaders<TBody, TResult>(
  path: string,
  body: TBody,
  context: string,
  headers: Readonly<Record<string, string>>,
): Promise<TResult> {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...headers },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });
  if (!response.ok) return handleError(response, context);
  if (response.status === 204) return undefined as unknown as TResult;
  return parsePossiblyEmptyJsonResponse<TResult>(response, context);
}

async function parsePossiblyEmptyJsonResponse<T>(response: Response, context: string): Promise<T> {
  const payload = await response.text();
  if (!payload.trim()) {
    return undefined as unknown as T;
  }

  try {
    return JSON.parse(payload) as T;
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`${context} returned invalid JSON: ${detail}`);
  }
}

async function patch<TBody, TResult>(path: string, body: TBody, context: string): Promise<TResult> {
  const response = await fetch(path, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });
  if (!response.ok) return handleError(response, context);
  if (response.status === 204) return undefined as unknown as TResult;
  return parseJsonResponse<TResult>(response, context);
}

async function patchWithHeaders<TBody, TResult>(
  path: string,
  body: TBody,
  context: string,
  headers: Readonly<Record<string, string>>,
): Promise<TResult> {
  const response = await fetch(path, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...headers },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });
  if (!response.ok) return handleError(response, context);
  if (response.status === 204) return undefined as unknown as TResult;
  return parseJsonResponse<TResult>(response, context);
}

async function put<TBody, TResult>(path: string, body: TBody, context: string): Promise<TResult> {
  const response = await fetch(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });
  if (!response.ok) return handleError(response, context);
  if (response.status === 204) return undefined as unknown as TResult;
  return parseJsonResponse<TResult>(response, context);
}

async function del<TResult>(path: string, context: string): Promise<TResult> {
  const response = await fetch(path, {
    method: 'DELETE',
    headers: { Accept: 'application/json' },
    credentials: 'same-origin',
  });
  if (!response.ok) return handleError(response, context);
  if (response.status === 204) return undefined as unknown as TResult;
  return parseJsonResponse<TResult>(response, context);
}

// ============================================================================
// Scoped Request Helpers
// ============================================================================

async function getScoped<T>(
  path: string,
  context: string,
  options: ScopedRequestOptions = {},
): Promise<T> {
  const { path: scopedPath, headers } = addScopeToRequest(path, options);
  return getWithHeaders<T>(scopedPath, context, headers);
}

async function postScoped<TBody, TResult>(
  path: string,
  body: TBody,
  context: string,
  options: ScopedRequestOptions = {},
): Promise<TResult> {
  const { path: scopedPath, headers } = addScopeToRequest(path, options);
  return postWithHeaders<TBody, TResult>(scopedPath, body, context, headers);
}

async function patchScoped<TBody, TResult>(
  path: string,
  body: TBody,
  context: string,
  options: ScopedRequestOptions = {},
): Promise<TResult> {
  const { path: scopedPath, headers } = addScopeToRequest(path, options);
  return patchWithHeaders<TBody, TResult>(scopedPath, body, context, headers);
}

async function putScoped<TBody, TResult>(
  path: string,
  body: TBody,
  context: string,
  options: ScopedRequestOptions = {},
): Promise<TResult> {
  const { path: scopedPath, headers } = addScopeToRequest(path, options);
  const response = await fetch(scopedPath, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...headers },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });
  if (!response.ok) return handleError(response, context);
  if (response.status === 204) return undefined as unknown as TResult;
  return parseJsonResponse<TResult>(response, context);
}

// Export scoped helpers
export { getScoped, postScoped, patchScoped, putScoped };

// ============================================================================
// Auth (legacy implementations not yet in generated client)
// ============================================================================

export interface AuthTokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface AuthProfileUpdateRequest {
  firstName?: string;
  lastName?: string;
  username?: string;
  email?: string;
  avatar?: string;
}

export const auth = {
  // For cookie-session mode, refresh is handled via cookies
  // Keep existing implementation until generated client supports cookie refresh
  refresh: (): Promise<AuthTokenResponse> => {
    return post<Record<string, never>, AuthTokenResponse>('/api/auth/refresh', {}, 'auth.refresh');
  },
  // For cookie-session mode, logout is handled via cookies
  // Keep existing implementation until generated client supports cookie logout
  logout: (): Promise<void> => {
    return post<Record<string, never>, void>('/api/auth/logout', {}, 'auth.logout');
  },
  // Keep existing implementation for updateProfile (not in generated client yet)
  updateProfile: (body: AuthProfileUpdateRequest) =>
    put<AuthProfileUpdateRequest, AuthProfileUpdateRequest>('/api/auth/profile', body, 'auth.updateProfile'),
  // Keep existing implementation for ssoCallback (not in generated client yet)
  ssoCallback: (body: { code: string; state?: string | null }) =>
    post<{ code: string; state?: string | null }, { token: string }>(
      '/api/auth/sso/callback',
      body,
      'auth.ssoCallback',
    ),
} as const;

// ============================================================================
// User Profile
// ============================================================================

export interface AccountProfile {
  id: string;
  name: string;
  email: string;
  role: string;
  avatar?: string;
  avatarUrl?: string;
  bio?: string;
  timezone?: string;
  theme?: 'dark' | 'light' | 'system';
  notifications?: {
    email?: boolean;
    push?: boolean;
    slack?: boolean;
    weeklyDigest?: boolean;
  };
  createdAt?: string;
}

export const userProfile = {
  get: () => get<AccountProfile>('/api/user/profile', 'userProfile.get'),
  update: (body: Partial<AccountProfile>) =>
    patch<Partial<AccountProfile>, AccountProfile>('/api/user/profile', body, 'userProfile.update'),
} as const;

// ============================================================================
// Workspace (legacy implementations not yet in generated client)
// ============================================================================

export const workspaces = {
  suggestName: () => get<{ name: string }>('/api/workspaces/suggest-name', 'workspaces.suggestName'),
  refreshAi: (workspaceId: string) =>
    post<Record<string, never>, void>(`/api/workspaces/${encodeURIComponent(workspaceId)}/refresh-ai`, {}, 'workspaces.refreshAi'),
  refreshAiDetails: (workspaceId: string) =>
    post<Record<string, never>, unknown>(
      `/api/workspaces/${encodeURIComponent(workspaceId)}/refresh-ai`,
      {},
      'workspaces.refreshAiDetails',
    ),
} as const;

// ============================================================================
// Helper Functions
// ============================================================================

function encodeQuery(params: Readonly<Record<string, string | undefined>>): string {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined) {
      query.set(key, value);
    }
  });
  const encoded = query.toString();
  return encoded ? `?${encoded}` : '';
}

function buildAuthHeaders(): Readonly<Record<string, string>> {
  const token = authService.getAuthToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

// ============================================================================
// Project Types (legacy implementations not yet in generated client)
// ============================================================================

import type { WorkspaceRole } from '@/services/workspace/accessControl';
import type { ResourceCapabilities } from '@/services/workspace/accessControl';
import type { ProjectBase } from './generatedClientAdapter';

export interface Project extends ProjectBase {
  workspaceId: string; // Frontend convenience field derived from ownerWorkspaceId
  role?: WorkspaceRole;
  isOwned?: boolean;
  isIncluded?: boolean;
  readOnly?: boolean;
  capabilities?: ResourceCapabilities;
  currentPhase?: string;
}

export interface ProjectListResponse {
  readonly owned: Project[];
  readonly included: Project[];
}

interface ProjectResourceResponse {
  readonly project: Project;
}

export interface ProjectActivityEvent {
  readonly id: string;
  readonly source: 'lifecycle' | 'audit';
  readonly action: string;
  readonly summary: string;
  readonly timestamp: string;
  readonly actor: string | null;
  readonly severity?: string | null;
  readonly success?: boolean | null;
}

export interface ProjectActivityResponse {
  readonly projectId: string;
  readonly activity: ProjectActivityEvent[];
}

export interface ProjectArtifactsResponse {
  readonly artifacts: unknown[];
}

export interface ProjectSprintCurrentResponse {
  readonly sprint: unknown | null;
}

export interface ProjectBacklogResponse {
  readonly items: unknown[];
}

export interface ProjectRunsResponse {
  readonly runs: unknown[];
}

export interface ProjectIncludeRequest {
  readonly projectId: string;
  readonly workspaceId: string;
}

export interface ProjectNameSuggestionRequest {
  readonly workspaceId: string;
  readonly type?: string;
}

export interface ProjectNameSuggestionResponse {
  readonly name?: string;
  readonly suggestion?: string;
}

export type ProjectDashboardActionKind = 'blocker' | 'review' | 'safe-to-continue';
export type ProjectDashboardActionSeverity = 'critical' | 'warning' | 'info';

export interface ProjectDashboardAction {
  readonly id: string;
  readonly projectId: string;
  readonly projectName: string;
  readonly workspaceId: string;
  readonly lifecyclePhase: string;
  readonly routePhase: string;
  readonly kind: ProjectDashboardActionKind;
  readonly title: string;
  readonly summary: string;
  readonly severity: ProjectDashboardActionSeverity;
  readonly source: 'project.aiNextActions' | 'project.lifecyclePhase';
  readonly isDegraded: boolean;
  readonly isFallback: boolean;
  readonly requiresReview: boolean;
  readonly safeToRun: boolean;
  readonly updatedAt: string;
}

export interface ProjectDashboardActionsResponse {
  readonly workspaceId: string;
  readonly blockedWork: ProjectDashboardAction[];
  readonly reviewRequired: ProjectDashboardAction[];
  readonly safeToContinue: ProjectDashboardAction[];
  readonly generatedAt: string;
}

export interface ExecuteProjectDashboardActionRequest {
  readonly workspaceId: string;
  readonly actionId: string;
}

export interface ExecuteProjectDashboardActionResponse {
  readonly projectId: string;
  readonly actionId: string;
  readonly outcome: 'opened-phase-cockpit';
  readonly targetPhase: string;
  readonly targetPath: string;
  readonly auditRecorded: boolean;
}

export interface PhaseTransitionPreviewResponse {
  readonly projectId: string;
  readonly currentPhase: string;
  readonly nextPhase: string | null;
  readonly canAdvance: boolean;
  readonly readiness: number;
  readonly blockers: string[];
  readonly requiredArtifacts: string[];
  readonly completedArtifacts: string[];
  readonly estimatedReadyIn: string | null;
  readonly estimatedReadyInHours: number | null;
  readonly predictionConfidence: number | null;
  readonly checkedAt: string;
}

function unwrapProjectResource(response: Project | ProjectResourceResponse): Project {
  if (
    typeof response === 'object' &&
    response !== null &&
    'project' in response &&
    typeof response.project === 'object' &&
    response.project !== null
  ) {
    return response.project;
  }
  return response as Project;
}

export const projects = {
  getScoped: async (projectId: string, workspaceId: string) =>
    unwrapProjectResource(
      await get<Project | ProjectResourceResponse>(
        `/api/projects/${encodeURIComponent(projectId)}${encodeQuery({ workspaceId })}`,
        'projects.getScoped',
      ),
    ),
  updateScoped: async (projectId: string, workspaceId: string, body: unknown) => {
    const response = await patchWithHeaders<unknown, Project | ProjectResourceResponse>(
      `/api/projects/${encodeURIComponent(projectId)}${encodeQuery({ workspaceId })}`,
      body,
      'projects.updateScoped',
      { 'X-Workspace-Id': workspaceId },
    );
    return unwrapProjectResource(response);
  },
  suggestName: (params?: ProjectNameSuggestionRequest) =>
    get<ProjectNameSuggestionResponse>(
      `/api/projects/suggest-name${encodeQuery({ workspaceId: params?.workspaceId, type: params?.type })}`,
      'projects.suggestName',
    ),
  setupSuggestion: (body: { workspaceId?: string; description?: string; preferredType?: string }) =>
    post<typeof body, unknown>('/api/projects/setup-suggestion', body, 'projects.setupSuggestion'),
  current: (projectId: string) =>
    get<Project>(`/api/projects/${encodeURIComponent(projectId)}/current`, 'projects.current'),
  activity: (projectId: string, workspaceId: string) =>
    get<ProjectActivityResponse>(
      `/api/projects/${encodeURIComponent(projectId)}/activity${encodeQuery({ workspaceId })}`,
      'projects.activity',
    ),
  artifacts: (projectId: string, workspaceId: string) =>
    get<ProjectArtifactsResponse>(
      `/api/projects/${encodeURIComponent(projectId)}/artifacts${encodeQuery({ workspaceId })}`,
      'projects.artifacts',
    ),
  sprintCurrent: (projectId: string, workspaceId: string) =>
    get<ProjectSprintCurrentResponse>(
      `/api/projects/${encodeURIComponent(projectId)}/sprints/current${encodeQuery({ workspaceId })}`,
      'projects.sprintCurrent',
    ),
  backlog: (projectId: string, workspaceId: string, limit: number = 20) =>
    get<ProjectBacklogResponse>(
      `/api/projects/${encodeURIComponent(projectId)}/backlog${encodeQuery({ workspaceId, limit: String(limit) })}`,
      'projects.backlog',
    ),
  recentRuns: (projectId: string, workspaceId: string, limit: number = 10) =>
    get<ProjectRunsResponse>(
      `/api/projects/${encodeURIComponent(projectId)}/runs${encodeQuery({ workspaceId, limit: String(limit) })}`,
      'projects.recentRuns',
    ),
  availableForInclusion: (workspaceId: string) =>
    get<Project[]>(
      `/api/projects/available-for-inclusion${encodeQuery({ workspaceId })}`,
      'projects.availableForInclusion',
    ),
  include: (body: ProjectIncludeRequest) =>
    post<ProjectIncludeRequest, void>('/api/projects/include', body, 'projects.include'),
  dashboardActions: (workspaceId: string) =>
    get<ProjectDashboardActionsResponse>(
      `/api/projects/dashboard-actions${encodeQuery({ workspaceId })}`,
      'projects.dashboardActions',
    ),
  executeDashboardAction: (projectId: string, body: ExecuteProjectDashboardActionRequest) =>
    post<ExecuteProjectDashboardActionRequest, ExecuteProjectDashboardActionResponse>(
      `/api/projects/${encodeURIComponent(projectId)}/dashboard-actions/execute`,
      body,
      'projects.executeDashboardAction',
    ),
  aiCost: (projectId: string) =>
    get<{ totalCostUsd: number; breakdown: unknown[] }>(`/api/projects/${encodeURIComponent(projectId)}/ai-cost`, 'projects.aiCost'),
  refreshAi: (projectId: string) =>
    post<Record<string, never>, void>(`/api/projects/${encodeURIComponent(projectId)}/refresh-ai`, {}, 'projects.refreshAi'),
  refreshAiDetails: (projectId: string) =>
    post<Record<string, never>, unknown>(
      `/api/projects/${encodeURIComponent(projectId)}/refresh-ai`,
      {},
      'projects.refreshAiDetails',
    ),
  export: (projectId: string, workspaceId: string) =>
    getResponse(
      `/api/projects/${encodeURIComponent(projectId)}/export${encodeQuery({ workspaceId })}`,
      'projects.export',
    ),
} as const;
