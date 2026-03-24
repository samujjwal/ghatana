/**
 * @doc.type hook
 * @doc.purpose Service health monitoring with live metrics integration for Journey 13.1 (SRE - Real-Time Incident Response)
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useEffect, useMemo } from 'react';
import { useReactFlow } from '@xyflow/react';
import type { Node, Edge } from '@xyflow/react';

/**
 * Health status levels
 */
export type HealthStatus = 'healthy' | 'degraded' | 'critical' | 'unknown';

/**
 * Service metric types
 */
export type MetricType = 'latency' | 'error_rate' | 'throughput' | 'cpu' | 'memory' | 'disk' | 'connections';

/**
 * Individual metric data point
 */
export interface Metric {
    type: MetricType;
    value: number;
    unit: string;
    threshold: {
        warning: number;
        critical: number;
    };
    timestamp: number;
}

/**
 * Service health data
 */
export interface ServiceHealthData {
    nodeId: string;
    serviceName: string;
    status: HealthStatus;
    metrics: Metric[];
    uptime: number; // seconds
    lastCheck: number; // timestamp
    dependencies: string[]; // node IDs
    alerts: Alert[];
}

/**
 * Alert definition
 */
export interface Alert {
    id: string;
    severity: 'info' | 'warning' | 'critical';
    message: string;
    timestamp: number;
    metricType?: MetricType;
    acknowledged: boolean;
}

/**
 * SLO (Service Level Objective) definition
 */
export interface SLO {
    id: string;
    name: string;
    target: number; // percentage (e.g., 99.9)
    current: number; // current achievement
    metricType: MetricType;
    timeWindow: '1h' | '24h' | '7d' | '30d';
    breached: boolean;
}

/**
 * Circuit breaker configuration
 */
export interface CircuitBreaker {
    nodeId: string;
    enabled: boolean;
    threshold: number; // failure threshold
    timeout: number; // seconds
    fallbackStrategy: 'cache' | 'default' | 'fail-fast';
}

/**
 * Prometheus query configuration
 */
export interface PrometheusConfig {
    endpoint: string;
    queries: Record<MetricType, string>;
    refreshInterval: number; // milliseconds
}

/**
 * Hook options
 */
export interface UseServiceHealthOptions {
    prometheusConfig?: PrometheusConfig;
    autoRefresh?: boolean;
    refreshInterval?: number; // milliseconds, default: 30000 (30s)
    enableAlerts?: boolean;
    sloTargets?: Record<string, number>; // nodeId -> target percentage
}

/**
 * Hook return value
 */
export interface UseServiceHealthResult {
    // Health data
    healthData: Map<string, ServiceHealthData>;
    getNodeHealth: (nodeId: string) => ServiceHealthData | null;
    overallHealth: HealthStatus;

    // Metrics
    refreshMetrics: (nodeId?: string) => Promise<void>;
    isRefreshing: boolean;
    lastRefresh: number | null;

    // Alerts
    alerts: Alert[];
    acknowledgeAlert: (alertId: string) => void;
    clearAlert: (alertId: string) => void;
    unacknowledgedCount: number;

    // SLOs
    slos: SLO[];
    addSLO: (slo: Omit<SLO, 'id' | 'current' | 'breached'>) => void;
    removeSLO: (sloId: string) => void;

    // Circuit Breakers
    circuitBreakers: Map<string, CircuitBreaker>;
    enableCircuitBreaker: (nodeId: string, config: Omit<CircuitBreaker, 'nodeId' | 'enabled'>) => void;
    disableCircuitBreaker: (nodeId: string) => void;

    // Visualization
    colorNodesByHealth: () => void;
    highlightUnhealthyPath: (nodeId: string) => void;

    // Incident Management
    createIncidentReport: (nodeId: string) => Promise<string>;
    getIncidentTimeline: (nodeId: string) => Array<{ timestamp: number; event: string }>;
}

/**
 * Mock Prometheus client (in production, replace with real Prometheus API)
 */
class MockPrometheusClient {
    private config: PrometheusConfig;

    constructor(config: PrometheusConfig) {
        this.config = config;
    }

    async query(metricType: MetricType, nodeId: string): Promise<number> {
        // Simulate API latency
        await new Promise(resolve => setTimeout(resolve, 100));

        // Generate realistic mock data based on metric type
        switch (metricType) {
            case 'latency':
                return Math.random() * 1000; // 0-1000ms
            case 'error_rate':
                return Math.random() * 5; // 0-5%
            case 'throughput':
                return Math.random() * 10000; // 0-10k req/s
            case 'cpu':
                return Math.random() * 100; // 0-100%
            case 'memory':
                return Math.random() * 100; // 0-100%
            case 'disk':
                return Math.random() * 100; // 0-100%
            case 'connections':
                return Math.random() * 1000; // 0-1000 connections
            default:
                return 0;
        }
    }
}

/**
 * Determine health status from metrics
 */
function calculateHealthStatus(metrics: Metric[]): HealthStatus {
    if (metrics.length === 0) return 'unknown';

    let criticalCount = 0;
    let warningCount = 0;

    for (const metric of metrics) {
        if (metric.value >= metric.threshold.critical) {
            criticalCount++;
        } else if (metric.value >= metric.threshold.warning) {
            warningCount++;
        }
    }

    if (criticalCount > 0) return 'critical';
    if (warningCount > 0) return 'degraded';
    return 'healthy';
}

/**
 * Generate alerts from metrics
 */
function generateAlerts(nodeId: string, serviceName: string, metrics: Metric[]): Alert[] {
    const alerts: Alert[] = [];

    for (const metric of metrics) {
        if (metric.value >= metric.threshold.critical) {
            alerts.push({
                id: `${nodeId}-${metric.type}-critical-${Date.now()}`,
                severity: 'critical',
                message: `${serviceName}: ${metric.type} is critical (${metric.value.toFixed(2)}${metric.unit} > ${metric.threshold.critical}${metric.unit})`,
                timestamp: Date.now(),
                metricType: metric.type,
                acknowledged: false,
            });
        } else if (metric.value >= metric.threshold.warning) {
            alerts.push({
                id: `${nodeId}-${metric.type}-warning-${Date.now()}`,
                severity: 'warning',
                message: `${serviceName}: ${metric.type} is degraded (${metric.value.toFixed(2)}${metric.unit} > ${metric.threshold.warning}${metric.unit})`,
                timestamp: Date.now(),
                metricType: metric.type,
                acknowledged: false,
            });
        }
    }

    return alerts;
}

/**
 * Service Health Monitoring Hook
 * 
 * Provides real-time health monitoring for service nodes with Prometheus integration,
 * alert management, SLO tracking, and circuit breaker controls.
 * 
 * @example
 * ```tsx
 * const {
 *   healthData,
 *   overallHealth,
 *   alerts,
 *   acknowledgeAlert,
 *   enableCircuitBreaker,
 *   colorNodesByHealth,
 * } = useServiceHealth({
 *   autoRefresh: true,
 *   refreshInterval: 30000,
 *   enableAlerts: true,
 * });
 * ```
 */
export function useServiceHealth(options: UseServiceHealthOptions = {}): UseServiceHealthResult {
    const {
        prometheusConfig = {
            endpoint: 'http://localhost:9090',
            queries: {
                latency: 'http_request_duration_seconds',
                error_rate: 'http_request_errors_total',
                throughput: 'http_requests_total',
                cpu: 'container_cpu_usage_seconds_total',
                memory: 'container_memory_usage_bytes',
                disk: 'node_filesystem_usage_bytes',
                connections: 'tcp_connections_total',
            },
            refreshInterval: 30000,
        },
        autoRefresh = true,
        refreshInterval = 30000,
        enableAlerts = true,
        sloTargets = {},
    } = options;

    const { getNodes, getEdges, setNodes } = useReactFlow();
    const [healthData, setHealthData] = useState<Map<string, ServiceHealthData>>(new Map());
    const [alerts, setAlerts] = useState<Alert[]>([]);
    const [slos, setSlos] = useState<SLO[]>([]);
    const [circuitBreakers, setCircuitBreakers] = useState<Map<string, CircuitBreaker>>(new Map());
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [lastRefresh, setLastRefresh] = useState<number | null>(null);
    const [prometheusClient] = useState(() => new MockPrometheusClient(prometheusConfig));

    // Get service nodes (type: 'service', 'api', 'database')
    const serviceNodes = useMemo(() => {
        return getNodes().filter(node =>
            ['service', 'api', 'database', 'queue', 'cache'].includes(node.type || '')
        );
    }, [getNodes]);

    // Refresh metrics for a single node or all nodes
    const refreshMetrics = useCallback(async (nodeId?: string) => {
        setIsRefreshing(true);

        try {
            const nodesToRefresh = nodeId
                ? serviceNodes.filter(n => n.id === nodeId)
                : serviceNodes;

            const newHealthData = new Map(healthData);
            const newAlerts: Alert[] = [];

            for (const node of nodesToRefresh) {
                const serviceName = node.data.label || node.data.name || node.id;

                // Fetch metrics from Prometheus
                const metrics: Metric[] = await Promise.all([
                    prometheusClient.query('latency', node.id).then(value => ({
                        type: 'latency' as MetricType,
                        value,
                        unit: 'ms',
                        threshold: { warning: 300, critical: 500 },
                        timestamp: Date.now(),
                    })),
                    prometheusClient.query('error_rate', node.id).then(value => ({
                        type: 'error_rate' as MetricType,
                        value,
                        unit: '%',
                        threshold: { warning: 1, critical: 5 },
                        timestamp: Date.now(),
                    })),
                    prometheusClient.query('cpu', node.id).then(value => ({
                        type: 'cpu' as MetricType,
                        value,
                        unit: '%',
                        threshold: { warning: 70, critical: 90 },
                        timestamp: Date.now(),
                    })),
                    prometheusClient.query('memory', node.id).then(value => ({
                        type: 'memory' as MetricType,
                        value,
                        unit: '%',
                        threshold: { warning: 75, critical: 90 },
                        timestamp: Date.now(),
                    })),
                ]);

                // Calculate health status
                const status = calculateHealthStatus(metrics);

                // Generate alerts
                if (enableAlerts) {
                    const nodeAlerts = generateAlerts(node.id, serviceName, metrics);
                    newAlerts.push(...nodeAlerts);
                }

                // Find dependencies (connected nodes)
                const edges = getEdges();
                const dependencies = edges
                    .filter(e => e.source === node.id)
                    .map(e => e.target);

                // Get existing health data or create new
                const existingData = newHealthData.get(node.id);
                const uptime = existingData?.uptime ?? 0;

                newHealthData.set(node.id, {
                    nodeId: node.id,
                    serviceName,
                    status,
                    metrics,
                    uptime: uptime + refreshInterval / 1000,
                    lastCheck: Date.now(),
                    dependencies,
                    alerts: existingData?.alerts ?? [],
                });
            }

            setHealthData(newHealthData);

            if (enableAlerts && newAlerts.length > 0) {
                setAlerts(prev => [...prev, ...newAlerts]);
            }

            setLastRefresh(Date.now());
        } catch (error) {
            console.error('Failed to refresh metrics:', error);
        } finally {
            setIsRefreshing(false);
        }
    }, [serviceNodes, healthData, enableAlerts, refreshInterval, prometheusClient, getEdges]);

    // Auto-refresh metrics
    useEffect(() => {
        if (autoRefresh && serviceNodes.length > 0) {
            refreshMetrics();
            const interval = setInterval(() => refreshMetrics(), refreshInterval);
            return () => clearInterval(interval);
        }
    }, [autoRefresh, refreshInterval, serviceNodes.length]); // Intentionally minimal deps

    // Calculate overall health
    const overallHealth = useMemo<HealthStatus>(() => {
        if (healthData.size === 0) return 'unknown';

        let criticalCount = 0;
        let degradedCount = 0;

        for (const data of healthData.values()) {
            if (data.status === 'critical') criticalCount++;
            else if (data.status === 'degraded') degradedCount++;
        }

        if (criticalCount > 0) return 'critical';
        if (degradedCount > 0) return 'degraded';
        return 'healthy';
    }, [healthData]);

    // Get health data for specific node
    const getNodeHealth = useCallback((nodeId: string): ServiceHealthData | null => {
        return healthData.get(nodeId) ?? null;
    }, [healthData]);

    // Alert management
    const acknowledgeAlert = useCallback((alertId: string) => {
        setAlerts(prev =>
            prev.map(alert =>
                alert.id === alertId ? { ...alert, acknowledged: true } : alert
            )
        );
    }, []);

    const clearAlert = useCallback((alertId: string) => {
        setAlerts(prev => prev.filter(alert => alert.id !== alertId));
    }, []);

    const unacknowledgedCount = useMemo(() => {
        return alerts.filter(a => !a.acknowledged).length;
    }, [alerts]);

    // SLO management
    const addSLO = useCallback((slo: Omit<SLO, 'id' | 'current' | 'breached'>) => {
        const newSLO: SLO = {
            ...slo,
            id: `slo-${Date.now()}`,
            current: 100, // Start at 100%
            breached: false,
        };
        setSlos(prev => [...prev, newSLO]);
    }, []);

    const removeSLO = useCallback((sloId: string) => {
        setSlos(prev => prev.filter(s => s.id !== sloId));
    }, []);

    // Circuit breaker management
    const enableCircuitBreaker = useCallback((
        nodeId: string,
        config: Omit<CircuitBreaker, 'nodeId' | 'enabled'>
    ) => {
        setCircuitBreakers(prev => new Map(prev).set(nodeId, {
            nodeId,
            enabled: true,
            ...config,
        }));
    }, []);

    const disableCircuitBreaker = useCallback((nodeId: string) => {
        setCircuitBreakers(prev => {
            const newMap = new Map(prev);
            newMap.delete(nodeId);
            return newMap;
        });
    }, []);

    // Color nodes by health status
    const colorNodesByHealth = useCallback(() => {
        const nodes = getNodes();
        const updatedNodes = nodes.map(node => {
            const health = healthData.get(node.id);
            if (!health) return node;

            const colorMap: Record<HealthStatus, string> = {
                healthy: '#4caf50', // green
                degraded: '#ff9800', // orange
                critical: '#f44336', // red
                unknown: '#9e9e9e', // gray
            };

            return {
                ...node,
                style: {
                    ...node.style,
                    borderColor: colorMap[health.status],
                    borderWidth: 3,
                },
            };
        });

        setNodes(updatedNodes);
    }, [getNodes, setNodes, healthData]);

    // Highlight unhealthy dependency path
    const highlightUnhealthyPath = useCallback((nodeId: string) => {
        const nodes = getNodes();
        const edges = getEdges();
        const health = healthData.get(nodeId);

        if (!health || health.status === 'healthy') return;

        // Find all dependencies (DFS)
        const visited = new Set<string>();
        const unhealthyPath: string[] = [];

        function dfs(currentNodeId: string) {
            if (visited.has(currentNodeId)) return;
            visited.add(currentNodeId);

            const currentHealth = healthData.get(currentNodeId);
            if (currentHealth && currentHealth.status !== 'healthy') {
                unhealthyPath.push(currentNodeId);
            }

            const dependencies = edges
                .filter(e => e.source === currentNodeId)
                .map(e => e.target);

            dependencies.forEach(dfs);
        }

        dfs(nodeId);

        // Highlight nodes in path
        const updatedNodes = nodes.map(node => ({
            ...node,
            style: {
                ...node.style,
                opacity: unhealthyPath.includes(node.id) ? 1 : 0.3,
            },
        }));

        setNodes(updatedNodes);
    }, [getNodes, getEdges, setNodes, healthData]);

    // Create incident report (AI-generated)
    const createIncidentReport = useCallback(async (nodeId: string): Promise<string> => {
        const health = healthData.get(nodeId);
        if (!health) return '';

        // Simulate AI generation
        await new Promise(resolve => setTimeout(resolve, 1000));

        const timeline = getIncidentTimeline(nodeId);
        const timelineStr = timeline.map(t =>
            `- ${new Date(t.timestamp).toISOString()}: ${t.event}`
        ).join('\n');

        return `
# Incident Report: ${health.serviceName}

## Summary
Service: ${health.serviceName}
Status: ${health.status.toUpperCase()}
Detection Time: ${new Date(health.lastCheck).toISOString()}

## Metrics at Incident
${health.metrics.map(m =>
            `- ${m.type}: ${m.value.toFixed(2)}${m.unit} (threshold: ${m.threshold.warning}/${m.threshold.critical})`
        ).join('\n')}

## Timeline
${timelineStr}

## Root Cause Analysis
[AI-generated analysis would appear here]

## Remediation Steps
1. Investigate metric: ${health.metrics[0].type}
2. Check dependencies: ${health.dependencies.join(', ')}
3. Consider circuit breaker activation
4. Review recent deployments

## Prevention
- Add monitoring for early detection
- Implement circuit breaker pattern
- Review SLO targets
    `.trim();
    }, [healthData]);

    // Get incident timeline for a node
    const getIncidentTimeline = useCallback((nodeId: string): Array<{ timestamp: number; event: string }> => {
        const health = healthData.get(nodeId);
        if (!health) return [];

        const timeline: Array<{ timestamp: number; event: string }> = [];

        // Add metric threshold breaches
        for (const metric of health.metrics) {
            if (metric.value >= metric.threshold.critical) {
                timeline.push({
                    timestamp: metric.timestamp,
                    event: `${metric.type} exceeded critical threshold (${metric.value.toFixed(2)}${metric.unit})`,
                });
            } else if (metric.value >= metric.threshold.warning) {
                timeline.push({
                    timestamp: metric.timestamp,
                    event: `${metric.type} exceeded warning threshold (${metric.value.toFixed(2)}${metric.unit})`,
                });
            }
        }

        // Add circuit breaker events
        const breaker = circuitBreakers.get(nodeId);
        if (breaker) {
            timeline.push({
                timestamp: Date.now(),
                event: `Circuit breaker ${breaker.enabled ? 'enabled' : 'disabled'}`,
            });
        }

        return timeline.sort((a, b) => a.timestamp - b.timestamp);
    }, [healthData, circuitBreakers]);

    return {
        healthData,
        getNodeHealth,
        overallHealth,
        refreshMetrics,
        isRefreshing,
        lastRefresh,
        alerts,
        acknowledgeAlert,
        clearAlert,
        unacknowledgedCount,
        slos,
        addSLO,
        removeSLO,
        circuitBreakers,
        enableCircuitBreaker,
        disableCircuitBreaker,
        colorNodesByHealth,
        highlightUnhealthyPath,
        createIncidentReport,
        getIncidentTimeline,
    };
}
