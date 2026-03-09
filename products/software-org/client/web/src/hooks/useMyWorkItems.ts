/**
 * useMyWorkItems Hook
 *
 * <p><b>Purpose</b><br>
 * React Query hook for fetching and managing work items assigned to the
 * current user. Used in the Engineer persona dashboard to display "My Stories".
 *
 * <p><b>Features</b><br>
 * - Automatic data fetching with caching
 * - Optimistic updates for status changes
 * - Filter support for status, priority, type
 * - Background refetching
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { useMyWorkItems } from '@/hooks/useMyWorkItems';
 *
 * function MyStoriesCard() {
 *   const { workItems, isLoading, error } = useMyWorkItems();
 *   // Render work items
 * }
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose Work items data fetching hook
 * @doc.layer product
 * @doc.pattern React Query Hook
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { userProfileAtom } from '@/state/jotai/atoms';
import { workItemsApi } from '@/services/api/workItemsApi';
import type {
    WorkItemSummary,
    WorkItemFilters,
    WorkItemStatus,
} from '@/types/workItem';

// Query keys for cache management
export const WORK_ITEMS_KEYS = {
    all: ['workItems'] as const,
    list: (filters?: WorkItemFilters) => ['workItems', 'list', filters] as const,
    myItems: (userId: string) => ['workItems', 'my', userId] as const,
    detail: (id: string) => ['workItems', 'detail', id] as const,
};

export interface UseMyWorkItemsOptions {
    filters?: WorkItemFilters;
    enabled?: boolean;
    refetchInterval?: number;
}

export interface UseMyWorkItemsResult {
    workItems: WorkItemSummary[];
    isLoading: boolean;
    isError: boolean;
    error: Error | null;
    refetch: () => void;
}

/**
 * Hook to fetch work items assigned to the current user
 */
export function useMyWorkItems(options: UseMyWorkItemsOptions = {}): UseMyWorkItemsResult {
    const { filters, enabled = true, refetchInterval = 30000 } = options;
    const [userProfile] = useAtom(userProfileAtom);

    const userId = userProfile?.userId ?? 'eng-1'; // Fallback for demo

    const {
        data = [],
        isLoading,
        isError,
        error,
        refetch,
    } = useQuery({
        queryKey: [...WORK_ITEMS_KEYS.myItems(userId), filters],
        queryFn: () => workItemsApi.getMyWorkItems(userId, filters),
        enabled,
        refetchInterval,
        staleTime: 10000, // Consider data fresh for 10 seconds
    });

    return {
        workItems: data,
        isLoading,
        isError,
        error: error as Error | null,
        refetch,
    };
}

/**
 * Hook to fetch all work items (not filtered by assignee)
 */
export function useAllWorkItems(options: UseMyWorkItemsOptions = {}): UseMyWorkItemsResult {
    const { filters, enabled = true, refetchInterval = 30000 } = options;

    const {
        data = [],
        isLoading,
        isError,
        error,
        refetch,
    } = useQuery({
        queryKey: WORK_ITEMS_KEYS.list(filters),
        queryFn: () => workItemsApi.getAllWorkItems(filters),
        enabled,
        refetchInterval,
        staleTime: 10000,
    });

    return {
        workItems: data,
        isLoading,
        isError,
        error: error as Error | null,
        refetch,
    };
}

/**
 * Hook to fetch a single work item by ID
 */
export function useWorkItem(id: string) {
    return useQuery({
        queryKey: WORK_ITEMS_KEYS.detail(id),
        queryFn: () => workItemsApi.getWorkItem(id),
        enabled: Boolean(id),
        staleTime: 5000,
    });
}

/**
 * Hook for updating work item status with optimistic updates
 */
export function useUpdateWorkItemStatus() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ id, status }: { id: string; status: WorkItemStatus }) =>
            workItemsApi.updateWorkItemStatus(id, status),
        onMutate: async ({ id, status }) => {
            // Cancel outgoing refetches
            await queryClient.cancelQueries({ queryKey: WORK_ITEMS_KEYS.all });

            // Snapshot previous value
            const previousDetail = queryClient.getQueryData(WORK_ITEMS_KEYS.detail(id));

            // Optimistically update the cache
            queryClient.setQueryData(WORK_ITEMS_KEYS.detail(id), (old: any) => {
                if (!old) return old;
                return { ...old, status, updatedAt: new Date().toISOString() };
            });

            return { previousDetail };
        },
        onError: (_err, { id }, context) => {
            // Rollback on error
            if (context?.previousDetail) {
                queryClient.setQueryData(WORK_ITEMS_KEYS.detail(id), context.previousDetail);
            }
        },
        onSettled: () => {
            // Refetch to ensure consistency
            queryClient.invalidateQueries({ queryKey: WORK_ITEMS_KEYS.all });
        },
    });
}

/**
 * Hook for updating work item plan
 */
export function useUpdateWorkItemPlan() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ id, plan }: { id: string; plan: Parameters<typeof workItemsApi.updateWorkItemPlan>[1] }) =>
            workItemsApi.updateWorkItemPlan(id, plan),
        onSuccess: (data, { id }) => {
            if (data) {
                queryClient.setQueryData(WORK_ITEMS_KEYS.detail(id), data);
            }
            queryClient.invalidateQueries({ queryKey: WORK_ITEMS_KEYS.all });
        },
    });
}

/**
 * Hook for completing a work item
 */
export function useCompleteWorkItem() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (id: string) => workItemsApi.completeWorkItem(id),
        onSuccess: (data, id) => {
            if (data) {
                queryClient.setQueryData(WORK_ITEMS_KEYS.detail(id), data);
            }
            queryClient.invalidateQueries({ queryKey: WORK_ITEMS_KEYS.all });
        },
    });
}

export default useMyWorkItems;
