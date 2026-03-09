import { QueryClient } from "@tanstack/react-query";

/**
 * Global TanStack Query client configuration.
 *
 * Purpose: Centralized query client for server state management with standardized
 * cache configuration, retry logic, and stale-while-revalidate patterns.
 *
 * Configuration:
 * - staleTime: 5 minutes (data stays "fresh" for 5 min after fetch)
 * - gcTime: 10 minutes (keep data in cache for 10 min, formerly cacheTime)
 * - retry: 1 attempt with exponential backoff (2^attempt, max 30s)
 * - refetchOnWindowFocus: disabled (don't auto-refetch on window focus)
 * - refetchOnMount: disabled (don't auto-refetch on component mount)
 * - refetchOnReconnect: enabled (auto-refetch when network reconnects)
 *
 * @doc.type configuration
 * @doc.purpose TanStack Query client setup
 * @doc.layer product
 * @doc.pattern Configuration
 */
export const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            staleTime: 1000 * 60 * 5, // 5 minutes
            gcTime: 1000 * 60 * 10, // 10 minutes (formerly cacheTime)
            retry: 1,
            retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
            refetchOnWindowFocus: false,
            refetchOnMount: false,
            refetchOnReconnect: true,
        },
        mutations: {
            retry: 1,
            retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
        },
    },
});
