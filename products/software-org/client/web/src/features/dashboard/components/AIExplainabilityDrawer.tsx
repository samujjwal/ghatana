import { memo, useState } from 'react';

/**
 * AI explainability drawer showing decision reasoning and model information.
 *
 * <p><b>Purpose</b><br>
 * Provides transparent explanations for AI-generated recommendations and predictions.
 * Shows feature importance, confidence breakdown, and model metadata.
 *
 * <p><b>Features</b><br>
 * - Feature importance visualization
 * - Confidence score with calibration
 * - Model version and training date
 * - Decision confidence factors
 * - Alternative predictions ranked by probability
 *
 * <p><b>Interactions</b><br>
 * - Expand/collapse sections
 * - Hover over features for details
 * - View model training info
 * - Compare with alternative predictions
 *
 * @doc.type component
 * @doc.purpose AI decision explainability interface
 * @doc.layer product
 * @doc.pattern Drawer
 */

interface FeatureImportance {
    name: string;
    value: number;
    contribution: 'positive' | 'negative' | 'neutral';
}

interface ModelInfo {
    name: string;
    version: string;
    trainedAt: string;
    accuracy: number;
    features: number;
}

interface AlternativePrediction {
    label: string;
    probability: number;
    reasoning: string;
}

interface AIExplainabilityDrawerProps {
    prediction: string;
    confidence: number;
    modelInfo: ModelInfo;
    features: FeatureImportance[];
    alternatives: AlternativePrediction[];
    onClose: () => void;
}

export const AIExplainabilityDrawer = memo(function AIExplainabilityDrawer({
    prediction,
    confidence,
    modelInfo,
    features,
    alternatives,
    onClose,
}: AIExplainabilityDrawerProps) {
    const [expandedSections, setExpandedSections] = useState<Record<string, boolean>>({
        features: true,
        model: false,
        alternatives: false,
    });

    const toggleSection = (section: string) => {
        setExpandedSections((prev) => ({
            ...prev,
            [section]: !prev[section],
        }));
    };

    const sortedFeatures = [...features].sort(
        (a, b) => Math.abs(b.value) - Math.abs(a.value)
    );
    const maxAbsValue = Math.max(...sortedFeatures.map((f) => Math.abs(f.value)));

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            {/* Drawer */}
            <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-lg bg-white shadow-xl dark:bg-slate-900">
                {/* Header */}
                <div className="sticky top-0 flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4 dark:border-neutral-600 dark:bg-slate-900">
                    <div className="space-y-1">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                            AI Decision Explanation
                        </h2>
                        <p className="text-sm text-slate-600 dark:text-neutral-400">
                            Understand why this prediction was made
                        </p>
                    </div>
                    <button
                        onClick={onClose}
                        className="text-slate-500 hover:text-slate-700 dark:text-neutral-400 dark:hover:text-slate-200"
                    >
                        ✕
                    </button>
                </div>

                {/* Content */}
                <div className="space-y-6 p-6">
                    {/* Prediction Summary */}
                    <div className="rounded-lg bg-slate-50 p-4 dark:bg-neutral-800">
                        <div className="space-y-3">
                            <div className="flex items-center justify-between">
                                <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                                    Prediction
                                </span>
                                <div className="flex items-center gap-2">
                                    <span className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                                        {prediction}
                                    </span>
                                </div>
                            </div>

                            <div>
                                <div className="flex items-center justify-between mb-1">
                                    <span className="text-xs font-medium text-slate-600 dark:text-neutral-400">
                                        Confidence Score
                                    </span>
                                    <span className="text-sm font-semibold text-slate-900 dark:text-neutral-100">
                                        {Math.round(confidence * 100)}%
                                    </span>
                                </div>
                                <div className="h-2 w-full rounded-full bg-slate-200 dark:bg-neutral-700">
                                    <div
                                        className="h-full rounded-full bg-gradient-to-r from-green-400 to-green-600"
                                        style={{ width: `${confidence * 100}%` }}
                                    />
                                </div>
                            </div>

                            <p className="text-xs text-slate-600 dark:text-neutral-400">
                                This prediction is based on {modelInfo.features} features analyzed by
                                the {modelInfo.name} model (v{modelInfo.version})
                            </p>
                        </div>
                    </div>

                    {/* Feature Importance */}
                    <div className="border-t border-slate-200 dark:border-neutral-600">
                        <button
                            onClick={() => toggleSection('features')}
                            className="flex w-full items-center justify-between py-3 hover:opacity-70"
                        >
                            <h3 className="text-sm font-semibold text-slate-900 dark:text-neutral-100">
                                📊 Feature Importance (Top 10)
                            </h3>
                            <span className="text-slate-500 dark:text-neutral-400">
                                {expandedSections.features ? '▼' : '▶'}
                            </span>
                        </button>

                        {expandedSections.features && (
                            <div className="space-y-2 pb-4">
                                {sortedFeatures.slice(0, 10).map((feature, i) => {
                                    const percentage = (Math.abs(feature.value) / maxAbsValue) * 100;
                                    const isPositive = feature.contribution === 'positive';

                                    return (
                                        <div key={i} className="space-y-1">
                                            <div className="flex items-center justify-between text-xs">
                                                <span className="text-slate-700 dark:text-neutral-300">
                                                    {feature.name}
                                                </span>
                                                <span
                                                    className={`font-medium ${isPositive
                                                            ? 'text-green-600 dark:text-green-400'
                                                            : 'text-red-600 dark:text-rose-400'
                                                        }`}
                                                >
                                                    {isPositive ? '+' : '−'}{Math.abs(feature.value).toFixed(3)}
                                                </span>
                                            </div>
                                            <div className="h-1.5 w-full rounded-full bg-slate-200 dark:bg-neutral-700">
                                                <div
                                                    className={`h-full rounded-full ${isPositive
                                                            ? 'bg-green-500'
                                                            : 'bg-red-500'
                                                        }`}
                                                    style={{ width: `${percentage}%` }}
                                                />
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>

                    {/* Model Information */}
                    <div className="border-t border-slate-200 dark:border-neutral-600">
                        <button
                            onClick={() => toggleSection('model')}
                            className="flex w-full items-center justify-between py-3 hover:opacity-70"
                        >
                            <h3 className="text-sm font-semibold text-slate-900 dark:text-neutral-100">
                                ⚙️ Model Information
                            </h3>
                            <span className="text-slate-500 dark:text-neutral-400">
                                {expandedSections.model ? '▼' : '▶'}
                            </span>
                        </button>

                        {expandedSections.model && (
                            <div className="grid grid-cols-2 gap-3 pb-4 text-xs">
                                <div className="rounded bg-slate-50 p-2 dark:bg-neutral-800">
                                    <div className="text-slate-600 dark:text-neutral-400">Model</div>
                                    <div className="font-semibold text-slate-900 dark:text-neutral-100">
                                        {modelInfo.name}
                                    </div>
                                </div>
                                <div className="rounded bg-slate-50 p-2 dark:bg-neutral-800">
                                    <div className="text-slate-600 dark:text-neutral-400">Version</div>
                                    <div className="font-semibold text-slate-900 dark:text-neutral-100">
                                        v{modelInfo.version}
                                    </div>
                                </div>
                                <div className="rounded bg-slate-50 p-2 dark:bg-neutral-800">
                                    <div className="text-slate-600 dark:text-neutral-400">Trained</div>
                                    <div className="font-semibold text-slate-900 dark:text-neutral-100">
                                        {new Date(modelInfo.trainedAt).toLocaleDateString()}
                                    </div>
                                </div>
                                <div className="rounded bg-slate-50 p-2 dark:bg-neutral-800">
                                    <div className="text-slate-600 dark:text-neutral-400">Accuracy</div>
                                    <div className="font-semibold text-slate-900 dark:text-neutral-100">
                                        {Math.round(modelInfo.accuracy * 100)}%
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>

                    {/* Alternative Predictions */}
                    {alternatives.length > 0 && (
                        <div className="border-t border-slate-200 dark:border-neutral-600">
                            <button
                                onClick={() => toggleSection('alternatives')}
                                className="flex w-full items-center justify-between py-3 hover:opacity-70"
                            >
                                <h3 className="text-sm font-semibold text-slate-900 dark:text-neutral-100">
                                    🔄 Alternative Predictions ({alternatives.length})
                                </h3>
                                <span className="text-slate-500 dark:text-neutral-400">
                                    {expandedSections.alternatives ? '▼' : '▶'}
                                </span>
                            </button>

                            {expandedSections.alternatives && (
                                <div className="space-y-2 pb-4">
                                    {alternatives.map((alt, i) => (
                                        <div key={i} className="rounded bg-slate-50 p-2 dark:bg-neutral-800">
                                            <div className="flex items-center justify-between">
                                                <span className="text-xs font-medium text-slate-700 dark:text-neutral-300">
                                                    {alt.label}
                                                </span>
                                                <span className="text-xs font-semibold text-slate-900 dark:text-neutral-100">
                                                    {Math.round(alt.probability * 100)}%
                                                </span>
                                            </div>
                                            <p className="mt-1 text-xs text-slate-600 dark:text-neutral-400">
                                                {alt.reasoning}
                                            </p>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="border-t border-slate-200 bg-slate-50 px-6 py-3 dark:border-neutral-600 dark:bg-neutral-800">
                    <button
                        onClick={onClose}
                        className="w-full rounded bg-slate-200 px-4 py-2 text-sm font-medium text-slate-900 hover:bg-slate-300 dark:bg-neutral-700 dark:text-neutral-100 dark:hover:bg-slate-600"
                    >
                        Close
                    </button>
                </div>
            </div>
        </div>
    );
});
