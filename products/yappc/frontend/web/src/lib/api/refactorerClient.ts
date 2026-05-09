/**
 * YAPPC Refactorer API Client
 *
 * Domain-scoped client for refactoring job operations.
 *
 * @doc.type module
 * @doc.purpose Domain-scoped REST client for YAPPC refactorer APIs
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
// Jobs
// ─────────────────────────────────────────────────────────────────────────────

export interface RefactorJob {
  readonly jobId: string;
  readonly status: 'pending' | 'running' | 'completed' | 'failed';
  readonly createdAt: string;
  readonly completedAt?: string;
  readonly error?: string;
}

export const jobs = {
  create: (body: { targetPath: string; rules: string[] }) =>
    post<typeof body, RefactorJob>('/api/v1/jobs', body, 'jobs.create'),
  get: (jobId: string) => get<RefactorJob>(`/api/v1/jobs/${encodeURIComponent(jobId)}`, 'jobs.get'),
  delete: (jobId: string) => del<{ success: boolean }>(`/api/v1/jobs/${encodeURIComponent(jobId)}`, 'jobs.delete'),
  report: (jobId: string) => get<{ changes: unknown[] }>(`/api/v1/jobs/${encodeURIComponent(jobId)}/report`, 'jobs.report'),
  start: (jobId: string) => post<{ jobId: string }, RefactorJob>(`/api/v1/jobs/${encodeURIComponent(jobId)}/start`, { jobId }, 'jobs.start'),
  stop: (jobId: string) => post<{ jobId: string }, RefactorJob>(`/api/v1/jobs/${encodeURIComponent(jobId)}/stop`, { jobId }, 'jobs.stop'),
  runs: (jobId: string) => post<{ jobId: string }, { runs: unknown[] }>(`/api/v1/jobs/${encodeURIComponent(jobId)}/runs`, { jobId }, 'jobs.runs'),
  getRuns: (jobId: string) => get<{ runs: unknown[] }>(`/api/v1/jobs/${encodeURIComponent(jobId)}/runs`, 'jobs.getRuns'),
  getRunLogs: (jobId: string, runId: string) =>
    get<{ logs: string[] }>(`/api/v1/jobs/${encodeURIComponent(jobId)}/runs/${encodeURIComponent(runId)}/logs`, 'jobs.getRunLogs'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Diagnostics
// ─────────────────────────────────────────────────────────────────────────────

export const diagnostics = {
  diagnose: (body: { code: string }) =>
    post<typeof body, { issues: unknown[] }>('/v1/diagnose', body, 'diagnostics.diagnose'),
  config: () => get<{ version: string; rules: string[] }>('/v1/config', 'diagnostics.config'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Export
// ─────────────────────────────────────────────────────────────────────────────

/**
 * YAPPC Refactorer API client
 * Groups all refactorer-related APIs: jobs, diagnostics
 */
export const refactorerClient = {
  jobs,
  diagnostics,
} as const;
