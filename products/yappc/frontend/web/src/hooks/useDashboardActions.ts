import { useCallback } from 'react';
import { useNavigate } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import type { ProjectDashboardAction } from '../lib/api';
import { yappcApi } from '../lib/api';

export function useDashboardActions(currentWorkspaceId: string | null) {
    const navigate = useNavigate();

    const executeDashboardAction = useMutation({
        mutationFn: async (action: ProjectDashboardAction) => {
            const workspaceId = currentWorkspaceId ?? action.workspaceId;
            return yappcApi.projects.executeDashboardAction(action.projectId, {
                workspaceId,
                actionId: action.id,
            });
        },
        onSuccess: (result) => {
            navigate(result.targetPath);
        },
    });

    const openDashboardAction = useCallback((action: ProjectDashboardAction) => {
        if (action.safeToRun) {
            executeDashboardAction.mutate(action);
            return;
        }
        navigate(`/p/${action.projectId}/${action.routePhase}`);
    }, [executeDashboardAction, navigate]);

    return {
        executeDashboardAction,
        openDashboardAction,
        isExecuting: executeDashboardAction.isPending,
    };
}
