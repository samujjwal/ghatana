/**
 * Health Panel Component
 *
 * Real-time health metrics, SLO status, and system health visualization.
 * Used in Deploy surface Health segment.
 *
 * @doc.type component
 * @doc.purpose OBSERVE phase health monitoring
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useState, useCallback } from 'react';
import { Heart as Favorite, CheckCircle, AlertTriangle as Warning, XCircle as Cancel, RefreshCw as Refresh, TrendingUp, TrendingDown, MoveRight as TrendingFlat, Gauge as Speed, Cpu as Memory, HardDrive as Storage, Cloud as CloudQueue } from 'lucide-react';

export interface HealthMetric {
    id: string;
    name: string;
    value: number;
    unit: string;
    status: 'healthy' | 'warning' | 'critical';
    threshold?: { warning: number; critical: number };
    trend?: 'up' | 'down' | 'stable';
    sparkline?: number[];
}

export interface SLOStatus {
    id: string;
    name: string;
    target: number;
    current: number;
    status: 'met' | 'at_risk' | 'breached';
    errorBudgetRemaining?: number;
    period: string;
}

export interface ServiceHealth {
    id: string;
    name: string;
    status: 'healthy' | 'degraded' | 'down';
    uptime: string;
    lastChecked: string;
    dependencies?: { name: string; status: 'healthy' | 'degraded' | 'down' }[];
}

export interface HealthPanelProps {
    metrics: HealthMetric[];
    slos: SLOStatus[];
    services: ServiceHealth[];
    onRefresh: () => Promise<void>;
    lastUpdated?: string;
    isPolling?: boolean;
}

const STATUS_CONFIG = {
    healthy: { icon: <CheckCircle className="w-4 h-4" />, color: 'text-green-500', bg: 'bg-green-100 dark:bg-green-900/30' },
    warning: { icon: <Warning className="w-4 h-4" />, color: 'text-yellow-500', bg: 'bg-yellow-100 dark:bg-yellow-900/30' },
    critical: { icon: <Cancel className="w-4 h-4" />, color: 'text-red-500', bg: 'bg-red-100 dark:bg-red-900/30' },
    degraded: { icon: <Warning className="w-4 h-4" />, color: 'text-yellow-500', bg: 'bg-yellow-100 dark:bg-yellow-900/30' },
    down: { icon: <Cancel className="w-4 h-4" />, color: 'text-red-500', bg: 'bg-red-100 dark:bg-red-900/30' },
    met: { icon: <CheckCircle className="w-4 h-4" />, color: 'text-green-500', bg: 'bg-green-100 dark:bg-green-900/30' },
    at_risk: { icon: <Warning className="w-4 h-4" />, color: 'text-yellow-500', bg: 'bg-yellow-100 dark:bg-yellow-900/30' },
    breached: { icon: <Cancel className="w-4 h-4" />, color: 'text-red-500', bg: 'bg-red-100 dark:bg-red-900/30' },
};

const TREND_ICONS = {
    up: <TrendingUp className="w-3 h-3" />,
    down: <TrendingDown className="w-3 h-3" />,
    stable: <TrendingFlat className="w-3 h-3" />,
};

const METRIC_ICONS: Record<string, React.ReactNode> = {
    cpu: <Speed className="w-5 h-5" />,
    memory: <Memory className="w-5 h-5" />,
    disk: <Storage className="w-5 h-5" />,
    requests: <CloudQueue className="w-5 h-5" />,
};

/**
 * Sparkline mini chart component.
 */
const Sparkline: React.FC<{ data: number[]; status: string }> = ({ data, status }) => {
    const max = Math.max(...data);
    const min = Math.min(...data);
    const range = max - min || 1;
    const height = 24;
    const width = 60;
    const points = data
        .map((v, i) => {
            const x = (i / (data.length - 1)) * width;
            const y = height - ((v - min) / range) * height;
            return `${x},${y}`;
        })
        .join(' ');

    const color = status === 'healthy' ? '#22c55e' : status === 'warning' ? '#eab308' : '#ef4444';

    return (
        <svg width={width} height={height} className="flex-shrink-0">
            <polyline
                points={points}
                fill="none"
                stroke={color}
                strokeWidth="1.5"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
        </svg>
    );
};

/**
 * Health Panel for OBSERVE phase.
 */
export const HealthPanel: React.FC<HealthPanelProps> = ({
    metrics,
    slos,
    services,
    onRefresh,
    lastUpdated,
    isPolling = false,
}) => {
    const [activeTab, setActiveTab] = useState<'overview' | 'slos' | 'services'>('overview');
    const [isRefreshing, setIsRefreshing] = useState(false);

    const handleRefresh = useCallback(async () => {
        setIsRefreshing(true);
        try {
            await onRefresh();
        } finally {
            setIsRefreshing(false);
        }
    }, [onRefresh]);

    const overallHealth = services.every((s) => s.status === 'healthy')
        ? 'healthy'
        : services.some((s) => s.status === 'down')
            ? 'critical'
            : 'warning';

    const slosMet = slos.filter((s) => s.status === 'met').length;
    const criticalMetrics = metrics.filter((m) => m.status === 'critical').length;

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-divider">
                <div className="flex items-center gap-3">
                    <div className={`p-2 rounded-lg ${STATUS_CONFIG[overallHealth].bg}`}>
                        <Favorite className={`w-5 h-5 ${STATUS_CONFIG[overallHealth].color}`} />
                    </div>
                    <div>
                        <h3 className="font-semibold text-text-primary">System Health</h3>
                        <p className="text-xs text-text-secondary capitalize">{overallHealth}</p>
                    </div>
                </div>
                <div className="flex items-center gap-2">
                    {lastUpdated && (
                        <span className="text-xs text-text-secondary">
                            Updated {new Date(lastUpdated).toLocaleTimeString()}
                        </span>
                    )}
                    <button
                        onClick={handleRefresh}
                        disabled={isRefreshing}
                        className="p-2 text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800 rounded-lg transition-colors disabled:opacity-50"
                    >
                        <Refresh className={`w-4 h-4 ${isRefreshing || isPolling ? 'animate-spin' : ''}`} />
                    </button>
                </div>
            </div>

            {/* Quick Stats */}
            <div className="grid grid-cols-3 gap-2 p-4 bg-grey-50 dark:bg-grey-800/50 border-b border-divider">
                <div className="text-center">
                    <div className={`text-2xl font-bold ${services.length === services.filter((s) => s.status === 'healthy').length ? 'text-green-500' : 'text-yellow-500'}`}>
                        {services.filter((s) => s.status === 'healthy').length}/{services.length}
                    </div>
                    <div className="text-xs text-text-secondary">Services Up</div>
                </div>
                <div className="text-center">
                    <div className={`text-2xl font-bold ${slosMet === slos.length ? 'text-green-500' : 'text-yellow-500'}`}>
                        {slosMet}/{slos.length}
                    </div>
                    <div className="text-xs text-text-secondary">SLOs Met</div>
                </div>
                <div className="text-center">
                    <div className={`text-2xl font-bold ${criticalMetrics === 0 ? 'text-green-500' : 'text-red-500'}`}>
                        {criticalMetrics}
                    </div>
                    <div className="text-xs text-text-secondary">Critical Alerts</div>
                </div>
            </div>

            {/* Tabs */}
            <div className="flex border-b border-divider">
                <button
                    onClick={() => setActiveTab('overview')}
                    className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'overview'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    Metrics
                </button>
                <button
                    onClick={() => setActiveTab('slos')}
                    className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'slos'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    SLOs ({slos.length})
                </button>
                <button
                    onClick={() => setActiveTab('services')}
                    className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'services'
                            ? 'border-primary-600 text-primary-600'
                            : 'border-transparent text-text-secondary hover:text-text-primary'
                        }`}
                >
                    Services ({services.length})
                </button>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto p-4">
                {activeTab === 'overview' && (
                    <div className="space-y-3">
                        {metrics.map((metric) => (
                            <div
                                key={metric.id}
                                className={`p-3 border rounded-lg ${metric.status === 'critical'
                                        ? 'border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/10'
                                        : metric.status === 'warning'
                                            ? 'border-yellow-200 dark:border-yellow-800 bg-yellow-50 dark:bg-yellow-900/10'
                                            : 'border-divider bg-bg-paper'
                                    }`}
                            >
                                <div className="flex items-center gap-3">
                                    <div className={`${STATUS_CONFIG[metric.status].color}`}>
                                        {METRIC_ICONS[metric.id.toLowerCase()] || <Speed className="w-5 h-5" />}
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <div className="flex items-center justify-between">
                                            <span className="font-medium text-sm text-text-primary">
                                                {metric.name}
                                            </span>
                                            <div className="flex items-center gap-2">
                                                <span className="font-mono font-bold text-text-primary">
                                                    {metric.value}
                                                    <span className="text-xs text-text-secondary ml-0.5">
                                                        {metric.unit}
                                                    </span>
                                                </span>
                                                {metric.trend && (
                                                    <span
                                                        className={
                                                            metric.trend === 'up'
                                                                ? 'text-red-500'
                                                                : metric.trend === 'down'
                                                                    ? 'text-green-500'
                                                                    : 'text-grey-500'
                                                        }
                                                    >
                                                        {TREND_ICONS[metric.trend]}
                                                    </span>
                                                )}
                                            </div>
                                        </div>
                                        {metric.threshold && (
                                            <div className="flex items-center gap-4 mt-1 text-xs text-text-secondary">
                                                <span>Warning: {metric.threshold.warning}{metric.unit}</span>
                                                <span>Critical: {metric.threshold.critical}{metric.unit}</span>
                                            </div>
                                        )}
                                    </div>
                                    {metric.sparkline && (
                                        <Sparkline data={metric.sparkline} status={metric.status} />
                                    )}
                                </div>
                            </div>
                        ))}
                    </div>
                )}

                {activeTab === 'slos' && (
                    <div className="space-y-3">
                        {slos.map((slo) => (
                            <div
                                key={slo.id}
                                className={`p-4 border rounded-lg ${slo.status === 'breached'
                                        ? 'border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/10'
                                        : slo.status === 'at_risk'
                                            ? 'border-yellow-200 dark:border-yellow-800 bg-yellow-50 dark:bg-yellow-900/10'
                                            : 'border-divider bg-bg-paper'
                                    }`}
                            >
                                <div className="flex items-center justify-between mb-2">
                                    <div className="flex items-center gap-2">
                                        <span className={STATUS_CONFIG[slo.status].color}>
                                            {STATUS_CONFIG[slo.status].icon}
                                        </span>
                                        <span className="font-medium text-text-primary">{slo.name}</span>
                                    </div>
                                    <span className="text-xs text-text-secondary">{slo.period}</span>
                                </div>
                                <div className="flex items-center justify-between mb-2">
                                    <span className="text-sm text-text-secondary">Target: {slo.target}%</span>
                                    <span className={`font-mono font-bold ${slo.current >= slo.target ? 'text-green-500' : 'text-red-500'
                                        }`}>
                                        {slo.current}%
                                    </span>
                                </div>
                                <div className="h-2 bg-grey-200 dark:bg-grey-700 rounded-full overflow-hidden">
                                    <div
                                        className={`h-full ${slo.status === 'met'
                                                ? 'bg-green-500'
                                                : slo.status === 'at_risk'
                                                    ? 'bg-yellow-500'
                                                    : 'bg-red-500'
                                            }`}
                                        style={{ width: `${Math.min(slo.current, 100)}%` }}
                                    />
                                </div>
                                {slo.errorBudgetRemaining !== undefined && (
                                    <div className="mt-2 text-xs text-text-secondary">
                                        Error budget remaining: {slo.errorBudgetRemaining}%
                                    </div>
                                )}
                            </div>
                        ))}
                    </div>
                )}

                {activeTab === 'services' && (
                    <div className="space-y-3">
                        {services.map((service) => (
                            <div
                                key={service.id}
                                className={`p-4 border rounded-lg ${service.status === 'down'
                                        ? 'border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/10'
                                        : service.status === 'degraded'
                                            ? 'border-yellow-200 dark:border-yellow-800 bg-yellow-50 dark:bg-yellow-900/10'
                                            : 'border-divider bg-bg-paper'
                                    }`}
                            >
                                <div className="flex items-center justify-between mb-2">
                                    <div className="flex items-center gap-2">
                                        <span className={STATUS_CONFIG[service.status].color}>
                                            {STATUS_CONFIG[service.status].icon}
                                        </span>
                                        <span className="font-medium text-text-primary">{service.name}</span>
                                    </div>
                                    <span className={`text-xs px-2 py-0.5 rounded capitalize ${STATUS_CONFIG[service.status].bg} ${STATUS_CONFIG[service.status].color}`}>
                                        {service.status}
                                    </span>
                                </div>
                                <div className="flex items-center gap-4 text-xs text-text-secondary">
                                    <span>Uptime: {service.uptime}</span>
                                    <span>Last checked: {new Date(service.lastChecked).toLocaleTimeString()}</span>
                                </div>
                                {service.dependencies && service.dependencies.length > 0 && (
                                    <div className="mt-3 pt-3 border-t border-divider">
                                        <div className="text-xs text-text-secondary mb-2">Dependencies</div>
                                        <div className="flex flex-wrap gap-2">
                                            {service.dependencies.map((dep, idx) => (
                                                <span
                                                    key={idx}
                                                    className={`px-2 py-1 text-xs rounded ${STATUS_CONFIG[dep.status].bg} ${STATUS_CONFIG[dep.status].color}`}
                                                >
                                                    {dep.name}
                                                </span>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};

export default HealthPanel;
