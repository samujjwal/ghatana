import type { MetricDefinition } from '@/config/personaConfig';

/**
 * Metrics grid for persona dashboard.
 *
 * <p><b>Purpose</b><br>
 * Displays 3-4 metric cards with icons, values, threshold indicators,
 * and trend arrows. Supports number, percentage, and duration formats.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <PersonaMetricsGrid
 *     metrics={personaConfig.metrics}
 *     data={{ workflowsActive: 42, avgApprovalTime: 3600, hitlBacklog: 8 }}
 * />
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Threshold indicators: green (ok), amber (warning), red (critical)
 * - Format support: number, percentage, duration (converts seconds to human-readable)
 * - Trend arrows (up/down) with color coding
 * - Dark mode support
 * - Responsive grid layout
 *
 * @doc.type component
 * @doc.purpose Metric cards with threshold indicators
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

export interface PersonaMetricsGridProps {
    /** Metric definitions from persona config */
    metrics: MetricDefinition[];
    /** Metric values (keyed by dataKey) */
    data: Record<string, number>;
    /** Additional CSS classes */
    className?: string;
}

/**
 * Renders a grid of metric cards with threshold indicators.
 */
export function PersonaMetricsGrid({ metrics, data, className = '' }: PersonaMetricsGridProps) {
    return (
        <div className={`mb-12 ${className}`}>
            {/* Section Title */}
            <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-200 mb-6">Key Metrics</h2>

            {/* Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                {metrics.map((metric) => {
                    const value = data[metric.dataKey] ?? 0;
                    const thresholdStatus = getThresholdStatus(value, metric.threshold);
                    const formattedValue = formatMetricValue(value, metric.format);

                    return (
                        <div
                            key={metric.id}
                            className={`
                                rounded-xl border-2 p-6
                                bg-white dark:bg-slate-900
                                transition-all duration-200
                                ${getThresholdBorderClass(thresholdStatus)}
                            `}
                        >
                            {/* Icon */}
                            <div className={`flex items-center justify-center w-12 h-12 rounded-lg mb-4 ${metric.color}`}>
                                <span className="text-2xl">{metric.icon}</span>
                            </div>

                            {/* Title */}
                            <h3 className="text-sm font-medium text-slate-600 dark:text-neutral-400 mb-2">
                                {metric.title}
                            </h3>

                            {/* Value */}
                            <div className="flex items-baseline justify-between">
                                <p className={`text-3xl font-bold ${getThresholdTextClass(thresholdStatus)}`}>
                                    {formattedValue}
                                </p>

                                {/* Threshold Indicator */}
                                {metric.threshold && (
                                    <div className="flex items-center gap-1">
                                        <span className={`text-xl ${getThresholdIconClass(thresholdStatus)}`}>
                                            {getThresholdIcon(thresholdStatus)}
                                        </span>
                                    </div>
                                )}
                            </div>

                            {/* Threshold Text */}
                            {metric.threshold && (
                                <p className="text-xs text-slate-500 dark:text-slate-500 dark:text-neutral-400 mt-2">
                                    {getThresholdText(thresholdStatus, metric.threshold)}
                                </p>
                            )}
                        </div>
                    );
                })}
            </div>

            {/* Empty State */}
            {metrics.length === 0 && (
                <div className="text-center py-12 text-slate-500 dark:text-neutral-400">
                    <p className="text-lg">No metrics available</p>
                    <p className="text-sm mt-2">Metrics will appear here once configured for your role.</p>
                </div>
            )}
        </div>
    );
}

/**
 * Determines threshold status based on value and thresholds.
 */
function getThresholdStatus(
    value: number,
    threshold?: { warning: number; critical: number }
): 'ok' | 'warning' | 'critical' {
    if (!threshold) return 'ok';

    if (value >= threshold.critical) return 'critical';
    if (value >= threshold.warning) return 'warning';
    return 'ok';
}

/**
 * Formats metric value based on format type.
 */
function formatMetricValue(value: number, format: MetricDefinition['format']): string {
    switch (format) {
        case 'percentage':
            return `${value.toFixed(1)}%`;
        case 'duration':
            return formatDuration(value);
        case 'number':
        default:
            return value.toLocaleString();
    }
}

/**
 * Formats duration in seconds to human-readable format.
 */
function formatDuration(seconds: number): string {
    if (seconds < 60) return `${seconds}s`;
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
    if (seconds < 86400) return `${Math.floor(seconds / 3600)}h`;
    return `${Math.floor(seconds / 86400)}d`;
}

/**
 * Returns border color class based on threshold status.
 */
function getThresholdBorderClass(status: ReturnType<typeof getThresholdStatus>): string {
    const classes = {
        ok: 'border-green-200 dark:border-green-800',
        warning: 'border-amber-200 dark:border-amber-800',
        critical: 'border-red-200 dark:border-red-800',
    };
    return classes[status];
}

/**
 * Returns text color class based on threshold status.
 */
function getThresholdTextClass(status: ReturnType<typeof getThresholdStatus>): string {
    const classes = {
        ok: 'text-green-600 dark:text-green-400',
        warning: 'text-amber-600 dark:text-amber-400',
        critical: 'text-red-600 dark:text-rose-400',
    };
    return classes[status];
}

/**
 * Returns icon color class based on threshold status.
 */
function getThresholdIconClass(status: ReturnType<typeof getThresholdStatus>): string {
    const classes = {
        ok: 'text-green-600 dark:text-green-400',
        warning: 'text-amber-600 dark:text-amber-400',
        critical: 'text-red-600 dark:text-rose-400',
    };
    return classes[status];
}

/**
 * Returns threshold icon based on status.
 */
function getThresholdIcon(status: ReturnType<typeof getThresholdStatus>): string {
    const icons = {
        ok: '✓',
        warning: '⚠',
        critical: '⚠',
    };
    return icons[status];
}

/**
 * Returns threshold description text.
 */
function getThresholdText(
    status: ReturnType<typeof getThresholdStatus>,
    threshold: { warning: number; critical: number }
): string {
    const texts = {
        ok: `Below warning threshold (${threshold.warning})`,
        warning: `Warning: above ${threshold.warning}`,
        critical: `Critical: above ${threshold.critical}`,
    };
    return texts[status];
}
