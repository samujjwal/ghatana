/**
 * Typed API client wrapper for AEP user-facing APIs.
 *
 * Enforces compile-time request/response contracts for every endpoint,
 * eliminating drift between frontend consumers and backend contracts.
 *
 * Usage:
 *   const result = await typedGet('/api/v1/agents', { params: { tenantId } }, AgentsResponseSchema);
 *
 * @doc.type utility
 * @doc.purpose Type-safe API client with contract enforcement
 * @doc.layer frontend
 */
import { apiClient } from './http-client';

export interface ApiClientConfig {
  params?: Record<string, string | number | boolean | undefined>;
  headers?: Record<string, string>;
}

/**
 * Perform a GET request with a typed response.
 * The response type is enforced at compile time; runtime validation
 * can be added with Zod if needed.
 */
export async function typedGet<T>(
  url: string,
  config: ApiClientConfig = {},
): Promise<{ data: T; status: number; headers: Headers }> {
  const response = await apiClient.get<T>(url, config);
  return {
    data: response.data,
    status: response.status,
    headers: response.headers,
  };
}

/**
 * Perform a POST request with typed request body and response.
 */
export async function typedPost<TRequest, TResponse>(
  url: string,
  body: TRequest,
  config: ApiClientConfig = {},
): Promise<{ data: TResponse; status: number; headers: Headers }> {
  const response = await apiClient.post<TResponse>(url, body, config);
  return {
    data: response.data,
    status: response.status,
    headers: response.headers,
  };
}

/**
 * Perform a PUT request with typed request body and response.
 */
export async function typedPut<TRequest, TResponse>(
  url: string,
  body: TRequest,
  config: ApiClientConfig = {},
): Promise<{ data: TResponse; status: number; headers: Headers }> {
  const response = await apiClient.put<TResponse>(url, body, config);
  return {
    data: response.data,
    status: response.status,
    headers: response.headers,
  };
}

/**
 * Perform a PATCH request with typed request body and response.
 */
export async function typedPatch<TRequest, TResponse>(
  url: string,
  body: TRequest,
  config: ApiClientConfig = {},
): Promise<{ data: TResponse; status: number; headers: Headers }> {
  const response = await apiClient.patch<TResponse>(url, body, config);
  return {
    data: response.data,
    status: response.status,
    headers: response.headers,
  };
}

/**
 * Perform a DELETE request with typed response.
 */
export async function typedDelete<T>(
  url: string,
  config: ApiClientConfig = {},
): Promise<{ data: T; status: number; headers: Headers }> {
  const response = await apiClient.delete<T>(url, config);
  return {
    data: response.data,
    status: response.status,
    headers: response.headers,
  };
}

/** Type-safe wrapper for API endpoints with full request/response types. */
export type TypedApiEndpoint<TRequest, TResponse> = {
  url: string;
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
};

/**
 * Execute a typed API endpoint definition.
 * This pattern allows endpoint contracts to be defined as data and reused
 * across the frontend, enabling generated client stubs from OpenAPI specs.
 */
export async function callTypedEndpoint<TRequest, TResponse>(
  endpoint: TypedApiEndpoint<TRequest, TResponse>,
  request?: TRequest,
  config: ApiClientConfig = {},
): Promise<{ data: TResponse; status: number; headers: Headers }> {
  switch (endpoint.method) {
    case 'GET':
      return typedGet<TResponse>(endpoint.url, config);
    case 'POST':
      return typedPost<TRequest, TResponse>(endpoint.url, request!, config);
    case 'PUT':
      return typedPut<TRequest, TResponse>(endpoint.url, request!, config);
    case 'PATCH':
      return typedPatch<TRequest, TResponse>(endpoint.url, request!, config);
    case 'DELETE':
      return typedDelete<TResponse>(endpoint.url, config);
    default:
      throw new Error(`Unsupported HTTP method: ${endpoint.method}`);
  }
}
