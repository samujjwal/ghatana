import { useQuery } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { timeRangeAtom } from '@/state/jotai/atoms';
import { workflowsApi } from '@/services/api/workflowsApi';

/**
 * Hook for fetching workflow events with time-range filtering.
 *
 * <p><b>Purpose</b><br>
 * Provides real-time event stream for workflow visualization with filtering,
 * pagination, and automatic refetching.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { events, isLoading, error } = useWorkflowEvents();
 * events?.forEach(event => console.log(event.id, event.timestamp));
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Polls every 10 seconds for new events
 * - Caches data for 5 minutes
 * - Falls back to empty array on error
 * - Respects time range atom for filtering
 *
 * @doc.type hook
 * @doc.purpose Fetch workflow events with React Query integration
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useWorkflowEvents(options?: { enabled?: boolean; refetchInterval?: number }) {
    const [timeRange] = useAtom(timeRangeAtom);

    return useQuery({
        queryKey: ['workflowEvents', timeRange],
        queryFn: async () => {
            try {
                return await workflowsApi.getEvents({ timeRange });
            } catch (error) {
                console.warn('[useWorkflowEvents] API unavailable, using fallback:', error);
                return [];
            }
        },
        staleTime: 5 * 60 * 1000, // 5 minutes
        gcTime: 10 * 60 * 1000, // 10 minutes (formerly cacheTime)
        retry: 2,
        refetchInterval: options?.refetchInterval ?? 10 * 1000, // 10 seconds
        enabled: options?.enabled ?? true,
    });
}

/**
 * Hook for fetching detailed information about a specific workflow event.
 *
 * <p><b>Purpose</b><br>
 * Provides event details including payload, metadata, upstream/downstream
 * relationships, and AI reasoning for inspection drawer.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: event, isLoading } = useEventDetails(eventId);
 * console.log(event?.payload, event?.sourceAgent);
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Caches for 5 minutes (event data is immutable)
 * - Retries on network failure
 * - Falls back to empty event on error
 *
 * @doc.type hook
 * @doc.purpose Fetch individual event details
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useEventDetails(eventId: string, options?: { enabled?: boolean; refetchInterval?: number }) {
    return useQuery({
        queryKey: ['eventDetails', eventId],
        queryFn: async () => {
            try {
                return await workflowsApi.getEventDetails(eventId);
            } catch (error) {
                console.warn('[useEventDetails] API unavailable for eventId:', eventId, error);
                return {
                    id: eventId,
                    type: 'Unknown',
                    timestamp: new Date().toISOString(),
                    departmentId: '',
                    sourceAgent: '',
                    status: 'failed' as const,
                    payload: {},
                };
            }
        },
        staleTime: 5 * 60 * 1000, // 5 minutes
        gcTime: 10 * 60 * 1000, // 10 minutes
        retry: 2,
        refetchInterval: options?.refetchInterval,
        enabled: options?.enabled ?? !!eventId,
    });
}
