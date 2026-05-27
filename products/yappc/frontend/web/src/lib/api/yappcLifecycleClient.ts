/**
 * YAPPC Lifecycle API Client
 *
 * Domain-scoped client for core lifecycle APIs including intent, shape, validate,
 * generate, run, observe, learn, evolve, and lifecycle phase management.
 *
 * @doc.type module
 * @doc.purpose Domain-scoped REST client for YAPPC lifecycle APIs
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

async function postPossiblyEmpty<TRequest, TResponse>(path: string, body: TRequest, context: string): Promise<TResponse> {
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
  const text = await response.text();
  if (!text) return {} as TResponse;
  return JSON.parse(text) as TResponse;
}

// ─────────────────────────────────────────────────────────────────────────────
// Lifecycle Phase Management
// ─────────────────────────────────────────────────────────────────────────────

export interface LifecyclePhaseInfo {
  readonly id: string;
  readonly name: string;
  readonly description: string;
  readonly order: number;
}

export interface LifecycleAdvanceRequest {
  readonly projectId: string;
  readonly targetPhase: string;
  readonly bypass?: boolean;
  readonly bypassReason?: string;
  readonly idempotencyKey?: string;
}

export interface LifecycleAdvanceResponse {
  readonly success: boolean;
  readonly currentPhase: string;
  readonly errors?: string[];
}

export const lifecycle = {
  phases: () => get<LifecyclePhaseInfo[]>('/api/v1/lifecycle/phases', 'lifecycle.phases'),
  advance: (body: LifecycleAdvanceRequest) =>
    post<LifecycleAdvanceRequest, LifecycleAdvanceResponse>('/api/v1/lifecycle/advance', body, 'lifecycle.advance'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Intent Phase
// ─────────────────────────────────────────────────────────────────────────────

export const intent = {
  capture: (body: { text: string; projectId: string }) =>
    post<typeof body, { id: string; status: string }>('/api/v1/yappc/intent/capture', body, 'intent.capture'),
  analyze: (body: { intentId: string }) =>
    post<typeof body, { analysis: unknown }>('/api/v1/yappc/intent/analyze', body, 'intent.analyze'),
  get: (id: string) =>
    get<{ id: string; text: string; analysis: unknown }>(`/api/v1/yappc/intent/${encodeURIComponent(id)}`, 'intent.get'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Shape Phase
// ─────────────────────────────────────────────────────────────────────────────

export const shape = {
  derive: (body: { intentId: string; projectId: string }) =>
    post<typeof body, { shapeId: string }>('/api/v1/yappc/shape/derive', body, 'shape.derive'),
  model: (body: { shapeId: string }) =>
    post<typeof body, { model: unknown }>('/api/v1/yappc/shape/model', body, 'shape.model'),
  get: (id: string) =>
    get<{ id: string; model: unknown }>(`/api/v1/yappc/shape/${encodeURIComponent(id)}`, 'shape.get'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Validate Phase
// ─────────────────────────────────────────────────────────────────────────────

export const validate = {
  run: (body: { executionId: string }) =>
    post<typeof body, { valid: boolean; errors: unknown[] }>('/api/v1/yappc/validate', body, 'validate.run'),
  withConfig: (body: { executionId: string; config: unknown }) =>
    post<typeof body, { valid: boolean; errors: unknown[] }>('/api/v1/yappc/validate/with-config', body, 'validate.withConfig'),
  withPolicy: (body: { executionId: string; policyId: string }) =>
    post<typeof body, { valid: boolean; errors: unknown[] }>('/api/v1/yappc/validate/with-policy', body, 'validate.withPolicy'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Generate Phase
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
  readonly runId: string;
  readonly executionId?: string;
  readonly status: 'pending' | 'running' | 'completed' | 'failed' | 'degraded';
  readonly reviewRequired: boolean;
  readonly diff?: GenerateDiffReview;
  // Provenance metadata (required for production-grade generation)
  readonly traceId?: string;
  readonly evidenceIds?: readonly string[];
  readonly policyDecisionId?: string;
  readonly degraded?: boolean;
  readonly degradedReason?: string;
  readonly generatedAt: string;
  readonly model?: string;
  readonly confidence?: number;
  readonly confidenceReason?: string;
}

export interface RegenerateDiffRequest {
  readonly runId: string;
  readonly diff: string;
}

export interface RegenerateDiffResponse {
  readonly runId: string;
  readonly status: 'pending' | 'running' | 'completed' | 'failed' | 'degraded';
  readonly diff?: GenerateDiffReview;
  readonly reviewRequired: boolean;
  // Provenance metadata
  readonly traceId?: string;
  readonly evidenceIds?: readonly string[];
  readonly policyDecisionId?: string;
  readonly degraded?: boolean;
  readonly degradedReason?: string;
  readonly generatedAt: string;
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
// Run Phase
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

export interface RetryRunRequest {
  readonly failedRunId: string;
  readonly runSpec: {
    readonly id: string;
    readonly artifactsRef?: string;
    readonly environment?: string;
    readonly tasks?: ReadonlyArray<Record<string, unknown>>;
    readonly config?: Record<string, string>;
  };
}

export const run = {
  retry: (body: RetryRunRequest) =>
    postPossiblyEmpty<RetryRunRequest, RunOperationResponse>('/api/v1/yappc/run/retry', body, 'run.retry'),
  rollback: (body: RunRollbackRequest) =>
    postPossiblyEmpty<RunRollbackRequest, RunOperationResponse>('/api/v1/yappc/run/rollback', body, 'run.rollback'),
  promote: (body: RunPromoteRequest) =>
    postPossiblyEmpty<RunPromoteRequest, RunOperationResponse>('/api/v1/yappc/run/promote', body, 'run.promote'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Observe Phase
// ─────────────────────────────────────────────────────────────────────────────

export const observe = {
  collect: (body: { runId: string; observations: unknown[] }) =>
    post<typeof body, { status: string }>('/api/v1/yappc/observe', body, 'observe.collect'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Learn Phase
// ─────────────────────────────────────────────────────────────────────────────

export const learn = {
  analyze: (body: { runId: string }) =>
    post<typeof body, { insights: unknown[] }>('/api/v1/yappc/learn', body, 'learn.analyze'),
  withContext: (body: { runId: string; context: Record<string, unknown> }) =>
    post<typeof body, { insights: unknown[] }>('/api/v1/yappc/learn/with-context', body, 'learn.withContext'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Evolve Phase
// ─────────────────────────────────────────────────────────────────────────────

export const evolve = {
  propose: (body: { projectId: string; currentArchitecture: unknown }) =>
    post<typeof body, { proposal: unknown }>('/api/v1/yappc/evolve', body, 'evolve.propose'),
  withConstraints: (body: { projectId: string; constraints: Record<string, unknown> }) =>
    post<typeof body, { proposal: unknown }>('/api/v1/yappc/evolve/with-constraints', body, 'evolve.withConstraints'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Export
// ─────────────────────────────────────────────────────────────────────────────

/**
 * YAPPC Lifecycle API client
 * Groups all lifecycle-related APIs: intent, shape, validate, generate, run, observe, learn, evolve
 */
export const yappcLifecycleClient = {
  lifecycle,
  intent,
  shape,
  validate,
  generate,
  run,
  observe,
  learn,
  evolve,
} as const;
