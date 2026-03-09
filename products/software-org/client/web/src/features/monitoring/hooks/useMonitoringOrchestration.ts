/**
 * Monitoring Orchestration Hook
 *
 * <p><b>Purpose</b><br>
 * Custom hook providing real-time monitoring orchestration, combining store state,
 * WebSocket subscriptions, API queries, and business logic for system health monitoring,
 * alert management, and anomaly detection.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const {
 *   systemHealth,
 *   alerts,
 *   anomalies,
 *   isConnected,
 *   selectMetric,
 *   acknowledgeAlert,
 *   dismissAnomaly,
 * } = useMonitoringOrchestration();
 * ```
 *
 * @doc.type hook
 * @doc.purpose Monitoring feature orchestration with WebSocket support
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import * as monitoringApi from '@/services/api/monitoringApi';
import {
    monitoringStateAtom,
    selectMetricAtom,
    setAlertFilterAtom,
    updateLastUpdateAtom,
} from '../stores/monitoring.store';
import type { Anomaly } from '@/types/ml-monitoring';
import type { SystemHealth } from '@/services/api/monitoringApi';

/**
 * Monitoring orchestration hook interface
 */
export interface UseMonitoringOrchestrationReturn {
    systemHealth: SystemHealth | undefined;
    alerts: unknown[] | undefined;
    anomalies: Anomaly[] | undefined;
    isLoading: boolean;
    isConnected: boolean;
    selectedMetric: string | null;
    alertFilter: string;
    selectMetric: (metricName: string) => void;
    setAlertFilter: (filter: string) => void;
    acknowledgeAlert: (alertId: string) => Promise<void>;
    dismissAnomaly: (anomalyId: string) => Promise<void>;
    reconnect: () => void;
}

/**
 * Custom hook for monitoring feature orchestration.
 *
 * @param tenantId - Tenant identifier
 * @param autoConnect - Auto-connect to WebSocket on mount
 * @returns Monitoring orchestration state and methods
 */
export function useMonitoringOrchestration(
    tenantId: string,
    autoConnect = true
): UseMonitoringOrchestrationReturn {
    const [monitoringState, setMonitoringState] = useAtom(monitoringStateAtom);
    const [, selectMetric] = useAtom(selectMetricAtom);
    const [, setAlertFilter] = useAtom(setAlertFilterAtom);
    const [, updateLastUpdate] = useAtom(updateLastUpdateAtom);
    const queryClient = useQueryClient();
    const wsRef = useRef<WebSocket | null>(null);
    const [isConnected, setIsConnected] = useState(false);

    // Fetch system health
    const { data: systemHealth, isLoading: healthLoading } = useQuery({
        queryKey: ['system-health', tenantId],
        queryFn: () => monitoringApi.getRealTimeMetrics(tenantId),
        staleTime: 5000,
        refetchInterval: 10000,
    });

    // Fetch alerts
    const { data: alerts, isLoading: alertsLoading } = useQuery({
        queryKey: ['alerts', tenantId],
        queryFn: () => monitoringApi.getAlerts(tenantId),
        staleTime: 10000,
        refetchInterval: 30000,
    });

    // Fetch anomalies
    const { data: anomalies, isLoading: anomaliesLoading } = useQuery({
        queryKey: ['anomalies', tenantId],
        queryFn: () => monitoringApi.getAnomalies(tenantId),
        staleTime: 30000,
        refetchInterval: 60000,
    });

    // Acknowledge alert mutation
    const acknowledgeAlertMutation = useMutation({
        mutationFn: (alertId: string) => monitoringApi.acknowledgeAlert(alertId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['alerts', tenantId] });
        },
    });

    // Dismiss anomaly mutation
    const dismissAnomalyMutation = useMutation({
        mutationFn: (anomalyId: string) => monitoringApi.dismissAnomaly(anomalyId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['anomalies', tenantId] });
        },
    });

    // WebSocket connection effect
    useEffect(() => {
        if (!autoConnect) return;

        const connectWebSocket = () => {
            try {
                const wsUrl = `${import.meta.env.VITE_WS_URL || 'ws://localhost:8080'}/metrics/${tenantId}`;
                wsRef.current = new WebSocket(wsUrl);

                wsRef.current.onopen = () => {
                    setIsConnected(true);
                    setMonitoringState((prev) => ({
                        ...prev,
                        isStreamingActive: true,
                        reconnectAttempts: 0,
                    }));
                };

                wsRef.current.onmessage = (event) => {
                    try {
                        const data = JSON.parse(event.data);
                        updateLastUpdate(new Date());
                        queryClient.setQueryData(['realtime-metrics', tenantId], data);
                    } catch (error) {
                        console.error('Failed to parse WebSocket message:', error);
                    }
                };

                wsRef.current.onerror = () => {
                    setIsConnected(false);
                };

                wsRef.current.onclose = () => {
                    setIsConnected(false);
                    setMonitoringState((prev) => ({
                        ...prev,
                        isStreamingActive: false,
                    }));
                };
            } catch (error) {
                console.error('Failed to connect WebSocket:', error);
                setIsConnected(false);
            }
        };

        connectWebSocket();

        return () => {
            if (wsRef.current) {
                wsRef.current.close();
            }
        };
    }, [tenantId, autoConnect, setMonitoringState, updateLastUpdate, queryClient]);

    // Memoized handlers
    const handleSelectMetric = useCallback(
        (metricName: string) => {
            selectMetric(metricName);
        },
        [selectMetric]
    );

    const handleSetAlertFilter = useCallback(
        (filter: string) => {
            setAlertFilter(filter);
        },
        [setAlertFilter]
    );

    const handleAcknowledgeAlert = useCallback(
        (alertId: string) => acknowledgeAlertMutation.mutateAsync(alertId),
        [acknowledgeAlertMutation]
    );

    const handleDismissAnomaly = useCallback(
        (anomalyId: string) => dismissAnomalyMutation.mutateAsync(anomalyId),
        [dismissAnomalyMutation]
    );

    const handleReconnect = useCallback(() => {
        if (wsRef.current) {
            wsRef.current.close();
        }
        setIsConnected(false);
        setTimeout(() => {
            const wsUrl = `${import.meta.env.VITE_WS_URL || 'ws://localhost:8080'}/metrics/${tenantId}`;
            wsRef.current = new WebSocket(wsUrl);
        }, 1000);
    }, [tenantId]);

    return {
        systemHealth,
        alerts,
        anomalies,
        isLoading: healthLoading || alertsLoading || anomaliesLoading,
        isConnected,
        selectedMetric: monitoringState.selectedMetric,
        alertFilter: monitoringState.alertFilter,
        selectMetric: handleSelectMetric,
        setAlertFilter: handleSetAlertFilter,
        acknowledgeAlert: handleAcknowledgeAlert,
        dismissAnomaly: handleDismissAnomaly,
        reconnect: handleReconnect,
    };
}

export default useMonitoringOrchestration;
