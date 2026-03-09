/**
 * useMetrics Hook
 *
 * <p><b>Purpose</b><br>
 * React Query hook for fetching role-specific metrics with auto-refresh.
 * Polls every 30 seconds for real-time metric updates.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { metrics, isLoading, error, refetch } = useMetrics('admin');
 *
 * if (isLoading) return <Spinner />;
 * return <MetricsGrid metrics={metrics} />;
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose Fetch role-specific metrics with polling
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useQuery } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { userProfileAtom } from '@/state/jotai/atoms';
import { api } from '@/services/personaApi';
import type { UserRole } from '@/config/personaConfig';

/**
 * Hook options
 */
export interface UseMetricsOptions {
    /**
     * Polling interval in milliseconds (default: 30000 = 30 seconds)
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
export interface UseMetricsResult {
    /**
     * Metric values keyed by dataKey (e.g., { openCases: 45, avgResolutionTime: 2.3 })
     */
    metrics: Record<string, number>;

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
 * Fetches role-specific metrics from API.
 *
 * Features:
 * - Auto-polling every 30 seconds (configurable)
 * - Automatic retry on failure (3 attempts)
 * - Uses current user's role if not specified
 * - Returns empty object if no data available
 *
 * @param roleOverride - Optional role override (uses current user's role if not provided)
 * @param options - Hook configuration options
 * @returns UseMetricsResult - Metrics, loading state, error, refetch
 */
export function useMetrics(roleOverride?: UserRole, options: UseMetricsOptions = {}): UseMetricsResult {
    const {
        refetchInterval = 30000, // 30 seconds
        enabled = true,
        retry = 3,
    } = options;

    const [userProfile] = useAtom(userProfileAtom);
    const role = roleOverride || userProfile?.role || 'viewer';

    const {
        data,
        error,
        isLoading,
        isFetching,
        refetch,
        dataUpdatedAt,
    } = useQuery({
        queryKey: ['metrics', role],
        queryFn: () => api.getMetrics(role),
        refetchInterval,
        enabled: enabled && !!role,
        retry,
        staleTime: 10000, // Consider data stale after 10 seconds
    });

    return {
        metrics: data ?? {},
        isLoading,
        error: error as Error | null,
        refetch,
        isFetching,
        dataUpdatedAt,
    };
}

/**
 * Calculates percentage change between current and previous metric values.
 *
 * @param current - Current metric value
 * @param previous - Previous metric value
 * @returns Percentage change (e.g., 15.5 for 15.5% increase)
 */
export function calculateMetricChange(current: number, previous: number): number {
    if (previous === 0) return current > 0 ? 100 : 0;
    return ((current - previous) / previous) * 100;
}

/**
 * Formats metric value based on type.
 *
 * @param value - Metric value
 * @param format - Format type ('number' | 'percentage' | 'duration')
 * @returns Formatted string
 */
export function formatMetricValue(value: number, format: 'number' | 'percentage' | 'duration'): string {
    switch (format) {
        case 'percentage':
            return `${value.toFixed(1)}%`;
        case 'duration':
            return formatDuration(value);
        case 'number':
        default:
            return value.toLocaleString();
    }
}

/**
 * Formats duration in hours to human-readable string.
 *
 * @param hours - Duration in hours
 * @returns Formatted string (e.g., "2.5h", "1d 3h")
 */
function formatDuration(hours: number): string {
    if (hours < 24) {
        return `${hours.toFixed(1)}h`;
    }
    const days = Math.floor(hours / 24);
    const remainingHours = Math.floor(hours % 24);
    return remainingHours > 0 ? `${days}d ${remainingHours}h` : `${days}d`;
}
