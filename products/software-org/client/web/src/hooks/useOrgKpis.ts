import { useQuery } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { timeRangeAtom } from '@/state/jotai/atoms';
import { kpisApi } from '@/services/api/kpisApi';

/**
 * Hook for fetching organization KPI dashboard data.
 *
 * <p><b>Purpose</b><br>
 * Provides aggregated KPIs across all departments:
 * - Deployment frequency and lead time
 * - MTTR (Mean Time To Recovery)
 * - Change failure rate
 * - Overall health score
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: kpis, isLoading, error } = useOrgKpis();
 * console.log(kpis?.deploymentFrequency, kpis?.mttr, kpis?.leadTime);
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Respects global time range atom
 * - Polls every 30 seconds
 * - Caches for 5 minutes
 * - Fallback to empty object on error
 * - Retries up to 2 times
 *
 * @doc.type hook
 * @doc.purpose Fetch organization KPI dashboard
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useOrgKpis(options?: { enabled?: boolean; refetchInterval?: number }) {
    const [timeRange] = useAtom(timeRangeAtom);

    return useQuery({
        queryKey: ['orgKpis', timeRange],
        queryFn: async () => {
            try {
                return await kpisApi.getOrgKpis(timeRange);
            } catch (error) {
                console.warn('[useOrgKpis] API unavailable, using fallback:', error);
                return {
                    deploymentFrequency: 0,
                    leadTime: 0,
                    mttr: 0,
                    changeFailureRate: 0,
                    healthScore: 0,
                };
            }
        },
        staleTime: 5 * 60 * 1000, // 5 minutes
        gcTime: 10 * 60 * 1000, // 10 minutes (formerly cacheTime)
        retry: 2,
        refetchInterval: options?.refetchInterval ?? 30 * 1000, // 30 seconds
        enabled: options?.enabled ?? true,
    });
}
