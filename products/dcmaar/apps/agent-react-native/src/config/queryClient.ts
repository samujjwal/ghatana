/**
 * React Query Configuration
 *
 * Global configuration and setup for TanStack Query (React Query).
 * Handles caching strategies, error handling, and app-wide settings.
 *
 * @see https://tanstack.com/query/latest
 */

import React from 'react';
import { QueryClient, DefaultOptions } from 'react-query';
import { QueryClientProvider as QCP } from 'react-query';

/**
 * Default query options
 * Applied to all queries globally
 */
const defaultQueryOptions: DefaultOptions = {
  queries: {
    staleTime: 5 * 60 * 1000, // 5 minutes
    cacheTime: 10 * 60 * 1000, // 10 minutes
    retry: 2,
    retryDelay: (attemptIndex: number) => {
      return Math.min(1000 * 2 ** attemptIndex, 30000);
    },
    // Disable automatic refetch on window focus for mobile
    refetchOnWindowFocus: false,
    // Only refetch if data is stale
    refetchOnMount: false,
    // Disable automatic background refetches
    refetchOnReconnect: false,
  },
  mutations: {
    retry: 1,
    retryDelay: (attemptIndex: number) => {
      return Math.min(1000 * 2 ** attemptIndex, 30000);
    },
  },
};

/**
 * Create and configure QueryClient
 */
export const createQueryClient = (): QueryClient => {
  return new QueryClient({
    defaultOptions: defaultQueryOptions,
  });
};

// Create singleton instance
let queryClient: QueryClient | null = null;

/**
 * Get singleton QueryClient instance
 */
export function getQueryClient(): QueryClient {
  if (!queryClient) {
    queryClient = createQueryClient();
  }
  return queryClient;
}

/**
 * React Query Provider Component
 *
 * Wrap your app with this provider to enable React Query
 *
 * @example
 * ```tsx
 * <ReactQueryProvider>
 *   <App />
 * </ReactQueryProvider>
 * ```
 */
export function ReactQueryProvider({ children }: { children: React.ReactNode }): React.ReactElement {
  const client = getQueryClient();

  return React.createElement(QCP, { client }, children);
}

/**
 * Error handler for failed queries/mutations
 *
 * @param error - Error object
 */
export function handleQueryError(error: unknown): void {
  console.error('[React Query Error]', error);

  const axiosError = error as { response?: { status: number } };
  if (axiosError.response?.status === 401) {
    console.warn('Unauthorized - token may have expired');
    // Dispatch logout action or refresh token
  } else if (axiosError.response?.status === 403) {
    console.warn('Forbidden - access denied');
  } else if (axiosError.response?.status && axiosError.response.status >= 500) {
    console.error('Server error - service may be unavailable');
  }
}

/**
 * Utility to manually invalidate queries
 *
 * @param queryKey - Query key to invalidate
 */
export function invalidateQuery(queryKey?: unknown): Promise<void> {
  if (queryKey) {
    // Handle different query key formats
    if (Array.isArray(queryKey)) {
      return getQueryClient().invalidateQueries({ queryKey });
    } else if (typeof queryKey === 'string' || typeof queryKey === 'number' || typeof queryKey === 'boolean') {
      return getQueryClient().invalidateQueries({ queryKey: [queryKey] });
    } else if (queryKey && typeof queryKey === 'object') {
      return getQueryClient().invalidateQueries(queryKey);
    }
  }
  return getQueryClient().invalidateQueries();
}

/**
 * Utility to reset all queries
 */
export function resetQueries(): Promise<void> {
  return getQueryClient().resetQueries();
}

/**
 * Utility to clear all cache
 */
export function clearQueryCache(): void {
  getQueryClient().clear();
}
