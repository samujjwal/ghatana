/**
 * YAPPC Workflows API Client
 *
 * Domain-scoped client for workflow engine operations.
 *
 * @doc.type module
 * @doc.purpose Domain-scoped REST client for YAPPC workflow APIs
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

// ─────────────────────────────────────────────────────────────────────────────
// Workflows
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
// Export
// ─────────────────────────────────────────────────────────────────────────────

/**
 * YAPPC Workflows API client
 * Groups all workflow-related APIs
 */
export const yappcWorkflowsClient = {
  workflows,
} as const;
