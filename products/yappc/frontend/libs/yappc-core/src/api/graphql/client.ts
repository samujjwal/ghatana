/**
 * GraphQL API Client
 *
 * @description Production-ready GraphQL client with authentication, caching,
 * error handling, retry logic, and request/response interceptors.
 *
 * @doc.type service
 * @doc.purpose GraphQL API communication
 * @doc.layer infrastructure
 * @doc.pattern Client Factory
 */

import {
  ApolloClient,
  type FetchResult,
  InMemoryCache,
  HttpLink,
  from,
  ApolloLink,
  type NormalizedCacheObject,
  Observable,
} from '@apollo/client';
import { onError } from '@apollo/client/link/error';
import { RetryLink } from '@apollo/client/link/retry';

// =============================================================================
// CONFIGURATION
// =============================================================================

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:7002';
const GRAPHQL_ENDPOINT = `${API_BASE_URL}/graphql`;

// =============================================================================
// TOKEN MANAGEMENT
// =============================================================================

let accessToken: string | null = null;
let refreshToken: string | null = null;
let tokenRefreshPromise: Promise<string | null> | null = null;

interface TokenRefreshResponse {
  accessToken: string;
  refreshToken: string;
}

interface GraphQLErrorLike {
  message: string;
  locations?: unknown;
  path?: unknown;
  extensions?: unknown;
}

interface TelemetryRecorder {
  recordMetric: (
    name: string,
    value: number,
    tags?: Record<string, string>
  ) => void;
}

interface GraphQLClientHandle {
  clearStore: () => Promise<unknown>;
  stop: () => void;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isTokenRefreshResponse(value: unknown): value is TokenRefreshResponse {
  return (
    isRecord(value) &&
    typeof value.accessToken === 'string' &&
    typeof value.refreshToken === 'string'
  );
}

function getTelemetryRecorder(): TelemetryRecorder | null {
  if (typeof window === 'undefined') {
    return null;
  }

  const candidate = (window as Window & { __YAPPC_TELEMETRY__?: unknown })
    .__YAPPC_TELEMETRY__;
  if (isRecord(candidate) && typeof candidate.recordMetric === 'function') {
    return candidate as TelemetryRecorder;
  }

  return null;
}

function getExtensionCode(extensions: unknown): string | null {
  if (!isRecord(extensions) || typeof extensions.code !== 'string') {
    return null;
  }

  return extensions.code;
}

function getRetryAfter(extensions: unknown): number {
  if (!isRecord(extensions) || typeof extensions.retryAfter !== 'number') {
    return 60;
  }

  return extensions.retryAfter;
}

interface PaginatedConnection<TEdge = unknown> {
  edges: TEdge[];
  pageInfo: Record<string, unknown> | null;
}

interface PaginationArgs {
  after?: string | null;
}

function mergePaginatedConnection<TEdge>(
  existing: PaginatedConnection<TEdge> | undefined,
  incoming: PaginatedConnection<TEdge>,
  args: PaginationArgs | null | undefined
): PaginatedConnection<TEdge> {
  if (!args?.after) {
    return incoming;
  }

  return {
    ...incoming,
    edges: [...(existing?.edges ?? []), ...incoming.edges],
  };
}

export const setTokens = (access: string | null, refresh: string | null) => {
  accessToken = access;
  refreshToken = refresh;
  if (access) {
    localStorage.setItem('yappc:access-token', access);
  } else {
    localStorage.removeItem('yappc:access-token');
  }
  if (refresh) {
    localStorage.setItem('yappc:refresh-token', refresh);
  } else {
    localStorage.removeItem('yappc:refresh-token');
  }
};

export const getAccessToken = (): string | null => {
  if (!accessToken) {
    accessToken = localStorage.getItem('yappc:access-token');
  }
  return accessToken;
};

export const getRefreshToken = (): string | null => {
  if (!refreshToken) {
    refreshToken = localStorage.getItem('yappc:refresh-token');
  }
  return refreshToken;
};

export const clearTokens = () => {
  accessToken = null;
  refreshToken = null;
  localStorage.removeItem('yappc:access-token');
  localStorage.removeItem('yappc:refresh-token');
};

// Token refresh logic
async function refreshAccessToken(): Promise<string | null> {
  const refresh = getRefreshToken();
  if (!refresh) return null;

  try {
    const response = await fetch(`${API_BASE_URL}/v1/auth/refresh`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ refreshToken: refresh }),
    });

    if (!response.ok) {
      throw new Error('Token refresh failed');
    }

    const data: unknown = await response.json();
    if (!isTokenRefreshResponse(data)) {
      throw new Error('Token refresh returned an invalid payload');
    }

    const tokenResponse = data;
    setTokens(tokenResponse.accessToken, tokenResponse.refreshToken);
    return tokenResponse.accessToken;
  } catch (error) {
    clearTokens();
    window.dispatchEvent(new CustomEvent('auth:session-expired'));
    return null;
  }
}

// =============================================================================
// APOLLO LINKS
// =============================================================================

// Request timing link (for performance monitoring)
const timingLink = new ApolloLink((operation, forward) => {
  const startTime = performance.now();
  operation.setContext({ startTime });

  if (!forward) {
    return null;
  }

  const forwarded = forward(operation) as Observable<
    FetchResult<Record<string, unknown>>
  >;

  return new Observable<FetchResult<Record<string, unknown>>>((observer) => {
    const subscription = forwarded.subscribe({
      next: (response: FetchResult<Record<string, unknown>>) => {
        const duration = performance.now() - startTime;
        const operationName = operation.operationName;

        if (import.meta.env.DEV && duration > 1000) {
          console.warn(
            `[GraphQL] Slow query: ${operationName} took ${duration.toFixed(0)}ms`
          );
        }

        const telemetry = getTelemetryRecorder();
        if (telemetry) {
          telemetry.recordMetric('graphql.query.duration', duration, {
            operation: operationName,
          });
        }

        observer.next(response);
      },
      error: (subscriptionError: unknown) => observer.error(subscriptionError),
      complete: () => observer.complete(),
    });

    return () => {
      subscription.unsubscribe();
    };
  });
});

// Authentication link
const authLink = new ApolloLink((operation, forward) => {
  const token = getAccessToken();

  if (token) {
    operation.setContext(
      ({ headers = {} }: { headers?: Record<string, string> }) => ({
        headers: {
          ...headers,
          Authorization: `Bearer ${token}`,
        },
      })
    );
  }

  return forward ? forward(operation) : null;
});

// Error handling link with token refresh
const errorLink = onError(
  ({ graphQLErrors, networkError, operation, forward }) => {
    if (graphQLErrors) {
      for (const graphQLError of graphQLErrors as readonly GraphQLErrorLike[]) {
        const message = graphQLError.message;
        const locations = graphQLError.locations;
        const path = Array.isArray(graphQLError.path)
          ? graphQLError.path.map((segment) => String(segment))
          : undefined;
        const extensions = graphQLError.extensions;

        // Log error details
        console.error(`[GraphQL Error]: Message: ${message}`, {
          locations,
          path,
          extensions,
          operation: operation.operationName,
        });

        // Handle authentication errors
        const extensionCode = getExtensionCode(extensions);

        if (
          extensionCode === 'UNAUTHENTICATED' ||
          extensionCode === 'TOKEN_EXPIRED'
        ) {
          // Attempt token refresh
          if (!tokenRefreshPromise) {
            tokenRefreshPromise = refreshAccessToken().finally(() => {
              tokenRefreshPromise = null;
            });
          }

          return new Observable((observer) => {
            void tokenRefreshPromise
              ?.then((newToken) => {
                if (newToken) {
                  if (!forward) {
                    observer.error(
                      new Error('Unable to retry GraphQL operation')
                    );
                    return;
                  }

                  // Retry the request with new token
                  operation.setContext(
                    ({
                      headers = {},
                    }: {
                      headers?: Record<string, string>;
                    }) => ({
                      headers: {
                        ...headers,
                        Authorization: `Bearer ${newToken}`,
                      },
                    })
                  );
                  const retried = forward(operation) as Observable<
                    FetchResult<Record<string, unknown>>
                  >;
                  retried.subscribe(observer);
                } else {
                  observer.error(new Error(message));
                }
              })
              .catch((refreshError: unknown) => {
                observer.error(refreshError);
              });
          });
        }

        // Handle authorization errors
        if (extensionCode === 'FORBIDDEN') {
          window.dispatchEvent(
            new CustomEvent('auth:forbidden', {
              detail: { operation: operation.operationName, path },
            })
          );
        }

        // Handle rate limiting
        if (extensionCode === 'RATE_LIMITED') {
          const retryAfter = getRetryAfter(extensions);
          window.dispatchEvent(
            new CustomEvent('api:rate-limited', {
              detail: { retryAfter },
            })
          );
        }
      }
    }

    if (networkError) {
      const networkErrorMessage =
        networkError instanceof Error
          ? networkError.message
          : 'Unknown network error';
      console.error(`[Network Error]: ${networkErrorMessage}`);

      // Dispatch network error event for global handling
      window.dispatchEvent(
        new CustomEvent('api:network-error', {
          detail: { message: networkErrorMessage },
        })
      );
    }
  }
);

// Retry link for transient failures
const retryLink = new RetryLink({
  delay: {
    initial: 300,
    max: 5000,
    jitter: true,
  },
  attempts: {
    max: 3,
    retryIf: (error, _operation) => {
      // Retry on network errors or 5xx status codes
      if (!error) return false;

      const statusCode =
        isRecord(error) && typeof error.statusCode === 'number'
          ? error.statusCode
          : null;
      const isNetworkError = statusCode === null;
      const isServerError =
        statusCode !== null && statusCode >= 500 && statusCode < 600;
      const isTimeout =
        error instanceof Error ? error.message.includes('timeout') : false;

      return isNetworkError || isServerError || isTimeout;
    },
  },
});

// Request ID link (for tracing)
const requestIdLink = new ApolloLink((operation, forward) => {
  const requestId = crypto.randomUUID();

  operation.setContext(
    ({ headers = {} }: { headers?: Record<string, string> }) => ({
      headers: {
        ...headers,
        'X-Request-ID': requestId,
      },
    })
  );

  return forward ? forward(operation) : null;
});

// HTTP link
const httpLink = new HttpLink({
  uri: GRAPHQL_ENDPOINT,
  credentials: 'include',
  headers: {
    'Content-Type': 'application/json',
  },
});

// =============================================================================
// CACHE CONFIGURATION
// =============================================================================

const cache = new InMemoryCache({
  typePolicies: {
    Query: {
      fields: {
        // Pagination for stories list
        stories: {
          keyArgs: ['projectId', 'filter', 'sort'],
          merge(
            existing: PaginatedConnection | undefined,
            incoming: PaginatedConnection,
            { args }: { args?: PaginationArgs }
          ): PaginatedConnection {
            return mergePaginatedConnection(existing, incoming, args);
          },
        },
        // Pagination for incidents
        incidents: {
          keyArgs: ['projectId', 'filter'],
          merge(
            existing: PaginatedConnection | undefined,
            incoming: PaginatedConnection,
            { args }: { args?: PaginationArgs }
          ): PaginatedConnection {
            return mergePaginatedConnection(existing, incoming, args);
          },
        },
        // Pagination for vulnerabilities
        vulnerabilities: {
          keyArgs: ['projectId', 'filter'],
          merge(
            existing: PaginatedConnection | undefined,
            incoming: PaginatedConnection,
            { args }: { args?: PaginationArgs }
          ): PaginatedConnection {
            return mergePaginatedConnection(existing, incoming, args);
          },
        },
        // Pagination for audit logs
        auditLogs: {
          keyArgs: ['filter'],
          merge(
            existing: PaginatedConnection | undefined,
            incoming: PaginatedConnection,
            { args }: { args?: PaginationArgs }
          ): PaginatedConnection {
            return mergePaginatedConnection(existing, incoming, args);
          },
        },
      },
    },
    Project: {
      keyFields: ['id'],
    },
    Sprint: {
      keyFields: ['id'],
    },
    Story: {
      keyFields: ['id'],
      fields: {
        comments: {
          merge(_existing: unknown, incoming: Record<string, unknown>) {
            return incoming;
          },
        },
      },
    },
    Incident: {
      keyFields: ['id'],
      fields: {
        timeline: {
          merge(_existing: unknown, incoming: Record<string, unknown>) {
            return incoming;
          },
        },
      },
    },
    BootstrapSession: {
      keyFields: ['id'],
    },
    Dashboard: {
      keyFields: ['id'],
    },
    Vulnerability: {
      keyFields: ['id'],
    },
    Secret: {
      keyFields: ['id'],
    },
    TeamMember: {
      keyFields: ['id'],
    },
  },
});

// =============================================================================
// CLIENT CREATION
// =============================================================================

export const createGraphQLClient = (): ApolloClient<NormalizedCacheObject> => {
  return new ApolloClient({
    link: from([
      timingLink,
      requestIdLink,
      errorLink,
      retryLink,
      authLink,
      httpLink,
    ]),
    cache,
    defaultOptions: {
      watchQuery: {
        fetchPolicy: 'cache-and-network',
        errorPolicy: 'all',
        notifyOnNetworkStatusChange: true,
      },
      query: {
        fetchPolicy: 'cache-first',
        errorPolicy: 'all',
      },
      mutate: {
        errorPolicy: 'all',
      },
    },
    connectToDevTools: import.meta.env.DEV,
  });
};

// Singleton client instance
let clientInstance: unknown = null;

export const getGraphQLClient = (): ApolloClient<NormalizedCacheObject> => {
  if (!clientInstance) {
    clientInstance = createGraphQLClient();
  }
  return clientInstance as ApolloClient<NormalizedCacheObject>;
};

// Reset client (useful for logout)
export const resetGraphQLClient = () => {
  const clientHandle = clientInstance as GraphQLClientHandle | null;

  if (clientHandle) {
    void clientHandle.clearStore();
    clientHandle.stop();
    clientInstance = null;
  }
};

// =============================================================================
// CONVENIENCE EXPORTS
// =============================================================================

export { gql } from '@apollo/client';
export type { ApolloClient, NormalizedCacheObject };

// Default export
export default getGraphQLClient;
