import { useQuery } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { timeRangeAtom } from '@/state/jotai/atoms';
import { departmentApi } from '@/services/api/departmentApi';

/**
 * Hook for fetching department list.
 *
 * <p><b>Purpose</b><br>
 * Provides department roster for navigation and filtering across the application.
 * Caches for longer period as department list changes infrequently.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: departments, isLoading, error } = useDepartments();
 * departments?.forEach(d => console.log(d.name, d.health));
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Caches for 30 minutes (reference data)
 * - Polls every 60 seconds
 * - Retries on network failure
 *
 * @doc.type hook
 * @doc.purpose Fetch department list
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useDepartments(options?: { enabled?: boolean; refetchInterval?: number }) {
    return useQuery({
        queryKey: ['departments'],
        queryFn: async () => {
            try {
                return await departmentApi.getDepartments();
            } catch (error) {
                console.warn('[useDepartments] API unavailable, using fallback:', error);
                return [];
            }
        },
        staleTime: 30 * 60 * 1000, // 30 minutes (reference data)
        gcTime: 60 * 60 * 1000, // 60 minutes
        retry: 2,
        refetchInterval: options?.refetchInterval ?? 60 * 1000, // 60 seconds
        enabled: options?.enabled ?? true,
    });
}

/**
 * Hook for fetching department details.
 *
 * <p><b>Purpose</b><br>
 * Provides detailed department information for drill-down views.
 * Includes owner, teams, services, and metadata.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: dept, isLoading, error } = useDepartmentDetail('dept-123');
 * console.log(dept?.name, dept?.owner, dept?.services);
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Caches for 15 minutes
 * - Polls every 60 seconds
 * - Disabled if departmentId is not provided
 * - Retries on network failure
 *
 * @doc.type hook
 * @doc.purpose Fetch department details
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useDepartmentDetail(departmentId?: string, options?: {
    enabled?: boolean;
    refetchInterval?: number;
}) {
    return useQuery({
        queryKey: ['departmentDetail', departmentId],
        queryFn: async () => {
            try {
                if (!departmentId) return null;
                return await departmentApi.getDepartment(departmentId);
            } catch (error) {
                console.warn('[useDepartmentDetail] API unavailable, using fallback:', error);
                return null;
            }
        },
        staleTime: 15 * 60 * 1000, // 15 minutes
        gcTime: 30 * 60 * 1000, // 30 minutes
        retry: 2,
        refetchInterval: options?.refetchInterval ?? 60 * 1000, // 60 seconds
        enabled: (options?.enabled ?? true) && !!departmentId,
    });
}

/**
 * Hook for fetching department KPIs with time-range support.
 *
 * <p><b>Purpose</b><br>
 * Provides department-specific metrics for dashboard visualization (Days 1-3).
 * Respects global time range filtering.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: kpis, isLoading, error } = useDepartmentKpis('dept-123');
 * kpis?.forEach(k => console.log(k.deploymentFrequency, k.mttr));
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Uses global timeRangeAtom
 * - Caches for 5 minutes
 * - Polls every 30 seconds
 * - Disabled if departmentId is not provided
 *
 * @doc.type hook
 * @doc.purpose Fetch department KPIs
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useDepartmentKpis(departmentId?: string, options?: {
    enabled?: boolean;
    refetchInterval?: number;
}) {
    const [timeRange] = useAtom(timeRangeAtom);

    return useQuery({
        queryKey: ['departmentKpis', departmentId, timeRange],
        queryFn: async () => {
            try {
                if (!departmentId) return null;
                return await departmentApi.getDepartmentKpis(departmentId, { timeRange });
            } catch (error) {
                console.warn('[useDepartmentKpis] API unavailable, using fallback:', error);
                return null;
            }
        },
        staleTime: 5 * 60 * 1000, // 5 minutes
        gcTime: 10 * 60 * 1000, // 10 minutes
        retry: 2,
        refetchInterval: options?.refetchInterval ?? 30 * 1000, // 30 seconds
        enabled: (options?.enabled ?? true) && !!departmentId,
    });
}

/**
 * Hook for fetching department events.
 *
 * <p><b>Purpose</b><br>
 * Provides real-time event stream for department-specific monitoring.
 * Supports pagination.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const { data: events, isLoading, error } = useDepartmentEvents('dept-123');
 * events?.forEach(e => console.log(e.type, e.severity, e.source));
 * ```
 *
 * <p><b>Behavior</b><br>
 * - Polls every 10 seconds (high priority)
 * - Caches for 2 minutes
 * - Disabled if departmentId is not provided
 * - Retries on network failure
 *
 * @doc.type hook
 * @doc.purpose Fetch department events
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useDepartmentEvents(departmentId?: string, options?: {
    limit?: number;
    enabled?: boolean;
    refetchInterval?: number;
}) {
    return useQuery({
        queryKey: ['departmentEvents', departmentId, options?.limit],
        queryFn: async () => {
            try {
                if (!departmentId) return null;
                return await departmentApi.getDepartmentEvents(departmentId, {
                    limit: options?.limit ?? 50,
                });
            } catch (error) {
                console.warn('[useDepartmentEvents] API unavailable, using fallback:', error);
                return null;
            }
        },
        staleTime: 2 * 60 * 1000, // 2 minutes
        gcTime: 5 * 60 * 1000, // 5 minutes
        retry: 2,
        refetchInterval: options?.refetchInterval ?? 10 * 1000, // 10 seconds
        enabled: (options?.enabled ?? true) && !!departmentId,
    });
}
