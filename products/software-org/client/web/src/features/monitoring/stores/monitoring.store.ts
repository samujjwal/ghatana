/**
 * Monitoring Feature Store
 *
 * <p><b>Purpose</b><br>
 * Jotai state management for Real-Time Monitor feature, handling metric selection,
 * alert filtering, and real-time status tracking.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const [monitoringState, setMonitoringState] = useAtom(monitoringStateAtom);
 * setMonitoringState(prev => ({ ...prev, selectedMetric: 'cpu_usage' }));
 * ```
 *
 * @doc.type service
 * @doc.purpose Real-Time Monitor state management
 * @doc.layer product
 * @doc.pattern State Management
 */

import { atom } from 'jotai';

export interface MonitoringState {
    selectedMetric: string | null;
    alertFilter: string;
    lastUpdate: Date | null;
    notification: {
        type: 'success' | 'error' | 'info';
        message: string;
    } | null;
    isStreamingActive: boolean;
    reconnectAttempts: number;
}

const initialMonitoringState: MonitoringState = {
    selectedMetric: null,
    alertFilter: 'all',
    lastUpdate: null,
    notification: null,
    isStreamingActive: false,
    reconnectAttempts: 0,
};

/**
 * Base monitoring state atom.
 * Provides core state for metric selection and alert management.
 */
export const monitoringStateAtom = atom<MonitoringState>(initialMonitoringState);

/**
 * Derived atom - Get selected metric name.
 */
export const selectedMetricAtom = atom((get) => get(monitoringStateAtom).selectedMetric);

/**
 * Derived atom - Get current alert filter.
 */
export const alertFilterAtom = atom((get) => get(monitoringStateAtom).alertFilter);

/**
 * Derived atom - Get streaming status.
 */
export const isStreamingActiveAtom = atom((get) => get(monitoringStateAtom).isStreamingActive);

/**
 * Derived atom - Get current notification.
 */
export const monitoringNotificationAtom = atom(
    (get) => get(monitoringStateAtom).notification
);

/**
 * Derived atom - Get last update timestamp.
 */
export const lastUpdateAtom = atom((get) => get(monitoringStateAtom).lastUpdate);

/**
 * Action atom - Select metric for detailed view.
 */
export const selectMetricAtom = atom(null, (_, set, metricName: string) => {
    set(monitoringStateAtom, (prev) => ({
        ...prev,
        selectedMetric: metricName,
    }));
});

/**
 * Action atom - Change alert filter.
 */
export const setAlertFilterAtom = atom(null, (_, set, filter: string) => {
    set(monitoringStateAtom, (prev) => ({
        ...prev,
        alertFilter: filter,
    }));
});

/**
 * Action atom - Update last update timestamp.
 */
export const updateLastUpdateAtom = atom(null, (_, set, timestamp: Date) => {
    set(monitoringStateAtom, (prev) => ({
        ...prev,
        lastUpdate: timestamp,
    }));
});

/**
 * Action atom - Set streaming active status.
 */
export const setStreamingActiveAtom = atom(null, (_, set, isActive: boolean) => {
    set(monitoringStateAtom, (prev) => ({
        ...prev,
        isStreamingActive: isActive,
    }));
});

/**
 * Action atom - Increment reconnect attempts.
 */
export const incrementReconnectAttemptsAtom = atom(null, (_, set) => {
    set(monitoringStateAtom, (prev) => ({
        ...prev,
        reconnectAttempts: prev.reconnectAttempts + 1,
    }));
});

/**
 * Action atom - Reset reconnect attempts.
 */
export const resetReconnectAttemptsAtom = atom(null, (_, set) => {
    set(monitoringStateAtom, (prev) => ({
        ...prev,
        reconnectAttempts: 0,
    }));
});

/**
 * Action atom - Show notification.
 */
export const showMonitoringNotificationAtom = atom(
    null,
    (
        _,
        set,
        notification: { type: 'success' | 'error' | 'info'; message: string }
    ) => {
        set(monitoringStateAtom, (prev) => ({
            ...prev,
            notification,
        }));
    }
);

/**
 * Action atom - Clear notification.
 */
export const clearMonitoringNotificationAtom = atom(null, (_, set) => {
    set(monitoringStateAtom, (prev) => ({
        ...prev,
        notification: null,
    }));
});

/**
 * Action atom - Reset all monitoring state.
 */
export const resetMonitoringStateAtom = atom(null, (_, set) => {
    set(monitoringStateAtom, initialMonitoringState);
});

export default {
    monitoringStateAtom,
    selectedMetricAtom,
    alertFilterAtom,
    isStreamingActiveAtom,
    monitoringNotificationAtom,
    lastUpdateAtom,
    selectMetricAtom,
    setAlertFilterAtom,
    updateLastUpdateAtom,
    setStreamingActiveAtom,
    incrementReconnectAttemptsAtom,
    resetReconnectAttemptsAtom,
    showMonitoringNotificationAtom,
    clearMonitoringNotificationAtom,
    resetMonitoringStateAtom,
};
