/**
 * YAPPC Vector API Client
 *
 * Domain-scoped client for vector search and RAG operations.
 *
 * @doc.type module
 * @doc.purpose Domain-scoped REST client for YAPPC vector APIs
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
// Vector Search
// ─────────────────────────────────────────────────────────────────────────────

export interface VectorSearchRequest {
  readonly query: string;
  readonly topK?: number;
  readonly filters?: Record<string, unknown>;
}

export interface VectorSearchResult {
  readonly id: string;
  readonly score: number;
  readonly metadata: Record<string, unknown>;
}

export const vector = {
  search: (body: VectorSearchRequest) =>
    post<VectorSearchRequest, VectorSearchResult[]>('/api/v1/vector/search', body, 'vector.search'),
  searchHybrid: (body: VectorSearchRequest) =>
    post<VectorSearchRequest, VectorSearchResult[]>('/api/v1/vector/search/hybrid', body, 'vector.searchHybrid'),
  similar: (id: string) =>
    get<VectorSearchResult[]>(`/api/v1/vector/similar/${encodeURIComponent(id)}`, 'vector.similar'),
  index: (body: { id: string; content: string; metadata?: Record<string, unknown> }) =>
    post<typeof body, { success: boolean }>('/api/v1/vector/index', body, 'vector.index'),
  indexBatch: (body: { id: string; content: string; metadata?: Record<string, unknown> }[]) =>
    post<typeof body, { success: boolean }>('/api/v1/vector/index/batch', body, 'vector.indexBatch'),
  delete: (id: string) =>
    del<{ success: boolean }>(`/api/v1/vector/index/${encodeURIComponent(id)}`, 'vector.delete'),
  rag: (body: { query: string; context?: Record<string, unknown> }) =>
    post<typeof body, { answer: string; sources: VectorSearchResult[] }>('/api/v1/vector/rag', body, 'vector.rag'),
  ragChat: (body: { query: string; conversationId?: string; context?: Record<string, unknown> }) =>
    post<typeof body, { answer: string; sources: VectorSearchResult[] }>('/api/v1/vector/rag/chat', body, 'vector.ragChat'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Export
// ─────────────────────────────────────────────────────────────────────────────

/**
 * YAPPC Vector API client
 * Groups all vector-related APIs: search, indexing, RAG
 */
export const yappcVectorClient = {
  vector,
} as const;
