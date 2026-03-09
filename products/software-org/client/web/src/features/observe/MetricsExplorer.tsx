import { useState, useEffect, useRef } from "react";
import { useAtomValue } from 'jotai';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import { useMetrics, type MetricResponse } from '@/hooks/useObserveApi';
import { TrendingUp, TrendingDown, Target, Activity } from 'lucide-react';
import { Badge } from "@/components/ui";

/**
 * Metrics Explorer
 *
 * <p><b>Purpose</b><br>
 * View and analyze key performance indicators and metrics.
 * Displays metrics from Observe API with filtering and navigation.
 *
 * <p><b>Features</b><br>
 * - Metric cards with trend indicators
 * - Filter by category (Velocity, Stability, Reliability)
 * - Filter by status (on-track, at-risk, off-track)
 * - Navigate to metric detail for time series analysis
 *
 * @doc.type component
 * @doc.purpose Metrics monitoring and analysis
 * @doc.layer product
 * @doc.pattern Page
 */
export function MetricsExplorer() {
    const selectedTenant = useAtomValue(selectedTenantAtom);
    const [filterCategory, setFilterCategory] = useState<string>('all');
    const [filterStatus, setFilterStatus] = useState<string>('all');
    const [selectedMetric, setSelectedMetric] = useState<MetricResponse | null>(null);
    const [isPollingEnabled, setIsPollingEnabled] = useState(true);
    const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
    
    const tenantId = selectedTenant || 'acme-payments-id';
    
    const { data: metricsData, isLoading, error, refetch, isRefetching } = useMetrics(
        tenantId,
        filterCategory === 'all' ? undefined : filterCategory,
        filterStatus === 'all' ? undefined : filterStatus
    );

    // Real-time polling effect
    useEffect(() => {
        if (isPollingEnabled) {
            intervalRef.current = setInterval(() => {
                refetch();
            }, 30000); // 30 seconds
        }
        return () => {
            if (intervalRef.current) {
                clearInterval(intervalRef.current);
            }
        };
    }, [isPollingEnabled, refetch]);

    const metrics = metricsData?.data || [];

    const categories = ['all', 'Velocity', 'Stability', 'Reliability'];
    const statuses = ['all', 'on-track', 'at-risk', 'off-track'];

    const stats = {
        total: metrics.length,
        onTrack: metrics.filter((m) => m.status === 'on-track').length,
        atRisk: metrics.filter((m) => m.status === 'at-risk').length,
        offTrack: metrics.filter((m) => m.status === 'off-track').length,
    };

    if (error) {
        return (
            <div className="p-6">
                <div className="bg-red-50 dark:bg-red-900/10 border border-red-200 dark:border-red-800 rounded-lg p-4">
                    <div className="flex items-start gap-3">
                        <div className="text-red-600 dark:text-red-400 text-sm">
                            <strong>Failed to load metrics:</strong> {error.message}
                        </div>
                        <button
                            onClick={() => refetch()}
                            className="ml-auto px-3 py-1 text-sm bg-red-600 text-white rounded hover:bg-red-700 transition-colors"
                        >
                            Retry
                        </button>
                    </div>
                </div>
            </div>
        );
    }

    const exportMetrics = () => {
        const csvContent = [
            ['Name', 'Category', 'Value', 'Target', 'Trend', 'Status'],
            ...metrics.map(m => [m.name, m.category, m.value, m.target, `${m.trend}%`, m.status])
        ].map(row => row.join(',')).join('\n');
        
        const blob = new Blob([csvContent], { type: 'text/csv' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `metrics-${new Date().toISOString().split('T')[0]}.csv`;
        a.click();
        URL.revokeObjectURL(url);
    };

    return (
        <div className="space-y-6">
            {/* Header with Controls */}
            <div className="flex items-start justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Metrics</h1>
                    <p className="text-slate-600 dark:text-neutral-400 mt-1">
                        Monitor key performance indicators and system health
                        {isPollingEnabled && <span className="ml-2 text-sm text-green-600 dark:text-green-400">● Live (30s refresh)</span>}
                    </p>
                </div>
                <div className="flex items-center gap-2">
                    <button
                        onClick={() => setIsPollingEnabled(!isPollingEnabled)}
                        className={`px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                            isPollingEnabled
                                ? 'bg-green-100 dark:bg-green-900/20 text-green-700 dark:text-green-400'
                                : 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-neutral-300'
                        }`}
                        title={isPollingEnabled ? 'Disable auto-refresh' : 'Enable auto-refresh'}
                    >
                        {isPollingEnabled ? '● Live' : 'Paused'}
                    </button>
                    <button
                        onClick={() => refetch()}
                        disabled={isRefetching}
                        className="px-3 py-2 rounded-lg text-sm font-medium bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-neutral-300 hover:bg-slate-200 dark:hover:bg-slate-700 disabled:opacity-50 transition-colors"
                    >
                        {isRefetching ? 'Refreshing...' : 'Refresh'}
                    </button>
                    <button
                        onClick={exportMetrics}
                        disabled={metrics.length === 0}
                        className="px-3 py-2 rounded-lg text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 transition-colors"
                    >
                        Export CSV
                    </button>
                </div>
            </div>

            {/* Stats Bar */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <StatCard label="Total Metrics" value={stats.total} icon={<Activity className="h-5 w-5" />} />
                <StatCard label="On Track" value={stats.onTrack} icon={<Target className="h-5 w-5 text-green-500" />} />
                <StatCard label="At Risk" value={stats.atRisk} icon={<TrendingDown className="h-5 w-5 text-amber-500" />} />
                <StatCard label="Off Track" value={stats.offTrack} icon={<TrendingUp className="h-5 w-5 text-red-500" />} />
            </div>

            {/* Filters */}
            <div className="space-y-3">
                <div>
                    <label className="text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2 block">
                        Category
                    </label>
                    <div className="flex gap-2 flex-wrap">
                        {categories.map((cat) => (
                            <button
                                key={cat}
                                onClick={() => setFilterCategory(cat)}
                                className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
                                    filterCategory === cat
                                        ? 'bg-blue-600 text-white'
                                        : 'bg-slate-200 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300 hover:bg-slate-300 dark:hover:bg-neutral-700'
                                }`}
                            >
                                {cat === 'all' ? 'All Categories' : cat}
                            </button>
                        ))}
                    </div>
                </div>
                
                <div>
                    <label className="text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2 block">
                        Status
                    </label>
                    <div className="flex gap-2 flex-wrap">
                        {statuses.map((stat) => (
                            <button
                                key={stat}
                                onClick={() => setFilterStatus(stat)}
                                className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
                                    filterStatus === stat
                                        ? stat === 'on-track'
                                            ? 'bg-green-600 text-white'
                                            : stat === 'at-risk'
                                                ? 'bg-amber-600 text-white'
                                                : stat === 'off-track'
                                                    ? 'bg-red-600 text-white'
                                                    : 'bg-blue-600 text-white'
                                        : 'bg-slate-200 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300 hover:bg-slate-300 dark:hover:bg-neutral-700'
                                }`}
                            >
                                {stat === 'all' ? 'All Status' : stat.split('-').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')}
                            </button>
                        ))}
                    </div>
                </div>
            </div>

            {/* Metrics Grid */}
            {isLoading ? (
                <div className="text-center py-8 text-slate-600 dark:text-neutral-400">
                    Loading metrics...
                </div>
            ) : metrics.length === 0 ? (
                <div className="text-center py-12 bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700">
                    <Activity className="h-12 w-12 text-slate-400 mx-auto mb-4" />
                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-2">
                        No Metrics Found
                    </h3>
                    <p className="text-sm text-slate-500 dark:text-neutral-500">
                        Try adjusting your filters
                    </p>
                </div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    {metrics.map((metric) => (
                        <MetricCard
                            key={metric.id}
                            metric={metric}
                            onClick={() => setSelectedMetric(metric)}
                        />
                    ))}
                </div>
            )}
            
            {/* Metric Detail Modal */}
            {selectedMetric && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={() => setSelectedMetric(null)}>
                    <div className="bg-white dark:bg-slate-900 rounded-lg max-w-2xl w-full max-h-[90vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
                        <div className="sticky top-0 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-700 p-6 flex items-center justify-between">
                            <div>
                                <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                                    {selectedMetric.name}
                                </h2>
                                <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                                    {selectedMetric.description}
                                </p>
                            </div>
                            <button
                                onClick={() => setSelectedMetric(null)}
                                className="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                            >
                                ✕
                            </button>
                        </div>
                        <div className="p-6 space-y-6">
                            <div className="bg-slate-50 dark:bg-slate-800 rounded-lg p-6">
                                <div className="text-sm text-slate-600 dark:text-neutral-400 mb-2">Current Value</div>
                                <div className="flex items-baseline gap-2">
                                    <span className="text-4xl font-bold text-slate-900 dark:text-neutral-100">
                                        {selectedMetric.value}
                                    </span>
                                    <span className="text-lg text-slate-600 dark:text-neutral-400">
                                        {selectedMetric.unit}
                                    </span>
                                </div>
                                <div className="mt-4 flex items-center justify-between text-sm">
                                    <span className="text-slate-600 dark:text-neutral-400">Target:</span>
                                    <span className="font-medium text-slate-900 dark:text-neutral-100">{selectedMetric.target}</span>
                                </div>
                            </div>
                            {selectedMetric.timeSeries && selectedMetric.timeSeries.length > 0 && (
                                <div>
                                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-3">
                                        Trend (Last 24h)
                                    </h3>
                                    <div className="border border-slate-200 dark:border-slate-700 rounded-lg p-6 h-48 flex items-center justify-center">
                                        <p className="text-slate-500 dark:text-neutral-500 text-sm">
                                            📈 Chart visualization (integrate YAPPC ChartComponent)
                                        </p>
                                    </div>
                                </div>
                            )}
                            {(selectedMetric.relatedIncidents.length > 0 || selectedMetric.relatedDeployments.length > 0) && (
                                <div>
                                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-3">
                                        Related Events
                                    </h3>
                                    {selectedMetric.relatedIncidents.length > 0 && (
                                        <div className="mb-2">
                                            <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                                                Incidents: {selectedMetric.relatedIncidents.length}
                                            </span>
                                        </div>
                                    )}
                                    {selectedMetric.relatedDeployments.length > 0 && (
                                        <div>
                                            <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                                                Deployments: {selectedMetric.relatedDeployments.length}
                                            </span>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

// Helper components

function StatCard({ label, value, icon }: { label: string; value: number; icon: React.ReactNode }) {
    return (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-4">
            <div className="flex items-center justify-between">
                <div>
                    <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100">{value}</div>
                    <div className="text-sm text-slate-600 dark:text-neutral-400 mt-1">{label}</div>
                </div>
                <div className="text-slate-400 dark:text-neutral-500">
                    {icon}
                </div>
            </div>
        </div>
    );
}

interface MetricCardProps {
    metric: {
        id: string;
        name: string;
        value: string;
        target: string;
        trend: number;
        category: string;
        status: 'on-track' | 'at-risk' | 'off-track';
        description: string;
    };
    onClick: () => void;
}

function MetricCard({ metric, onClick }: MetricCardProps) {
    const isPositive = metric.trend > 0;
    const TrendIcon = isPositive ? TrendingUp : TrendingDown;

    // For metrics where lower is better (like failure rate, lead time)
    const lowerIsBetter = metric.name.includes('Failure') || metric.name.includes('Time') || metric.name.includes('Incident');
    const trendColor = lowerIsBetter
        ? (isPositive ? 'text-red-500' : 'text-green-500')
        : (isPositive ? 'text-green-500' : 'text-red-500');

    const statusConfig = {
        'on-track': { label: 'On Track', variant: 'success' as const },
        'at-risk': { label: 'At Risk', variant: 'warning' as const },
        'off-track': { label: 'Off Track', variant: 'danger' as const },
    };

    return (
        <div
            onClick={onClick}
            className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-5 hover:border-blue-500 dark:hover:border-blue-500 transition-all cursor-pointer hover:shadow-lg"
        >
            <div className="flex items-start justify-between mb-3">
                <div className="flex-1">
                    <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-1">
                        {metric.name}
                    </h3>
                    <p className="text-xs text-slate-500 dark:text-neutral-500">
                        {metric.description}
                    </p>
                </div>
                <Badge variant={statusConfig[metric.status].variant}>
                    {statusConfig[metric.status].label}
                </Badge>
            </div>

            <div className="space-y-3">
                <div className="flex items-baseline gap-2">
                    <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                        {metric.value}
                    </div>
                    <div className={`flex items-center gap-1 text-sm font-medium ${trendColor}`}>
                        <TrendIcon className="h-4 w-4" />
                        <span>{Math.abs(metric.trend)}%</span>
                    </div>
                </div>

                <div className="flex items-center justify-between text-sm">
                    <span className="text-slate-500 dark:text-neutral-500">Target:</span>
                    <span className="font-medium text-slate-700 dark:text-neutral-300">{metric.target}</span>
                </div>

                <div className="pt-2 border-t border-slate-200 dark:border-slate-700">
                    <span className="inline-flex items-center px-2 py-1 rounded-md text-xs font-medium bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-neutral-400">
                        {metric.category}
                    </span>
                </div>
            </div>
        </div>
    );
}
