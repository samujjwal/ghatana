/**
 * usePinnedFeatures Hook
 *
 * <p><b>Purpose</b><br>
 * React Query hook for managing user's pinned features with optimistic updates.
 * Syncs with backend API and Jotai atom for global state.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { features, isLoading, pin, unpin } = usePinnedFeatures();
 *
 * return (
 *   <FeatureGrid
 *     features={features}
 *     onPin={(title) => pin(title)}
 *     onUnpin={(title) => unpin(title)}
 *   />
 * );
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose Manage pinned features with optimistic updates
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { pinnedFeaturesAtom, userProfileAtom, userRoleAtom } from '@/state/jotai/atoms';
import type { Feature } from '@/shared/components/FeatureGrid';
import { api } from '@/services/personaApi';

/**
 * Hook return type
 */
export interface UsePinnedFeaturesResult {
    /**
     * Pinned features (from Jotai atom)
     */
    features: Feature[];

    /**
     * Loading state (first fetch)
     */
    isLoading: boolean;

    /**
     * Error if fetch failed
     */
    error: Error | null;

    /**
     * Pin a feature (optimistic update)
     */
    pin: (featureTitle: string) => void;

    /**
     * Unpin a feature (optimistic update)
     */
    unpin: (featureTitle: string) => void;

    /**
     * Update entire pinned features array (for drag-drop reorder)
     */
    updateAll: (features: Feature[]) => void;

    /**
     * True if pin/unpin mutation is in progress
     */
    isMutating: boolean;

    /**
     * Refetch function for manual refresh
     */
    refetch: () => void;
}

/**
 * Manages user's pinned features with optimistic updates.
 *
 * Features:
 * - Optimistic UI updates (instant feedback)
 * - Automatic rollback on API failure
 * - Syncs with Jotai atom for global state
 * - Support for pin, unpin, and reorder operations
 *
 * @returns UsePinnedFeaturesResult - Features, loading state, mutation functions
 */
export function usePinnedFeatures(): UsePinnedFeaturesResult {
    const [features, setFeatures] = useAtom(pinnedFeaturesAtom);
    const [userProfile] = useAtom(userProfileAtom);
    const [userRoleAtomValue] = useAtom(userRoleAtom);
    const role = userProfile?.role || userRoleAtomValue;
    const queryClient = useQueryClient();

    // Fetch pinned features from API
    const {
        data,
        error,
        isLoading,
        refetch,
    } = useQuery({
        queryKey: ['pinnedFeatures', role],
        queryFn: () => api.getPinnedFeatures(),
        staleTime: 5 * 60 * 1000, // 5 minutes
        retry: 3,
    });

    // Sync React Query data with Jotai atom
    useEffect(() => {
        if (data) {
            setFeatures(data);
        }
    }, [data, setFeatures]);

    // Mutation for updating pinned features
    const updateMutation = useMutation({
        mutationFn: (newFeatures: Feature[]) => api.updatePinnedFeatures(newFeatures),
        onMutate: async (newFeatures) => {
            // Cancel outgoing refetches
            await queryClient.cancelQueries({ queryKey: ['pinnedFeatures'] });

            // Snapshot previous value
            const previousFeatures = queryClient.getQueryData<Feature[]>(['pinnedFeatures']);

            // Optimistically update cache
            queryClient.setQueryData(['pinnedFeatures'], newFeatures);

            // Return context for rollback
            return { previousFeatures };
        },
        onError: (_err, _newFeatures, context) => {
            // Rollback on error
            if (context?.previousFeatures) {
                queryClient.setQueryData(['pinnedFeatures'], context.previousFeatures);
                setFeatures(context.previousFeatures);
            }
        },
        onSettled: () => {
            // Refetch to ensure sync
            queryClient.invalidateQueries({ queryKey: ['pinnedFeatures'] });
        },
    });

    // Pin mutation
    const pinMutation = useMutation({
        mutationFn: (featureTitle: string) => api.pinFeature(featureTitle),
        onMutate: async (featureTitle) => {
            await queryClient.cancelQueries({ queryKey: ['pinnedFeatures'] });
            const previousFeatures = queryClient.getQueryData<Feature[]>(['pinnedFeatures']);

            // Note: This is optimistic - we'd need the full Feature object
            // In practice, we'd fetch it from available features list
            console.log('Pinning feature:', featureTitle);

            return { previousFeatures };
        },
        onError: (_err, _featureTitle, context) => {
            if (context?.previousFeatures) {
                queryClient.setQueryData(['pinnedFeatures'], context.previousFeatures);
                setFeatures(context.previousFeatures);
            }
        },
        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: ['pinnedFeatures'] });
        },
    });

    // Unpin mutation
    const unpinMutation = useMutation({
        mutationFn: (featureTitle: string) => api.unpinFeature(featureTitle),
        onMutate: async (featureTitle) => {
            await queryClient.cancelQueries({ queryKey: ['pinnedFeatures'] });
            const previousFeatures = queryClient.getQueryData<Feature[]>(['pinnedFeatures']);

            // Optimistically remove from UI
            const newFeatures = features.filter((f) => f.title !== featureTitle);
            queryClient.setQueryData(['pinnedFeatures'], newFeatures);
            setFeatures(newFeatures);

            return { previousFeatures };
        },
        onError: (_err, _featureTitle, context) => {
            if (context?.previousFeatures) {
                queryClient.setQueryData(['pinnedFeatures'], context.previousFeatures);
                setFeatures(context.previousFeatures);
            }
        },
        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: ['pinnedFeatures'] });
        },
    });

    const isMutating = updateMutation.isPending || pinMutation.isPending || unpinMutation.isPending;

    return {
        features,
        isLoading,
        error: error as Error | null,
        pin: (featureTitle: string) => pinMutation.mutate(featureTitle),
        unpin: (featureTitle: string) => unpinMutation.mutate(featureTitle),
        updateAll: (newFeatures: Feature[]) => updateMutation.mutate(newFeatures),
        isMutating,
        refetch,
    };
}
