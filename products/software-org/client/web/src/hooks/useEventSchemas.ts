import { useQuery } from '@tanstack/react-query';
import { eventsApi } from '@/services/api/eventsApi';

/**
 * Hook for fetching event schemas for validation and simulation.
 *
 * <p><b>Purpose</b><br>
 * Provides schema definitions for event types, enabling validation and UI generation
 * for event simulator (Day 6).
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: schemas, isLoading, error } = useEventSchemas();
 * schemas?.forEach(schema => console.log(schema.name, schema.fields));
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Caches for 30 minutes (schemas rarely change)
 * - No polling (static reference data)
 * - Retries on network failure
 *
 * @doc.type hook
 * @doc.purpose Fetch event schemas for simulator
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useEventSchemas(options?: { enabled?: boolean }) {
    return useQuery({
        queryKey: ['eventSchemas'],
        queryFn: async () => {
            try {
                return await eventsApi.getSchemas();
            } catch (error) {
                console.warn('[useEventSchemas] API unavailable, using fallback:', error);
                return [];
            }
        },
        staleTime: 30 * 60 * 1000, // 30 minutes (reference data)
        gcTime: 60 * 60 * 1000, // 60 minutes
        retry: 2,
        refetchInterval: undefined, // No polling
        enabled: options?.enabled ?? true,
    });
}
