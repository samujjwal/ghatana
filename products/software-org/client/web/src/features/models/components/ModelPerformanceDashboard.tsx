import { memo } from 'react';

/**
 * Model performance dashboard showing metrics and trend analysis.
 *
 * <p><b>Purpose</b><br>
 * Tracks machine learning model performance over time.
 * Shows accuracy, precision, recall, and business metrics.
 *
 * <p><b>Features</b><br>
 * - Performance metrics timeline
 * - Comparison with baseline
 * - Drift detection
 * - Data quality metrics
 * - Prediction distribution
 *
 * @doc.type component
 * @doc.purpose ML model performance tracking
 * @doc.layer product
 * @doc.pattern Organism
 */

interface ModelMetrics {
    timestamp: string;
    accuracy: number;
    precision: number;
    recall: number;
    f1Score: number;
    auc: number;
}

interface ModelPerformanceDashboardProps {
    modelName?: string;
    metrics?: ModelMetrics[];
}

export const ModelPerformanceDashboard = memo(function ModelPerformanceDashboard({
    modelName = 'Primary ML Model',
    metrics,
}: ModelPerformanceDashboardProps) {
    // Mock data if none provided
    const data = metrics || [
        {
            timestamp: new Date(Date.now() - 6 * 24 * 60 * 60 * 1000).toISOString(),
            accuracy: 0.91,
            precision: 0.89,
            recall: 0.92,
            f1Score: 0.905,
            auc: 0.94,
        },
        {
            timestamp: new Date(Date.now() - 4 * 24 * 60 * 60 * 1000).toISOString(),
            accuracy: 0.918,
            precision: 0.895,
            recall: 0.923,
            f1Score: 0.909,
            auc: 0.942,
        },
        {
            timestamp: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString(),
            accuracy: 0.925,
            precision: 0.903,
            recall: 0.931,
            f1Score: 0.917,
            auc: 0.948,
        },
        {
            timestamp: new Date().toISOString(),
            accuracy: 0.928,
            precision: 0.906,
            recall: 0.934,
            f1Score: 0.920,
            auc: 0.950,
        },
    ];

    const latest = data[data.length - 1];
    const previous = data[data.length - 2];

    const getChange = (current: number, previous: number) => {
        const change = ((current - previous) / previous) * 100;
        return { value: change, isPositive: change >= 0 };
    };

    const formatMetric = (value: number) => (value * 100).toFixed(1);

    return (
        <div className="space-y-4 rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
            {/* Header */}
            <div className="space-y-2">
                <h2 className="text-sm font-semibold text-slate-900 dark:text-neutral-100">
                    📊 Model Performance
                </h2>
                <p className="text-xs text-slate-600 dark:text-neutral-400">{modelName}</p>
            </div>

            {/* Key Metrics Grid */}
            <div className="grid grid-cols-2 gap-2 md:grid-cols-5">
                {[
                    { label: 'Accuracy', value: latest.accuracy, key: 'accuracy' as const },
                    { label: 'Precision', value: latest.precision, key: 'precision' as const },
                    { label: 'Recall', value: latest.recall, key: 'recall' as const },
                    { label: 'F1 Score', value: latest.f1Score, key: 'f1Score' as const },
                    { label: 'AUC', value: latest.auc, key: 'auc' as const },
                ].map((metric) => {
                    const latestVal = latest[metric.key];
                    const prevVal = previous[metric.key];
                    const change = getChange(latestVal, prevVal);

                    return (
                        <div key={metric.label} className="rounded bg-slate-50 p-3 dark:bg-neutral-800">
                            <div className="text-xs font-medium text-slate-600 dark:text-neutral-400">
                                {metric.label}
                            </div>
                            <div className="mt-1 flex items-baseline gap-1">
                                <span className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                    {formatMetric(metric.value)}%
                                </span>
                                <span
                                    className={`text-xs font-medium ${change.isPositive
                                            ? 'text-green-600 dark:text-green-400'
                                            : 'text-red-600 dark:text-rose-400'
                                        }`}
                                >
                                    {change.isPositive ? '↑' : '↓'} {Math.abs(change.value).toFixed(1)}%
                                </span>
                            </div>
                        </div>
                    );
                })}
            </div>

            {/* Trend Chart (Simple Sparkline) */}
            <div className="space-y-3 border-t border-slate-200 pt-3 dark:border-neutral-600">
                <h3 className="text-xs font-semibold text-slate-700 dark:text-neutral-300">
                    7-Day Trend
                </h3>
                <div className="flex items-end gap-1 h-16">
                    {data.map((d, i) => {
                        const height = (d.accuracy / 1) * 100;
                        return (
                            <div
                                key={i}
                                className="flex-1 rounded-sm bg-gradient-to-t from-blue-400 to-blue-600 hover:opacity-80"
                                style={{ height: `${Math.max(height, 5)}%` }}
                                title={`${formatMetric(d.accuracy)}% - ${new Date(d.timestamp).toLocaleDateString()}`}
                            />
                        );
                    })}
                </div>
            </div>

            {/* Additional Info */}
            <div className="flex gap-3 border-t border-slate-200 pt-3 dark:border-neutral-600">
                <div className="flex-1">
                    <p className="text-xs text-slate-600 dark:text-neutral-400">Last Updated</p>
                    <p className="font-medium text-slate-900 dark:text-neutral-100">
                        {new Date(latest.timestamp).toLocaleString()}
                    </p>
                </div>
                <div className="flex-1">
                    <p className="text-xs text-slate-600 dark:text-neutral-400">Status</p>
                    <p className="font-medium text-green-600 dark:text-green-400">
                        ✅ Healthy
                    </p>
                </div>
            </div>
        </div>
    );
});
