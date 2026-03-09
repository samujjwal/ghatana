import { useState } from 'react';
import { Badge } from '@/components/ui';
import { AiHintBanner, GlobalFilterBar, ContextualHints } from '@/shared/components';

/**
 * Real-Time Monitor
 *
 * <p><b>Purpose</b><br>
 * NOC-style live view of infrastructure and services showing streaming metrics,
 * anomalies, and alerts for rapid incident detection and response.
 *
 * <p><b>Features</b><br>
 * - Live system health metrics (CPU, Memory, Disk, Uptime)
 * - Real-time metrics visualization with time-series data
 * - Anomaly detection and display
 * - Alert management with severity filtering
 * - Acknowledge/dismiss actions for alerts
 * - Live connection status indicator
 * - Metric selector for detailed viewing
 *
 * <p><b>Specs</b><br>
 * See web-page-specs/15_real_time_monitor.md for complete specification.
 *
 * <p><b>Mock Data</b><br>
 * All data is currently mocked. Integrate with WebSocket API at `/api/v1/metrics`
 * for live streaming. Replace mock data with real-time data from backend.
 *
 * @doc.type component
 * @doc.purpose Real-time infrastructure and service monitoring
 * @doc.layer product
 * @doc.pattern Page
 */

interface SystemMetric {
    name: string;
    value: number;
    threshold: number;
    unit: string;
    status: 'healthy' | 'warning' | 'critical';
}

interface Anomaly {
    id: string;
    service: string;
    type: string;
    description: string;
    severity: 'info' | 'warning' | 'critical';
    timestamp: string;
}

interface Alert {
    id: string;
    title: string;
    message: string;
    severity: 'info' | 'warning' | 'critical';
    timestamp: string;
    acknowledged: boolean;
}

export function RealTimeMonitor() {
    const [isLive] = useState(true);
    const [selectedMetric, setSelectedMetric] = useState('cpu');
    const [alertFilter, setAlertFilter] = useState<'all' | 'critical' | 'warning' | 'info'>('all');
    const [alerts, setAlerts] = useState<Alert[]>([
        {
            id: 'a-1',
            title: 'High CPU Usage',
            message: 'CPU usage on prod-server-03 reached 94%',
            severity: 'critical',
            timestamp: new Date().toISOString(),
            acknowledged: false,
        },
        {
            id: 'a-2',
            title: 'Memory Pressure',
            message: 'Memory utilization at 87% on payment-service-02',
            severity: 'warning',
            timestamp: new Date(Date.now() - 300000).toISOString(),
            acknowledged: false,
        },
        {
            id: 'a-3',
            title: 'Disk Space Low',
            message: 'Disk usage on storage-01 reached 92%',
            severity: 'critical',
            timestamp: new Date(Date.now() - 600000).toISOString(),
            acknowledged: true,
        },
    ]);

    const systemHealth: SystemMetric[] = [
        { name: 'CPU Usage', value: 68, threshold: 80, unit: '%', status: 'healthy' },
        { name: 'Memory Usage', value: 72, threshold: 85, unit: '%', status: 'healthy' },
        { name: 'Disk Usage', value: 58, threshold: 90, unit: '%', status: 'healthy' },
        { name: 'Uptime', value: 99.98, threshold: 99.9, unit: '%', status: 'healthy' },
    ];

    const anomalies: Anomaly[] = [
        {
            id: 'an-1',
            service: 'payment-processor',
            type: 'Latency Spike',
            description: 'P95 latency increased from 120ms to 280ms',
            severity: 'warning',
            timestamp: new Date().toISOString(),
        },
        {
            id: 'an-2',
            service: 'api-gateway',
            type: 'Error Rate Increase',
            description: '5xx errors increased from 0.2% to 1.8%',
            severity: 'critical',
            timestamp: new Date(Date.now() - 180000).toISOString(),
        },
    ];

    const filteredAlerts = alertFilter === 'all'
        ? alerts
        : alerts.filter(a => a.severity === alertFilter);

    const handleAcknowledge = (alertId: string) => {
        setAlerts(alerts.map(a =>
            a.id === alertId ? { ...a, acknowledged: true } : a
        ));
    };

    const getSeverityColor = (severity: string) => {
        if (severity === 'critical') return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-rose-400';
        if (severity === 'warning') return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400';
        return 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-indigo-400';
    };

    const getStatusColor = (status: string) => {
        if (status === 'critical') return 'text-red-600 dark:text-rose-400';
        if (status === 'warning') return 'text-yellow-600 dark:text-yellow-400';
        return 'text-green-600 dark:text-green-400';
    };

    const [showSreHint, setShowSreHint] = useState(() => {
        if (typeof window === 'undefined') {
            return true;
        }
        const stored = window.localStorage.getItem('softwareOrg.monitoring.sreHint.dismissed');
        return stored !== 'true';
    });

    return (
        <div className="space-y-6">
            {/* Global Filter Bar */}
            <GlobalFilterBar
                showEnvironmentFilter
                showTimeRangeFilter
                showTenantFilter
                showCompareMode={false}
                compact
            />

            {/* Contextual Navigation Hints */}
            <ContextualHints context="monitoring" personaId="sre" size="sm" />

            {/* Header */}
            <div className="flex items-start justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Real-Time Monitor</h1>
                    <p className="text-slate-600 dark:text-neutral-400 mt-1">Live metrics, anomalies and alerts for your services</p>
                </div>
                <div className="flex items-center gap-2">
                    <div className={`flex items-center gap-2 px-3 py-1 rounded-full text-sm font-medium ${isLive
                        ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400'
                        : 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-rose-400'
                        }`}>
                        <span className={`w-2 h-2 rounded-full ${isLive ? 'bg-green-600' : 'bg-red-600'} animate-pulse`}></span>
                        {isLive ? '● Live' : 'Reconnecting...'}
                    </div>
                </div>
            </div>

            {/* SRE DevSecOps hint */}
            {showSreHint && (
                <AiHintBanner
                    icon="📟"
                    title="AI-style SRE guidance"
                    body="When you see critical alerts or anomalies here, stabilize the service first, then capture follow-up work on the DevSecOps board using the SRE persona view so incidents turn into tracked improvements."
                    ctaLabel="Open SRE view in DevSecOps board"
                    ctaHref="/devsecops/board?persona=sre&status=blocked"
                    onDismiss={() => {
                        setShowSreHint(false);
                        if (typeof window !== 'undefined') {
                            window.localStorage.setItem('softwareOrg.monitoring.sreHint.dismissed', 'true');
                        }
                    }}
                />
            )}

            {/* System Health Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                {systemHealth.map((metric) => (
                    <div key={metric.name} className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-4">
                        <div className="text-sm text-slate-600 dark:text-neutral-400 mb-2">{metric.name}</div>
                        <div className={`text-3xl font-bold ${getStatusColor(metric.status)}`}>
                            {metric.value}{metric.unit}
                        </div>
                        <div className="text-xs text-slate-500 dark:text-slate-500 dark:text-neutral-400 mt-2">
                            Threshold: {metric.threshold}{metric.unit}
                        </div>
                    </div>
                ))}
            </div>

            {/* Main Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Left Column - Metrics & Anomalies */}
                <div className="lg:col-span-2 space-y-6">
                    {/* Real-Time Metrics */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-6">
                        <div className="mb-6">
                            <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">Real-Time Metrics</h2>
                            <div className="flex flex-wrap gap-2">
                                {['cpu', 'memory', 'disk', 'requests'].map(metric => (
                                    <button
                                        key={metric}
                                        onClick={() => setSelectedMetric(metric)}
                                        className={`px-3 py-1 rounded text-sm font-medium transition ${selectedMetric === metric
                                            ? 'bg-blue-600 text-white'
                                            : 'bg-slate-100 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300 hover:bg-slate-200 dark:hover:bg-slate-700'
                                            }`}
                                    >
                                        {metric.charAt(0).toUpperCase() + metric.slice(1)}
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Chart Placeholder */}
                        <div className="bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-800 dark:to-slate-900 rounded h-64 flex items-center justify-center border border-dashed border-slate-300 dark:border-neutral-600">
                            <div className="text-center">
                                <div className="text-4xl mb-2">📊</div>
                                <p className="text-slate-600 dark:text-neutral-400">Time-series chart for {selectedMetric}</p>
                                <p className="text-sm text-slate-500 dark:text-slate-500 dark:text-neutral-400 mt-1">Real data via API integration</p>
                            </div>
                        </div>
                    </div>

                    {/* Anomalies */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">Detected Anomalies</h2>
                        <div className="space-y-3">
                            {anomalies.length > 0 ? (
                                anomalies.map(anomaly => (
                                    <div key={anomaly.id} className="flex items-start gap-4 p-3 bg-slate-50 dark:bg-neutral-800 rounded border border-slate-200 dark:border-neutral-600">
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2 mb-1">
                                                <span className="font-medium text-slate-900 dark:text-neutral-100">{anomaly.service}</span>
                                                <Badge className={getSeverityColor(anomaly.severity)}>
                                                    {anomaly.type}
                                                </Badge>
                                            </div>
                                            <p className="text-sm text-slate-600 dark:text-neutral-400">{anomaly.description}</p>
                                            <p className="text-xs text-slate-500 dark:text-slate-500 dark:text-neutral-400 mt-1">
                                                {new Date(anomaly.timestamp).toLocaleTimeString()}
                                            </p>
                                        </div>
                                    </div>
                                ))
                            ) : (
                                <div className="text-center py-6 text-slate-500 dark:text-slate-500">
                                    No anomalies detected
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                {/* Right Column - Alerts */}
                <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-6 h-fit">
                    <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">Alerts</h2>

                    {/* Severity Filter */}
                    <div className="flex flex-wrap gap-2 mb-4">
                        {['all', 'critical', 'warning', 'info'].map(severity => (
                            <button
                                key={severity}
                                onClick={() => setAlertFilter(severity as any)}
                                className={`px-2 py-1 rounded text-xs font-medium transition ${alertFilter === severity
                                    ? 'bg-blue-600 text-white'
                                    : 'bg-slate-100 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300 hover:bg-slate-200 dark:hover:bg-slate-700'
                                    }`}
                            >
                                {severity.charAt(0).toUpperCase() + severity.slice(1)}
                            </button>
                        ))}
                    </div>

                    {/* Alert List */}
                    <div className="space-y-2 max-h-96 overflow-y-auto">
                        {filteredAlerts.length > 0 ? (
                            filteredAlerts.map(alert => (
                                <div
                                    key={alert.id}
                                    className={`p-3 rounded border-l-4 ${alert.acknowledged
                                        ? 'bg-slate-50 dark:bg-neutral-800 border-slate-300 dark:border-neutral-600'
                                        : alert.severity === 'critical'
                                            ? 'bg-red-50 dark:bg-rose-600/30 border-red-500'
                                            : alert.severity === 'warning'
                                                ? 'bg-yellow-50 dark:bg-orange-600/30 border-yellow-500'
                                                : 'bg-blue-50 dark:bg-indigo-600/30 border-blue-500'
                                        }`}
                                >
                                    <div className="flex items-start justify-between gap-2 mb-2">
                                        <span className={`text-sm font-medium ${alert.severity === 'critical' ? 'text-red-800 dark:text-rose-400' :
                                            alert.severity === 'warning' ? 'text-yellow-800 dark:text-yellow-400' :
                                                'text-blue-800 dark:text-indigo-400'
                                            }`}>
                                            {alert.title}
                                        </span>
                                        {!alert.acknowledged && (
                                            <button
                                                onClick={() => handleAcknowledge(alert.id)}
                                                className="text-xs px-2 py-1 bg-blue-600 hover:bg-blue-700 text-white rounded transition whitespace-nowrap"
                                            >
                                                Ack
                                            </button>
                                        )}
                                    </div>
                                    <p className="text-xs text-slate-700 dark:text-neutral-300 mb-1">{alert.message}</p>
                                    <p className="text-xs text-slate-500 dark:text-slate-500">
                                        {new Date(alert.timestamp).toLocaleTimeString()}
                                    </p>
                                </div>
                            ))
                        ) : (
                            <div className="text-center py-6 text-slate-500 dark:text-slate-500 text-sm">
                                No alerts
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}

export default RealTimeMonitor;
