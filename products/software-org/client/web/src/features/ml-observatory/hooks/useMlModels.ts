import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as mlApi from '@/services/api/mlApi';
import { useCallback } from 'react';

interface ModelData {
    id: string;
    name: string;
    version: string;
    status: string;
    accuracy?: number;
    precision?: number;
    recall?: number;
    f1Score?: number;
    deployedAt?: string;
    metrics?: Record<string, unknown>;
}

/**
 * Custom hook for fetching and managing ML models.
 *
 * Provides:
 * - Cached model data with React Query
 * - Individual model metrics
 * - Model comparison data
 * - Manual refetch capability
 * - Automatic stale time management (5 minutes)
 *
 * @param options - Query options
 * @returns Object with models query state and utilities
 *
 * @example
 * const { models, isLoading, error, refetch } = useMlModels();
 * return (
 *   <div>
 *     {models?.map(m => <ModelCard key={m.id} model={m} />)}
 *   </div>
 * );
 */
export function useMlModels(options = {}) {
    const queryClient = useQueryClient();
    const tenantId = (typeof window !== 'undefined' && localStorage.getItem('tenantId')) || 'default';
    const defaultTimeRange = {
        start: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000),
        end: new Date(),
    };

    // Query for all models
    const modelsQuery = useQuery({
        queryKey: ['ml', 'models'],
        queryFn: async () => {
            const models = await mlApi.getModels(tenantId);
            // Fetch metrics for each model in parallel
            const modelsWithMetrics = await Promise.all(
                models.map(async (model: ModelData) => {
                    try {
                        const metrics = await mlApi.getModelMetrics(model.id, defaultTimeRange);
                        return { ...model, metrics };
                    } catch (_error) {
                        // Return model without metrics if fetch fails
                        return model;
                    }
                })
            );
            return modelsWithMetrics;
        },
        staleTime: 5 * 60 * 1000, // 5 minutes
        gcTime: 10 * 60 * 1000, // 10 minutes
        ...options,
    });

    /**
     * Fetch metrics for a specific model
     *
     * @param modelId - Model ID to fetch metrics for
     * @returns Promise with model metrics
     */
    const getModelMetrics = useCallback(
        async (modelId: string) => {
            try {
                const metrics = await mlApi.getModelMetrics(modelId, defaultTimeRange);
                // Update model in cache with new metrics
                queryClient.setQueryData(['ml', 'models'], (oldData: ModelData[] | undefined) =>
                    oldData?.map((m) =>
                        m.id === modelId ? { ...m, metrics } : m
                    )
                );
                return metrics;
            } catch (error) {
                console.error(`Failed to fetch metrics for model ${modelId}:`, error);
                throw error;
            }
        },
        [queryClient, defaultTimeRange]
    );

    /**
     * Fetch comparison data for multiple models
     *
     * @param modelIds - Array of model IDs to compare
     * @returns Promise with comparison data
     */
    const compareModels = useCallback(
        async (modelIds: string[]) => {
            try {
                const comparison = await mlApi.compareModels(modelIds);
                return comparison;
            } catch (error) {
                console.error(`Failed to compare models:`, error);
                throw error;
            }
        },
        []
    );

    /**
     * Get single model by ID
     *
     * @param modelId - Model ID to retrieve
     * @returns Model from cache or undefined
     */
    const getModelById = useCallback(
        (modelId: string) => {
            return modelsQuery.data?.find((m: ModelData) => m.id === modelId);
        },
        [modelsQuery.data]
    );

    /**
     * Refetch models with optional force refresh
     *
     * @param force - Force immediate refetch (bypass stale time)
     */
    const refetch = useCallback(
        (force = false) => {
            if (force) {
                queryClient.removeQueries({ queryKey: ['ml', 'models'] });
            }
            return modelsQuery.refetch();
        },
        [queryClient, modelsQuery]
    );

    return {
        // Query state
        models: modelsQuery.data,
        isLoading: modelsQuery.isLoading,
        isFetching: modelsQuery.isFetching,
        error: modelsQuery.error as Error | null,
        isError: modelsQuery.isError,

        // Utilities
        getModelMetrics,
        compareModels,
        getModelById,
        refetch,
    };
}
