/**
 * System Health Card Component
 *
 * <p><b>Purpose</b><br>
 * Displays a single system health metric with status indicator, value, and threshold
 * comparison. Used in Real-Time Monitor for quick system overview.
 *
 * <p><b>Features</b><br>
 * - Metric value display
 * - Threshold comparison
 * - Status indicator (healthy/warning/critical)
 * - Progress bar visualization
 * - Unit display
 * - Dark mode support
 *
 * @doc.type component
 * @doc.purpose System health metric card component
 * @doc.layer product
 * @doc.pattern Display Component
 */

import { memo } from 'react';

interface SystemHealthCardProps {
    name: string;
    value: number;
    unit: string;
    threshold?: number;
}

/**
 * SystemHealthCard component - displays individual system health metric.
 *
 * GIVEN: System metric with value and threshold
 * WHEN: Component renders
 * THEN: Display metric with status indicator and visual feedback
 *
 * @param name - Metric name
 * @param value - Current metric value
 * @param unit - Unit of measurement
 * @param threshold - Warning threshold (optional)
 * @returns Rendered health card component
 */
const SystemHealthCard = memo(
    ({ name, value, unit, threshold }: SystemHealthCardProps) => {
        // Determine status based on threshold
        const getStatus = () => {
            if (threshold === 0 || threshold === undefined) {
                return 'normal'; // No threshold, always normal
            }
            if (value >= threshold) return 'critical';
            if (value >= threshold * 0.75) return 'warning';
            return 'healthy';
        };

        const getStatusColor = (status: string) => {
            switch (status) {
                case 'critical':
                    return 'text-red-600 dark:text-rose-400 bg-red-50 dark:bg-rose-600/30 border-red-200 dark:border-red-800';
                case 'warning':
                    return 'text-yellow-600 dark:text-yellow-400 bg-yellow-50 dark:bg-orange-600/30 border-yellow-200 dark:border-yellow-800';
                case 'normal':
                    return 'text-slate-600 dark:text-neutral-400 bg-slate-50 dark:bg-neutral-800 border-slate-200 dark:border-neutral-600';
                default:
                    return 'text-green-600 dark:text-green-400 bg-green-50 dark:bg-green-600/30 border-green-200 dark:border-green-800';
            }
        };

        const getStatusIcon = (status: string) => {
            switch (status) {
                case 'critical':
                    return '🔴';
                case 'warning':
                    return '🟡';
                case 'normal':
                    return '⚪';
                default:
                    return '🟢';
            }
        };

        const getProgressBarColor = (status: string) => {
            switch (status) {
                case 'critical':
                    return 'bg-red-500 dark:bg-red-600';
                case 'warning':
                    return 'bg-yellow-500 dark:bg-yellow-600';
                case 'normal':
                    return 'bg-slate-400 dark:bg-slate-600';
                default:
                    return 'bg-green-500 dark:bg-green-600';
            }
        };

        const status = getStatus();
        const statusLabel =
            status === 'critical'
                ? 'Critical'
                : status === 'warning'
                    ? 'Warning'
                    : status === 'normal'
                        ? 'Normal'
                        : 'Healthy';

        // Calculate percentage for progress bar (useful for capacity metrics)
        const percentage = threshold ? Math.min((value / threshold) * 100, 100) : value;

        return (
            <div
                className={`rounded-lg border-2 p-4 transition-all ${getStatusColor(status)}`}
                role="status"
                aria-label={`${name}: ${value} ${unit} - ${statusLabel}`}
            >
                {/* Header with Status Icon */}
                <div className="flex justify-between items-start mb-3">
                    <div>
                        <h3 className="font-bold text-sm">{name}</h3>
                        <p className="text-xs opacity-60 mt-1">
                            {getStatusIcon(status)} {statusLabel}
                        </p>
                    </div>
                    <div className="text-right">
                        <div className="text-2xl font-bold">
                            {typeof value === 'number' ? value.toFixed(value > 100 ? 0 : 1) : value}
                        </div>
                        <div className="text-xs opacity-60">{unit}</div>
                    </div>
                </div>

                {/* Progress Bar (for percentage-based metrics) */}
                {unit === '%' && threshold !== 0 && (
                    <div className="mb-3">
                        <div className="w-full bg-slate-200 dark:bg-slate-600 rounded-full h-2 overflow-hidden">
                            <div
                                className={`h-full transition-all duration-300 ${getProgressBarColor(status)}`}
                                style={{ width: `${Math.min(percentage, 100)}%` }}
                                role="progressbar"
                                aria-valuenow={value}
                                aria-valuemin={0}
                                aria-valuemax={threshold}
                            />
                        </div>
                    </div>
                )}

                {/* Threshold Info */}
                {threshold !== 0 && threshold !== undefined && (
                    <div className="text-xs opacity-60">
                        Threshold: {threshold}
                        {unit}
                    </div>
                )}
            </div>
        );
    }
);

SystemHealthCard.displayName = 'SystemHealthCard';

export default SystemHealthCard;
