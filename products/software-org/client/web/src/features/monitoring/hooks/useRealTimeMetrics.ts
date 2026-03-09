import { useEffect, useRef, useState } from 'react';
import { useAtom } from 'jotai';
import * as monitoringApi from '@/services/api/monitoringApi';
import { monitoringStateAtom } from '../stores/monitoring.store';
import type { SystemMetric } from '@/services/api/monitoringApi';

interface RealTimeMetricsState {
    metrics: Map<string, Record<string, unknown>>;
    isConnected: boolean;
    lastUpdate: Date | null;
    error: Error | null;
}

/**
 * Custom hook for subscribing to real-time system metrics.
 *
 * Provides:
 * - WebSocket subscription to real-time metrics
 * - Automatic reconnection on disconnection
 * - Metric data caching and updates
 * - Connection state management
 * - Cleanup on unmount
 *
 * @param metricIds - Array of metric IDs to subscribe to
 * @param options - Hook options (autoReconnect, maxRetries)
 * @returns Object with metrics state and utilities
 *
 * @example
 * const { metrics, isConnected, error } = useRealTimeMetrics(['cpu', 'memory']);
 * return (
 *   <div>
 *     CPU: {metrics.get('cpu')?.value}%
 *   </div>
 * );
 */
export function useRealTimeMetrics(
    metricIds: string[] = [],
    options: { autoReconnect?: boolean; maxRetries?: number } = {}
) {
    const { autoReconnect = true, maxRetries = 5 } = options;
    const [_monitoringState, setMonitoringState] = useAtom(monitoringStateAtom);

    const [state, setState] = useState<RealTimeMetricsState>({
        metrics: new Map(),
        isConnected: false,
        lastUpdate: null,
        error: null,
    });

    const unsubscribesRef = useRef<Array<() => void>>([]);
    const reconnectAttemptsRef = useRef(0);
    const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);

    /**
     * Subscribe to metrics with exponential backoff reconnection
     */
    useEffect(() => {
        if (metricIds.length === 0) return;

        const subscribe = async () => {
            try {
                setState((prev) => ({ ...prev, error: null }));

                const tenantId = (typeof window !== 'undefined' && localStorage.getItem('tenantId')) || 'default';

                // Subscribe to metrics via WebSocket
                const unsubscribe = await monitoringApi.subscribeToMetrics(
                    tenantId,
                    (metrics: SystemMetric[]) => {
                        setState((prev) => {
                            const newMetrics = new Map(prev.metrics);
                            metrics
                                .filter((m) => metricIds.length === 0 || metricIds.includes(m.name))
                                .forEach((m) => {
                                    newMetrics.set(m.name, {
                                        name: m.name,
                                        value: m.value,
                                        unit: m.unit,
                                        timestamp: m.timestamp,
                                        status: m.status,
                                    });
                                });
                            return {
                                ...prev,
                                metrics: newMetrics,
                                isConnected: true,
                                lastUpdate: new Date(),
                            };
                        });
                        reconnectAttemptsRef.current = 0;
                        setMonitoringState((prev) => ({
                            ...prev,
                            isStreamingActive: true,
                            reconnectAttempts: 0,
                        }));
                    }
                );

                unsubscribesRef.current.push(unsubscribe);
                setState((prev) => ({ ...prev, isConnected: true }));
            } catch (error) {
                const err = error instanceof Error ? error : new Error(String(error));
                setState((prev) => ({
                    ...prev,
                    isConnected: false,
                    error: err,
                }));

                // Attempt reconnection with exponential backoff
                if (
                    autoReconnect &&
                    reconnectAttemptsRef.current < maxRetries
                ) {
                    reconnectAttemptsRef.current += 1;
                    const backoffMs = Math.min(1000 * Math.pow(2, reconnectAttemptsRef.current), 30000);

                    setMonitoringState((prev) => ({
                        ...prev,
                        reconnectAttempts: reconnectAttemptsRef.current,
                    }));

                    reconnectTimeoutRef.current = setTimeout(subscribe, backoffMs);
                }
            }
        };

        subscribe();

        return () => {
            // Cleanup subscriptions
            unsubscribesRef.current.forEach((unsub) => unsub());
            unsubscribesRef.current = [];

            if (reconnectTimeoutRef.current) {
                clearTimeout(reconnectTimeoutRef.current);
            }

            setState((prev) => ({
                ...prev,
                isConnected: false,
            }));
        };
    }, [metricIds, autoReconnect, maxRetries, setMonitoringState]);

    /**
     * Get metric value by ID
     *
     * @param metricId - Metric ID to retrieve
     * @returns Metric value or undefined
     */
    const getMetric = (metricId: string) => state.metrics.get(metricId);

    /**
     * Manually reconnect to metrics
     */
    const reconnect = () => {
        reconnectAttemptsRef.current = 0;
        unsubscribesRef.current.forEach((unsub) => unsub());
        unsubscribesRef.current = [];
    };

    return {
        // State
        metrics: state.metrics,
        isConnected: state.isConnected,
        lastUpdate: state.lastUpdate,
        error: state.error,
        reconnectAttempts: reconnectAttemptsRef.current,

        // Utilities
        getMetric,
        reconnect,
    };
}
