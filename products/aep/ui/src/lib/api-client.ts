/**
 * AEP API Client Wrapper
 *
 * Type-safe API client for AEP backend using generated types.
 *
 * NOTE: Pipeline CRUD and SSE are now in `@/api/pipeline.api.ts` and `@/api/sse.ts`.
 * This module provides typed wrappers using OpenAPI-generated types for consumers
 * that need strict contract adherence. All HTTP calls go through the shared
 * `http-client.ts` configuration.
 *
 * @doc.type api-client
 * @doc.purpose Type-safe AEP API wrapper using generated OpenAPI types
 * @doc.layer frontend
 */
import type { paths } from '../generated/aep-client';
import { API_BASE_URL, getAuthToken } from './http-client';

// Helper to make authenticated fetch requests
async function fetchWithAuth<T>(
  url: string,
  options: RequestInit = {}
): Promise<T> {
  const token = getAuthToken();

  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token && { 'Authorization': `Bearer ${token}` }),
      ...options.headers,
    },
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ error: 'Unknown error' }));
    throw new Error(error.error || `HTTP ${response.status}`);
  }

  return response.json();
}

// Pipeline API
export const pipelineApi = {
  list: (params?: { tenantId?: string; limit?: number; offset?: number }) => 
    fetchWithAuth<paths['/api/v1/pipelines']['get']['responses'][200]['content']['application/json']>(
      `${API_BASE_URL}/api/v1/pipelines?${new URLSearchParams(params as Record<string, string>)}`
    ),

  get: (id: string) =>
    fetchWithAuth<paths['/api/v1/pipelines/{id}']['get']['responses'][200]['content']['application/json']>(
      `${API_BASE_URL}/api/v1/pipelines/${id}`
    ),

  create: (data: paths['/api/v1/pipelines']['post']['requestBody']['content']['application/json']) =>
    fetchWithAuth<paths['/api/v1/pipelines']['post']['responses'][201]['content']['application/json']>(
      `${API_BASE_URL}/api/v1/pipelines`,
      { method: 'POST', body: JSON.stringify(data) }
    ),

  update: (id: string, data: paths['/api/v1/pipelines/{id}']['put']['requestBody']['content']['application/json']) =>
    fetchWithAuth<paths['/api/v1/pipelines/{id}']['put']['responses'][200]['content']['application/json']>(
      `${API_BASE_URL}/api/v1/pipelines/${id}`,
      { method: 'PUT', body: JSON.stringify(data) }
    ),

  delete: (id: string) =>
    fetchWithAuth<never>(
      `${API_BASE_URL}/api/v1/pipelines/${id}`,
      { method: 'DELETE' }
    ),
};

// Health API
export const healthApi = {
  check: () =>
    fetchWithAuth<paths['/health']['get']['responses'][200]['content']['application/json']>(
      `${API_BASE_URL}/health`
    ),
  
  ready: () =>
    fetchWithAuth<paths['/ready']['get']['responses'][200]['content']['application/json']>(
      `${API_BASE_URL}/ready`
    ),
};

// Event Stream (SSE) — prefer `@/api/sse.ts` for new code (has reconnect logic)
export function createEventStream(token?: string): EventSource {
  const url = new URL('/events/stream', API_BASE_URL || window.location.origin);
  if (token) {
    url.searchParams.set('token', token);
  }
  return new EventSource(url.toString());
}

// Export types for convenience
export type { paths };
export type Pipeline = paths['/api/v1/pipelines']['get']['responses'][200]['content']['application/json']['pipelines'][number];
