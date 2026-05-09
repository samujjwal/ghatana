/**
 * YAPPC Typed REST API Client
 *
 * Single canonical client for all REST API calls from the YAPPC web frontend.
 * Every REST-owned surface (workspaces, projects, auth, lifecycle, intent/shape,
 * code-generation, code-associations, artifacts, telemetry) is typed here.
 *
 * Usage rules (SIMP-Y19):
 *  - All NEW code calling REST endpoints must use this client, not raw `fetch`.
 *  - GraphQL-owned domains (workflows, requirements, approvals, AI agents,
 *    versioning, devsecops) must NOT be called via this client — use the
 *    GraphQL client instead. See docs/API_SURFACE_CANONICALIZATION.md.
 *  - Existing hand-coded fetches are migration candidates; migrate them when
 *    touching the surrounding code.
 *
 * P1-9: Lint rule required to forbid raw fetch outside API infrastructure.
 * All HTTP calls must go through typed helpers (get, post, patch, put, del).
 *
 * @doc.type module
 * @doc.purpose Typed REST API client (SIMP-Y19)
 * @doc.layer product
 * @doc.pattern API Client
 */

import { parseJsonResponse, readErrorResponse } from '@/lib/http';
import { authService } from '@/services/auth/AuthService';
import type { ResourceCapabilities, WorkspaceRole } from '@/services/workspace/accessControl';

// ─────────────────────────────────────────────────────────────────────────────
// Domain types (kept lean — callers own the full domain model imports)
// ─────────────────────────────────────────────────────────────────────────────

export interface ApiError {
  readonly status: number;
  readonly message: string;
  /** RFC-7807 type URI, if the server returned one. */
  readonly type?: string;
}

export class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly type?: string,
  ) {
    super(message);
    this.name = 'ApiRequestError';
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal fetch helpers
// ─────────────────────────────────────────────────────────────────────────────

async function handleError(response: Response, context: string): Promise<never> {
  const message = await readErrorResponse(response, `${context} failed (${response.status})`);
  // RFC-7807: attempt to extract `type` from problem+json
  let type: string | undefined;
  try {
    const ct = response.headers.get('content-type') ?? '';
    if (ct.includes('problem+json')) {
      const body = JSON.parse(message) as { type?: unknown };
      if (typeof body.type === 'string') type = body.type;
    }
  } catch {
    // non-parseable, ignore
  }
  throw new ApiRequestError(response.status, message, type);
}

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

// ─────────────────────────────────────────────────────────────────────────────
// Auth  (/api/auth/*)
// ─────────────────────────────────────────────────────────────────────────────

export interface LoginRequest {
  email: string;
  password: string;
}
export interface AuthTokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}
export interface LoginSessionResponse {
  user: {
    id: string;
    email: string;
    name: string;
    firstName?: string;
    lastName?: string;
    avatar?: string;
    avatarUrl?: string;
    tenantId?: string;
    workspaceIds?: string[];
    roles?: string[];
    role: 'VIEWER' | 'EDITOR' | 'ADMIN' | 'OWNER';
  };
  tokens: AuthTokenResponse;
}
export interface AuthProfileUpdateRequest {
  firstName?: string;
  lastName?: string;
  username?: string;
  email?: string;
  avatar?: string;
}
export interface UserProfile {
  id: string;
  email: string;
  name: string;
  role: string;
  tenantId: string;
}

export const auth = {
  login: (body: LoginRequest) => post<LoginRequest, AuthTokenResponse>('/api/auth/login', body, 'auth.login'),
  loginSession: (body: LoginRequest) =>
    post<LoginRequest, LoginSessionResponse>('/api/auth/login', body, 'auth.loginSession'),
  refresh: (body: { refreshToken: string }) =>
    post<{ refreshToken: string }, AuthTokenResponse>('/api/auth/refresh', body, 'auth.refresh'),
  logout: (body: { refreshToken: string }) => post<{ refreshToken: string }, void>('/api/auth/logout', body, 'auth.logout'),
  updateProfile: (body: AuthProfileUpdateRequest) =>
    put<AuthProfileUpdateRequest, AuthProfileUpdateRequest>('/api/auth/profile', body, 'auth.updateProfile'),
  ssoCallback: (body: { code: string; state?: string | null }) =>
    post<{ code: string; state?: string | null }, { token: string }>(
      '/api/auth/sso/callback',
      body,
      'auth.ssoCallback',
    ),
  validate: () => get<{ valid: boolean }>('/api/auth/validate', 'auth.validate'),
  me: () => get<UserProfile>('/api/auth/me', 'auth.me'),
} as const;

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

// ─────────────────────────────────────────────────────────────────────────────
// Billing / Operations (page-level read models)
// ─────────────────────────────────────────────────────────────────────────────

export const billing = {
  getSummary: <T>() => get<T>('/api/billing', 'billing.getSummary'),
} as const;

export const operations = {
  getOnCallSchedule: <T>() => get<T>('/api/oncall', 'operations.getOnCallSchedule'),
  getServiceTopology: <T>() =>
    get<T>('/api/services/topology', 'operations.getServiceTopology'),
} as const;

export const collaboration = {
  getActivityFeed: <T>() => get<T>('/api/activity', 'collaboration.getActivityFeed'),
  getTeamHub: <T>() => get<T>('/api/teams/hub', 'collaboration.getTeamHub'),
  getMessageChannels: <T>() =>
    get<T>('/api/messages/channels', 'collaboration.getMessageChannels'),
  getChannelMessages: <T>(channelId: string) =>
    get<T>(
      `/api/messages/channels/${encodeURIComponent(channelId)}`,
      'collaboration.getChannelMessages',
    ),
  getCalendarEvents: <T>() =>
    get<T>('/api/calendar/events', 'collaboration.getCalendarEvents'),
} as const;

export const settings = {
  getWorkspaceSettings: <T>() => get<T>('/api/settings', 'settings.getWorkspaceSettings'),
} as const;

export const anomalies = {
  query: <T>(body: {
    tenantId: string;
    startDate: Date;
    endDate: Date;
    severity?: string;
    status?: string;
  }) => post<typeof body, T>('/api/anomalies', body, 'anomalies.query'),
  byUser: <T>(userId: string, tenantId: string) =>
    getWithHeaders<T>(
      `/api/anomalies/user/${encodeURIComponent(userId)}`,
      'anomalies.byUser',
      { 'X-Tenant-Id': tenantId },
    ),
  updateStatus: <T>(anomalyId: string, tenantId: string, body: { status: string; notes?: string }) =>
    patchWithHeaders<typeof body, T>(
      `/api/anomalies/${encodeURIComponent(anomalyId)}/status`,
      body,
      'anomalies.updateStatus',
      { 'X-Tenant-Id': tenantId },
    ),
  createInvestigation: <T>(
    anomalyId: string,
    tenantId: string,
    body: { assignedTo: string },
  ) =>
    postWithHeaders<typeof body, T>(
      `/api/anomalies/${encodeURIComponent(anomalyId)}/investigation`,
      body,
      'anomalies.createInvestigation',
      { 'X-Tenant-Id': tenantId },
    ),
  baselines: <T>(tenantId: string) =>
    getWithHeaders<T>('/api/anomaly-baselines', 'anomalies.baselines', { 'X-Tenant-Id': tenantId }),
  threatIntelligence: <T>(tenantId: string) =>
    getWithHeaders<T>('/api/threat-intelligence', 'anomalies.threatIntelligence', {
      'X-Tenant-Id': tenantId,
    }),
  riskScores: <T>(tenantId: string) =>
    getWithHeaders<T>('/api/risk-scores', 'anomalies.riskScores', { 'X-Tenant-Id': tenantId }),
  detail: <T>(anomalyId: string, tenantId: string) =>
    getWithHeaders<T>(`/api/anomalies/${encodeURIComponent(anomalyId)}`, 'anomalies.detail', {
      'X-Tenant-Id': tenantId,
    }),
} as const;

export const canvas = {
  save: <T>(body: { projectId: string; canvasId: string; data: unknown }) =>
    post<typeof body, T>('/api/canvas', body, 'canvas.save'),
} as const;

export const errorReporting = {
  report: <T>(body: {
    message: string;
    stack?: string;
    componentStack?: string;
    errorId?: string;
    context?: string;
    timestamp: string;
    userAgent?: string;
    url?: string;
  }) => post<typeof body, T>('/api/errors', body, 'errorReporting.report'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Workspaces  (/api/workspaces/*)
// ─────────────────────────────────────────────────────────────────────────────

export interface Workspace {
  id: string;
  name: string;
  description?: string;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}
export interface CreateWorkspaceRequest {
  name: string;
  description?: string;
}
export interface UpdateWorkspaceRequest {
  name?: string;
  description?: string;
}

export const workspaces = {
  list: () => get<Workspace[]>('/api/workspaces', 'workspaces.list'),
  get: (workspaceId: string) => get<Workspace>(`/api/workspaces/${encodeURIComponent(workspaceId)}`, 'workspaces.get'),
  create: (body: CreateWorkspaceRequest) => post<CreateWorkspaceRequest, Workspace>('/api/workspaces', body, 'workspaces.create'),
  update: (workspaceId: string, body: UpdateWorkspaceRequest) =>
    patch<UpdateWorkspaceRequest, Workspace>(`/api/workspaces/${encodeURIComponent(workspaceId)}`, body, 'workspaces.update'),
  delete: (workspaceId: string) => del<void>(`/api/workspaces/${encodeURIComponent(workspaceId)}`, 'workspaces.delete'),
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

// ─────────────────────────────────────────────────────────────────────────────
// Projects  (/api/projects/*)
// ─────────────────────────────────────────────────────────────────────────────

export interface Project {
  id: string;
  name: string;
  description?: string;
  workspaceId: string;
  ownerWorkspaceId?: string;
  type?: string;
  status?: string;
  lifecyclePhase?: string;
  isDefault?: boolean;
  aiNextActions?: string[];
  aiHealthScore?: number;
  role?: WorkspaceRole;
  isOwned?: boolean;
  isIncluded?: boolean;
  readOnly?: boolean;
  capabilities?: ResourceCapabilities;
  currentPhase?: string;
  createdAt: string;
  updatedAt: string;
}
export interface CreateProjectRequest {
  name: string;
  description?: string;
  workspaceId: string;
}
export interface UpdateProjectRequest {
  name?: string;
  description?: string;
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
export interface WorkspaceNameSuggestionResponse {
  readonly name?: string;
  readonly suggestion?: string;
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

export interface PreviewSessionContext {
  readonly projectId: string;
  readonly artifactId: string;
}

export interface PreviewSessionIssueResponse {
  readonly sessionId: string;
  readonly sessionToken: string;
  readonly expiresAt: string;
}

export interface PreviewSessionValidateResponse {
  readonly valid: boolean;
  readonly reason?: string;
}

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
  list: (workspaceId: string) =>
    get<ProjectListResponse | Project[]>(
      `/api/projects${encodeQuery({ workspaceId })}`,
      'projects.list',
    ),
  get: async (projectId: string) =>
    unwrapProjectResource(
      await get<Project | ProjectResourceResponse>(
        `/api/projects/${encodeURIComponent(projectId)}`,
        'projects.get',
      ),
    ),
  getScoped: async (projectId: string, workspaceId: string) =>
    unwrapProjectResource(
      await get<Project | ProjectResourceResponse>(
        `/api/projects/${encodeURIComponent(projectId)}${encodeQuery({ workspaceId })}`,
        'projects.getScoped',
      ),
    ),
  create: (body: CreateProjectRequest) => post<CreateProjectRequest, Project>('/api/projects', body, 'projects.create'),
  update: (projectId: string, body: UpdateProjectRequest) =>
    patch<UpdateProjectRequest, Project>(`/api/projects/${encodeURIComponent(projectId)}`, body, 'projects.update'),
  updateScoped: async (projectId: string, workspaceId: string, body: UpdateProjectRequest) => {
    // P1-9: Replace raw fetch with typed helper to maintain type safety
    const response = await patchWithHeaders<UpdateProjectRequest, Project | ProjectResourceResponse>(
      `/api/projects/${encodeURIComponent(projectId)}${encodeQuery({ workspaceId })}`,
      body,
      'projects.updateScoped',
      { 'X-Workspace-Id': workspaceId },
    );
    return unwrapProjectResource(response);
  },
  delete: (projectId: string) => del<void>(`/api/projects/${encodeURIComponent(projectId)}`, 'projects.delete'),
  suggestName: (params?: ProjectNameSuggestionRequest) =>
    get<ProjectNameSuggestionResponse>(
      `/api/projects/suggest-name${encodeQuery({ workspaceId: params?.workspaceId, type: params?.type })}`,
      'projects.suggestName',
    ),
  setupSuggestion: (body: { workspaceId?: string; description?: string; preferredType?: string }) =>
    post<typeof body, unknown>('/api/projects/setup-suggestion', body, 'projects.setupSuggestion'),
  current: (projectId: string) =>
    get<Project>(`/api/projects/${encodeURIComponent(projectId)}/current`, 'projects.current'),
  activity: (projectId: string) =>
    get<ProjectActivityResponse>(`/api/projects/${encodeURIComponent(projectId)}/activity`, 'projects.activity'),
  artifacts: (projectId: string) =>
    get<ProjectArtifactsResponse>(`/api/projects/${encodeURIComponent(projectId)}/artifacts`, 'projects.artifacts'),
  sprintCurrent: (projectId: string) =>
    get<ProjectSprintCurrentResponse>(`/api/projects/${encodeURIComponent(projectId)}/sprints/current`, 'projects.sprintCurrent'),
  backlog: (projectId: string, limit: number = 20) =>
    get<ProjectBacklogResponse>(`/api/projects/${encodeURIComponent(projectId)}/backlog${encodeQuery({ limit: String(limit) })}`, 'projects.backlog'),
  recentRuns: (projectId: string, limit: number = 10) =>
    get<ProjectRunsResponse>(`/api/projects/${encodeURIComponent(projectId)}/runs${encodeQuery({ limit: String(limit) })}`, 'projects.recentRuns'),
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
  // P1-9: Removed stale 'capabilities' endpoint - use ResourceCapabilities from access control instead
  refreshAi: (projectId: string) =>
    post<Record<string, never>, void>(`/api/projects/${encodeURIComponent(projectId)}/refresh-ai`, {}, 'projects.refreshAi'),
  refreshAiDetails: (projectId: string) =>
    post<Record<string, never>, unknown>(
      `/api/projects/${encodeURIComponent(projectId)}/refresh-ai`,
      {},
      'projects.refreshAiDetails',
    ),
  export: (projectId: string) =>
    getResponse(`/api/projects/${encodeURIComponent(projectId)}/export`, 'projects.export'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Lifecycle  (/api/v1/lifecycle/*)
// ─────────────────────────────────────────────────────────────────────────────

export interface LifecyclePhaseInfo {
  id: string;
  label: string;
  description: string;
}
export interface LifecycleAdvanceRequest {
  projectId: string;
  fromPhase: string;
  toPhase: string;
  userId: string;
  bypass?: boolean;
  bypassReason?: string;
}
export interface LifecycleAdvanceResponse {
  success: boolean;
  currentPhase: string;
  errors?: string[];
}

export const lifecycle = {
  phases: () => get<LifecyclePhaseInfo[]>('/api/v1/lifecycle/phases', 'lifecycle.phases'),
  advance: (body: LifecycleAdvanceRequest) =>
    post<LifecycleAdvanceRequest, LifecycleAdvanceResponse>('/api/v1/lifecycle/advance', body, 'lifecycle.advance'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Intent / Shape  (/api/v1/yappc/intent/*, /api/v1/yappc/shape/*)
// ─────────────────────────────────────────────────────────────────────────────

export const intent = {
  capture: (body: { text: string; projectId: string }) =>
    post<typeof body, { id: string; status: string }>('/api/v1/yappc/intent/capture', body, 'intent.capture'),
  analyze: (body: { intentId: string }) =>
    post<typeof body, { analysis: unknown }>('/api/v1/yappc/intent/analyze', body, 'intent.analyze'),
  get: (id: string) =>
    get<{ id: string; text: string; analysis: unknown }>(`/api/v1/yappc/intent/${encodeURIComponent(id)}`, 'intent.get'),
} as const;

export const shape = {
  derive: (body: { intentId: string; projectId: string }) =>
    post<typeof body, { shapeId: string }>('/api/v1/yappc/shape/derive', body, 'shape.derive'),
  model: (body: { shapeId: string }) =>
    post<typeof body, { model: unknown }>('/api/v1/yappc/shape/model', body, 'shape.model'),
  get: (id: string) =>
    get<{ id: string; model: unknown }>(`/api/v1/yappc/shape/${encodeURIComponent(id)}`, 'shape.get'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Code Generation / Validation  (/api/v1/yappc/generate, /api/v1/yappc/validate)
// ─────────────────────────────────────────────────────────────────────────────

export interface GenerateArtifactsRequest {
  readonly projectId: string;
  readonly phase: string;
  readonly techStack?: readonly string[];
  readonly options?: Record<string, never>;
}
export interface GenerateArtifactProvenance {
  readonly requirementId: string;
  readonly phase: string;
  readonly canvasNodeId: string;
  readonly sourceArtifactId: string;
  readonly confidence: number;
  readonly approvingActorId: string;
  readonly approvedAt?: string;
}
export interface GenerateDiffRegion {
  readonly id: string;
  readonly type: 'addition' | 'deletion' | 'modification';
  readonly startLine: number;
  readonly endLine: number;
  readonly originalContent: string;
  readonly newContent: string;
  readonly owner: 'system' | 'user';
  readonly provenance: GenerateArtifactProvenance;
}
export interface GeneratedFileDiff {
  readonly id: string;
  readonly path: string;
  readonly language: string;
  readonly artifactType: 'source' | 'test' | 'config' | 'documentation' | 'schema' | 'api' | 'infrastructure';
  readonly provenance: GenerateArtifactProvenance;
  readonly diffRegions: readonly GenerateDiffRegion[];
}
export interface GenerateDiffReview {
  readonly files: readonly GeneratedFileDiff[];
}
export interface GenerateArtifactsResponse {
  readonly runId?: string;
  readonly executionId?: string;
  readonly status?: string;
  readonly reviewRequired?: boolean;
  readonly diff?: GenerateDiffReview;
}
export interface RegenerateDiffRequest {
  readonly runId: string;
  readonly diff: string;
}
export interface RegenerateDiffResponse {
  readonly runId?: string;
  readonly status?: string;
  readonly diff?: GenerateDiffReview;
  readonly reviewRequired?: boolean;
}
export type GenerateReviewDecision = 'apply' | 'reject' | 'rollback';
export interface GenerateReviewDecisionRequest {
  readonly projectId: string;
  readonly actorId: string;
  readonly reason?: string;
}
export interface GenerateReviewDecisionResponse {
  readonly runId?: string;
  readonly projectId?: string;
  readonly decision?: GenerateReviewDecision;
  readonly status?: string;
  readonly reviewRequired?: boolean;
  readonly actorId?: string;
  readonly decidedAt?: string;
  readonly auditEvent?: string;
  readonly message?: string;
}

export const generate = {
  run: (body: GenerateArtifactsRequest) =>
    postPossiblyEmpty<GenerateArtifactsRequest, GenerateArtifactsResponse>('/api/v1/yappc/generate', body, 'generate.run'),
  diff: (body: RegenerateDiffRequest) =>
    postPossiblyEmpty<RegenerateDiffRequest, RegenerateDiffResponse>('/api/v1/yappc/generate/diff', body, 'generate.diff'),
  review: (runId: string, decision: GenerateReviewDecision, body: GenerateReviewDecisionRequest) =>
    postPossiblyEmpty<GenerateReviewDecisionRequest, GenerateReviewDecisionResponse>(
      `/api/v1/yappc/generate/runs/${encodeURIComponent(runId)}/${decision}`,
      body,
      `generate.review.${decision}`,
    ),
  artifact: (id: string) =>
    get<{ id: string; content: unknown }>(`/api/v1/yappc/generate/artifacts/${encodeURIComponent(id)}`, 'generate.artifact'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// AI helpers  (/api/ai/*)
// ─────────────────────────────────────────────────────────────────────────────

export const ai = {
  assist: (body: { kind: string; projectId: string }) =>
    post<typeof body, Record<string, unknown>>('/api/ai/assist', body, 'ai.assist'),
  phaseGateReadiness: <T>(body: {
    projectId: string;
    targetPhase: string;
    artifacts: unknown[];
  }) => post<typeof body, T>('/api/ai/phase-gate-readiness', body, 'ai.phaseGateReadiness'),
  suggestArtifacts: <T>(body: {
    prompt: string;
    context: Record<string, unknown>;
  }) => post<typeof body, T>('/api/ai/suggest-artifacts', body, 'ai.suggestArtifacts'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Workflows  (/api/v1/workflows/*)
// ─────────────────────────────────────────────────────────────────────────────

export type WorkflowRunStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface WorkflowStartResponse {
  readonly runId?: string;
  readonly templateId?: string;
  readonly status?: Extract<WorkflowRunStatus, 'PENDING' | 'RUNNING'>;
  readonly startedAt?: string;
}

export interface WorkflowStatus {
  readonly runId?: string;
  readonly templateId?: string;
  readonly status?: WorkflowRunStatus;
  readonly startedAt?: string;
  readonly completedAt?: string | null;
  readonly error?: string | null;
}

export const workflows = {
  start: (templateId: string, tenantId: string) =>
    postWithHeaders<Record<string, never>, WorkflowStartResponse>(
      `/api/v1/workflows/${encodeURIComponent(templateId)}/start`,
      {},
      'workflows.start',
      { 'X-Tenant-Id': tenantId },
    ),
  status: (runId: string) =>
    get<WorkflowStatus>(`/api/v1/workflows/${encodeURIComponent(runId)}/status`, 'workflows.status'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Run phase operations  (/api/v1/yappc/run/*)
// ─────────────────────────────────────────────────────────────────────────────

export interface RunOperationResponse {
  readonly id?: string;
  readonly runSpecRef?: string;
  readonly status?: string;
  readonly metadata?: Record<string, string>;
}

export interface RunRollbackRequest {
  readonly deploymentId: string;
  readonly targetVersion: string;
}

export interface RunPromoteRequest {
  readonly deploymentId: string;
  readonly targetEnvironment: string;
}

export const run = {
  rollback: (body: RunRollbackRequest) =>
    postPossiblyEmpty<RunRollbackRequest, RunOperationResponse>('/api/v1/yappc/run/rollback', body, 'run.rollback'),
  promote: (body: RunPromoteRequest) =>
    postPossiblyEmpty<RunPromoteRequest, RunOperationResponse>('/api/v1/yappc/run/promote', body, 'run.promote'),
} as const;

export const validate = {
  run: (body: { executionId: string }) =>
    post<typeof body, { valid: boolean; errors: unknown[] }>('/api/v1/yappc/validate', body, 'validate.run'),
  withConfig: (body: { executionId: string; config: unknown }) =>
    post<typeof body, { valid: boolean; errors: unknown[] }>('/api/v1/yappc/validate/with-config', body, 'validate.withConfig'),
  withPolicy: (body: { executionId: string; policyId: string }) =>
    post<typeof body, { valid: boolean; errors: unknown[] }>('/api/v1/yappc/validate/with-policy', body, 'validate.withPolicy'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Artifacts  (/api/artifacts/*)
// ─────────────────────────────────────────────────────────────────────────────

export interface Artifact {
  id: string;
  projectId: string;
  kind: string;
  status: string;
  content: unknown;
  createdAt: string;
}

export const artifacts = {
  list: (params?: { projectId?: string; kind?: string }) => {
    const qs = params ? `?${new URLSearchParams(Object.entries(params).filter(([, v]) => v !== undefined) as [string, string][]).toString()}` : '';
    return get<Artifact[]>(`/api/artifacts${qs}`, 'artifacts.list');
  },
  get: (artifactId: string) =>
    get<Artifact>(`/api/artifacts/${encodeURIComponent(artifactId)}`, 'artifacts.get'),
  create: (body: { projectId: string; kind: string; content: unknown }) =>
    post<typeof body, Artifact>('/api/artifacts', body, 'artifacts.create'),
} as const;

export interface PageArtifactScopeHeaders {
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
}

export type PageArtifactSaveResult =
  | { readonly status: 'saved' }
  | { readonly status: 'conflict'; readonly remoteVersion: string | null };

export const pageArtifacts = {
  saveDocument: async (
    artifactId: string,
    documentId: string,
    document: unknown,
    scope: PageArtifactScopeHeaders,
  ): Promise<PageArtifactSaveResult> => {
    const response = await fetch(
      `/api/v1/page-artifacts/${encodeURIComponent(artifactId)}/document`,
      {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          'If-Match': documentId,
          'X-Tenant-ID': scope.tenantId,
          'X-Workspace-ID': scope.workspaceId,
          'X-Project-ID': scope.projectId,
        },
        credentials: 'include',
        body: JSON.stringify(document),
      },
    );

    if (response.status === 409) {
      return {
        status: 'conflict',
        remoteVersion: response.headers.get('X-Current-Version'),
      };
    }

    if (!response.ok) {
      return handleError(response, 'pageArtifacts.saveDocument');
    }

    return { status: 'saved' };
  },
  loadDocument: async <T>(artifactId: string, scope: PageArtifactScopeHeaders): Promise<T | null> => {
    const response = await fetch(
      `/api/v1/page-artifacts/${encodeURIComponent(artifactId)}/document`,
      {
        method: 'GET',
        headers: {
          Accept: 'application/json',
          'X-Tenant-ID': scope.tenantId,
          'X-Workspace-ID': scope.workspaceId,
          'X-Project-ID': scope.projectId,
        },
        credentials: 'include',
      },
    );

    if (response.status === 404) {
      return null;
    }

    if (!response.ok) {
      return handleError(response, 'pageArtifacts.loadDocument');
    }

    return parseJsonResponse<T>(response, 'pageArtifacts.loadDocument');
  },
  ingestGraph: async (request: unknown, scope: PageArtifactScopeHeaders): Promise<void> => {
    const response = await fetch('/api/v1/yappc/artifact/graph/ingest', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-Tenant-ID': scope.tenantId,
        'X-Workspace-ID': scope.workspaceId,
        'X-Project-ID': scope.projectId,
      },
      credentials: 'include',
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      return handleError(response, 'pageArtifacts.ingestGraph');
    }
  },
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Source Import  (/api/v1/yappc/artifact/import-source)
// ─────────────────────────────────────────────────────────────────────────────

export interface SourceImportRequest {
  readonly sourceType: string;
  readonly source: string;
  readonly projectId: string;
  readonly targetComponentName?: string;
  readonly options?: {
    readonly includeDependencies?: boolean;
    readonly includeStyles?: boolean;
    readonly includeTests?: boolean;
    readonly includeDocumentation?: boolean;
    readonly preserveStructure?: boolean;
    readonly allowUnsafeComponents?: boolean;
  };
}

export interface SourceImportFile {
  readonly path: string;
  readonly content: string;
  readonly type: 'component' | 'style' | 'test' | 'documentation' | 'other' | 'route';
  readonly source?: string;
}

export type SourceImportJobStatus = 'VALIDATING' | 'FETCHING_SOURCE' | 'REVIEW_REQUIRED' | 'REJECTED' | 'FAILED';
export type SourceImportProgressStepStatus = 'pending' | 'running' | 'completed' | 'failed' | 'skipped';

export interface SourceImportProgressStep {
  readonly id: string;
  readonly label: string;
  readonly status: SourceImportProgressStepStatus;
  readonly percent: number;
  readonly message?: string;
  readonly startedAt?: string;
  readonly completedAt?: string;
}

export interface SourceImportJobSnapshot {
  readonly id: string;
  readonly status: SourceImportJobStatus;
  readonly reason?: string;
  readonly tenantId?: string | null;
  readonly workspaceId?: string | null;
  readonly projectId?: string | null;
  readonly sourceType?: string;
  readonly source?: string;
  readonly componentName?: string;
  readonly percentComplete: number;
  readonly currentStep: string;
  readonly steps: readonly SourceImportProgressStep[];
  readonly createdAt: string;
  readonly updatedAt?: string;
  readonly auditRecorded?: boolean;
}

export interface SourceImportJobStatusResponse {
  readonly job: SourceImportJobSnapshot;
}

export interface SourceImportResponse {
  readonly success: boolean;
  readonly componentId?: string;
  readonly files: SourceImportFile[];
  readonly warnings: string[];
  readonly errors: string[];
  readonly metadata: {
    readonly sourceType: string;
    readonly source: string;
    readonly importedAt: string;
    readonly componentName?: string;
    readonly dependencies: string[];
    readonly fileCount: number;
    readonly totalSize: number;
  };
  readonly extractedComponents?: readonly unknown[];
  readonly job?: SourceImportJobSnapshot;
}

export const sourceImports = {
  start: (
    body: SourceImportRequest,
    scope: { readonly tenantId: string; readonly workspaceId: string; readonly projectId: string },
  ) =>
    postWithHeaders<SourceImportRequest, SourceImportResponse>(
      '/api/v1/yappc/artifact/import-source',
      body,
      'sourceImports.start',
      {
        'X-Tenant-ID': scope.tenantId,
        'X-Workspace-ID': scope.workspaceId,
        'X-Project-ID': scope.projectId,
      },
    ),
  status: (
    jobId: string,
    scope: { readonly tenantId: string; readonly workspaceId: string; readonly projectId: string },
  ) =>
    getWithHeaders<SourceImportJobStatusResponse>(
      `/api/v1/yappc/artifact/import-source/${encodeURIComponent(jobId)}`,
      'sourceImports.status',
      {
        'X-Tenant-ID': scope.tenantId,
        'X-Workspace-ID': scope.workspaceId,
        'X-Project-ID': scope.projectId,
      },
    ),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Code Associations  (/api/code-associations/*, /api/artifacts/:id/code-associations)
// ─────────────────────────────────────────────────────────────────────────────

export interface CodeAssociation {
  id: string;
  artifactId: string;
  repository: string;
  filePath: string;
  lineStart?: number;
  lineEnd?: number;
  createdAt: string;
}
export interface CreateCodeAssociationRequest {
  artifactId: string;
  repository: string;
  filePath: string;
  lineStart?: number;
  lineEnd?: number;
}

export const codeAssociations = {
  list: () => get<CodeAssociation[]>('/api/code-associations', 'codeAssociations.list'),
  listForArtifact: (artifactId: string) =>
    get<CodeAssociation[]>(`/api/artifacts/${encodeURIComponent(artifactId)}/code-associations`, 'codeAssociations.listForArtifact'),
  stats: (artifactId: string) =>
    get<{ count: number }>(`/api/code-associations/stats/${encodeURIComponent(artifactId)}`, 'codeAssociations.stats'),
  create: (body: CreateCodeAssociationRequest) =>
    post<CreateCodeAssociationRequest, CodeAssociation>('/api/code-associations', body, 'codeAssociations.create'),
  delete: (associationId: string) =>
    del<void>(`/api/code-associations/${encodeURIComponent(associationId)}`, 'codeAssociations.delete'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Phase Gates  (/api/gates/*)
// ─────────────────────────────────────────────────────────────────────────────

export interface GateValidationRequest {
  projectId: string;
  fromPhase: string;
  toPhase: string;
}
export interface GateValidationResponse {
  canTransition: boolean;
  gateId?: string;
  blockedReason?: string;
  missingArtifacts?: string[];
}

export const gates = {
  validate: (body: GateValidationRequest) =>
    post<GateValidationRequest, GateValidationResponse>('/api/gates/validate', body, 'gates.validate'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Telemetry  (/api/telemetry/*)
// ─────────────────────────────────────────────────────────────────────────────

export interface FrontendErrorReport {
  message: string;
  stack?: string;
  componentName?: string;
  url: string;
  userAgent: string;
  dataClassification?: TelemetryDataClassification;
  tenantId?: string;
  userId?: string;
}

export type TelemetryDataClassification =
  | 'PUBLIC'
  | 'INTERNAL'
  | 'CONFIDENTIAL'
  | 'RESTRICTED'
  | 'SENSITIVE'
  | 'CREDENTIALS'
  | 'REGULATED';

export interface TelemetryConsentContext {
  readonly userTelemetryConsent: boolean;
  readonly tenantTelemetryConsent: boolean;
  readonly dataClassification: TelemetryDataClassification;
  readonly allowSensitiveTelemetry?: boolean;
  readonly tenantId?: string;
  readonly userId?: string;
}

export interface TelemetryReportResult {
  readonly accepted: boolean;
  readonly blockedReason?: string;
}

const TELEMETRY_BLOCKED_CLASSIFICATIONS: ReadonlySet<TelemetryDataClassification> = new Set([
  'SENSITIVE',
  'CREDENTIALS',
  'REGULATED',
  'RESTRICTED',
]);

export function evaluateTelemetryConsent(context: TelemetryConsentContext): TelemetryReportResult {
  if (!context.tenantTelemetryConsent) {
    return { accepted: false, blockedReason: 'Tenant telemetry consent is not enabled.' };
  }
  if (!context.userTelemetryConsent) {
    return { accepted: false, blockedReason: 'User telemetry consent is not enabled.' };
  }
  if (
    TELEMETRY_BLOCKED_CLASSIFICATIONS.has(context.dataClassification) &&
    context.allowSensitiveTelemetry !== true
  ) {
    return {
      accepted: false,
      blockedReason: `Telemetry blocked for ${context.dataClassification.toLowerCase()} data classification.`,
    };
  }

  return { accepted: true };
}

export const telemetry = {
  reportError: async (
    body: FrontendErrorReport,
    consent: TelemetryConsentContext,
  ): Promise<TelemetryReportResult> => {
    const decision = evaluateTelemetryConsent(consent);
    if (!decision.accepted) {
      return decision;
    }

    const payload: FrontendErrorReport = {
      ...body,
      dataClassification: consent.dataClassification,
      ...(consent.tenantId ? { tenantId: consent.tenantId } : {}),
      ...(consent.userId ? { userId: consent.userId } : {}),
    };

    return post<FrontendErrorReport, TelemetryReportResult>(
      '/api/telemetry/frontend-errors',
      payload,
      'telemetry.reportError',
    );
  },
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Audit  (/api/audit/*)
// ─────────────────────────────────────────────────────────────────────────────

export interface AuditEventRequest {
  readonly type: string;
  readonly userId: string;
  readonly projectId: string;
  readonly artifactId?: string;
  readonly flowStage: string;
  readonly phase: string;
  readonly metadata?: Record<string, unknown>;
  readonly description: string;
}

export interface AuditEventResponse extends AuditEventRequest {
  readonly id: string;
  readonly timestamp: string;
}

export const audit = {
  emit: (body: AuditEventRequest) =>
    post<AuditEventRequest, AuditEventResponse>('/api/audit/events', body, 'audit.emit'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Persons / Phases  (/api/personas/*, /api/phases/*)
// ─────────────────────────────────────────────────────────────────────────────

export const personas = {
  derive: (body: { projectId: string; intent?: string }) =>
    post<typeof body, unknown[]>('/api/personas/derive', body, 'personas.derive'),
} as const;

export const phases = {
  list: () => get<unknown[]>('/api/phases', 'phases.list'),
  next: (phase: string, projectId: string) =>
    get<PhaseTransitionPreviewResponse>(
      `/api/phases/${encodeURIComponent(phase)}/next${encodeQuery({ projectId })}`,
      'phases.next',
    ),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Preview sessions  (/api/v1/yappc/preview/sessions)
// ─────────────────────────────────────────────────────────────────────────────

export const previewSessions = {
  issue: (body: PreviewSessionContext) =>
    postWithHeaders<PreviewSessionContext, PreviewSessionIssueResponse>(
      '/api/v1/yappc/preview/sessions',
      body,
      'previewSessions.issue',
      buildAuthHeaders(),
    ),
  validate: (sessionToken: string) =>
    postWithHeaders<{ sessionToken: string }, PreviewSessionValidateResponse>(
      '/api/v1/yappc/preview/sessions/validate',
      { sessionToken },
      'previewSessions.validate',
      buildAuthHeaders(),
    ),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Rate limit  (/api/rate-limit/*)
// ─────────────────────────────────────────────────────────────────────────────

export interface RateLimitStatusResponse {
  identifier: string;
  tier: string;
  used: number;
  limit: number;
  remaining: number;
  percentage: number;
  resetTime: string;
  isLimited: boolean;
  lastRequestAt?: string;
  statusColor: string;
  statusLabel: string;
}

export interface RateLimitTierResponse {
  name: string;
  description: string;
  requestsPerHour: number;
  requestsPerDay: number;
  burstSize: number;
  monthlyCost: number;
  features: string[];
}

export interface RateLimitUpgradeRequestResponse {
  id: string;
  userId: string;
  requestedTier: string;
  currentTier: string;
  status: string;
  createdAt: string;
  processedAt?: string;
}

export const rateLimit = {
  status: (identifier: string = 'me') =>
    get<RateLimitStatusResponse>(
      `/api/rate-limit/status/${encodeURIComponent(identifier)}`,
      'rateLimit.status',
    ),
  tiers: () => get<RateLimitTierResponse[]>('/api/rate-limit/tiers', 'rateLimit.tiers'),
  upgradeRequests: () =>
    get<RateLimitUpgradeRequestResponse[]>(
      '/api/rate-limit/upgrade-requests',
      'rateLimit.upgradeRequests',
    ),
  upgrade: (body: { requestedTier: string }) =>
    post<{ requestedTier: string }, unknown>(
      '/api/rate-limit/upgrade',
      body,
      'rateLimit.upgrade',
    ),
  reset: (body: { userId?: string }) =>
    post<{ userId?: string }, unknown>('/api/rate-limit/reset', body, 'rateLimit.reset'),
  downgrade: () =>
    post<Record<string, never>, unknown>(
      '/api/rate-limit/downgrade',
      {},
      'rateLimit.downgrade',
    ),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// User data (GDPR / CCPA)  (/api/users/me/*)
// ─────────────────────────────────────────────────────────────────────────────

export interface DeleteMyDataResponse {
  statusUrl?: string;
  message?: string;
}

export const userData = {
  /**
   * Request deletion of all user data (GDPR Art. 17 / CCPA).
   * Returns 202 Accepted with a Location header and optional statusUrl.
   * C-Y15 / F-Y058.
   */
  requestDeletion: async (): Promise<{ statusUrl: string | null }> => {
    const response = await fetch('/api/users/me/data', {
      method: 'DELETE',
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    });
    if (!response.ok) {
      const message = await readErrorResponse(response, 'userData.requestDeletion failed');
      throw new ApiRequestError(response.status, message);
    }
    const locationHeader = response.headers.get('Location');
    let statusUrl = locationHeader;
    if (!locationHeader && response.status !== 204) {
      try {
        const body = await parseJsonResponse<DeleteMyDataResponse>(response, 'userData.requestDeletion');
        statusUrl = body.statusUrl ?? null;
      } catch {
        statusUrl = null;
      }
    }
    return { statusUrl };
  },
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Execution Results  (/api/results/*)
// ─────────────────────────────────────────────────────────────────────────────

export const results = {
  list: () => get<unknown[]>('/api/results', 'results.list'),
  get: (executionId: string) =>
    get<unknown>(`/api/results/${encodeURIComponent(executionId)}`, 'results.get'),
  phase: (executionId: string) =>
    get<unknown>(`/api/results/${encodeURIComponent(executionId)}/phase`, 'results.phase'),
  forProject: (projectId: string) =>
    get<unknown[]>(`/api/projects/${encodeURIComponent(projectId)}/results`, 'results.forProject'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Canonical export — named sub-clients and the error type
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Typed REST API client (SIMP-Y19).
 *
 * All REST-owned endpoints are available as namespaced sub-clients.
 * GraphQL-owned domains are NOT here — use the GraphQL client.
 */
export const yappcApi = {
  auth,
  userProfile,
  billing,
  operations,
  collaboration,
  settings,
  anomalies,
  canvas,
  errorReporting,
  workspaces,
  projects,
  lifecycle,
  intent,
  shape,
  generate,
  ai,
  workflows,
  run,
  validate,
  artifacts,
  pageArtifacts,
  sourceImports,
  codeAssociations,
  gates,
  telemetry,
  audit,
  personas,
  phases,
  previewSessions,
  rateLimit,
  userData,
  results,
} as const;

export type YappcApiClient = typeof yappcApi;
