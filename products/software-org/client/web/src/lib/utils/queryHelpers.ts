/**
 * React Query Integration Utilities
 *
 * <p><b>Purpose</b><br>
 * Utility functions for React Query integration, query management, mutation handling,
 * cache invalidation, and optimistic updates.
 *
 * <p><b>Functions</b><br>
 * - createQueryOptions: Create standardized query options
 * - createMutationOptions: Create standardized mutation options
 * - invalidateQueries: Invalidate related queries
 * - prefetchQuery: Prefetch query data
 * - optimisticUpdate: Apply optimistic updates
 * - handleMutationError: Error handling for mutations
 *
 * @doc.type utility
 * @doc.purpose React Query integration utilities
 * @doc.layer product
 * @doc.pattern Utility Module
 */

import {
    UseQueryOptions,
    UseMutationOptions,
    QueryClient,
} from '@tanstack/react-query';
import { handleApiError, retryRequest, RetryConfig } from './apiService';

/**
 * Query options for common patterns
 */
export interface QueryOptionsConfig {
    staleTime?: number;
    cacheTime?: number;
    refetchInterval?: number;
    enabled?: boolean;
    retryConfig?: RetryConfig;
    onError?: (error: any) => void;
    onSuccess?: (data: any) => void;
}

/**
 * Mutation options for common patterns
 */
export interface MutationOptionsConfig {
    onError?: (error: any) => void;
    onSuccess?: (data: any) => void;
    onSettled?: (data: any, error: any) => void;
    retryConfig?: RetryConfig;
}

/**
 * Create standardized query options
 *
 * @param fn - Query function
 * @param config - Configuration options
 * @returns Query options
 */
export function createQueryOptions<T>(
    fn: () => Promise<T>,
    config: QueryOptionsConfig = {}
): UseQueryOptions<T> {
    const {
        staleTime = 30000,
        cacheTime = 5 * 60 * 1000,
        refetchInterval,
        enabled = true,
        retryConfig,
        onError,
        onSuccess,
    } = config;

    return {
        queryFn: retryConfig ? () => retryRequest(fn, retryConfig) : fn,
        staleTime,
        gcTime: cacheTime,
        refetchInterval,
        enabled,
        retry: retryConfig?.maxRetries ?? 3,
        onError: onError ? (error: unknown) => onError(handleApiError(error)) : undefined,
        onSuccess,
    } as any;
}

/**
 * Create standardized mutation options
 *
 * @param fn - Mutation function
 * @param config - Configuration options
 * @returns Mutation options
 */
export function createMutationOptions<T, V>(
    fn: (variables: V) => Promise<T>,
    config: MutationOptionsConfig = {}
): UseMutationOptions<T, any, V> {
    const {
        onError,
        onSuccess,
        onSettled,
        retryConfig,
    } = config;

    return {
        mutationFn: retryConfig
            ? (variables: V) => retryRequest(() => fn(variables), retryConfig)
            : fn,
        onError: onError
            ? (error: unknown) => onError(handleApiError(error))
            : undefined,
        onSuccess,
        onSettled,
        retry: retryConfig?.maxRetries ?? 1,
    } as any;
}

/**
 * Invalidate related queries for data synchronization
 *
 * @param queryClient - React Query client
 * @param keys - Query keys to invalidate
 */
export async function invalidateQueries(
    queryClient: QueryClient,
    keys: (string | string[])[]
): Promise<void> {
    const promises = keys.map((key) => {
        const normalizedKey = Array.isArray(key) ? key : [key];
        return queryClient.invalidateQueries({
            queryKey: normalizedKey,
        });
    });
    await Promise.all(promises);
}

/**
 * Prefetch query data for better performance
 *
 * @param queryClient - React Query client
 * @param key - Query key
 * @param fn - Query function
 * @param options - Query options
 */
export async function prefetchQuery<T>(
    queryClient: QueryClient,
    key: string | string[],
    fn: () => Promise<T>,
    options?: Omit<UseQueryOptions<T>, 'queryKey' | 'queryFn'>
): Promise<void> {
    const normalizedKey = Array.isArray(key) ? key : [key];
    await queryClient.prefetchQuery({
        queryKey: normalizedKey,
        queryFn: fn,
        ...options,
    } as any);
}

/**
 * Apply optimistic update to query cache
 *
 * @param queryClient - React Query client
 * @param key - Query key
 * @param updateFn - Function to update data
 * @returns Previous data for rollback
 */
export function optimisticUpdate<T>(
    queryClient: QueryClient,
    key: string | string[],
    updateFn: (previous: T | undefined) => T
): T | undefined {
    const normalizedKey = Array.isArray(key) ? key : [key];
    const previousData = queryClient.getQueryData<T>(normalizedKey);
    const optimisticData = updateFn(previousData);

    queryClient.setQueryData(normalizedKey, optimisticData);

    return previousData;
}

/**
 * Rollback optimistic update
 *
 * @param queryClient - React Query client
 * @param key - Query key
 * @param previousData - Previous data to restore
 */
export function rollbackOptimisticUpdate<T>(
    queryClient: QueryClient,
    key: string | string[],
    previousData: T | undefined
): void {
    const normalizedKey = Array.isArray(key) ? key : [key];
    if (previousData !== undefined) {
        queryClient.setQueryData(normalizedKey, previousData);
    } else {
        queryClient.removeQueries({
            queryKey: normalizedKey,
        });
    }
}

/**
 * Handle mutation error with standard processing
 *
 * @param error - Error object
 * @param defaultMessage - Default error message
 * @returns Processed error
 */
export function handleMutationError(
    error: any,
    defaultMessage = 'An error occurred'
): {
    message: string;
    status?: number;
    code?: string;
    details?: Record<string, unknown>;
} {
    return handleApiError(error, defaultMessage);
}

/**
 * Build invalidation strategy for related queries
 *
 * @returns Strategy object with query keys
 */
export function getInvalidationStrategy() {
    return {
        all: ['models', 'workflows', 'monitoring', 'alerts', 'anomalies'],
        models: [
            ['models'],
            ['training-jobs'],
            ['ab-tests'],
            ['feature-importance'],
        ],
        workflows: [
            ['workflows'],
            ['workflow-executions'],
            ['workflow-triggers'],
            ['workflow-stats'],
        ],
        monitoring: [
            ['system-health'],
            ['alerts'],
            ['anomalies'],
            ['realtime-metrics'],
        ],
    };
}

/**
 * Create query cache key generator
 *
 * @param namespace - Query namespace
 * @returns Key generator function
 */
export function createQueryKeyFactory(namespace: string) {
    return {
        all: [namespace] as const,
        lists: () => [namespace, 'list'] as const,
        list: (filters?: Record<string, any>) =>
            [namespace, 'list', filters] as const,
        details: () => [namespace, 'detail'] as const,
        detail: (id: string | number) =>
            [namespace, 'detail', id] as const,
        stats: () => [namespace, 'stats'] as const,
        stat: (id: string | number) =>
            [namespace, 'stats', id] as const,
    };
}

/**
 * Setup common query client configuration
 *
 * @param queryClient - React Query client
 */
export function setupQueryClientDefaults(
    queryClient: QueryClient
): void {
    queryClient.setDefaultOptions({
        queries: {
            staleTime: 30000,
            gcTime: 5 * 60 * 1000,
            retry: 3,
            refetchOnWindowFocus: true,
            refetchOnReconnect: true,
        },
        mutations: {
            retry: 1,
        },
    });
}

export default {
    createQueryOptions,
    createMutationOptions,
    invalidateQueries,
    prefetchQuery,
    optimisticUpdate,
    rollbackOptimisticUpdate,
    handleMutationError,
    getInvalidationStrategy,
    createQueryKeyFactory,
    setupQueryClientDefaults,
};
