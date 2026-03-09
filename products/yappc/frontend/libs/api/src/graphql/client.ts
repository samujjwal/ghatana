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
  InMemoryCache,
  HttpLink,
  from,
  ApolloLink,
  NormalizedCacheObject,
  split,
  Observable,
} from '@apollo/client';
import { onError } from '@apollo/client/link/error';
import { RetryLink } from '@apollo/client/link/retry';
import { GraphQLWsLink } from '@apollo/client/link/subscriptions';
import { getMainDefinition } from '@apollo/client/utilities';
import { createClient } from 'graphql-ws';

// =============================================================================
// CONFIGURATION
// =============================================================================

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:7002';
const GRAPHQL_ENDPOINT = `${API_BASE_URL}/graphql`;
const WS_ENDPOINT = API_BASE_URL.replace('http', 'ws') + '/graphql';

// =============================================================================
// TOKEN MANAGEMENT
// =============================================================================

let accessToken: string | null = null;
let refreshToken: string | null = null;
let tokenRefreshPromise: Promise<string | null> | null = null;

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

    const data = await response.json();
    setTokens(data.accessToken, data.refreshToken);
    return data.accessToken;
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

  return forward(operation).map((response) => {
    const duration = performance.now() - startTime;
    const operationName = operation.operationName;

    // Log slow queries in development
    if (import.meta.env.DEV && duration > 1000) {
      console.warn(`[GraphQL] Slow query: ${operationName} took ${duration.toFixed(0)}ms`);
    }

    // Report to observability
    if (typeof window !== 'undefined' && (window as unknown).__YAPPC_TELEMETRY__) {
      (window as unknown).__YAPPC_TELEMETRY__.recordMetric('graphql.query.duration', duration, {
        operation: operationName,
      });
    }

    return response;
  });
});

// Authentication link
const authLink = new ApolloLink((operation, forward) => {
  const token = getAccessToken();

  if (token) {
    operation.setContext(({ headers = {} }) => ({
      headers: {
        ...headers,
        Authorization: `Bearer ${token}`,
      },
    }));
  }

  return forward(operation);
});

// Error handling link with token refresh
const errorLink = onError(({ graphQLErrors, networkError, operation, forward }) => {
  if (graphQLErrors) {
    for (const error of graphQLErrors) {
      const { message, locations, path, extensions } = error;

      // Log error details
      console.error(
        `[GraphQL Error]: Message: ${message}`,
        {
          locations,
          path,
          extensions,
          operation: operation.operationName,
        }
      );

      // Handle authentication errors
      if (extensions?.code === 'UNAUTHENTICATED' || extensions?.code === 'TOKEN_EXPIRED') {
        // Attempt token refresh
        if (!tokenRefreshPromise) {
          tokenRefreshPromise = refreshAccessToken().finally(() => {
            tokenRefreshPromise = null;
          });
        }

        return new Observable((observer) => {
          tokenRefreshPromise!.then((newToken) => {
            if (newToken) {
              // Retry the request with new token
              operation.setContext(({ headers = {} }) => ({
                headers: {
                  ...headers,
                  Authorization: `Bearer ${newToken}`,
                },
              }));
              forward(operation).subscribe(observer);
            } else {
              observer.error(error);
            }
          });
        });
      }

      // Handle authorization errors
      if (extensions?.code === 'FORBIDDEN') {
        window.dispatchEvent(
          new CustomEvent('auth:forbidden', {
            detail: { operation: operation.operationName, path },
          })
        );
      }

      // Handle rate limiting
      if (extensions?.code === 'RATE_LIMITED') {
        const retryAfter = extensions.retryAfter || 60;
        window.dispatchEvent(
          new CustomEvent('api:rate-limited', {
            detail: { retryAfter },
          })
        );
      }
    }
  }

  if (networkError) {
    console.error(`[Network Error]: ${networkError.message}`);

    // Dispatch network error event for global handling
    window.dispatchEvent(
      new CustomEvent('api:network-error', {
        detail: { error: networkError },
      })
    );
  }
});

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
      
      const statusCode = (error as unknown).statusCode;
      const isNetworkError = !statusCode;
      const isServerError = statusCode >= 500 && statusCode < 600;
      const isTimeout = error.message?.includes('timeout');
      
      return isNetworkError || isServerError || isTimeout;
    },
  },
});

// Request ID link (for tracing)
const requestIdLink = new ApolloLink((operation, forward) => {
  const requestId = crypto.randomUUID();
  
  operation.setContext(({ headers = {} }) => ({
    headers: {
      ...headers,
      'X-Request-ID': requestId,
    },
  }));

  return forward(operation);
});

// HTTP link
const httpLink = new HttpLink({
  uri: GRAPHQL_ENDPOINT,
  credentials: 'include',
  headers: {
    'Content-Type': 'application/json',
  },
});

// WebSocket link for subscriptions
const wsLink = new GraphQLWsLink(
  createClient({
    url: WS_ENDPOINT,
    connectionParams: () => ({
      authorization: getAccessToken() ? `Bearer ${getAccessToken()}` : undefined,
    }),
    retryAttempts: 5,
    shouldRetry: () => true,
    on: {
      connected: () => {
        window.dispatchEvent(new CustomEvent('ws:connected'));
      },
      closed: () => {
        window.dispatchEvent(new CustomEvent('ws:disconnected'));
      },
      error: (error) => {
        console.error('[WebSocket Error]:', error);
        window.dispatchEvent(new CustomEvent('ws:error', { detail: error }));
      },
    },
  })
);

// Split link for queries/mutations vs subscriptions
const splitLink = split(
  ({ query }) => {
    const definition = getMainDefinition(query);
    return (
      definition.kind === 'OperationDefinition' &&
      definition.operation === 'subscription'
    );
  },
  wsLink,
  httpLink
);

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
          merge(existing = { edges: [], pageInfo: null }, incoming, { args }) {
            if (args?.after === null || args?.after === undefined) {
              return incoming;
            }
            return {
              ...incoming,
              edges: [...existing.edges, ...incoming.edges],
            };
          },
        },
        // Pagination for incidents
        incidents: {
          keyArgs: ['projectId', 'filter'],
          merge(existing = { edges: [], pageInfo: null }, incoming, { args }) {
            if (!args?.after) {
              return incoming;
            }
            return {
              ...incoming,
              edges: [...existing.edges, ...incoming.edges],
            };
          },
        },
        // Pagination for vulnerabilities
        vulnerabilities: {
          keyArgs: ['projectId', 'filter'],
          merge(existing = { edges: [], pageInfo: null }, incoming, { args }) {
            if (!args?.after) {
              return incoming;
            }
            return {
              ...incoming,
              edges: [...existing.edges, ...incoming.edges],
            };
          },
        },
        // Pagination for audit logs
        auditLogs: {
          keyArgs: ['filter'],
          merge(existing = { edges: [], pageInfo: null }, incoming, { args }) {
            if (!args?.after) {
              return incoming;
            }
            return {
              ...incoming,
              edges: [...existing.edges, ...incoming.edges],
            };
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
          merge(_existing, incoming) {
            return incoming;
          },
        },
      },
    },
    Incident: {
      keyFields: ['id'],
      fields: {
        timeline: {
          merge(_existing, incoming) {
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
      splitLink,
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
let clientInstance: ApolloClient<NormalizedCacheObject> | null = null;

export const getGraphQLClient = (): ApolloClient<NormalizedCacheObject> => {
  if (!clientInstance) {
    clientInstance = createGraphQLClient();
  }
  return clientInstance;
};

// Reset client (useful for logout)
export const resetGraphQLClient = () => {
  if (clientInstance) {
    clientInstance.clearStore();
    clientInstance.stop();
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
