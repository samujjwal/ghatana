import { useCallback, useRef, useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';

/**
 * P1-032: Cache Invalidation Hook
 *
 * Provides intelligent cache invalidation for React Query:
 * - Automatic invalidation on mutations
 * - Optimistic updates with rollback
 * - Pattern-based cache key matching
 * - Stale-while-revalidate strategy
 * - Dependency-based invalidation
 * - Batch invalidation operations
 */

export type CacheKeyPattern = string | string[] | RegExp;

export interface InvalidationRule {
  mutationType: string;
  affectedQueries: CacheKeyPattern[];
  optimisticUpdate?: (oldData: unknown, newData: unknown) => unknown;
  shouldRefetch?: boolean;
  delayMs?: number;
}

export interface CacheDependency {
  key: string;
  dependencies: string[];
}

/**
 * P1-032: Main cache invalidation hook
 */
export const useCacheInvalidation = () => {
  const queryClient = useQueryClient();
  const pendingInvalidations = useRef<Set<string>>(new Set());
  const invalidationRules = useRef<InvalidationRule[]>([
    // Default rules
    {
      mutationType: 'CREATE_CAMPAIGN',
      affectedQueries: ['campaigns', 'dashboard-stats'],
      shouldRefetch: true
    },
    {
      mutationType: 'UPDATE_CAMPAIGN',
      affectedQueries: [['campaigns'], /campaigns\/[^/]+/],
      shouldRefetch: true,
      delayMs: 100
    },
    {
      mutationType: 'DELETE_CAMPAIGN',
      affectedQueries: ['campaigns', 'dashboard-stats', 'budgets'],
      optimisticUpdate: (oldData: any, deletedId: string) => {
        if (Array.isArray(oldData)) {
          return oldData.filter((item: any) => item.id !== deletedId);
        }
        return oldData;
      },
      shouldRefetch: true
    },
    {
      mutationType: 'SUBMIT_APPROVAL',
      affectedQueries: ['approvals', 'pending-approvals', ['campaigns']],
      shouldRefetch: true
    },
    {
      mutationType: 'APPROVE_REQUEST',
      affectedQueries: ['approvals', 'pending-approvals', ['campaigns'], /campaigns\/[^/]+/],
      shouldRefetch: true,
      delayMs: 200
    },
    {
      mutationType: 'UPDATE_BUDGET',
      affectedQueries: ['budgets', ['campaigns'], /campaigns\/[^/]+/],
      shouldRefetch: true
    },
    {
      mutationType: 'GENERATE_STRATEGY',
      affectedQueries: ['strategies', ['campaigns'], /campaigns\/[^/]+\/strategy/],
      shouldRefetch: true,
      delayMs: 500 // Wait for async generation
    }
  ]);

  /**
   * P1-032: Invalidate specific query keys
   */
  const invalidateQueries = useCallback(async (
    keys: CacheKeyPattern[],
    options?: { 
      exact?: boolean;
      refetchType?: 'active' | 'inactive' | 'all';
      delayMs?: number;
    }
  ) => {
    const delay = options?.delayMs || 0;
    
    if (delay > 0) {
      await new Promise(resolve => setTimeout(resolve, delay));
    }

    for (const key of keys) {
      if (typeof key === 'string') {
        // Exact match
        await queryClient.invalidateQueries({
          queryKey: [key],
          exact: options?.exact ?? false,
          refetchType: options?.refetchType ?? 'active'
        });
      } else if (Array.isArray(key)) {
        // Array query key
        await queryClient.invalidateQueries({
          queryKey: key,
          exact: options?.exact ?? false,
          refetchType: options?.refetchType ?? 'active'
        });
      } else if (key instanceof RegExp) {
        // Pattern match - find and invalidate matching keys
        const allQueries = queryClient.getQueryCache().getAll();
        const matchingQueries = allQueries.filter(query => {
          const queryKeyString = JSON.stringify(query.queryKey);
          return key.test(queryKeyString);
        });

        for (const query of matchingQueries) {
          await queryClient.invalidateQueries({
            queryKey: query.queryKey,
            exact: true,
            refetchType: options?.refetchType ?? 'active'
          });
        }
      }
    }
  }, [queryClient]);

  /**
   * P1-032: Invalidate based on mutation type
   */
  const invalidateForMutation = useCallback(async (
    mutationType: string,
    mutationData?: unknown
  ) => {
    const rule = invalidationRules.current.find(r => r.mutationType === mutationType);
    
    if (!rule) {
      console.warn(`[Cache] No invalidation rule found for mutation: ${mutationType}`);
      return;
    }

    // Apply optimistic update if defined
    if (rule.optimisticUpdate && mutationData) {
      for (const pattern of rule.affectedQueries) {
        const matchingQueries = findQueriesByPattern(queryClient, pattern);
        
        for (const query of matchingQueries) {
          const currentData = queryClient.getQueryData(query.queryKey);
          if (currentData) {
            const optimisticData = rule.optimisticUpdate(currentData, mutationData);
            queryClient.setQueryData(query.queryKey, optimisticData);
          }
        }
      }
    }

    // Invalidate queries
    await invalidateQueries(rule.affectedQueries, {
      delayMs: rule.delayMs,
      refetchType: rule.shouldRefetch ? 'active' : 'inactive'
    });
  }, [invalidateQueries, queryClient]);

  /**
   * P1-032: Set query data with automatic dependency tracking
   */
  const setQueryData = useCallback(<T,>(
    queryKey: string | string[],
    updater: T | ((oldData: T | undefined) => T),
    dependencies?: string[]
  ) => {
    const key = Array.isArray(queryKey) ? queryKey : [queryKey];
    
    // Set the data
    queryClient.setQueryData(key, updater);

    // Track dependencies for later invalidation
    if (dependencies) {
      const keyString = JSON.stringify(key);
      const existingDeps = queryClient.getQueryData(['__cache_dependencies__', keyString]) as string[] || [];
      queryClient.setQueryData(
        ['__cache_dependencies__', keyString],
        [...new Set([...existingDeps, ...dependencies])]
      );
    }
  }, [queryClient]);

  /**
   * P1-032: Remove specific queries from cache
   */
  const removeQueries = useCallback((keys: CacheKeyPattern[]) => {
    for (const key of keys) {
      if (typeof key === 'string') {
        queryClient.removeQueries({ queryKey: [key], exact: false });
      } else if (Array.isArray(key)) {
        queryClient.removeQueries({ queryKey: key, exact: true });
      } else if (key instanceof RegExp) {
        const allQueries = queryClient.getQueryCache().getAll();
        const matchingQueries = allQueries.filter(query => {
          const queryKeyString = JSON.stringify(query.queryKey);
          return key.test(queryKeyString);
        });

        for (const query of matchingQueries) {
          queryClient.removeQueries({ queryKey: query.queryKey, exact: true });
        }
      }
    }
  }, [queryClient]);

  /**
   * P1-032: Prefetch queries for anticipated navigation
   */
  const prefetchQueries = useCallback(async (keys: Array<string | string[]>) => {
    const prefetchPromises = keys.map(async key => {
      const queryKey = Array.isArray(key) ? key : [key];
      
      // Check if already in cache and not stale
      const query = queryClient.getQueryCache().find({ queryKey, exact: true });
      if (query && !query.isStaleByTime(Date.now())) {
        return; // Already fresh in cache
      }

      // Trigger prefetch
      await queryClient.prefetchQuery({
        queryKey,
        staleTime: 5 * 60 * 1000 // 5 minutes
      });
    });

    await Promise.all(prefetchPromises);
  }, [queryClient]);

  /**
   * P1-032: Clear all cache
   */
  const clearAllCache = useCallback(() => {
    queryClient.clear();
    pendingInvalidations.current.clear();
  }, [queryClient]);

  /**
   * P1-032: Register custom invalidation rule
   */
  const registerInvalidationRule = useCallback((rule: InvalidationRule) => {
    invalidationRules.current = [
      ...invalidationRules.current.filter(r => r.mutationType !== rule.mutationType),
      rule
    ];
  }, []);

  /**
   * P1-032: Batch multiple invalidations
   */
  const batchInvalidation = useCallback(async (
    operations: Array<{ type: string; data?: unknown }>
  ) => {
    const affectedKeys = new Set<string>();

    // Collect all affected keys
    for (const op of operations) {
      const rule = invalidationRules.current.find(r => r.mutationType === op.type);
      if (rule) {
        rule.affectedQueries.forEach(key => {
          if (typeof key === 'string') {
            affectedKeys.add(JSON.stringify([key]));
          } else if (Array.isArray(key)) {
            affectedKeys.add(JSON.stringify(key));
          }
        });
      }
    }

    // Apply all optimistic updates first
    for (const op of operations) {
      const rule = invalidationRules.current.find(r => r.mutationType === op.type);
      if (rule?.optimisticUpdate && op.data) {
        for (const key of affectedKeys) {
          const queryKey = JSON.parse(key);
          const currentData = queryClient.getQueryData(queryKey);
          if (currentData) {
            const optimisticData = rule.optimisticUpdate(currentData, op.data);
            queryClient.setQueryData(queryKey, optimisticData);
          }
        }
      }
    }

    // Batch invalidate all collected keys
    const keys = Array.from(affectedKeys).map(k => JSON.parse(k));
    for (const key of keys) {
      await queryClient.invalidateQueries({ queryKey: key, exact: true });
    }
  }, [queryClient]);

  /**
   * P1-032: Subscribe to cache changes
   */
  useEffect(() => {
    const unsubscribe = queryClient.getQueryCache().subscribe(event => {
      if (event.type === 'updated' && event.query.state.data !== undefined) {
        // Check for dependent queries to invalidate
        const keyString = JSON.stringify(event.query.queryKey);
        const dependencies = queryClient.getQueryData(['__cache_dependencies__', keyString]) as string[];
        
        if (dependencies) {
          dependencies.forEach(depKey => {
            if (!pendingInvalidations.current.has(depKey)) {
              pendingInvalidations.current.add(depKey);
              
              // Debounce invalidation
              setTimeout(() => {
                if (pendingInvalidations.current.has(depKey)) {
                  queryClient.invalidateQueries({ queryKey: [depKey] });
                  pendingInvalidations.current.delete(depKey);
                }
              }, 100);
            }
          });
        }
      }
    });

    return () => unsubscribe();
  }, [queryClient]);

  return {
    invalidateQueries,
    invalidateForMutation,
    setQueryData,
    removeQueries,
    prefetchQueries,
    clearAllCache,
    registerInvalidationRule,
    batchInvalidation,
    queryClient
  };
};

/**
 * P1-032: Helper to find queries matching a pattern
 */
const findQueriesByPattern = (queryClient: any, pattern: CacheKeyPattern) => {
  const allQueries = queryClient.getQueryCache().getAll();
  
  return allQueries.filter((query: any) => {
    if (typeof pattern === 'string') {
      return JSON.stringify(query.queryKey).includes(pattern);
    } else if (Array.isArray(pattern)) {
      return JSON.stringify(query.queryKey) === JSON.stringify(pattern);
    } else if (pattern instanceof RegExp) {
      return pattern.test(JSON.stringify(query.queryKey));
    }
    return false;
  });
};

/**
 * P1-032: Hook for optimistic updates
 */
export const useOptimisticUpdate = <T,>(
  queryKey: string | string[],
  mutationFn: (variables: T) => Promise<unknown>
) => {
  const { setQueryData, invalidateQueries } = useCacheInvalidation();
  const queryClient = useQueryClient();

  const optimisticMutate = useCallback(async (
    variables: T,
    optimisticUpdater: (currentData: any) => any,
    rollbackOnError: boolean = true
  ) => {
    const key = Array.isArray(queryKey) ? queryKey : [queryKey];
    
    // Snapshot current data for potential rollback
    const previousData = queryClient.getQueryData(key);
    
    // Apply optimistic update
    setQueryData(key, (old: any) => optimisticUpdater(old));

    try {
      const result = await mutationFn(variables);
      
      // Invalidate to get server-confirmed data
      await invalidateQueries([key], { refetchType: 'active' });
      
      return { success: true, data: result };
    } catch (error) {
      // Rollback on error
      if (rollbackOnError && previousData !== undefined) {
        setQueryData(key, previousData);
      }
      
      return { success: false, error };
    }
  }, [queryKey, mutationFn, setQueryData, invalidateQueries, queryClient]);

  return { optimisticMutate };
};

/**
 * P1-032: Hook for stale-while-revalidate pattern
 */
export const useStaleWhileRevalidate = (
  key: string | string[],
  fetchFn: () => Promise<unknown>,
  options?: { staleTime?: number; cacheTime?: number }
) => {
  const queryClient = useQueryClient();
  const { prefetchQueries } = useCacheInvalidation();

  const getData = useCallback(async () => {
    const queryKey = Array.isArray(key) ? key : [key];
    
    // Check cache first
    const cached = queryClient.getQueryData(queryKey);
    const query = queryClient.getQueryCache().find({ queryKey, exact: true });
    const isStale = !query || query.isStaleByTime(Date.now());

    if (cached && !isStale) {
      // Return fresh cached data
      return { data: cached, fromCache: true, stale: false };
    }

    if (cached && isStale) {
      // Return stale data while revalidating
      prefetchQueries([key]).catch(console.error);
      return { data: cached, fromCache: true, stale: true };
    }

    // No cache, must fetch
    const data = await queryClient.fetchQuery({
      queryKey,
      queryFn: fetchFn,
      staleTime: options?.staleTime || 5 * 60 * 1000,
      cacheTime: options?.cacheTime || 10 * 60 * 1000
    });

    return { data, fromCache: false, stale: false };
  }, [key, fetchFn, queryClient, prefetchQueries, options]);

  return { getData };
};

export default useCacheInvalidation;
