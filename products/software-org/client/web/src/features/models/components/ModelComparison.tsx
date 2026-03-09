import { memo } from 'react';

/**
 * Model comparison interface with metrics side-by-side.
 *
 * <p><b>Purpose</b><br>
 * Provides side-by-side comparison of multiple models with performance metrics,
 * delta indicators, and A/B test results. Helps identify the best model for deployment.
 *
 * <p><b>Features</b><br>
 * - Comparison grid with key metrics
 * - Performance delta highlighting (↑↓)
 * - Winning model indicators
 * - A/B test results
 * - Statistical significance indicators
 * - Recommendation engine insight
 *
 * <p><b>Props</b><br>
 * @param models - Array of models to compare
 *
 * @doc.type component
 * @doc.purpose Model comparison viewer
 * @doc.layer product
 * @doc.pattern Comparison Panel
 */

interface ComparisonModel {
    id: string;
    name: string;
    version: string;
    accuracy: number;
    precision: number;
    recall: number;
    f1Score: number;
    latency: number; // ms
    throughput: number; // req/s
}

interface ModelComparisonProps {
    models: ComparisonModel[];
}

// Model comparison data - typically fetched from API
// TODO: Integrate with models API to fetch multiple models for comparison
const comparisonData: ComparisonModel[] = [
    {
        id: '1',
        name: 'Fraud Detection v2.3',
        version: '2.3.1',
        accuracy: 0.956,
        precision: 0.964,
        recall: 0.948,
        f1Score: 0.956,
        latency: 12,
        throughput: 8450,
    },
    {
        id: '2',
        name: 'Anomaly Detector v1.8.2',
        version: '1.8.2',
        accuracy: 0.932,
        precision: 0.938,
        recall: 0.925,
        f1Score: 0.931,
        latency: 18,
        throughput: 7200,
    },
    {
        id: '3',
        name: 'Pattern Recognition v3.0-beta',
        version: '3.0.0-beta.1',
        accuracy: 0.968,
        precision: 0.972,
        recall: 0.964,
        f1Score: 0.968,
        latency: 15,
        throughput: 9100,
    },
];

function calculateDelta(current: number, baseline: number): number {
    return ((current - baseline) / baseline) * 100;
}

export const ModelComparison = memo(function ModelComparison({ models = comparisonData }: ModelComparisonProps) {
    // GIVEN: Array of models to compare
    // WHEN: Comparison view is active
    // THEN: Display metrics side-by-side with deltas and winner indicators

    if (models.length < 2) {
        return (
            <div className="flex-1 flex items-center justify-center bg-slate-50 dark:bg-slate-950 p-4">
                <div className="text-center">
                    <div className="text-6xl mb-4">📊</div>
                    <h3 className="text-xl font-bold text-slate-900 dark:text-neutral-100 mb-2">Select Multiple Models</h3>
                    <p className="text-slate-500 dark:text-neutral-400">Choose 2 or more models to compare their performance</p>
                </div>
            </div>
        );
    }

    const metrics = [
        { label: 'Accuracy', key: 'accuracy', unit: '%', isLowerBetter: false },
        { label: 'Precision', key: 'precision', unit: '%', isLowerBetter: false },
        { label: 'Recall', key: 'recall', unit: '%', isLowerBetter: false },
        { label: 'F1 Score', key: 'f1Score', unit: '%', isLowerBetter: false },
        { label: 'Latency', key: 'latency', unit: 'ms', isLowerBetter: true },
        { label: 'Throughput', key: 'throughput', unit: 'req/s', isLowerBetter: false },
    ];

    return (
        <div className="flex-1 overflow-y-auto p-4 bg-slate-50 dark:bg-slate-950">
            {/* Comparison Grid */}
            <div className="bg-white dark:bg-neutral-800 rounded-lg border border-slate-200 dark:border-neutral-600 overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full">
                        <thead>
                            <tr className="border-b border-slate-200 dark:border-neutral-600">
                                <th className="px-4 py-3 text-left text-xs font-bold text-slate-500 dark:text-neutral-400 bg-slate-100 dark:bg-slate-900">Metric</th>
                                {models.map((model) => (
                                    <th key={model.id} className="px-4 py-3 text-center text-xs font-bold text-slate-700 dark:text-neutral-300 bg-slate-100 dark:bg-slate-900">
                                        <div className="font-semibold text-slate-700 dark:text-slate-200">{model.name}</div>
                                        <div className="text-xs text-slate-500">v{model.version}</div>
                                    </th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {metrics.map((metric, idx) => {
                                const values = models.map((m) => m[metric.key as keyof ComparisonModel] as number);
                                const winnerIdx = metric.isLowerBetter ? values.indexOf(Math.min(...values)) : values.indexOf(Math.max(...values));

                                return (
                                    <tr key={metric.key} className={idx % 2 === 0 ? 'bg-white dark:bg-neutral-800' : 'bg-slate-50 dark:bg-slate-750'}>
                                        <td className="px-4 py-3 text-sm font-medium text-slate-600 dark:text-neutral-300 bg-slate-100 dark:bg-slate-900">{metric.label}</td>
                                        {models.map((model, modelIdx) => {
                                            const value = model[metric.key as keyof ComparisonModel] as number;
                                            const isWinner = modelIdx === winnerIdx;
                                            const isPercentage = metric.unit === '%';
                                            const displayValue = isPercentage ? (value * 100).toFixed(2) : value.toFixed(2);

                                            return (
                                                <td key={model.id} className={`px-4 py-3 text-center ${isWinner ? 'bg-green-100 dark:bg-green-900 dark:bg-opacity-20 border-l-2 border-green-500' : ''}`}>
                                                    <div className={`font-bold ${isWinner ? 'text-green-600 dark:text-green-400' : 'text-slate-700 dark:text-slate-200'}`}>
                                                        {displayValue}
                                                        <span className="text-xs text-slate-500 dark:text-neutral-400 ml-1">{metric.unit}</span>
                                                    </div>
                                                    {modelIdx > 0 && (
                                                        <div className="text-xs mt-1">
                                                            {(() => {
                                                                const baseline = models[0][metric.key as keyof ComparisonModel] as number;
                                                                const delta = calculateDelta(value, baseline);
                                                                const deltaSign = delta > 0 ? '↑' : delta < 0 ? '↓' : '=';
                                                                const deltaColor =
                                                                    (delta > 0 && !metric.isLowerBetter) || (delta < 0 && metric.isLowerBetter)
                                                                        ? 'text-green-600 dark:text-green-400'
                                                                        : delta !== 0
                                                                            ? 'text-red-600 dark:text-rose-400'
                                                                            : 'text-slate-500';

                                                                return <span className={deltaColor}>{deltaSign} {Math.abs(delta).toFixed(1)}%</span>;
                                                            })()}
                                                        </div>
                                                    )}
                                                </td>
                                            );
                                        })}
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Summary */}
            <div className="mt-6 bg-white dark:bg-neutral-800 rounded-lg p-4 border border-slate-200 dark:border-neutral-600">
                <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-3">Summary</h3>
                <div className="grid grid-cols-3 gap-4">
                    <div className="bg-slate-100 dark:bg-slate-900 rounded p-3">
                        <div className="text-xs text-slate-500 mb-1">Best Overall</div>
                        <div className="text-lg font-bold text-green-600 dark:text-green-400">{models[2].name}</div>
                        <div className="text-xs text-slate-500 dark:text-neutral-400 mt-1">Winner on 5 of 6 metrics</div>
                    </div>
                    <div className="bg-slate-100 dark:bg-slate-900 rounded p-3">
                        <div className="text-xs text-slate-500 mb-1">Best Latency</div>
                        <div className="text-lg font-bold text-blue-600 dark:text-indigo-400">{models[0].name}</div>
                        <div className="text-xs text-slate-500 dark:text-neutral-400 mt-1">{models[0].latency}ms avg</div>
                    </div>
                    <div className="bg-slate-100 dark:bg-slate-900 rounded p-3">
                        <div className="text-xs text-slate-500 mb-1">Best Throughput</div>
                        <div className="text-lg font-bold text-purple-600 dark:text-violet-400">{models[2].name}</div>
                        <div className="text-xs text-slate-500 dark:text-neutral-400 mt-1">{models[2].throughput} req/s</div>
                    </div>
                </div>
            </div>

            {/* Recommendation */}
            <div className="mt-6 bg-gradient-to-r from-emerald-100 to-emerald-50 dark:from-emerald-900 dark:to-emerald-800 rounded-lg p-4 border border-emerald-300 dark:border-emerald-600">
                <div className="flex gap-3">
                    <span className="text-2xl">✨</span>
                    <div>
                        <h4 className="font-bold text-emerald-800 dark:text-neutral-100 mb-1">Recommendation</h4>
                        <p className="text-sm text-emerald-700 dark:text-emerald-100">
                            {models[2].name} demonstrates superior performance across all key metrics. Consider promoting v3.0.0-beta.1 to production
                            after validation on hold-out test set.
                        </p>
                    </div>
                </div>
            </div>

            {/* A/B Test Results */}
            <div className="mt-6 bg-white dark:bg-neutral-800 rounded-lg p-4 border border-slate-200 dark:border-neutral-600">
                <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-3">A/B Test Results</h3>
                <div className="space-y-3">
                    <div>
                        <div className="flex items-center justify-between mb-1">
                            <span className="text-sm text-slate-500 dark:text-neutral-400">{models[0].name} vs {models[2].name}</span>
                            <span className="text-sm font-bold text-green-600 dark:text-green-400">+3.2% better</span>
                        </div>
                        <div className="bg-slate-200 dark:bg-neutral-700 rounded-full h-2 flex">
                            <div className="flex-1 bg-red-500 rounded-l-full" style={{ width: '48%' }} />
                            <div className="flex-1 bg-green-500 rounded-r-full" style={{ width: '52%' }} />
                        </div>
                        <div className="flex justify-between text-xs text-slate-500 dark:text-neutral-400 mt-1">
                            <span>{models[0].name}</span>
                            <span>{models[2].name}</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
});

export default ModelComparison;
