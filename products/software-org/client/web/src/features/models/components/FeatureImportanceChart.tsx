/**
 * Feature Importance Chart Component
 *
 * <p><b>Purpose</b><br>
 * Displays horizontal bar chart showing feature importance rankings for ML models.
 * Helps identify which features most influence model predictions.
 *
 * <p><b>Features</b><br>
 * - Horizontal bar visualization
 * - Feature ranking with scores
 * - Responsive design
 * - Dark mode support
 * - Sortable by importance
 * - Percentage display
 *
 * @doc.type component
 * @doc.purpose Feature importance visualization component
 * @doc.layer product
 * @doc.pattern Display Component
 */

import { memo, useMemo } from 'react';

export interface FeatureImportance {
    name: string;
    importance: number;
    category?: string;
}

interface FeatureImportanceChartProps {
    data: FeatureImportance[];
    modelName: string;
    maxFeatures?: number;
}

/**
 * FeatureImportanceChart component - displays feature importance rankings.
 *
 * GIVEN: Feature importance data from ML model
 * WHEN: Component renders
 * THEN: Display sorted bar chart with importance scores
 *
 * @param data - Array of feature importance data
 * @param modelName - Model name for display
 * @param maxFeatures - Maximum features to show (default: 10)
 * @returns Rendered feature importance chart
 */
const FeatureImportanceChart = memo(
    ({ data, modelName, maxFeatures = 10 }: FeatureImportanceChartProps) => {
        // Sort and limit features
        const sortedFeatures = useMemo(() => {
            return [...data]
                .sort((a, b) => b.importance - a.importance)
                .slice(0, maxFeatures);
        }, [data, maxFeatures]);

        // Find max importance for scaling
        const maxImportance = useMemo(
            () => Math.max(...sortedFeatures.map((f) => f.importance), 1),
            [sortedFeatures]
        );

        // Get color based on importance level
        const getImportanceColor = (importance: number, max: number) => {
            const ratio = importance / max;
            if (ratio > 0.7) return 'bg-red-500 dark:bg-red-600';
            if (ratio > 0.4) return 'bg-yellow-500 dark:bg-yellow-600';
            return 'bg-blue-500 dark:bg-blue-600';
        };

        return (
            <div
                className="rounded-lg bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 p-6"
                role="region"
                aria-label={`Feature importance for ${modelName}`}
            >
                {/* Header */}
                <h3 className="text-lg font-bold text-slate-900 dark:text-neutral-100 mb-2">
                    Feature Importance
                </h3>
                <p className="text-sm text-slate-600 dark:text-neutral-400 mb-6">
                    {modelName} - Top {sortedFeatures.length} features
                </p>

                {/* Chart */}
                <div className="space-y-4">
                    {sortedFeatures.map((feature, idx) => {
                        const percentage = (feature.importance / maxImportance) * 100;
                        return (
                            <div key={idx}>
                                {/* Feature Label */}
                                <div className="flex justify-between items-center mb-2">
                                    <div>
                                        <span className="font-medium text-slate-900 dark:text-neutral-100">
                                            {feature.name}
                                        </span>
                                        {feature.category && (
                                            <span className="ml-2 text-xs bg-slate-200 dark:bg-neutral-700 text-slate-700 dark:text-neutral-300 px-2 py-0.5 rounded">
                                                {feature.category}
                                            </span>
                                        )}
                                    </div>
                                    <span className="font-mono text-sm font-bold text-slate-600 dark:text-neutral-400">
                                        {feature.importance.toFixed(4)}
                                    </span>
                                </div>

                                {/* Bar Container */}
                                <div className="bg-slate-100 dark:bg-neutral-700 rounded-full h-6 overflow-hidden">
                                    {/* Bar */}
                                    <div
                                        className={`h-full flex items-center justify-end pr-2 transition-all duration-300 ${getImportanceColor(
                                            feature.importance,
                                            maxImportance
                                        )}`}
                                        style={{ width: `${percentage}%` }}
                                        role="progressbar"
                                        aria-valuenow={percentage}
                                        aria-valuemin={0}
                                        aria-valuemax={100}
                                        aria-label={`${feature.name} importance: ${percentage.toFixed(1)}%`}
                                    >
                                        <span className="text-xs font-bold text-white">
                                            {percentage > 10 && `${percentage.toFixed(0)}%`}
                                        </span>
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </div>

                {/* Footer Stats */}
                <div className="mt-6 pt-4 border-t border-slate-200 dark:border-neutral-600">
                    <div className="grid grid-cols-3 gap-4 text-sm">
                        <div>
                            <div className="text-slate-600 dark:text-neutral-400 font-medium">
                                Features Analyzed
                            </div>
                            <div className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                {data.length}
                            </div>
                        </div>
                        <div>
                            <div className="text-slate-600 dark:text-neutral-400 font-medium">
                                Top Feature
                            </div>
                            <div className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                {sortedFeatures[0]?.name || 'N/A'}
                            </div>
                        </div>
                        <div>
                            <div className="text-slate-600 dark:text-neutral-400 font-medium">
                                Cumulative Importance
                            </div>
                            <div className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                {(
                                    sortedFeatures.reduce((sum, f) => sum + f.importance, 0) *
                                    100
                                ).toFixed(1)}
                                %
                            </div>
                        </div>
                    </div>
                </div>

                {/* Legend */}
                <div className="mt-4 text-xs text-slate-600 dark:text-neutral-400 space-y-1">
                    <div className="flex items-center gap-2">
                        <div className="w-3 h-3 rounded bg-red-500" />
                        <span>High Importance (&gt; 70%)</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <div className="w-3 h-3 rounded bg-yellow-500" />
                        <span>Medium Importance (40-70%)</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <div className="w-3 h-3 rounded bg-blue-500" />
                        <span>Low Importance (&lt; 40%)</span>
                    </div>
                </div>
            </div>
        );
    }
);

FeatureImportanceChart.displayName = 'FeatureImportanceChart';

export default FeatureImportanceChart;
