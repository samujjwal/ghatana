/**
 * YAPPC Agents API Client
 *
 * Domain-scoped client for agent registry and execution operations.
 *
 * @doc.type module
 * @doc.purpose Domain-scoped REST client for YAPPC agent APIs
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

// ─────────────────────────────────────────────────────────────────────────────
// Agents
// ─────────────────────────────────────────────────────────────────────────────

export interface AgentInfo {
  readonly name: string;
  readonly description: string;
  readonly capabilities: readonly string[];
  readonly status: 'available' | 'unavailable';
}

export const agents = {
  list: () => get<AgentInfo[]>('/api/v1/agents', 'agents.list'),
  health: () => get<{ status: string }>(' /api/v1/agents/health', 'agents.health'),
  capabilities: () => get<{ capabilities: string[] }>('/api/v1/agents/capabilities', 'agents.capabilities'),
  byCapability: (capability: string) =>
    get<AgentInfo[]>(`/api/v1/agents/by-capability/${encodeURIComponent(capability)}`, 'agents.byCapability'),
  get: (name: string) => get<AgentInfo>(`/api/v1/agents/${encodeURIComponent(name)}`, 'agents.get'),
  healthByName: (name: string) =>
    get<{ status: string }>(`/api/v1/agents/${encodeURIComponent(name)}/health`, 'agents.healthByName'),
  execute: (name: string, body: { input: unknown; context?: Record<string, unknown> }) =>
    post<typeof body, { result: unknown }>(`/api/v1/agents/${encodeURIComponent(name)}/execute`, body, 'agents.execute'),
  copilotChat: (body: { message: string; context?: Record<string, unknown> }) =>
    post<typeof body, { response: string }>('/api/v1/agents/copilot/chat', body, 'agents.copilotChat'),
  search: (body: { query: string }) =>
    post<typeof body, AgentInfo[]>('/api/v1/agents/search', body, 'agents.search'),
  predict: (body: { input: unknown }) =>
    post<typeof body, { prediction: unknown }>('/api/v1/agents/predict', body, 'agents.predict'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Export
// ─────────────────────────────────────────────────────────────────────────────

/**
 * YAPPC Agents API client
 * Groups all agent-related APIs
 */
export const yappcAgentsClient = {
  agents,
} as const;
