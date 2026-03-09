/**
 * useRecentActivities Hook
 *
 * <p><b>Purpose</b><br>
 * React Query hook for fetching and auto-refreshing user's recent activities.
 * Polls every 60 seconds and syncs with Jotai atom for global state.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { activities, isLoading, error, refetch } = useRecentActivities(5);
 *
 * if (isLoading) return <Spinner />;
 * return <Timeline activities={activities} />;
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose Fetch and sync recent activities with polling
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { recentActivitiesAtom, type Activity, userProfileAtom, userRoleAtom } from '@/state/jotai/atoms';
import { api } from '@/services/personaApi';

/**
 * Hook options
 */
export interface UseRecentActivitiesOptions {
    /**
     * Maximum number of activities to fetch (default: 5)
     */
    maxItems?: number;

    /**
     * Polling interval in milliseconds (default: 60000 = 60 seconds)
     */
    refetchInterval?: number;

    /**
     * Enable/disable automatic polling (default: true)
     */
    enabled?: boolean;

    /**
     * Retry failed requests (default: 3)
     */
    retry?: number;
}

/**
 * Hook return type
 */
export interface UseRecentActivitiesResult {
    /**
     * Recent activities (from Jotai atom)
     */
    activities: Activity[];

    /**
     * Loading state (first fetch)
     */
    isLoading: boolean;

    /**
     * Error if fetch failed
     */
    error: Error | null;

    /**
     * Refetch function for manual refresh
     */
    refetch: () => void;

    /**
     * True if background refetch is happening
     */
    isFetching: boolean;

    /**
     * Timestamp of last successful fetch
     */
    dataUpdatedAt: number;
}

/**
 * Fetches and syncs user's recent activities with Jotai atom.
 *
 * Features:
 * - Auto-polling every 60 seconds (configurable)
 * - Automatic retry on failure (3 attempts)
 * - Syncs with Jotai atom for global state
 * - Configurable activity limit
 *
 * @param options - Hook configuration options
 * @returns UseRecentActivitiesResult - Activities, loading state, error, refetch
 */
export function useRecentActivities(options: UseRecentActivitiesOptions = {}): UseRecentActivitiesResult {
    const {
        maxItems = 5,
        refetchInterval = 60000, // 60 seconds
        enabled = true,
        retry = 3,
    } = options;

    const [activities, setActivities] = useAtom(recentActivitiesAtom);
    const [userProfile] = useAtom(userProfileAtom);
    const [userRoleAtomValue] = useAtom(userRoleAtom);
    const role = userProfile?.role || userRoleAtomValue;

    const {
        data,
        error,
        isLoading,
        isFetching,
        refetch,
        dataUpdatedAt,
    } = useQuery({
        queryKey: ['recentActivities', maxItems, role],
        queryFn: () => api.getRecentActivities(maxItems),
        refetchInterval,
        enabled,
        retry,
        staleTime: 30000, // Consider data stale after 30 seconds
    });

    // Sync React Query data with Jotai atom
    useEffect(() => {
        if (data) {
            setActivities(data);
        }
    }, [data, setActivities]);

    return {
        activities,
        isLoading,
        error: error as Error | null,
        refetch,
        isFetching,
        dataUpdatedAt,
    };
}

/**
 * Filters activities by status.
 *
 * @param activities - Array of activities
 * @param status - Status to filter by
 * @returns Filtered activities
 */
export function filterActivitiesByStatus(
    activities: Activity[],
    status: 'success' | 'failed' | 'pending'
): Activity[] {
    return activities.filter((activity) => activity.status === status);
}

/**
 * Groups activities by date.
 *
 * @param activities - Array of activities
 * @returns Activities grouped by date string (YYYY-MM-DD)
 */
export function groupActivitiesByDate(activities: Activity[]): Record<string, Activity[]> {
    return activities.reduce((groups, activity) => {
        const date = activity.timestamp.toISOString().split('T')[0];
        if (!groups[date]) {
            groups[date] = [];
        }
        groups[date].push(activity);
        return groups;
    }, {} as Record<string, Activity[]>);
}
