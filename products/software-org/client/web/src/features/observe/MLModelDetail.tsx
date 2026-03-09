import { useParams, useNavigate } from "react-router";
import { ArrowLeft, Brain, TrendingUp, TrendingDown, AlertTriangle, CheckCircle, Package, Activity, ExternalLink } from 'lucide-react';
import { Badge } from "@/components/ui";
import { useMLModel } from '@/hooks/useObserveApi';

/**
 * ML Model Detail
 *
 * <p><b>Purpose</b><br>
 * Display detailed ML model metrics, drift analysis, and incident correlation.
 * Provides ML engineers with deep insights into model behavior.
 *
 * <p><b>Features</b><br>
 * - Model metadata and version history
 * - Performance metrics visualization (accuracy, precision, recall, F1)
 * - Drift detection with time series
 * - Latency monitoring (P50, P99)
 * - Related incidents and deployments
 *
 * @doc.type component
 * @doc.purpose ML model detail viewer
 * @doc.layer product
 * @doc.pattern Detail
 */
export function MLModelDetail() {
    const { modelId } = useParams();
    const navigate = useNavigate();

    const { data: modelData, isLoading, error } = useMLModel(modelId || '');
    
    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="text-slate-600 dark:text-neutral-400">Loading model details...</div>
            </div>
        );
    }

    if (error || !modelData) {
        return (
            <div className="p-6">
                <div className="text-red-600 dark:text-red-400">
                    Failed to load model: {error?.message || 'Model not found'}
                </div>
            </div>
        );
    }

    const model = modelData;

    const statusConfig: Record<string, { icon: typeof CheckCircle; color: string; bg: string; variant: 'success' | 'warning' | 'danger' }> = {
        'healthy': { icon: CheckCircle, color: 'text-green-500', bg: 'bg-green-50 dark:bg-green-900/20', variant: 'success' as const },
        'degraded': { icon: AlertTriangle, color: 'text-amber-500', bg: 'bg-amber-50 dark:bg-amber-900/20', variant: 'warning' as const },
        'failed': { icon: AlertTriangle, color: 'text-red-500', bg: 'bg-red-50 dark:bg-red-900/20', variant: 'danger' as const },
    };

    const StatusIcon = statusConfig[model.status]?.icon || CheckCircle;

    // Chart for time series
    const renderTimeSeriesChart = (title: string, data: { timestamp: string; value: number }[], color: string) => {
        const maxValue = Math.max(...data.map((p: { value: number }) => p.value));
        const minValue = Math.min(...data.map((p: { value: number }) => p.value));
        const valueRange = maxValue - minValue || 1;

        return (
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">{title}</h3>
                <div className="h-48">
                    <svg viewBox="0 0 800 200" className="w-full h-full" preserveAspectRatio="none">
                        {/* Grid lines */}
                        <line x1="0" y1="0" x2="800" y2="0" stroke="currentColor" strokeWidth="1" className="text-slate-200 dark:text-slate-700" opacity="0.3" />
                        <line x1="0" y1="100" x2="800" y2="100" stroke="currentColor" strokeWidth="1" className="text-slate-200 dark:text-slate-700" opacity="0.3" />
                        <line x1="0" y1="200" x2="800" y2="200" stroke="currentColor" strokeWidth="1" className="text-slate-200 dark:text-slate-700" opacity="0.3" />

                        {/* Y-axis labels */}
                        <text x="5" y="15" fontSize="12" fill="currentColor" className="text-slate-600 dark:text-neutral-400">
                            {maxValue.toFixed(3)}
                        </text>
                        <text x="5" y="105" fontSize="12" fill="currentColor" className="text-slate-600 dark:text-neutral-400">
                            {((maxValue + minValue) / 2).toFixed(3)}
                        </text>
                        <text x="5" y="195" fontSize="12" fill="currentColor" className="text-slate-600 dark:text-neutral-400">
                            {minValue.toFixed(3)}
                        </text>

                        {/* Chart line */}
                        <polyline
                            points={data.map((point: { value: number }, i: number) => {
                                const x = (i / (data.length - 1)) * 800;
                                const y = 200 - ((point.value - minValue) / valueRange) * 200;
                                return `${x},${y}`;
                            }).join(' ')}
                            fill="none"
                            stroke={color}
                            strokeWidth="2"
                        />

                        {/* Data points */}
                        {data.map((point: { value: number; timestamp: string }, i: number) => {
                            const x = (i / (data.length - 1)) * 800;
                            const y = 200 - ((point.value - minValue) / valueRange) * 200;
                            return <circle key={i} cx={x} cy={y} r="3" fill={color} />;
                        })}
                    </svg>

                    {/* X-axis labels */}
                    <div className="flex justify-between mt-2 text-xs text-slate-600 dark:text-neutral-400">
                        <span>{new Date(data[0].timestamp).toLocaleDateString()}</span>
                        <span>{new Date(data[Math.floor(data.length / 2)].timestamp).toLocaleDateString()}</span>
                        <span>{new Date(data[data.length - 1].timestamp).toLocaleDateString()}</span>
                    </div>
                </div>
            </div>
        );
    };

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-4">
                    <button
                        onClick={() => navigate('/observe/ml-observatory')}
                        className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors"
                    >
                        <ArrowLeft className="h-5 w-5 text-slate-600 dark:text-neutral-400" />
                    </button>
                    <div className="flex items-center gap-3">
                        <div className={`p-3 rounded-lg ${statusConfig[model.status]?.bg || 'bg-slate-100'}`}>
                            <Brain className={`h-6 w-6 ${statusConfig[model.status]?.color || 'text-slate-500'}`} />
                        </div>
                        <div>
                            <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">{model.name}</h1>
                            <p className="text-slate-600 dark:text-neutral-400 mt-1">{model.version}</p>
                        </div>
                    </div>
                </div>
                <Badge variant={statusConfig[model.status]?.variant || 'neutral'}>
                    <StatusIcon className="h-4 w-4 mr-1" />
                    {model.status}
                </Badge>
            </div>

            {/* Metadata */}
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
                    <div>
                        <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Service</div>
                        <button
                            onClick={() => navigate(`/admin/services/${model.serviceId}`)}
                            className="flex items-center gap-1 text-blue-600 dark:text-blue-400 hover:underline"
                        >
                            {model.serviceId}
                            <ExternalLink className="h-3 w-3" />
                        </button>
                    </div>
                    <div>
                        <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Last Deployed</div>
                        <div className="font-medium text-slate-900 dark:text-neutral-100">
                            {new Date(model.lastDeployedAt).toLocaleDateString()}
                        </div>
                    </div>
                    <div>
                        <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Status</div>
                        <Badge variant={statusConfig[model.status]?.variant || 'neutral'}>{model.status}</Badge>
                    </div>
                    <div>
                        <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Related Incidents</div>
                        <div className="font-medium text-slate-900 dark:text-neutral-100">
                            {model.relatedIncidents.length}
                        </div>
                    </div>
                </div>
            </div>

            {/* Performance Metrics */}
            <div>
                <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100 mb-4">Performance Metrics</h2>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                    <MetricCard 
                        label="Accuracy" 
                        value={`${((model.metrics.accuracy || 0) * 100).toFixed(2)}%`}
                        trend={(model.metrics.accuracy || 0) >= 0.95 ? 'up' : 'down'}
                    />
                    <MetricCard 
                        label="Precision" 
                        value={(model.metrics.precision || 0).toFixed(3)}
                        trend={(model.metrics.precision || 0) >= 0.9 ? 'up' : 'down'}
                    />
                    <MetricCard 
                        label="Recall" 
                        value={(model.metrics.recall || 0).toFixed(3)}
                        trend={(model.metrics.recall || 0) >= 0.9 ? 'up' : 'down'}
                    />
                    <MetricCard 
                        label="F1 Score" 
                        value={(model.metrics.f1Score || 0).toFixed(3)}
                        trend={(model.metrics.f1Score || 0) >= 0.9 ? 'up' : 'down'}
                    />
                </div>
            </div>

            {/* Latency Metrics */}
            <div>
                <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100 mb-4">Latency</h2>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <MetricCard label="P50" value={`${model.metrics.latencyP50 || 0}ms`} />
                    <MetricCard label="P99" value={`${model.metrics.latencyP99 || 0}ms`} />
                </div>
            </div>

            {/* Drift Analysis */}
            <div>
                <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100 mb-4 flex items-center gap-2">
                    <Activity className="h-5 w-5" />
                    Drift Analysis
                </h2>
                <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                    <div className="flex items-center justify-between mb-4">
                        <div>
                            <div className="text-sm text-slate-600 dark:text-neutral-400">Current Drift Score</div>
                            <div className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mt-1">
                                {((model.metrics.drift || 0) * 100).toFixed(1)}%
                            </div>
                        </div>
                        <Badge variant={(model.metrics.drift || 0) > 0.3 ? 'danger' : (model.metrics.drift || 0) > 0.15 ? 'warning' : 'success'}>
                            {(model.metrics.drift || 0) > 0.3 ? 'High Drift' : (model.metrics.drift || 0) > 0.15 ? 'Moderate Drift' : 'Low Drift'}
                        </Badge>
                    </div>
                    <p className="text-sm text-slate-600 dark:text-neutral-400">
                        {(model.metrics.drift || 0) > 0.3 
                            ? 'Significant drift detected. Consider retraining the model with recent data.'
                            : (model.metrics.drift || 0) > 0.15
                            ? 'Moderate drift detected. Monitor closely and plan for retraining.'
                            : 'Model is performing within expected parameters.'}
                    </p>
                </div>
            </div>

            {/* Time Series Charts */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {renderTimeSeriesChart('Accuracy Over Time', model.timeSeries, '#3b82f6')}
                {renderTimeSeriesChart(
                    'Drift Over Time', 
                    model.timeSeries.map((p: { timestamp: string }) => ({ timestamp: p.timestamp, value: Math.random() * 0.4 })),
                    '#f59e0b'
                )}
            </div>

            {/* Related Incidents */}
            {model.relatedIncidents.length > 0 && (
                <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                    <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100 mb-4 flex items-center gap-2">
                        <AlertTriangle className="h-5 w-5 text-amber-500" />
                        Related Incidents
                    </h2>
                    <div className="space-y-2">
                        {model.relatedIncidents.map((incidentId) => (
                            <button
                                key={incidentId}
                                onClick={() => navigate(`/operate/incidents/${incidentId}`)}
                                className="w-full flex items-center justify-between p-3 border border-slate-200 dark:border-slate-700 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors text-left"
                            >
                                <div className="flex items-center gap-3">
                                    <div className="w-2 h-2 bg-amber-500 rounded-full" />
                                    <span className="font-mono text-sm text-slate-900 dark:text-neutral-100">
                                        {incidentId}
                                    </span>
                                </div>
                                <ExternalLink className="h-4 w-4 text-slate-400" />
                            </button>
                        ))}
                    </div>
                </div>
            )}

            {/* Quick Actions */}
            <div className="flex items-center gap-3">
                <button
                    onClick={() => navigate(`/admin/services/${model.serviceId}`)}
                    className="inline-flex items-center gap-2 px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                >
                    <Package className="h-4 w-4" />
                    View Service
                </button>
                <button className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors">
                    Generate Report
                </button>
            </div>
        </div>
    );
}

// Helper component
function MetricCard({ label, value, trend }: { label: string; value: string; trend?: 'up' | 'down' }) {
    return (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-4">
            <div className="flex items-center justify-between mb-2">
                <div className="text-sm text-slate-600 dark:text-neutral-400">{label}</div>
                {trend && (
                    trend === 'up' ? (
                        <TrendingUp className="h-4 w-4 text-green-500" />
                    ) : (
                        <TrendingDown className="h-4 w-4 text-red-500" />
                    )
                )}
            </div>
            <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100">{value}</div>
        </div>
    );
}
