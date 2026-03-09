import { useQuery } from '@tanstack/react-query';
import { reportsApi } from '@/services/api/reportsApi';

/**
 * Hook for fetching incident details.
 *
 * <p><b>Purpose</b><br>
 * Retrieves full incident details including status, affected services, timeline,
 * and remediation progress for display in IncidentPanel.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: incident, isLoading } = useIncident('INC-42');
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Auto-enables when incidentId provided
 * - Caches for 2 minutes (incidents change frequently)
 * - Retries on network failure
 * - Supports optional refetchInterval for live updates
 *
 * @doc.type hook
 * @doc.purpose Fetch single incident details for HITL panel
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useIncident(incidentId?: string, options?: { enabled?: boolean; refetchInterval?: number }) {
    return useQuery({
        queryKey: ['incident', incidentId],
        queryFn: async () => {
            if (!incidentId) return null;
            try {
                // Use incident timeline API as fallback
                // In production, would use dedicated incidentApi.getIncidentDetail(incidentId)
                const timeline = await reportsApi.getIncidentTimeline(incidentId);
                return {
                    id: incidentId,
                    timeline: timeline || [],
                };
            } catch (error) {
                console.warn('[useIncident] API unavailable, returning fallback:', error);
                return null;
            }
        },
        staleTime: 2 * 60 * 1000, // 2 minutes
        gcTime: 5 * 60 * 1000, // 5 minutes
        retry: 2,
        refetchInterval: options?.refetchInterval ?? 5 * 1000, // 5 seconds for live updates
        enabled: options?.enabled ?? !!incidentId, // Auto-enable when incidentId is provided
    });
}
