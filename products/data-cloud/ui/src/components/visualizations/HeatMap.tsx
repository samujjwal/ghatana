/**
 * HeatMap Component
 * 
 * Displays data access patterns or activity levels as a heat map grid.
 * Supports customizable color scales and tooltips.
 * 
 * @doc.type component
 * @doc.purpose Heat map visualization
 * @doc.layer frontend
 * @doc.pattern Presentational Component
 */

import React, { useMemo, useState } from 'react';
import { cn, textStyles, cardStyles } from '../../lib/theme';

/**
 * Heat map cell data
 */
export interface HeatMapCell {
    x: string;
    y: string;
    value: number;
    label?: string;
}

/**
 * Color scale type
 */
export type ColorScale = 'blue' | 'green' | 'red' | 'purple' | 'orange';

/**
 * Color scale definitions
 */
const colorScales: Record<ColorScale, string[]> = {
    blue: [
        'bg-blue-50 dark:bg-blue-950',
        'bg-blue-100 dark:bg-blue-900',
        'bg-blue-200 dark:bg-blue-800',
        'bg-blue-300 dark:bg-blue-700',
        'bg-blue-400 dark:bg-blue-600',
        'bg-blue-500 dark:bg-blue-500',
        'bg-blue-600 dark:bg-blue-400',
        'bg-blue-700 dark:bg-blue-300',
    ],
    green: [
        'bg-green-50 dark:bg-green-950',
        'bg-green-100 dark:bg-green-900',
        'bg-green-200 dark:bg-green-800',
        'bg-green-300 dark:bg-green-700',
        'bg-green-400 dark:bg-green-600',
        'bg-green-500 dark:bg-green-500',
        'bg-green-600 dark:bg-green-400',
        'bg-green-700 dark:bg-green-300',
    ],
    red: [
        'bg-red-50 dark:bg-red-950',
        'bg-red-100 dark:bg-red-900',
        'bg-red-200 dark:bg-red-800',
        'bg-red-300 dark:bg-red-700',
        'bg-red-400 dark:bg-red-600',
        'bg-red-500 dark:bg-red-500',
        'bg-red-600 dark:bg-red-400',
        'bg-red-700 dark:bg-red-300',
    ],
    purple: [
        'bg-purple-50 dark:bg-purple-950',
        'bg-purple-100 dark:bg-purple-900',
        'bg-purple-200 dark:bg-purple-800',
        'bg-purple-300 dark:bg-purple-700',
        'bg-purple-400 dark:bg-purple-600',
        'bg-purple-500 dark:bg-purple-500',
        'bg-purple-600 dark:bg-purple-400',
        'bg-purple-700 dark:bg-purple-300',
    ],
    orange: [
        'bg-orange-50 dark:bg-orange-950',
        'bg-orange-100 dark:bg-orange-900',
        'bg-orange-200 dark:bg-orange-800',
        'bg-orange-300 dark:bg-orange-700',
        'bg-orange-400 dark:bg-orange-600',
        'bg-orange-500 dark:bg-orange-500',
        'bg-orange-600 dark:bg-orange-400',
        'bg-orange-700 dark:bg-orange-300',
    ],
};

interface HeatMapProps {
    data: HeatMapCell[];
    xLabels: string[];
    yLabels: string[];
    colorScale?: ColorScale;
    title?: string;
    xAxisLabel?: string;
    yAxisLabel?: string;
    showValues?: boolean;
    cellSize?: number;
    className?: string;
    onCellClick?: (cell: HeatMapCell) => void;
}

/**
 * HeatMap Component
 */
export function HeatMap({
    data,
    xLabels,
    yLabels,
    colorScale = 'blue',
    title,
    xAxisLabel,
    yAxisLabel,
    showValues = false,
    cellSize = 40,
    className,
    onCellClick,
}: HeatMapProps): React.ReactElement {
    const [hoveredCell, setHoveredCell] = useState<HeatMapCell | null>(null);

    // Calculate min/max values
    const { minValue, maxValue } = useMemo(() => {
        const values = data.map((d) => d.value);
        return {
            minValue: Math.min(...values),
            maxValue: Math.max(...values),
        };
    }, [data]);

    // Get color for value
    const getColorClass = (value: number): string => {
        const scale = colorScales[colorScale];
        const range = maxValue - minValue;
        if (range === 0) return scale[4];

        const normalized = (value - minValue) / range;
        const index = Math.min(Math.floor(normalized * scale.length), scale.length - 1);
        return scale[index];
    };

    // Get cell data
    const getCellData = (x: string, y: string): HeatMapCell | undefined => {
        return data.find((d) => d.x === x && d.y === y);
    };

    return (
        <div className={cn('', className)}>
            {title && <h3 className={cn(textStyles.h4, 'mb-4')}>{title}</h3>}

            <div className="flex">
                {/* Y-axis label */}
                {yAxisLabel && (
                    <div className="flex items-center justify-center w-6 mr-2">
                        <span
                            className={cn(textStyles.xs, 'transform -rotate-90 whitespace-nowrap')}
                        >
                            {yAxisLabel}
                        </span>
                    </div>
                )}

                <div className="flex-1">
                    {/* Heat map grid */}
                    <div className="overflow-x-auto">
                        <div className="inline-block">
                            {/* X-axis labels */}
                            <div className="flex ml-16">
                                {xLabels.map((label) => (
                                    <div
                                        key={label}
                                        className={cn(textStyles.xs, 'text-center truncate')}
                                        style={{ width: cellSize }}
                                    >
                                        {label}
                                    </div>
                                ))}
                            </div>

                            {/* Grid rows */}
                            {yLabels.map((yLabel) => (
                                <div key={yLabel} className="flex items-center">
                                    {/* Y-axis label */}
                                    <div
                                        className={cn(textStyles.xs, 'w-16 pr-2 text-right truncate')}
                                        title={yLabel}
                                    >
                                        {yLabel}
                                    </div>

                                    {/* Cells */}
                                    {xLabels.map((xLabel) => {
                                        const cell = getCellData(xLabel, yLabel);
                                        const value = cell?.value ?? 0;

                                        return (
                                            <div
                                                key={`${xLabel}-${yLabel}`}
                                                className={cn(
                                                    'border border-white dark:border-gray-800 cursor-pointer transition-all',
                                                    'hover:ring-2 hover:ring-blue-500 hover:z-10',
                                                    getColorClass(value)
                                                )}
                                                style={{ width: cellSize, height: cellSize }}
                                                onMouseEnter={() => setHoveredCell(cell || null)}
                                                onMouseLeave={() => setHoveredCell(null)}
                                                onClick={() => cell && onCellClick?.(cell)}
                                            >
                                                {showValues && (
                                                    <div className="flex items-center justify-center h-full">
                                                        <span className={cn(
                                                            'text-xs font-medium',
                                                            value > (maxValue - minValue) / 2 + minValue
                                                                ? 'text-white'
                                                                : 'text-gray-900 dark:text-white'
                                                        )}>
                                                            {value}
                                                        </span>
                                                    </div>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            ))}

                            {/* X-axis label */}
                            {xAxisLabel && (
                                <div className={cn(textStyles.xs, 'text-center mt-2 ml-16')}>
                                    {xAxisLabel}
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Tooltip */}
                    {hoveredCell && (
                        <div className={cn(
                            'mt-2 p-2 rounded-lg text-sm',
                            'bg-gray-100 dark:bg-gray-800'
                        )}>
                            <p className={textStyles.body}>
                                <strong>{hoveredCell.x}</strong> × <strong>{hoveredCell.y}</strong>
                            </p>
                            <p className={textStyles.muted}>
                                Value: {hoveredCell.value}
                                {hoveredCell.label && ` (${hoveredCell.label})`}
                            </p>
                        </div>
                    )}

                    {/* Legend */}
                    <div className="flex items-center gap-2 mt-4">
                        <span className={textStyles.xs}>Low</span>
                        <div className="flex">
                            {colorScales[colorScale].map((color, i) => (
                                <div
                                    key={i}
                                    className={cn('w-6 h-4', color)}
                                />
                            ))}
                        </div>
                        <span className={textStyles.xs}>High</span>
                    </div>
                </div>
            </div>
        </div>
    );
}

export default HeatMap;
