import { useParams, useNavigate } from "react-router";
import { useMetric } from '@/hooks/useObserveApi';
import { ArrowLeft, TrendingUp, TrendingDown, AlertTriangle, Package } from 'lucide-react';
import { Badge } from "@/components/ui";

/**
 * Metric Detail
 *
 * <p><b>Purpose</b><br>
 * Detailed view of a single metric showing time series data,
 * trend analysis, and correlations with incidents and deployments.
 *
 * <p><b>Features</b><br>
 * - Time series chart with historical data
 * - Trend indicators and status
 * - Related incidents list
 * - Related deployments list
 * - Navigation to related resources
 *
 * @doc.type component
 * @doc.purpose Metric detail and correlation analysis
 * @doc.layer product
 * @doc.pattern Page
 */
export function MetricDetail() {
    const navigate = useNavigate();
    const { metricId } = useParams<{ metricId: string }>();
    
    const { data: metric, isLoading, error } = useMetric(metricId!);

    if (isLoading) {
        return (
            <div className="p-6">
                <div className="text-slate-600 dark:text-neutral-400">Loading metric...</div>
            </div>
        );
    }

    if (error || !metric) {
        return (
            <div className="p-6">
                <div className="text-red-600 dark:text-red-400">
                    Failed to load metric: {error?.message || 'Not found'}
                </div>
            </div>
        );
    }

    const handleBack = () => {
        navigate('/observe/metrics');
    };

    const isPositive = metric.trend > 0;
    const TrendIcon = isPositive ? TrendingUp : TrendingDown;

    // For metrics where lower is better
    const lowerIsBetter = metric.name.includes('Failure') || metric.name.includes('Time') || metric.name.includes('Incident');
    const trendColor = lowerIsBetter
        ? (isPositive ? 'text-red-500' : 'text-green-500')
        : (isPositive ? 'text-green-500' : 'text-red-500');

    const statusConfig = {
        'on-track': { label: 'On Track', variant: 'success' as const, color: 'text-green-600 dark:text-green-400' },
        'at-risk': { label: 'At Risk', variant: 'warning' as const, color: 'text-amber-600 dark:text-amber-400' },
        'off-track': { label: 'Off Track', variant: 'danger' as const, color: 'text-red-600 dark:text-red-400' },
    };

    // Format time series data for display
    const chartData = metric.timeSeries.map((point) => ({
        date: new Date(point.timestamp).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
        value: point.value,
        timestamp: point.timestamp,
    }));

    const maxValue = Math.max(...metric.timeSeries.map(p => p.value));
    const minValue = Math.min(...metric.timeSeries.map(p => p.value));
    const valueRange = maxValue - minValue || 1;

    return (
        <div className="p-6 space-y-6">
            {/* Breadcrumb / Back Navigation */}
            <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-neutral-400">
                <button 
                    onClick={handleBack}
                    className="hover:text-slate-900 dark:hover:text-neutral-100 transition-colors flex items-center gap-2"
                >
                    <ArrowLeft className="h-4 w-4" />
                    Metrics
                </button>
                <span>/</span>
                <span className="text-slate-900 dark:text-neutral-100">{metric.name}</span>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Main Content */}
                <div className="lg:col-span-2 space-y-6">
                    {/* Header Card */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                        <div className="flex items-start justify-between mb-4">
                            <div className="flex-1">
                                <h1 className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mb-2">
                                    {metric.name}
                                </h1>
                                <p className="text-slate-600 dark:text-neutral-400 text-sm">
                                    {metric.description}
                                </p>
                            </div>
                            <Badge variant={statusConfig[metric.status].variant}>
                                {statusConfig[metric.status].label}
                            </Badge>
                        </div>

                        <div className="grid grid-cols-3 gap-6 mt-6">
                            <div>
                                <div className="text-sm text-slate-500 dark:text-neutral-500 mb-1">Current Value</div>
                                <div className="text-3xl font-bold text-slate-900 dark:text-neutral-100">
                                    {metric.value}
                                </div>
                            </div>
                            <div>
                                <div className="text-sm text-slate-500 dark:text-neutral-500 mb-1">Target</div>
                                <div className="text-3xl font-bold text-slate-900 dark:text-neutral-100">
                                    {metric.target}
                                </div>
                            </div>
                            <div>
                                <div className="text-sm text-slate-500 dark:text-neutral-500 mb-1">Trend</div>
                                <div className={`text-3xl font-bold flex items-center gap-2 ${trendColor}`}>
                                    <TrendIcon className="h-6 w-6" />
                                    {Math.abs(metric.trend)}%
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Time Series Chart */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Time Series (Last 30 Days)
                        </h2>
                        <div className="h-80 relative">
                            {/* Y-axis labels */}
                            <div className="absolute left-0 top-0 bottom-8 w-12 flex flex-col justify-between text-xs text-slate-500 dark:text-neutral-500">
                                <span>{maxValue.toFixed(1)}</span>
                                <span>{((maxValue + minValue) / 2).toFixed(1)}</span>
                                <span>{minValue.toFixed(1)}</span>
                            </div>
                            
                            {/* Chart area */}
                            <div className="ml-12 mr-4 h-full relative">
                                <svg className="w-full h-full" viewBox="0 0 800 300" preserveAspectRatio="none">
                                    {/* Grid lines */}
                                    <line x1="0" y1="0" x2="800" y2="0" stroke="#374151" strokeWidth="1" opacity="0.2" />
                                    <line x1="0" y1="150" x2="800" y2="150" stroke="#374151" strokeWidth="1" opacity="0.2" />
                                    <line x1="0" y1="300" x2="800" y2="300" stroke="#374151" strokeWidth="1" opacity="0.2" />
                                    
                                    {/* Line chart */}
                                    <polyline
                                        points={chartData.map((point, i) => {
                                            const x = (i / (chartData.length - 1)) * 800;
                                            const y = 300 - ((point.value - minValue) / valueRange) * 300;
                                            return `${x},${y}`;
                                        }).join(' ')}
                                        fill="none"
                                        stroke="#3b82f6"
                                        strokeWidth="2"
                                    />
                                    
                                    {/* Data points */}
                                    {chartData.map((point, i) => {
                                        const x = (i / (chartData.length - 1)) * 800;
                                        const y = 300 - ((point.value - minValue) / valueRange) * 300;
                                        return (
                                            <circle
                                                key={i}
                                                cx={x}
                                                cy={y}
                                                r="4"
                                                fill="#3b82f6"
                                            />
                                        );
                                    })}
                                </svg>
                                
                                {/* X-axis labels */}
                                <div className="flex justify-between mt-2 text-xs text-slate-500 dark:text-neutral-500">
                                    <span>{chartData[0]?.date}</span>
                                    <span>{chartData[Math.floor(chartData.length / 2)]?.date}</span>
                                    <span>{chartData[chartData.length - 1]?.date}</span>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Related Incidents */}
                    {metric.relatedIncidents.length > 0 && (
                        <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                            <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4 flex items-center gap-2">
                                <AlertTriangle className="h-5 w-5 text-amber-500" />
                                Related Incidents ({metric.relatedIncidents.length})
                            </h2>
                            <div className="space-y-2">
                                {metric.relatedIncidents.map((incidentId) => (
                                    <div
                                        key={incidentId}
                                        className="flex items-center justify-between p-3 bg-slate-50 dark:bg-slate-800 rounded-md border border-slate-200 dark:border-slate-700 hover:border-blue-500 dark:hover:border-blue-500 transition-colors cursor-pointer"
                                        onClick={() => navigate(`/operate/incidents/${incidentId}`)}
                                    >
                                        <div className="flex items-center gap-3">
                                            <div className="w-2 h-2 rounded-full bg-amber-500"></div>
                                            <span className="font-mono text-sm text-slate-700 dark:text-neutral-300">
                                                {incidentId}
                                            </span>
                                        </div>
                                        <button className="text-sm text-blue-600 dark:text-blue-400 hover:underline">
                                            View Details →
                                        </button>
                                    </div>
                                ))}
                            </div>
                            <p className="mt-3 text-xs text-slate-500 dark:text-neutral-500">
                                These incidents occurred during periods of metric degradation
                            </p>
                        </div>
                    )}

                    {/* Related Deployments */}
                    {metric.relatedDeployments.length > 0 && (
                        <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                            <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4 flex items-center gap-2">
                                <Package className="h-5 w-5 text-blue-500" />
                                Related Deployments ({metric.relatedDeployments.length})
                            </h2>
                            <div className="space-y-2">
                                {metric.relatedDeployments.map((deploymentId) => (
                                    <div
                                        key={deploymentId}
                                        className="flex items-center justify-between p-3 bg-slate-50 dark:bg-slate-800 rounded-md border border-slate-200 dark:border-slate-700"
                                    >
                                        <div className="flex items-center gap-3">
                                            <div className="w-2 h-2 rounded-full bg-green-500"></div>
                                            <span className="font-mono text-sm text-slate-700 dark:text-neutral-300">
                                                {deploymentId}
                                            </span>
                                        </div>
                                        <Badge variant="success">Success</Badge>
                                    </div>
                                ))}
                            </div>
                            <p className="mt-3 text-xs text-slate-500 dark:text-neutral-500">
                                Deployments that may have impacted this metric
                            </p>
                        </div>
                    )}
                </div>

                {/* Sidebar */}
                <div className="space-y-6">
                    {/* Metadata */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Metadata
                        </h2>
                        <div className="space-y-3 text-sm">
                            <div>
                                <div className="text-slate-500 dark:text-neutral-500 mb-1">Category</div>
                                <Badge variant="neutral">{metric.category}</Badge>
                            </div>
                            <div>
                                <div className="text-slate-500 dark:text-neutral-500 mb-1">Unit</div>
                                <div className="text-slate-700 dark:text-neutral-300 font-mono">
                                    {metric.unit}
                                </div>
                            </div>
                            <div>
                                <div className="text-slate-500 dark:text-neutral-500 mb-1">Status</div>
                                <div className={`font-semibold ${statusConfig[metric.status].color}`}>
                                    {statusConfig[metric.status].label}
                                </div>
                            </div>
                            <div>
                                <div className="text-slate-500 dark:text-neutral-500 mb-1">Last Updated</div>
                                <div className="text-slate-700 dark:text-neutral-300">
                                    {new Date(metric.updatedAt).toLocaleString()}
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Quick Stats */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Quick Stats
                        </h2>
                        <div className="space-y-4">
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-slate-600 dark:text-neutral-400">Data Points</span>
                                <span className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                    {metric.timeSeries.length}
                                </span>
                            </div>
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-slate-600 dark:text-neutral-400">Min Value</span>
                                <span className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                    {minValue.toFixed(2)}
                                </span>
                            </div>
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-slate-600 dark:text-neutral-400">Max Value</span>
                                <span className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                    {maxValue.toFixed(2)}
                                </span>
                            </div>
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-slate-600 dark:text-neutral-400">Avg Value</span>
                                <span className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                    {(metric.timeSeries.reduce((sum, p) => sum + p.value, 0) / metric.timeSeries.length).toFixed(2)}
                                </span>
                            </div>
                        </div>
                    </div>

                    {/* Actions */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Actions
                        </h2>
                        <div className="space-y-2">
                            <button
                                onClick={() => navigate('/observe/reports')}
                                className="w-full px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors text-sm font-medium"
                            >
                                Generate Report
                            </button>
                            <button
                                onClick={() => navigate('/admin/services')}
                                className="w-full px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-md hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors text-sm font-medium"
                            >
                                View Related Services
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
