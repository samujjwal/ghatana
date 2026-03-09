/**
 * Growth Plans API Hooks
 *
 * React Query hooks for the Personal Growth Plan journey.
 *
 * @doc.type hooks
 * @doc.purpose Growth plan API integration
 * @doc.layer product
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { growthPlansApi, type CreateGrowthPlanRequest, type UpdateGrowthPlanRequest } from '@/services/api';

export const growthPlansQueryKeys = {
    all: ['growth-plans'] as const,
    list: (userId?: string) => [...growthPlansQueryKeys.all, 'list', userId] as const,
    detail: (id?: string) => [...growthPlansQueryKeys.all, 'detail', id] as const,
};

export function useGrowthPlansList(params: {
    userId?: string;
    status?: string;
    period?: string;
    limit?: number;
    offset?: number;
}) {
    const { userId, status, period, limit, offset } = params;

    return useQuery({
        queryKey: [...growthPlansQueryKeys.list(userId), status, period, limit, offset],
        queryFn: async () => {
            if (!userId) throw new Error('userId is required');
            return growthPlansApi.listGrowthPlans({ userId, status, period, limit, offset });
        },
        enabled: !!userId,
    });
}

export function useGrowthPlan(id?: string) {
    return useQuery({
        queryKey: growthPlansQueryKeys.detail(id),
        queryFn: async () => {
            if (!id) throw new Error('id is required');
            return growthPlansApi.getGrowthPlan(id);
        },
        enabled: !!id,
    });
}

export function useCreateGrowthPlan() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (body: CreateGrowthPlanRequest) => growthPlansApi.createGrowthPlan(body),
        onSuccess: (created) => {
            queryClient.invalidateQueries({ queryKey: growthPlansQueryKeys.list(created.userId) });
        },
    });
}

export function useUpdateGrowthPlan() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ id, body }: { id: string; body: UpdateGrowthPlanRequest }) =>
            growthPlansApi.updateGrowthPlan(id, body),
        onSuccess: (updated) => {
            queryClient.invalidateQueries({ queryKey: growthPlansQueryKeys.detail(updated.id) });
            queryClient.invalidateQueries({ queryKey: growthPlansQueryKeys.list(updated.userId) });
        },
    });
}

export function useCompleteGrowthPlan() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (id: string) => growthPlansApi.completeGrowthPlan(id),
        onSuccess: (updated) => {
            queryClient.invalidateQueries({ queryKey: growthPlansQueryKeys.detail(updated.id) });
            queryClient.invalidateQueries({ queryKey: growthPlansQueryKeys.list(updated.userId) });
        },
    });
}
