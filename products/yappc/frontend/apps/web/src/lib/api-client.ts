/**
 * Centralized API Client Factory for YAPPC
 * 
 * Provides configured HTTP and GraphQL clients with:
 * - Automatic authentication headers
 * - Tenant context injection
 * - Error handling and retry logic
 * - Request/response interceptors
 * 
 * @doc.type module
 * @doc.purpose API client configuration
 * @doc.layer infrastructure
 * @doc.pattern Factory
 */

import { ApolloClient, InMemoryCache, createHttpLink, from } from '@apollo/client';
import { setContext } from '@apollo/client/link/context';
import { onError } from '@apollo/client/link/error';
import { RetryLink } from '@apollo/client/link/retry';

// ============================================================================
// CONFIGURATION
// ============================================================================

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
const GRAPHQL_ENDPOINT = `${API_BASE_URL}/graphql`;
const REST_API_ENDPOINT = `${API_BASE_URL}/api/v1`;

// ============================================================================
// AUTH TOKEN MANAGEMENT
// ============================================================================

/**
 * Gets the current authentication token from session storage or cookies.
 */
function getAuthToken(): string | null {
  // Try session storage first (set by AuthProvider)
  const sessionToken = sessionStorage.getItem('auth_token');
  if (sessionToken) {
    return sessionToken;
  }

  // Fallback to cookie-based auth (set by auth-gateway)
  const cookies = document.cookie.split(';');
  const authCookie = cookies.find(c => c.trim().startsWith('auth_token='));
  if (authCookie) {
    return authCookie.split('=')[1];
  }

  return null;
}

/**
 * Gets the current tenant ID from session storage.
 */
function getTenantId(): string | null {
  return sessionStorage.getItem('tenant_id');
}

// ============================================================================
// HTTP CLIENT (Fetch-based)
// ============================================================================

export interface ApiClientConfig {
  headers?: Record<string, string>;
  timeout?: number;
  retries?: number;
}

export interface ApiResponse<T> {
  data: T;
  status: number;
  headers: Headers;
}

export interface ApiError {
  message: string;
  status?: number;
  code?: string;
  details?: unknown;
}

/**
 * HTTP API Client with automatic auth and tenant headers.
 */
export class HttpApiClient {
  private baseUrl: string;
  private defaultHeaders: Record<string, string>;
  private timeout: number;
  private retries: number;

  constructor(config: ApiClientConfig = {}) {
    this.baseUrl = REST_API_ENDPOINT;
    this.defaultHeaders = {
      'Content-Type': 'application/json',
      ...config.headers,
    };
    this.timeout = config.timeout || 30000; // 30 seconds
    this.retries = config.retries || 2;
  }

  /**
   * Builds request headers with auth and tenant context.
   */
  private buildHeaders(customHeaders?: Record<string, string>): Record<string, string> {
    const headers = { ...this.defaultHeaders, ...customHeaders };

    // Add auth token if available
    const token = getAuthToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    // Add tenant ID if available
    const tenantId = getTenantId();
    if (tenantId) {
      headers['X-Tenant-Id'] = tenantId;
    }

    return headers;
  }

  /**
   * Executes a fetch request with timeout and retry logic.
   */
  private async fetchWithRetry<T>(
    url: string,
    options: RequestInit,
    attempt = 1
  ): Promise<ApiResponse<T>> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.timeout);

    try {
      const response = await fetch(url, {
        ...options,
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        // Retry on 5xx errors
        if (response.status >= 500 && attempt < this.retries) {
          await new Promise(resolve => setTimeout(resolve, 1000 * attempt));
          return this.fetchWithRetry(url, options, attempt + 1);
        }

        const errorBody = await response.json().catch(() => ({}));
        throw {
          message: errorBody.message || response.statusText,
          status: response.status,
          code: errorBody.code,
          details: errorBody,
        } as ApiError;
      }

      const data = await response.json();
      return {
        data,
        status: response.status,
        headers: response.headers,
      };
    } catch (error) {
      clearTimeout(timeoutId);

      if (error instanceof Error && error.name === 'AbortError') {
        throw {
          message: 'Request timeout',
          code: 'TIMEOUT',
        } as ApiError;
      }

      throw error;
    }
  }

  /**
   * GET request
   */
  async get<T>(path: string, headers?: Record<string, string>): Promise<ApiResponse<T>> {
    return this.fetchWithRetry<T>(`${this.baseUrl}${path}`, {
      method: 'GET',
      headers: this.buildHeaders(headers),
    });
  }

  /**
   * POST request
   */
  async post<T>(
    path: string,
    body?: unknown,
    headers?: Record<string, string>
  ): Promise<ApiResponse<T>> {
    return this.fetchWithRetry<T>(`${this.baseUrl}${path}`, {
      method: 'POST',
      headers: this.buildHeaders(headers),
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  /**
   * PUT request
   */
  async put<T>(
    path: string,
    body?: unknown,
    headers?: Record<string, string>
  ): Promise<ApiResponse<T>> {
    return this.fetchWithRetry<T>(`${this.baseUrl}${path}`, {
      method: 'PUT',
      headers: this.buildHeaders(headers),
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  /**
   * PATCH request
   */
  async patch<T>(
    path: string,
    body?: unknown,
    headers?: Record<string, string>
  ): Promise<ApiResponse<T>> {
    return this.fetchWithRetry<T>(`${this.baseUrl}${path}`, {
      method: 'PATCH',
      headers: this.buildHeaders(headers),
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  /**
   * DELETE request
   */
  async delete<T>(path: string, headers?: Record<string, string>): Promise<ApiResponse<T>> {
    return this.fetchWithRetry<T>(`${this.baseUrl}${path}`, {
      method: 'DELETE',
      headers: this.buildHeaders(headers),
    });
  }
}

// ============================================================================
// GRAPHQL CLIENT (Apollo)
// ============================================================================

/**
 * Creates an Apollo Client instance with auth and error handling.
 */
export function createGraphQLClient() {
  // HTTP link for GraphQL requests
  const httpLink = createHttpLink({
    uri: GRAPHQL_ENDPOINT,
  });

  // Auth link - adds auth and tenant headers
  const authLink = setContext((_, { headers }) => {
    const token = getAuthToken();
    const tenantId = getTenantId();

    return {
      headers: {
        ...headers,
        ...(token && { authorization: `Bearer ${token}` }),
        ...(tenantId && { 'x-tenant-id': tenantId }),
      },
    };
  });

  // Error link - handles GraphQL and network errors
  const errorLink = onError((errorResponse: unknown) => {
    const { graphQLErrors, networkError, operation } = errorResponse;
    
    if (graphQLErrors) {
      graphQLErrors.forEach((error: unknown) => {
        const { message, locations, path, extensions } = error;
        console.error(
          `[GraphQL error]: Message: ${message}, Location: ${locations}, Path: ${path}`,
          extensions
        );

        // Handle auth errors
        if (extensions?.code === 'UNAUTHENTICATED') {
          // Redirect to login or refresh token
          window.location.href = '/login';
        }
      });
    }

    if (networkError) {
      console.error(`[Network error]: ${networkError}`, {
        operation: operation.operationName,
      });
    }
  });

  // Retry link - retries failed requests
  const retryLink = new RetryLink({
    delay: {
      initial: 300,
      max: 3000,
      jitter: true,
    },
    attempts: {
      max: 3,
      retryIf: (error) => {
        // Retry on network errors and 5xx server errors
        return !!error && !error.message.includes('UNAUTHENTICATED');
      },
    },
  });

  return new ApolloClient({
    link: from([errorLink, authLink, retryLink, httpLink]),
    cache: new InMemoryCache({
      typePolicies: {
        Query: {
          fields: {
            // Add cache policies here
          },
        },
      },
    }),
    defaultOptions: {
      watchQuery: {
        fetchPolicy: 'cache-and-network',
        errorPolicy: 'all',
      },
      query: {
        fetchPolicy: 'network-only',
        errorPolicy: 'all',
      },
      mutate: {
        errorPolicy: 'all',
      },
    },
  });
}

// ============================================================================
// SINGLETON INSTANCES
// ============================================================================

/**
 * Default HTTP API client instance.
 * Use this for REST API calls.
 */
export const apiClient = new HttpApiClient();

/**
 * Default GraphQL client instance.
 * Use this for GraphQL queries and mutations.
 */
export const graphqlClient = createGraphQLClient();

// ============================================================================
// CONVENIENCE HOOKS
// ============================================================================

/**
 * Hook to get the HTTP API client.
 * Creates a new instance if custom config is needed.
 */
export function useApiClient(config?: ApiClientConfig): HttpApiClient {
  if (config) {
    return new HttpApiClient(config);
  }
  return apiClient;
}

/**
 * Hook to get the GraphQL client.
 */
export function useGraphQLClient() {
  return graphqlClient;
}
