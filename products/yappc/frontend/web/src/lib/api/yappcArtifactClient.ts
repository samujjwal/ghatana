/**
 * YAPPC Artifact API Client
 *
 * Domain-scoped client for artifact graph operations and preview session management.
 *
 * @doc.type module
 * @doc.purpose Domain-scoped REST client for YAPPC artifact APIs
 * @doc.layer product
 * @doc.pattern API Client
 */

import { parseJsonResponse, readErrorResponse } from '@/lib/http';

// ─────────────────────────────────────────────────────────────────────────────
// Domain types
// ─────────────────────────────────────────────────────────────────────────────

export interface ApiError {
  readonly status: number;
  readonly message: string;
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

async function post<TRequest, TResponse>(path: string, body: TRequest, context: string): Promise<TResponse> {
  const response = await fetch(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });
  if (!response.ok) return handleError(response, context);
  return parseJsonResponse<TResponse>(response, context);
}

async function postWithHeaders<TRequest, TResponse>(
  path: string,
  body: TRequest,
  context: string,
  headers: Readonly<Record<string, string>>,
): Promise<TResponse> {
  const response = await fetch(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...headers,
    },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });
  if (!response.ok) return handleError(response, context);
  return parseJsonResponse<TResponse>(response, context);
}

async function del<T>(path: string, context: string): Promise<T> {
  const response = await fetch(path, {
    method: 'DELETE',
    headers: { Accept: 'application/json' },
    credentials: 'same-origin',
  });
  if (!response.ok) return handleError(response, context);
  return parseJsonResponse<T>(response, context);
}

// ─────────────────────────────────────────────────────────────────────────────
// Artifacts
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
// Page Artifacts
// ─────────────────────────────────────────────────────────────────────────────

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
// Source Import
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
// Code Associations
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
// Phase Gates
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
// Preview Sessions
// ─────────────────────────────────────────────────────────────────────────────

export interface PreviewSessionContext {
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
  readonly artifactId: string;
  readonly userId: string;
  readonly expiration?: number; // seconds until expiration, defaults to server default
  readonly viewport?: { width: number; height: number };
  readonly theme?: 'light' | 'dark' | 'auto';
  readonly locale?: string;
}

export interface PreviewSessionIssueResponse {
  readonly sessionId: string;
  readonly sessionToken: string;
  readonly expiresAt: string;
  readonly previewUrl: string;
}

export interface PreviewSessionValidateResponse {
  readonly valid: boolean;
  readonly sessionId?: string;
  readonly expiresAt?: string;
  readonly error?: string;
}

function buildAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = {};
  // In a real implementation, this would extract auth headers from the auth service
  // For now, this is a placeholder that would be implemented with proper auth integration
  return headers;
}

export const previewSessions = {
  issue: (body: PreviewSessionContext) =>
    postWithHeaders<PreviewSessionContext, PreviewSessionIssueResponse>(
      '/api/v1/preview/session/create',
      body,
      'previewSessions.issue',
      buildAuthHeaders(),
    ),
  validate: (sessionToken: string) =>
    postWithHeaders<{ sessionToken: string }, PreviewSessionValidateResponse>(
      '/api/v1/preview/session/validate',
      { sessionToken },
      'previewSessions.validate',
      buildAuthHeaders(),
    ),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Export
// ─────────────────────────────────────────────────────────────────────────────

/**
 * YAPPC Artifact API client
 * Groups all artifact-related APIs: artifacts, pageArtifacts, sourceImports, codeAssociations, gates, previewSessions
 */
export const yappcArtifactClient = {
  artifacts,
  pageArtifacts,
  sourceImports,
  codeAssociations,
  gates,
  previewSessions,
} as const;
