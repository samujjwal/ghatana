/**
 * ML Model Card Component
 *
 * <p><b>Purpose</b><br>
 * Displays a single ML model with performance metrics, health score, and quick actions.
 * Supports selection and deployment functionality with dark mode.
 *
 * <p><b>Features</b><br>
 * - Model metadata display
 * - Health score indicator
 * - Performance metrics
 * - Selection state
 * - Deploy action button
 * - Compare action
 * - Dark mode support
 *
 * @doc.type component
 * @doc.purpose ML model card display component
 * @doc.layer product
 * @doc.pattern Display Component
 */

import { memo } from 'react';

export interface ModelData {
    id: string;
    name: string;
    version: number;
    accuracy: number;
    precision: number;
    recall: number;
    f1Score: number;
    lastUpdated: Date;
    deployedAt?: Date;
}

interface MLModelCardProps {
    model: ModelData;
    healthScore: number;
    isSelected: boolean;
    onSelect: (modelId: string) => void;
    onDeploy: (modelId: string) => void;
    onCompare?: (modelId: string) => void;
}

/**
 * MLModelCard component - displays single model with metrics and actions.
 *
 * GIVEN: ML model data with performance metrics
 * WHEN: Component renders with model information
 * THEN: User can select, deploy, or compare model
 *
 * @param model - Model data object
 * @param healthScore - Health score 0-100
 * @param isSelected - Whether model is currently selected
 * @param onSelect - Callback when model is selected
 * @param onDeploy - Callback to deploy model
 * @param onCompare - Optional callback to compare model
 * @returns Rendered model card component
 */
const MLModelCard = memo(
    ({
        model,
        healthScore,
        isSelected,
        onSelect,
        onDeploy,
        onCompare,
    }: MLModelCardProps) => {
        // Determine health status color
        const getHealthColor = (score: number) => {
            if (score >= 90) return 'text-green-600 dark:text-green-400 bg-green-50 dark:bg-green-600/30';
            if (score >= 75) return 'text-yellow-600 dark:text-yellow-400 bg-yellow-50 dark:bg-orange-600/30';
            return 'text-red-600 dark:text-rose-400 bg-red-50 dark:bg-rose-600/30';
        };

        const getHealthBorder = (score: number) => {
            if (score >= 90) return 'border-green-200 dark:border-green-800';
            if (score >= 75) return 'border-yellow-200 dark:border-yellow-800';
            return 'border-red-200 dark:border-red-800';
        };

        return (
            <div
                className={`rounded-lg border-2 transition-all cursor-pointer ${isSelected
                    ? 'border-blue-500 bg-blue-50 dark:bg-indigo-600/30 shadow-lg'
                    : `${getHealthBorder(
                        healthScore
                    )} bg-white dark:bg-neutral-800 hover:shadow-md`
                    }`}
                onClick={() => onSelect(model.id)}
                role="button"
                tabIndex={0}
                aria-label={`Model: ${model.name}`}
                aria-pressed={isSelected}
                onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        onSelect(model.id);
                    }
                }}
            >
                {/* Card Content */}
                <div className="p-4">
                    {/* Header */}
                    <div className="flex justify-between items-start mb-4">
                        <div className="flex-1">
                            <h3 className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                {model.name}
                            </h3>
                            <p className="text-sm text-slate-600 dark:text-neutral-400">
                                v{model.version}
                            </p>
                        </div>
                        <div
                            className={`text-right ${getHealthColor(healthScore)}`}
                            role="status"
                            aria-label={`Health score: ${healthScore}`}
                        >
                            <div className="text-2xl font-bold">{healthScore}</div>
                            <div className="text-xs font-medium">Health</div>
                        </div>
                    </div>

                    {/* Performance Metrics */}
                    <div className="grid grid-cols-2 gap-2 mb-4 text-sm">
                        <div className="bg-slate-50 dark:bg-neutral-700 rounded p-2">
                            <div className="text-xs text-slate-600 dark:text-neutral-400 font-medium">
                                Accuracy
                            </div>
                            <div className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                {(model.accuracy * 100).toFixed(1)}%
                            </div>
                        </div>
                        <div className="bg-slate-50 dark:bg-neutral-700 rounded p-2">
                            <div className="text-xs text-slate-600 dark:text-neutral-400 font-medium">
                                F1 Score
                            </div>
                            <div className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                {model.f1Score.toFixed(3)}
                            </div>
                        </div>
                        <div className="bg-slate-50 dark:bg-neutral-700 rounded p-2">
                            <div className="text-xs text-slate-600 dark:text-neutral-400 font-medium">
                                Precision
                            </div>
                            <div className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                {(model.precision * 100).toFixed(1)}%
                            </div>
                        </div>
                        <div className="bg-slate-50 dark:bg-neutral-700 rounded p-2">
                            <div className="text-xs text-slate-600 dark:text-neutral-400 font-medium">
                                Recall
                            </div>
                            <div className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                                {(model.recall * 100).toFixed(1)}%
                            </div>
                        </div>
                    </div>

                    {/* Metadata */}
                    <div className="mb-4 text-xs text-slate-600 dark:text-neutral-400 space-y-1">
                        {(() => {
                            const lastUpdatedDate = model.lastUpdated instanceof Date ? model.lastUpdated : new Date(model.lastUpdated as any);
                            const deployedAtDate = model.deployedAt ? (model.deployedAt instanceof Date ? model.deployedAt : new Date(model.deployedAt as any)) : null;
                            return (
                                <>
                                    <div>
                                        Last Updated: {isNaN(lastUpdatedDate.getTime()) ? '—' : lastUpdatedDate.toLocaleDateString()}
                                    </div>
                                    {deployedAtDate && (
                                        <div>
                                            Deployed: {isNaN(deployedAtDate.getTime()) ? '—' : deployedAtDate.toLocaleDateString()}
                                        </div>
                                    )}
                                </>
                            );
                        })()}
                    </div>

                    {/* Actions */}
                    <div className="flex gap-2">
                        <button
                            onClick={(e) => {
                                e.stopPropagation();
                                onDeploy(model.id);
                            }}
                            className="flex-1 px-3 py-2 bg-blue-500 text-white rounded text-sm font-medium hover:bg-blue-600 transition-colors"
                            aria-label={`Deploy ${model.name}`}
                        >
                            Deploy
                        </button>
                        {onCompare && (
                            <button
                                onClick={(e) => {
                                    e.stopPropagation();
                                    onCompare(model.id);
                                }}
                                className="px-3 py-2 bg-slate-200 dark:bg-neutral-700 text-slate-900 dark:text-neutral-100 rounded text-sm font-medium hover:bg-slate-300 dark:hover:bg-slate-600 transition-colors"
                                aria-label={`Compare ${model.name}`}
                            >
                                Compare
                            </button>
                        )}
                    </div>
                </div>
            </div>
        );
    }
);

MLModelCard.displayName = 'MLModelCard';

export default MLModelCard;
