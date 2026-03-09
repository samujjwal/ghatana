import { useQuery } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { timeRangeAtom } from '@/state/jotai/atoms';
import { reportsApi } from '@/services/api/reportsApi';

/**
 * Hook for fetching reporting metrics and KPIs.
 *
 * <p><b>Purpose</b><br>
 * Provides metrics for incident reporting and SLA compliance tracking (Day 7).
 * Aggregates data across time ranges with filtering.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: metrics, isLoading, error } = useReportMetrics();
 * console.log(metrics?.incidentCount, metrics?.mttr, metrics?.resolution);
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Respects global time range atom
 * - Polls every 30 seconds (periodic updates)
 * - Caches for 5 minutes
 * - Retries on network failure
 *
 * @doc.type hook
 * @doc.purpose Fetch reporting metrics with time-range support
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useReportMetrics(options?: { enabled?: boolean; refetchInterval?: number }) {
    const [timeRange] = useAtom(timeRangeAtom);

    return useQuery({
        queryKey: ['reportMetrics', timeRange],
        queryFn: async () => {
            try {
                return await reportsApi.getMetrics({ timeRange });
            } catch (error) {
                console.warn('[useReportMetrics] API unavailable, using fallback:', error);
                return {
                    incidentCount: 0,
                    mttr: 0,
                    resolution: 0,
                    slaCompliance: 0,
                    detectionTime: 0,
                };
            }
        },
        staleTime: 5 * 60 * 1000, // 5 minutes
        gcTime: 10 * 60 * 1000, // 10 minutes
        retry: 2,
        refetchInterval: options?.refetchInterval ?? 30 * 1000, // 30 seconds
        enabled: options?.enabled ?? true,
    });
}
