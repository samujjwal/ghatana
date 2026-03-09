/**
 * ML Orchestration Hook
 *
 * <p><b>Purpose</b><br>
 * Custom hook providing comprehensive ML feature orchestration, combining store state,
 * API queries, and business logic for model management, training job orchestration,
 * and A/B test coordination.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const {
 *   models,
 *   trainingJobs,
 *   abTests,
 *   isLoading,
 *   selectModel,
 *   compareModels,
 *   cancelTraining,
 * } = useMLOrchestration();
 * ```
 *
 * @doc.type hook
 * @doc.purpose ML feature orchestration and state management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useCallback, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import * as mlApi from '@/services/api/mlApi';
import {
    mlStateAtom,
    selectModelAtom,
    addToComparisonAtom,
    removeFromComparisonAtom,
    showMLNotificationAtom,
} from '../stores/ml.store';
import type { Model, TrainingJob, ABTest } from '@/types/ml-monitoring';

/**
 * ML orchestration hook interface
 */
export interface UseMLOrchestrationReturn {
    models: Model[] | undefined;
    trainingJobs: TrainingJob[] | undefined;
    abTests: ABTest[] | undefined;
    isLoading: boolean;
    selectedModelId: string | null;
    comparisonModels: Model[];
    selectModel: (modelId: string) => void;
    addToComparison: (modelId: string) => void;
    removeFromComparison: (modelId: string) => void;
    cancelTraining: (jobId: string) => Promise<void>;
    stopTest: (testId: string) => Promise<void>;
}

/**
 * Custom hook for ML feature orchestration.
 *
 * @param tenantId - Tenant identifier
 * @returns ML orchestration state and methods
 */
export function useMLOrchestration(tenantId: string): UseMLOrchestrationReturn {
    const [mlState] = useAtom(mlStateAtom);
    const [, selectModel] = useAtom(selectModelAtom);
    const [, addToComparison] = useAtom(addToComparisonAtom);
    const [, removeFromComparison] = useAtom(removeFromComparisonAtom);
    const [, showNotification] = useAtom(showMLNotificationAtom);
    const queryClient = useQueryClient();

    // Fetch models
    const { data: models, isLoading: modelsLoading } = useQuery<Model[]>({
        queryKey: ['models', tenantId],
        queryFn: async () => (await mlApi.getModels(tenantId)) as Model[],
        staleTime: 30000,
    });

    // Fetch training jobs
    const { data: trainingJobs, isLoading: trainingLoading } = useQuery({
        queryKey: ['training-jobs', tenantId],
        queryFn: () => mlApi.getTrainingJobs(tenantId),
        staleTime: 15000,
    });

    // Fetch A/B tests
    const { data: abTests, isLoading: abTestsLoading } = useQuery({
        queryKey: ['ab-tests', tenantId],
        queryFn: () => mlApi.getABTests(tenantId),
        staleTime: 20000,
    });

    // Cancel training mutation
    const cancelTrainingMutation = useMutation({
        mutationFn: (jobId: string) => mlApi.cancelTrainingJob(jobId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['training-jobs', tenantId] });
            showNotification({
                type: 'success',
                message: 'Training job cancelled successfully',
            });
        },
        onError: () => {
            showNotification({
                type: 'error',
                message: 'Failed to cancel training job',
            });
        },
    });

    // Stop A/B test mutation
    const stopTestMutation = useMutation({
        mutationFn: (testId: string) => mlApi.stopABTest(testId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['ab-tests', tenantId] });
            showNotification({
                type: 'success',
                message: 'A/B test stopped successfully',
            });
        },
        onError: () => {
            showNotification({
                type: 'error',
                message: 'Failed to stop A/B test',
            });
        },
    });

    // Get comparison models
    const comparisonModels = useMemo(() => {
        if (!models) return [];
        return models.filter((m) => mlState.compareModelIds.includes(m.id));
    }, [models, mlState.compareModelIds]);

    // Memoized handlers
    const handleSelectModel = useCallback(
        (modelId: string) => {
            selectModel(modelId);
        },
        [selectModel]
    );

    const handleAddToComparison = useCallback(
        (modelId: string) => {
            addToComparison(modelId);
        },
        [addToComparison]
    );

    const handleRemoveFromComparison = useCallback(
        (modelId: string) => {
            removeFromComparison(modelId);
        },
        [removeFromComparison]
    );

    const handleCancelTraining = useCallback(
        (jobId: string) => cancelTrainingMutation.mutateAsync(jobId),
        [cancelTrainingMutation]
    );

    const handleStopTest = useCallback(
        (testId: string) => stopTestMutation.mutateAsync(testId),
        [stopTestMutation]
    );

    return {
        models,
        trainingJobs,
        abTests,
        isLoading: modelsLoading || trainingLoading || abTestsLoading,
        selectedModelId: mlState.selectedModelId,
        comparisonModels,
        selectModel: handleSelectModel,
        addToComparison: handleAddToComparison,
        removeFromComparison: handleRemoveFromComparison,
        cancelTraining: handleCancelTraining,
        stopTest: handleStopTest,
    };
}

export default useMLOrchestration;
