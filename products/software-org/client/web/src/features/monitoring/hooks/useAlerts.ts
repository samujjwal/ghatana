import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as monitoringApi from '@/services/api/monitoringApi';
import { useCallback } from 'react';

/**
 * Custom hook for managing system alerts and notifications.
 *
 * Provides:
 * - Fetch alerts with auto-refresh
 * - Filter and sort alerts by severity
 * - Acknowledge alerts with mutations
 * - Create new alerts
 * - Auto-refresh with configurable interval
 * - Caching and cache invalidation
 *
 * @param options - Hook options (refetchInterval, filterSeverity, autoAcknowledge)
 * @returns Object with alerts state and utilities
 *
 * @example
 * const { alerts, critical, acknowledge, createAlert } = useAlerts();
 * return (
 *   <div>
 *     Critical: {critical.length}
 *     {alerts.map(a => (
 *       <AlertRow key={a.id} alert={a} onAck={() => acknowledge(a.id)} />
 *     ))}
 *   </div>
 * );
 */
export function useAlerts(options: {
    refetchInterval?: number;
    filterSeverity?: string;
    autoAcknowledge?: boolean;
} = {}) {
    const { refetchInterval = 30000, filterSeverity, autoAcknowledge: _ = false } = options;
    const queryClient = useQueryClient();

    // Fetch alerts
    const alertsQuery = useQuery({
        queryKey: ['monitoring', 'alerts', filterSeverity],
        queryFn: async () => {
            try {
                const tenantId = (typeof window !== 'undefined' && localStorage.getItem('tenantId')) || 'default';
                const alerts = await monitoringApi.getAlerts(tenantId);
                // Filter by severity if specified
                if (filterSeverity && filterSeverity !== 'all') {
                    return alerts.filter((a: any) => a.severity === filterSeverity);
                }
                return alerts;
            } catch (error) {
                console.error('Failed to fetch alerts:', error);
                return [];
            }
        },
        staleTime: 5000, // 5 seconds
        gcTime: 15 * 60 * 1000, // 15 minutes
        refetchInterval,
    });

    // Acknowledge alert mutation
    const acknowledgeMutation = useMutation({
        mutationFn: async (alertId: string) => {
            try {
                const result = await monitoringApi.acknowledgeAlert(alertId);
                return result;
            } catch (error) {
                console.error(`Failed to acknowledge alert ${alertId}:`, error);
                throw error;
            }
        },
        onSuccess: () => {
            // Invalidate alerts cache to refetch
            queryClient.invalidateQueries({
                queryKey: ['monitoring', 'alerts'],
            });
        },
    });

    // Create alert mutation
    const createMutation = useMutation({
        mutationFn: async (alertData: {
            title: string;
            message: string;
            severity: 'info' | 'warning' | 'critical';
            source?: string;
        }) => {
            try {
                const result = await monitoringApi.createAlert(alertData);
                return result;
            } catch (error) {
                console.error('Failed to create alert:', error);
                throw error;
            }
        },
        onSuccess: () => {
            // Invalidate alerts cache
            queryClient.invalidateQueries({
                queryKey: ['monitoring', 'alerts'],
            });
        },
    });

    /**
     * Acknowledge an alert
     *
     * @param alertId - Alert ID to acknowledge
     * @returns Promise with result
     */
    const acknowledge = useCallback(
        async (alertId: string) => {
            try {
                const result = await acknowledgeMutation.mutateAsync(alertId);
                return result;
            } catch (error) {
                throw error;
            }
        },
        [acknowledgeMutation]
    );

    /**
     * Create a new alert
     *
     * @param alertData - Alert data
     * @returns Promise with created alert
     */
    const createAlert = useCallback(
        async (alertData: {
            title: string;
            message: string;
            severity: 'info' | 'warning' | 'critical';
            source?: string;
        }) => {
            try {
                const result = await createMutation.mutateAsync(alertData);
                return result;
            } catch (error) {
                throw error;
            }
        },
        [createMutation]
    );

    /**
     * Get count of alerts by severity
     */
    const getAlertCounts = useCallback(() => {
        const alerts = alertsQuery.data || [];
        return {
            total: alerts.length,
            critical: alerts.filter((a: any) => a.severity === 'critical').length,
            warning: alerts.filter((a: any) => a.severity === 'warning').length,
            info: alerts.filter((a: any) => a.severity === 'info').length,
            unacknowledged: alerts.filter((a: any) => !a.acknowledged).length,
        };
    }, [alertsQuery.data]);

    /**
     * Get unacknowledged alerts
     */
    const getUnacknowledged = useCallback(() => {
        return (alertsQuery.data || []).filter((a: any) => !a.acknowledged);
    }, [alertsQuery.data]);

    /**
     * Get acknowledged alerts
     */
    const getAcknowledged = useCallback(() => {
        return (alertsQuery.data || []).filter((a: any) => a.acknowledged);
    }, [alertsQuery.data]);

    /**
     * Refetch alerts manually
     */
    const refetch = useCallback(() => {
        return alertsQuery.refetch();
    }, [alertsQuery]);

    return {
        // Query state
        alerts: alertsQuery.data || [],
        isLoading: alertsQuery.isLoading,
        isFetching: alertsQuery.isFetching,
        error: alertsQuery.error as Error | null,

        // Derived data
        unacknowledged: getUnacknowledged(),
        acknowledged: getAcknowledged(),
        counts: getAlertCounts(),

        // Mutations
        isAcknowledging: acknowledgeMutation.isPending,
        isCreating: createMutation.isPending,

        // Utilities
        acknowledge,
        createAlert,
        refetch,
    };
}
