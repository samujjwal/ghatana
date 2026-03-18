import { useCallback } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { LifecyclePhase } from '@/types/lifecycle';
import { useParams } from 'react-router';
import { useNavigate } from 'react-router-dom';
import { getRouteForPhase } from '@/types/lifecycle';
import { currentWorkspaceIdAtom } from '@/state/atoms/workspaceAtom';

/**
 * Hook for managing lifecycle phase transitions
 */
export function useLifecyclePhaseTransition() {
    const { projectId } = useParams<{ projectId: string }>();
    const workspaceId = useAtomValue(currentWorkspaceIdAtom);
    const navigate = useNavigate();
    const queryClient = useQueryClient();

    const updateProjectMutation = useMutation({
        mutationFn: async (phase: LifecyclePhase) => {
            if (!projectId) throw new Error('No project ID');
            if (!workspaceId) throw new Error('No workspace ID');

            const response = await fetch(`/api/projects/${projectId}?workspaceId=${workspaceId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ lifecyclePhase: phase }),
            });

            if (!response.ok) {
                throw new Error(`Failed to update project lifecycle: ${response.status}`);
            }

            return response.json();
        },
        onSuccess: (data) => {
            // Invalidate project query to refetch
            queryClient.invalidateQueries({ queryKey: ['project', projectId] });

            // Invalidate projects list
            queryClient.invalidateQueries({ queryKey: ['projects'] });

            console.log('[LifecycleTransition] Phase updated successfully:', data);
        },
    });

    const transitionToPhase = useCallback(
        async (toPhase: LifecyclePhase) => {
            if (!projectId) {
                console.error('[LifecycleTransition] No project ID available');
                return;
            }

            try {
                console.log('[LifecycleTransition] Transitioning to phase:', toPhase);

                // Update the project with new phase
                await updateProjectMutation.mutateAsync(toPhase);

                // Navigate to the route for the new phase
                const newRoute = getRouteForPhase(projectId, toPhase);
                navigate(newRoute);

                console.log('[LifecycleTransition] Navigated to:', newRoute);
            } catch (error) {
                console.error('[LifecycleTransition] Error during transition:', error);
                throw error;
            }
        },
        [projectId, updateProjectMutation, navigate]
    );

    return {
        transitionToPhase,
        isLoading: updateProjectMutation.isPending,
        error: updateProjectMutation.error,
    };
}

export default useLifecyclePhaseTransition;
