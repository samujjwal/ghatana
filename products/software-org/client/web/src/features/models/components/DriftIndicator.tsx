/**
 * Drift Indicator Component
 *
 * <p><b>Purpose</b><br>
 * Displays drift detection status for ML models with visual indicators, metrics,
 * and severity levels. Used in ML Observatory for monitoring model performance degradation.
 *
 * <p><b>Features</b><br>
 * - Drift detection status visualization
 * - Severity indicator with colors
 * - Historical trend indicator
 * - Quick statistics
 * - Dark mode support
 * - Loading state support
 *
 * @doc.type component
 * @doc.purpose Drift detection status display component
 * @doc.layer product
 * @doc.pattern Display Component
 */

import { memo } from 'react';

export interface DriftData {
    modelId: string;
    detected: boolean;
    severity: 'low' | 'medium' | 'high' | 'critical';
    driftScore: number;
    features: Array<{
        name: string;
        drift: number;
        severity: string;
    }>;
    detectedAt: Date;
    recommendation: string;
}

interface DriftIndicatorProps {
    modelId: string;
    modelName: string;
    driftData?: DriftData;
    isLoading?: boolean;
}

/**
 * DriftIndicator component - displays model drift detection status.
 *
 * GIVEN: Drift detection data for ML model
 * WHEN: Component renders
 * THEN: Display drift status with visual indicators and metrics
 *
 * @param modelId - Model identifier
 * @param modelName - Model display name
 * @param driftData - Drift detection results
 * @param isLoading - Loading state indicator
 * @returns Rendered drift indicator component
 */
const DriftIndicator = memo(
    ({ modelName, driftData, isLoading }: DriftIndicatorProps) => {
        // Determine color scheme based on severity
        const getSeverityColor = (severity?: string) => {
            switch (severity) {
                case 'critical':
                    return 'text-red-600 dark:text-rose-400 bg-red-50 dark:bg-rose-600/30 border-red-200 dark:border-red-800';
                case 'high':
                    return 'text-orange-600 dark:text-orange-400 bg-orange-50 dark:bg-orange-500/10 border-orange-200 dark:border-orange-800';
                case 'medium':
                    return 'text-yellow-600 dark:text-yellow-400 bg-yellow-50 dark:bg-orange-600/30 border-yellow-200 dark:border-yellow-800';
                case 'low':
                    return 'text-blue-600 dark:text-indigo-400 bg-blue-50 dark:bg-indigo-600/30 border-blue-200 dark:border-blue-800';
                default:
                    return 'text-slate-600 dark:text-neutral-400 bg-slate-50 dark:bg-neutral-800 border-slate-200 dark:border-neutral-600';
            }
        };

        const getSeverityLabel = (severity?: string) => {
            switch (severity) {
                case 'critical':
                    return '🔴 Critical';
                case 'high':
                    return '🟠 High';
                case 'medium':
                    return '🟡 Medium';
                case 'low':
                    return '🔵 Low';
                default:
                    return '⚪ No Drift';
            }
        };

        return (
            <div
                className={`rounded-lg border-2 p-4 ${driftData?.detected
                        ? getSeverityColor(driftData.severity)
                        : 'border-green-200 dark:border-green-800 bg-green-50 dark:bg-green-600/30'
                    }`}
                role="status"
                aria-label={`Drift detection for ${modelName}`}
            >
                {isLoading ? (
                    <div className="flex items-center justify-center h-16">
                        <div className="animate-spin rounded-full h-6 w-6 border-t-2 border-b-2 border-current" />
                    </div>
                ) : driftData ? (
                    <>
                        {/* Header */}
                        <div className="flex justify-between items-start mb-3">
                            <div>
                                <h4 className="font-bold text-sm">{modelName}</h4>
                                <p className="text-xs opacity-75 mt-1">
                                    {getSeverityLabel(driftData.detected ? driftData.severity : undefined)}
                                </p>
                            </div>
                            <div className="text-right">
                                <div className="text-2xl font-bold">
                                    {(driftData.driftScore * 100).toFixed(1)}%
                                </div>
                                <div className="text-xs opacity-75">Drift Score</div>
                            </div>
                        </div>

                        {/* Top Features with Drift */}
                        {driftData.features && driftData.features.length > 0 && (
                            <div className="mb-3 space-y-2">
                                <div className="text-xs font-semibold opacity-75">Top Drifting Features:</div>
                                {driftData.features.slice(0, 3).map((feature, idx) => (
                                    <div
                                        key={idx}
                                        className="flex justify-between items-center text-xs bg-black/10 dark:bg-white/10 rounded px-2 py-1"
                                    >
                                        <span>{feature.name}</span>
                                        <span className="font-mono font-bold">
                                            {(feature.drift * 100).toFixed(1)}%
                                        </span>
                                    </div>
                                ))}
                            </div>
                        )}

                        {/* Recommendation */}
                        {driftData.recommendation && (
                            <div className="text-xs opacity-75 italic border-t border-current/20 pt-2 mt-2">
                                💡 {driftData.recommendation}
                            </div>
                        )}

                        {/* Timestamp */}
                        <div className="text-xs opacity-50 mt-2">
                            Detected: {new Date(driftData.detectedAt).toLocaleTimeString()}
                        </div>
                    </>
                ) : (
                    <div className="text-xs text-slate-600 dark:text-neutral-400">
                        <p className="font-semibold mb-1">{modelName}</p>
                        <p>✓ No drift detected</p>
                    </div>
                )}
            </div>
        );
    }
);

DriftIndicator.displayName = 'DriftIndicator';

export default DriftIndicator;
