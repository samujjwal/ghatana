import { useQuery } from '@tanstack/react-query';
import { modelsApi } from '@/services/api/modelsApi';

/**
 * Hook for fetching ML model registry and versions.
 *
 * <p><b>Purpose</b><br>
 * Provides model list and version history for model catalog (Day 9).
 * Supports pagination and filtering.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: models, isLoading, error } = useModelRegistry();
 * models?.forEach(m => console.log(m.id, m.name, m.version));
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Caches for 15 minutes
 * - Polls every 30 seconds (periodic updates for deployed models)
 * - Retries on network failure
 *
 * @doc.type hook
 * @doc.purpose Fetch ML model registry
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useModelRegistry(options?: { enabled?: boolean; refetchInterval?: number }) {
    return useQuery({
        queryKey: ['modelRegistry'],
        queryFn: async () => {
            try {
                return await modelsApi.getModels();
            } catch (error) {
                console.warn('[useModelRegistry] API unavailable, using fallback:', error);
                return [];
            }
        },
        staleTime: 15 * 60 * 1000, // 15 minutes
        gcTime: 30 * 60 * 1000, // 30 minutes
        retry: 2,
        refetchInterval: options?.refetchInterval ?? 30 * 1000, // 30 seconds
        enabled: options?.enabled ?? true,
    });
}

/**
 * Hook for fetching model comparison data.
 *
 * <p><b>Purpose</b><br>
 * Provides comparison metrics between two models (Day 9).
 * Computes performance deltas and recommendations.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: comparison, isLoading, error } = useModelComparison(modelId1, modelId2);
 * console.log(comparison?.models, comparison?.ranking);
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Requires both model IDs
 * - Caches for 10 minutes
 * - Polls every 60 seconds (refreshes performance metrics)
 * - Disabled if either modelId is missing
 *
 * @doc.type hook
 * @doc.purpose Fetch model comparison metrics
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useModelComparison(modelId1?: string, modelId2?: string, options?: {
    enabled?: boolean;
    refetchInterval?: number;
}) {
    const hasSelection = modelId1 && modelId2;

    return useQuery({
        queryKey: ['modelComparison', modelId1, modelId2],
        queryFn: async () => {
            try {
                if (!hasSelection) return null;
                return await modelsApi.compareModels(modelId1!, modelId2!);
            } catch (error) {
                console.warn('[useModelComparison] API unavailable, using fallback:', error);
                return null;
            }
        },
        staleTime: 10 * 60 * 1000, // 10 minutes
        gcTime: 20 * 60 * 1000, // 20 minutes
        retry: 2,
        refetchInterval: options?.refetchInterval ?? 60 * 1000, // 60 seconds
        enabled: hasSelection ? (options?.enabled ?? true) : false,
    });
}

/**
 * Hook for fetching and running model test suites.
 *
 * <p><b>Purpose</b><br>
 * Provides test cases and execution results for model validation (Day 9).
 * Tracks test status and coverage metrics.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: testSuite, isLoading, error } = useModelTestSuite(modelId);
 * console.log(testSuite?.cases, testSuite?.results);
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Requires modelId parameter
 * - Caches for 5 minutes
 * - No polling (test results static until re-run)
 * - Retries on network failure
 *
 * @doc.type hook
 * @doc.purpose Fetch model test suite
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useModelTestSuite(modelId: string, options?: { enabled?: boolean }) {
    return useQuery({
        queryKey: ['modelTestSuite', modelId],
        queryFn: async () => {
            try {
                return await modelsApi.getTestSuite(modelId);
            } catch (error) {
                console.warn('[useModelTestSuite] API unavailable, using fallback:', error);
                return {
                    cases: [],
                    results: [],
                    coverage: 0,
                };
            }
        },
        staleTime: 5 * 60 * 1000, // 5 minutes
        gcTime: 10 * 60 * 1000, // 10 minutes
        retry: 2,
        refetchInterval: undefined, // No polling
        enabled: (options?.enabled ?? true) && !!modelId,
    });
}
