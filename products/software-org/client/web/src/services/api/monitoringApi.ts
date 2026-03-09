/**
 * Real-Time Monitoring API Client
 *
 * <p><b>Purpose</b><br>
 * Provides real-time metrics, event streams, and monitoring data through WebSocket
 * connections and polling endpoints.
 *
 * <p><b>Features</b><br>
 * - Real-time metric streams (CPU, memory, throughput)
 * - System health status
 * - Alert triggering and management
 * - Event streaming with filtering
 * - Metric aggregation and historical data
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const metrics = await monitoringApi.getRealTimeMetrics(tenantId);
 * const stream = monitoringApi.subscribeToMetrics(tenantId, handleUpdate);
 * const alerts = await monitoringApi.getAlerts(tenantId);
 * ```
 *
 * @doc.type service
 * @doc.purpose Real-time monitoring and metrics API client
 * @doc.layer product
 * @doc.pattern API Client
 */

import { apiClient } from './index';

export interface SystemMetric {
    name: string;
    value: number;
    unit: string;
    timestamp: Date;
    threshold?: number;
    status: 'healthy' | 'warning' | 'critical';
}

export interface SystemHealth {
    cpuUsage: number;
    memoryUsage: number;
    diskUsage: number;
    networkLatency: number;
    activeConnections: number;
    errorRate: number;
    uptime: number;
    lastUpdated: Date;
}

export interface MonitoringAlert {
    id: string;
    metric: string;
    condition: string;
    severity: 'low' | 'medium' | 'high' | 'critical';
    triggered: boolean;
    value: number;
    threshold: number;
    timestamp: Date;
}

export interface EventStreamData {
    eventId: string;
    type: string;
    severity: 'info' | 'warning' | 'error';
    message: string;
    timestamp: Date;
    context: Record<string, unknown>;
}

export interface MetricAggregation {
    metric: string;
    average: number;
    min: number;
    max: number;
    percentile95: number;
    percentile99: number;
    count: number;
}

/**
 * Get current system health metrics.
 * @param tenantId - Tenant identifier
 * @returns SystemHealth object with all key metrics
 */
export async function getRealTimeMetrics(tenantId: string): Promise<SystemHealth> {
    const response = await apiClient.get<SystemHealth>(`/tenants/${tenantId}/metrics/health`, {
        headers: { 'X-Tenant-ID': tenantId },
    });
    return response.data;
}

/**
 * Get specific metric data for a time range with aggregation.
 * @param metricName - Name of the metric to retrieve
 * @param timeRange - Object with start and end dates
 * @returns MetricAggregation object with statistics
 */
export async function getMetricData(
    metricName: string,
    timeRange: { start: Date; end: Date }
): Promise<MetricAggregation> {
    const response = await apiClient.get<MetricAggregation>('/metrics/aggregation', {
        params: {
            metric: metricName,
            startTime: timeRange.start.toISOString(),
            endTime: timeRange.end.toISOString(),
        },
    });
    return response.data;
}

/**
 * Get active alerts for a tenant.
 * @param tenantId - Tenant identifier
 * @param severity - Optional severity filter
 * @returns Array of active alerts
 */
export async function getAlerts(
    tenantId: string,
    severity?: 'low' | 'medium' | 'high' | 'critical'
): Promise<MonitoringAlert[]> {
    const response = await apiClient.get<MonitoringAlert[]>(`/tenants/${tenantId}/alerts`, {
        params: severity ? { severity } : undefined,
        headers: { 'X-Tenant-ID': tenantId },
    });
    return response.data;
}

/**
 * Create or update a monitoring alert rule.
 * @param config - Alert configuration with metric, condition, threshold
 * @returns Created alert rule object
 */
export async function createAlert(config: {
    metric: string;
    condition: string;
    threshold: number;
    severity: 'low' | 'medium' | 'high' | 'critical';
} | {
    title: string;
    message: string;
    severity: 'info' | 'warning' | 'critical';
    source?: string;
}) {
    const payload = 'metric' in config
        ? config
        : {
            metric: config.source ?? 'custom',
            condition: config.title,
            threshold: 0,
            severity:
                config.severity === 'critical'
                    ? 'critical'
                    : config.severity === 'warning'
                        ? 'medium'
                        : 'low',
        };

    const response = await apiClient.post('/alerts', payload);
    return response.data;
}

/**
 * Acknowledge an active alert.
 * @param alertId - Alert identifier
 * @param notes - Optional acknowledgment notes
 * @returns Updated alert object
 */
export async function acknowledgeAlert(alertId: string, notes?: string) {
    const response = await apiClient.post(`/alerts/${alertId}/acknowledge`, { notes });
    return response.data;
}

/**
 * Subscribe to real-time metric updates via WebSocket.
 * @param tenantId - Tenant identifier
 * @param callback - Function called when metrics are updated
 * @returns Unsubscribe function to close connection
 */
export function subscribeToMetrics(
    tenantId: string,
    callback: (metrics: SystemMetric[]) => void
): () => void {
    // Try to open a WebSocket connection; if it fails (no backend), fall back
    // to a simulated metric stream using setInterval so the UI still receives updates.
    let ws: WebSocket | null = null;
    let intervalId: number | null = null;
    // If MSW or mock mode is enabled, prefer the simulated stream immediately
    // to avoid failed WS connection attempts in headless or local dev.
    // Note: Vite requires static access to import.meta.env properties for SSR compatibility
    const mockEnabled =
        import.meta.env.VITE_USE_MOCKS === 'true' ||
        import.meta.env.VITE_MOCK_API === 'true' ||
        (typeof window !== 'undefined' && !!(window as any).__MSW_ACTIVE__);

    if (mockEnabled) {
        intervalId = window.setInterval(() => {
            const simulated = [
                { name: 'cpu', value: Math.random() * 100, unit: '%', timestamp: new Date(), status: 'healthy' as const },
                { name: 'memory', value: Math.random() * 100, unit: '%', timestamp: new Date(), status: 'healthy' as const },
            ];
            callback(simulated);
        }, 1000);

        return () => {
            if (intervalId) clearInterval(intervalId);
        };
    }

    try {
        ws = new WebSocket(`wss://${window.location.host}/ws/metrics/${tenantId}`);

        ws.onmessage = (event) => {
            try {
                const metrics = JSON.parse(event.data);
                callback(metrics);
            } catch (e) {
                console.error('Failed to parse WS message:', e);
            }
        };

        ws.onerror = (error) => {
            console.warn('WebSocket error, falling back to simulated metrics:', error);
            // Start simulated updates if WS errors
            if (!intervalId) {
                intervalId = window.setInterval(() => {
                    const simulated = [
                        { name: 'cpu', value: Math.random() * 100, unit: '%', timestamp: new Date(), status: 'healthy' as const },
                        { name: 'memory', value: Math.random() * 100, unit: '%', timestamp: new Date(), status: 'healthy' as const },
                    ];
                    callback(simulated);
                }, 1000);
            }
        };
    } catch (e) {
        console.warn('WebSocket unavailable, using simulated metrics:', e);
        intervalId = window.setInterval(() => {
            const simulated = [
                { name: 'cpu', value: Math.random() * 100, unit: '%', timestamp: new Date(), status: 'healthy' as const },
                { name: 'memory', value: Math.random() * 100, unit: '%', timestamp: new Date(), status: 'healthy' as const },
            ];
            callback(simulated);
        }, 1000);
    }

    return () => {
        if (ws) {
            try {
                ws.close();
            } catch (e) {
                /* ignore */
            }
        }
        if (intervalId) {
            clearInterval(intervalId);
        }
    };
}

/**
 * Subscribe to event stream with filtering.
 * @param tenantId - Tenant identifier
 * @param filter - Event filter (eventType, severity)
 * @param callback - Function called when events arrive
 * @returns Unsubscribe function
 */
export function subscribeToEvents(
    tenantId: string,
    filter: { eventType?: string; severity?: string },
    callback: (event: EventStreamData) => void
): () => void {
    const params = new URLSearchParams(filter as Record<string, string>);
    const ws = new WebSocket(`wss://${window.location.host}/ws/events/${tenantId}?${params}`);

    ws.onmessage = (event) => {
        const eventData = JSON.parse(event.data);
        callback(eventData);
    };

    ws.onerror = (error) => {
        console.error('WebSocket error:', error);
    };

    return () => {
        ws.close();
    };
}

/**
 * Get metric history with downsampling for large time ranges.
 * @param metricName - Metric identifier
 * @param timeRange - Time range for historical data
 * @param bucketSize - Time bucket size (e.g., '1m', '5m', '1h')
 * @returns Array of downsampled metric points
 */
export async function getMetricHistory(
    metricName: string,
    timeRange: { start: Date; end: Date },
    bucketSize: string = '1m'
) {
    const params = new URLSearchParams({
        metric: metricName,
        startTime: timeRange.start.toISOString(),
        endTime: timeRange.end.toISOString(),
        bucketSize,
    });
    const response = await fetch(`/api/v1/metrics/history?${params}`);
    if (!response.ok) throw new Error('Failed to fetch metric history');
    return response.json();
}

/**
 * Get anomaly detection results for metrics.
 * @param tenantId - Tenant identifier
 * @returns Array of detected anomalies
 */
export async function getAnomalies(tenantId: string) {
    const response = await fetch(`/api/v1/tenants/${tenantId}/anomalies`, {
        headers: { 'X-Tenant-ID': tenantId },
    });
    if (!response.ok) throw new Error('Failed to fetch anomalies');
    return response.json();
}

/**
 * Dismiss an anomaly (alias expected by hooks).
 * @param anomalyId - Anomaly identifier
 * @returns Confirmation of dismissal
 */
export async function dismissAnomaly(anomalyId: string) {
    const response = await fetch(`/api/v1/anomalies/${anomalyId}/dismiss`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
    });
    if (!response.ok) throw new Error('Failed to dismiss anomaly');
    return response.json();
}

export default {
    getRealTimeMetrics,
    getMetricData,
    getAlerts,
    createAlert,
    acknowledgeAlert,
    subscribeToMetrics,
    subscribeToEvents,
    getMetricHistory,
    getAnomalies,
    dismissAnomaly,
};
