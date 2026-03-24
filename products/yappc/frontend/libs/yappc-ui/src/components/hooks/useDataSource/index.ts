import { useState, useEffect, useCallback, useRef } from 'react';

import { SimpleCache, DataSourceUtils } from './utils';

import type {
  DataSourceConfig,
  DataSourceResult,
  DataSourceType,
} from './types';

// Global shared cache instance
const CACHE = new SimpleCache();

/**
 * React hook for fetching and caching data from multiple sources.
 *
 * Supports REST, GraphQL, and static data sources with automatic caching,
 * response transformation, and lifecycle callbacks. Provides methods to
 * refetch, mutate, and clear cached data.
 *
 * Features:
 * - Automatic caching with TTL (time-to-live)
 * - Response transformation
 * - Optimistic updates via mutation
 * - Error handling with callbacks
 * - Background refetching with validation state
 * - Memory leak prevention with mounted check
 *
 * @template TData - Type of data returned by the source
 * @param config - Data source configuration (REST/GraphQL/static)
 * @returns Object with data, loading/error states, and action methods
 *
 * @example
 * ```tsx
 * // REST endpoint
 * const { data: users, isLoading, error, refetch } = useDataSource({
 *   type: 'rest',
 *   url: 'https://api.example.com/users',
 *   cache: true,
 *   cacheTTL: 300000
 * });
 *
 * // GraphQL query
 * const { data, isValidating, mutate } = useDataSource({
 *   type: 'graphql',
 *   url: 'https://api.example.com/graphql',
 *   query: '{ users { id name } }',
 *   onSuccess: (data) => console.log('Loaded:', data)
 * });
 *
 * // Static data
 * const { data } = useDataSource({
 *   type: 'static',
 *   data: { id: 1, name: 'Alice' }
 * });
 * ```
 */
// eslint-disable-next-line max-lines-per-function
export function useDataSource<TData = unknown>(
  config: DataSourceConfig<TData>
): DataSourceResult<TData> {
  const {
    type,
    cache = true,
    cacheKey,
    cacheTTL = 300000,
    onError,
    onSuccess,
  } = config;

  const [data, setData] = useState<TData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isValidating, setIsValidating] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const mounted = useRef(true);

  // Generate cache key based on data source type
  const cacheKeyValue = cacheKey ?? generateCacheKey(type, config);

  /**
   * Get the appropriate fetch promise based on data source type.
   */
  const getFetchPromise = useCallback(
    (cfg: DataSourceConfig<TData>) => {
      if (type === 'static') {
        return Promise.resolve(cfg.data as TData);
      }
      if (type === 'rest') {
        return DataSourceUtils.fetchREST<TData>(cfg);
      }
      return DataSourceUtils.fetchGraphQL<TData>(cfg);
    },
    [type]
  );

  /**
   * Load data from source, handling cache and error cases.
   */
  const load = useCallback(
    async (refetch = false) => {
      try {
        if (!refetch) {
          setIsLoading(true);
        } else {
          setIsValidating(true);
        }
        setError(null);

        // Check cache if not refetching
        if (cache && !refetch) {
          const cached = CACHE.get<TData>(cacheKeyValue);
          if (cached !== null) {
            setData(cached);
            setIsLoading(false);
            onSuccess?.(cached);
            return;
          }
        }

        // Fetch fresh data
        const result = await getFetchPromise(config);

        // Don't update if component unmounted
        if (!mounted.current) return;

        setData(result);
        setIsLoading(false);
        setIsValidating(false);

        // Cache result if caching enabled
        if (cache) {
          CACHE.set(cacheKeyValue, result, cacheTTL);
        }

        onSuccess?.(result);
      } catch (err) {
        const error = err instanceof Error ? err : new Error(String(err));

        // Don't update if component unmounted
        if (!mounted.current) return;

        setError(error);
        setIsLoading(false);
        setIsValidating(false);
        onError?.(error);
      }
    },
    [
      cacheKeyValue,
      cache,
      cacheTTL,
      config,
      onError,
      onSuccess,
      getFetchPromise,
    ]
  );

  // Load data on mount
  useEffect(() => {
    load(false);
    return () => {
      mounted.current = false;
    };
  }, [load]);

  /**
   * Refetch data, bypassing cache.
   */
  const refetch = useCallback(async () => load(true), [load]);

  /**
   * Update data directly (optimistic updates).
   */
  const mutate = useCallback(
    (newData: TData | ((prev: TData | null) => TData)) => {
      let updated: TData;
      if (typeof newData === 'function') {
        const fn = newData as (prev: TData | null) => TData;
        updated = fn(data);
      } else {
        updated = newData;
      }
      setData(updated as TData);

      // Update cache if enabled
      if (cache) {
        CACHE.set(cacheKeyValue, updated as TData, cacheTTL);
      }
    },
    [data, cache, cacheKeyValue, cacheTTL]
  );

  /**
   * Clear cached data.
   */
  const clearCache = useCallback(() => {
    CACHE.delete(cacheKeyValue);
  }, [cacheKeyValue]);

  return {
    data,
    isLoading,
    error,
    isValidating,
    refetch,
    mutate,
    clearCache,
  };
}

/**
 * Generate a cache key based on data source type and configuration.
 *
 * @param type - Type of data source ('rest' | 'graphql' | 'static')
 * @param config - Data source configuration
 * @returns Generated cache key string
 */
function generateCacheKey<TData>(
  type: DataSourceType,
  config: DataSourceConfig<TData>
): string {
  if (type === 'rest') {
    return `${config.method ?? 'GET'}:${config.url}`;
  }
  if (type === 'graphql') {
    return `graphql:${config.query}`;
  }
  // For static, generate a random key so each instance has separate cache
  return `static:${Math.random().toString(36).slice(2, 8)}`;
}

export default useDataSource;
