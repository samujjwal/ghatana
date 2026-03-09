import { memo } from 'react';

/**
 * A/B test results and champion/challenger model comparison.
 *
 * <p><b>Purpose</b><br>
 * Displays results from A/B tests comparing model versions.
 * Shows statistical significance and business impact metrics.
 *
 * <p><b>Features</b><br>
 * - Test status (running, completed, paused)
 * - Metric comparison (champion vs challenger)
 * - Statistical significance indicators
 * - Confidence intervals
 * - Recommendation engine
 *
 * @doc.type component
 * @doc.purpose A/B testing and model comparison
 * @doc.layer product
 * @doc.pattern Organism
 */

interface TestMetrics {
    name: string;
    champion: number;
    challenger: number;
    difference: number;
    pValue: number;
    confidenceInterval: [number, number];
}

interface ABTestResult {
    id: string;
    name: string;
    status: 'running' | 'completed' | 'paused';
    startDate: string;
    endDate?: string;
    sampleSize: number;
    metrics: TestMetrics[];
    winner?: 'champion' | 'challenger' | 'no-significant-difference';
}

interface ABTestResultsProps {
    tests?: ABTestResult[];
    onSelectTest?: (test: ABTestResult) => void;
}

export const ABTestResults = memo(function ABTestResults({
    tests,
    onSelectTest,
}: ABTestResultsProps) {
    // Mock data if none provided
    const data = tests || [
        {
            id: 'test-001',
            name: 'Model v2.1 vs Current',
            status: 'completed',
            startDate: new Date(Date.now() - 14 * 24 * 60 * 60 * 1000).toISOString(),
            endDate: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString(),
            sampleSize: 50000,
            metrics: [
                {
                    name: 'Accuracy',
                    champion: 0.925,
                    challenger: 0.938,
                    difference: 0.013,
                    pValue: 0.002,
                    confidenceInterval: [0.008, 0.018],
                },
                {
                    name: 'Precision',
                    champion: 0.906,
                    challenger: 0.912,
                    difference: 0.006,
                    pValue: 0.156,
                    confidenceInterval: [-0.002, 0.014],
                },
                {
                    name: 'Recall',
                    champion: 0.934,
                    challenger: 0.947,
                    difference: 0.013,
                    pValue: 0.001,
                    confidenceInterval: [0.007, 0.019],
                },
            ],
            winner: 'challenger',
        },
        {
            id: 'test-002',
            name: 'Model v2.2 Feature Experiment',
            status: 'running',
            startDate: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString(),
            sampleSize: 25000,
            metrics: [
                {
                    name: 'Accuracy',
                    champion: 0.925,
                    challenger: 0.926,
                    difference: 0.001,
                    pValue: 0.42,
                    confidenceInterval: [-0.003, 0.005],
                },
            ],
        },
    ];

    const getStatusIcon = (status: ABTestResult['status']) => {
        switch (status) {
            case 'completed':
                return '✅';
            case 'running':
                return '🔄';
            case 'paused':
                return '⏸️';
            default:
                return '•';
        }
    };

    const getSignificanceLabel = (pValue: number) => {
        if (pValue < 0.001) return '***';
        if (pValue < 0.01) return '**';
        if (pValue < 0.05) return '*';
        return 'ns';
    };

    const isSignificant = (pValue: number) => pValue < 0.05;

    return (
        <div className="space-y-4 rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
            {/* Header */}
            <div className="space-y-2">
                <h2 className="text-sm font-semibold text-slate-900 dark:text-neutral-100">
                    🧪 A/B Test Results
                </h2>
                <p className="text-xs text-slate-600 dark:text-neutral-400">
                    Champion vs Challenger Models
                </p>
            </div>

            {/* Test List */}
            <div className="space-y-4">
                {data.map((test) => (
                    <button
                        key={test.id}
                        onClick={() => onSelectTest?.(test)}
                        className="w-full space-y-3 rounded border border-slate-200 p-4 text-left hover:bg-slate-50 dark:border-neutral-600 dark:hover:bg-slate-800"
                    >
                        {/* Test Header */}
                        <div className="flex items-center justify-between">
                            <div className="flex items-center gap-2">
                                <span>{getStatusIcon(test.status)}</span>
                                <div>
                                    <h3 className="font-medium text-slate-900 dark:text-neutral-100">
                                        {test.name}
                                    </h3>
                                    <p className="text-xs text-slate-600 dark:text-neutral-400">
                                        {test.sampleSize.toLocaleString()} samples
                                    </p>
                                </div>
                            </div>
                            {test.winner && (
                                <div className="rounded bg-green-50 px-2 py-1 dark:bg-green-600/30">
                                    <p className="text-xs font-medium text-green-700 dark:text-green-400">
                                        {test.winner === 'challenger' ? '🏆 Challenger' : '✅ Champion'}
                                    </p>
                                </div>
                            )}
                        </div>

                        {/* Test Dates */}
                        <div className="flex gap-4 text-xs text-slate-600 dark:text-neutral-400">
                            <span>Started: {new Date(test.startDate).toLocaleDateString()}</span>
                            {test.endDate && (
                                <span>Ended: {new Date(test.endDate).toLocaleDateString()}</span>
                            )}
                        </div>

                        {/* Metrics Summary */}
                        <div className="space-y-2">
                            {test.metrics.map((metric) => {
                                const sig = isSignificant(metric.pValue);

                                return (
                                    <div
                                        key={metric.name}
                                        className="grid grid-cols-4 gap-2 rounded bg-slate-50 p-2 text-xs dark:bg-neutral-800"
                                    >
                                        <div className="font-medium text-slate-700 dark:text-neutral-300">
                                            {metric.name}
                                        </div>
                                        <div className="text-slate-600 dark:text-neutral-400">
                                            Champ: {(metric.champion * 100).toFixed(1)}%
                                        </div>
                                        <div className="text-slate-600 dark:text-neutral-400">
                                            Chal: {(metric.challenger * 100).toFixed(1)}%
                                        </div>
                                        <div
                                            className={`text-right font-medium ${sig
                                                    ? metric.difference > 0
                                                        ? 'text-green-600 dark:text-green-400'
                                                        : 'text-red-600 dark:text-rose-400'
                                                    : 'text-slate-600 dark:text-neutral-400'
                                                }`}
                                        >
                                            {metric.difference > 0 ? '+' : ''}
                                            {(metric.difference * 100).toFixed(1)}%{' '}
                                            <span className="text-xs">{getSignificanceLabel(metric.pValue)}</span>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>

                        {/* Significance Legend */}
                        <div className="text-xs text-slate-500 dark:text-neutral-400">
                            *** p&lt;0.001 ** p&lt;0.01 * p&lt;0.05 ns = not significant
                        </div>
                    </button>
                ))}
            </div>

            {data.length === 0 && (
                <div className="rounded bg-slate-50 p-4 text-center dark:bg-neutral-800">
                    <p className="text-sm text-slate-600 dark:text-neutral-400">
                        No A/B tests found
                    </p>
                </div>
            )}
        </div>
    );
});
