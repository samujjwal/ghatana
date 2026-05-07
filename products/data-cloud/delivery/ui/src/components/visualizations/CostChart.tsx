/**
 * CostChart Component
 * 
 * Displays cost/usage data as a bar or line chart.
 * Supports multiple series, tooltips, and responsive sizing.
 * 
 * @doc.type component
 * @doc.purpose Cost visualization
 * @doc.layer frontend
 * @doc.pattern Presentational Component
 */

import React, { useMemo, useState } from 'react';
import { cn, textStyles, cardStyles } from '../../lib/theme';

/**
 * Chart data point
 */
export interface CostDataPoint {
    label: string;
    value: number;
    previousValue?: number;
    color?: string;
}

/**
 * Chart series
 */
export interface CostSeries {
    name: string;
    data: CostDataPoint[];
    color: string;
}

/**
 * Chart type
 */
export type ChartType = 'bar' | 'line' | 'area';

interface CostChartProps {
    data: CostDataPoint[];
    series?: CostSeries[];
    type?: ChartType;
    title?: string;
    subtitle?: string;
    height?: number;
    showLegend?: boolean;
    showGrid?: boolean;
    showValues?: boolean;
    formatValue?: (value: number) => string;
    className?: string;
    onBarClick?: (point: CostDataPoint) => void;
}

/**
 * Default value formatter
 */
const defaultFormatValue = (value: number): string => {
    if (value >= 1000000) return `$${(value / 1000000).toFixed(1)}M`;
    if (value >= 1000) return `$${(value / 1000).toFixed(1)}K`;
    return `$${value.toFixed(2)}`;
};

/**
 * CostChart Component
 */
export function CostChart({
    data,
    series,
    type = 'bar',
    title,
    subtitle,
    height = 300,
    showLegend = true,
    showGrid = true,
    showValues = false,
    formatValue = defaultFormatValue,
    className,
    onBarClick,
}: CostChartProps): React.ReactElement {
    const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

    // Calculate max value for scaling
    const maxValue = useMemo(() => {
        if (series) {
            return Math.max(...series.flatMap((s) => s.data.map((d) => d.value)));
        }
        return Math.max(...data.map((d) => d.value));
    }, [data, series]);

    // Calculate total
    const total = useMemo(() => {
        return data.reduce((sum, d) => sum + d.value, 0);
    }, [data]);

    // Calculate change from previous
    const totalChange = useMemo(() => {
        const currentTotal = data.reduce((sum, d) => sum + d.value, 0);
        const previousTotal = data.reduce((sum, d) => sum + (d.previousValue ?? d.value), 0);
        if (previousTotal === 0) return 0;
        return ((currentTotal - previousTotal) / previousTotal) * 100;
    }, [data]);

    // Get bar height percentage
    const getBarHeight = (value: number): number => {
        if (maxValue === 0) return 0;
        return (value / maxValue) * 100;
    };

    // Default colors
    const defaultColors = [
        'bg-blue-500',
        'bg-green-500',
        'bg-purple-500',
        'bg-orange-500',
        'bg-pink-500',
        'bg-cyan-500',
    ];

    return (
        <div className={cn(cardStyles.base, className)}>
            {/* Header */}
            {(title || subtitle) && (
                <div className="mb-4">
                    {title && <h3 className={textStyles.h3}>{title}</h3>}
                    {subtitle && <p className={textStyles.muted}>{subtitle}</p>}
                </div>
            )}

            {/* Summary */}
            <div className="flex items-baseline gap-4 mb-6">
                <span className="text-3xl font-bold text-gray-900 dark:text-white">
                    {formatValue(total)}
                </span>
                {totalChange !== 0 && (
                    <span className={cn(
                        'text-sm font-medium',
                        totalChange > 0 ? 'text-red-600' : 'text-green-600'
                    )}>
                        {totalChange > 0 ? '+' : ''}{totalChange.toFixed(1)}%
                    </span>
                )}
            </div>

            {/* Chart */}
            <div className="relative" style={{ height }}>
                {/* Grid lines */}
                {showGrid && (
                    <div className="absolute inset-0 flex flex-col justify-between pointer-events-none">
                        {[0, 25, 50, 75, 100].map((pct) => (
                            <div
                                key={pct}
                                className="border-t border-gray-100 dark:border-gray-800 w-full"
                            />
                        ))}
                    </div>
                )}

                {/* Y-axis labels */}
                {showGrid && (
                    <div className="absolute left-0 top-0 bottom-0 w-12 flex flex-col justify-between text-right pr-2">
                        {[100, 75, 50, 25, 0].map((pct) => (
                            <span key={pct} className={textStyles.xs}>
                                {formatValue((maxValue * pct) / 100)}
                            </span>
                        ))}
                    </div>
                )}

                {/* Bars */}
                <div
                    className={cn(
                        'absolute inset-0 flex items-end justify-around gap-2',
                        showGrid && 'ml-14'
                    )}
                >
                    {data.map((point, index) => {
                        const barHeight = getBarHeight(point.value);
                        const isHovered = hoveredIndex === index;
                        const color = point.color || defaultColors[index % defaultColors.length];

                        return (
                            <div
                                key={point.label}
                                className="flex-1 flex flex-col items-center"
                                onMouseEnter={() => setHoveredIndex(index)}
                                onMouseLeave={() => setHoveredIndex(null)}
                            >
                                {/* Value label */}
                                {(showValues || isHovered) && (
                                    <div className={cn(
                                        'mb-1 px-2 py-1 rounded text-xs font-medium',
                                        'bg-gray-900 text-white dark:bg-white dark:text-gray-900'
                                    )}>
                                        {formatValue(point.value)}
                                    </div>
                                )}

                                {/* Bar */}
                                {type === 'bar' && (
                                    <div
                                        className={cn(
                                            'w-full max-w-16 rounded-t transition-all cursor-pointer',
                                            color,
                                            isHovered && 'opacity-80 ring-2 ring-offset-2 ring-blue-500'
                                        )}
                                        style={{ height: `${barHeight}%` }}
                                        onClick={() => onBarClick?.(point)}
                                    />
                                )}

                                {/* Previous value indicator */}
                                {point.previousValue !== undefined && (
                                    <div
                                        className="absolute w-full max-w-16 border-t-2 border-dashed border-gray-400"
                                        style={{
                                            bottom: `${getBarHeight(point.previousValue)}%`,
                                            left: '50%',
                                            transform: 'translateX(-50%)',
                                        }}
                                    />
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>

            {/* X-axis labels */}
            <div className={cn(
                'flex justify-around mt-2',
                showGrid && 'ml-14'
            )}>
                {data.map((point) => (
                    <div
                        key={point.label}
                        className={cn(textStyles.xs, 'flex-1 text-center truncate px-1')}
                        title={point.label}
                    >
                        {point.label}
                    </div>
                ))}
            </div>

            {/* Legend */}
            {showLegend && series && (
                <div className="flex flex-wrap gap-4 mt-4 pt-4 border-t border-gray-200 dark:border-gray-700">
                    {series.map((s) => (
                        <div key={s.name} className="flex items-center gap-2">
                            <div className={cn('w-3 h-3 rounded', s.color)} />
                            <span className={textStyles.small}>{s.name}</span>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

/**
 * Sparkline variant for compact display
 */
export function CostSparkline({
    data,
    height = 40,
    color = 'text-blue-500',
    className,
}: {
    data: number[];
    height?: number;
    color?: string;
    className?: string;
}): React.ReactElement {
    const maxValue = Math.max(...data);
    const minValue = Math.min(...data);
    const range = maxValue - minValue || 1;

    // Generate SVG path
    const points = data.map((value, index) => {
        const x = (index / (data.length - 1)) * 100;
        const y = 100 - ((value - minValue) / range) * 100;
        return `${x},${y}`;
    }).join(' ');

    return (
        <div className={cn('relative', className)} style={{ height }}>
            <svg
                viewBox="0 0 100 100"
                preserveAspectRatio="none"
                className={cn('w-full h-full', color)}
                style={{ fill: 'none', stroke: 'currentColor', strokeWidth: 2 }}
            >
                <polyline points={points} />
            </svg>
        </div>
    );
}

export default CostChart;
