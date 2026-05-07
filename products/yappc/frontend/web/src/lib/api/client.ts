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
 * @doc.type module
 * @doc.purpose Typed REST API client (SIMP-Y19)
 * @doc.layer product
 * @doc.pattern API Client
 */

import { parseJsonResponse, readErrorResponse } from '@/lib/http';

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
export interface UserProfile {
  id: string;
  email: string;
  name: string;
  role: string;
  tenantId: string;
}

export const auth = {
  login: (body: LoginRequest) => post<LoginRequest, AuthTokenResponse>('/api/auth/login', body, 'auth.login'),
  refresh: (body: { refreshToken: string }) =>
    post<{ refreshToken: string }, AuthTokenResponse>('/api/auth/refresh', body, 'auth.refresh'),
  logout: () => post<Record<string, never>, void>('/api/auth/logout', {}, 'auth.logout'),
  validate: () => get<{ valid: boolean }>('/api/auth/validate', 'auth.validate'),
  me: () => get<UserProfile>('/api/auth/me', 'auth.me'),
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
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Projects  (/api/projects/*)
// ─────────────────────────────────────────────────────────────────────────────

export interface Project {
  id: string;
  name: string;
  description?: string;
  workspaceId: string;
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

export const projects = {
  list: (workspaceId: string) =>
    get<ProjectListResponse | Project[]>(
      `/api/projects${encodeQuery({ workspaceId })}`,
      'projects.list',
    ),
  get: (projectId: string) => get<Project>(`/api/projects/${encodeURIComponent(projectId)}`, 'projects.get'),
  create: (body: CreateProjectRequest) => post<CreateProjectRequest, Project>('/api/projects', body, 'projects.create'),
  update: (projectId: string, body: UpdateProjectRequest) =>
    patch<UpdateProjectRequest, Project>(`/api/projects/${encodeURIComponent(projectId)}`, body, 'projects.update'),
  delete: (projectId: string) => del<void>(`/api/projects/${encodeURIComponent(projectId)}`, 'projects.delete'),
  suggestName: () => get<{ name: string }>('/api/projects/suggest-name', 'projects.suggestName'),
  setupSuggestion: (body: { intent?: string; workspaceId?: string }) =>
    post<typeof body, { name: string; description: string }>('/api/projects/setup-suggestion', body, 'projects.setupSuggestion'),
  current: (projectId: string) =>
    get<Project>(`/api/projects/${encodeURIComponent(projectId)}/current`, 'projects.current'),
  activity: (projectId: string) =>
    get<ProjectActivityResponse>(`/api/projects/${encodeURIComponent(projectId)}/activity`, 'projects.activity'),
  aiCost: (projectId: string) =>
    get<{ totalCostUsd: number; breakdown: unknown[] }>(`/api/projects/${encodeURIComponent(projectId)}/ai-cost`, 'projects.aiCost'),
  capabilities: (projectId: string) =>
    get<unknown>(`/api/projects/${encodeURIComponent(projectId)}/capabilities`, 'projects.capabilities'),
  refreshAi: (projectId: string) =>
    post<Record<string, never>, void>(`/api/projects/${encodeURIComponent(projectId)}/refresh-ai`, {}, 'projects.refreshAi'),
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
export interface GenerateArtifactsResponse {
  readonly runId?: string;
  readonly executionId?: string;
  readonly status?: string;
  readonly reviewRequired?: boolean;
  readonly diff?: unknown;
}
export interface RegenerateDiffRequest {
  readonly runId: string;
  readonly diff: string;
}
export interface RegenerateDiffResponse {
  readonly runId?: string;
  readonly status?: string;
  readonly diff?: unknown;
  readonly reviewRequired?: boolean;
}

export const generate = {
  run: (body: GenerateArtifactsRequest) =>
    postPossiblyEmpty<GenerateArtifactsRequest, GenerateArtifactsResponse>('/api/v1/yappc/generate', body, 'generate.run'),
  diff: (body: RegenerateDiffRequest) =>
    postPossiblyEmpty<RegenerateDiffRequest, RegenerateDiffResponse>('/api/v1/yappc/generate/diff', body, 'generate.diff'),
  artifact: (id: string) =>
    get<{ id: string; content: unknown }>(`/api/v1/yappc/generate/artifacts/${encodeURIComponent(id)}`, 'generate.artifact'),
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
}

export const telemetry = {
  reportError: (body: FrontendErrorReport) =>
    post<FrontendErrorReport, void>('/api/telemetry/frontend-errors', body, 'telemetry.reportError'),
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
  workspaces,
  projects,
  lifecycle,
  intent,
  shape,
  generate,
  workflows,
  validate,
  artifacts,
  sourceImports,
  codeAssociations,
  gates,
  telemetry,
  audit,
  personas,
  phases,
  userData,
  results,
} as const;

export type YappcApiClient = typeof yappcApi;
