/**
 * @fileoverview Activity Heatmap
 * 
 * Hour-of-day usage intensity visualization.
 */

import React, { useMemo } from 'react';

export interface ActivityHeatmapProps {
    /** Hourly activity data (0-23 array of milliseconds) */
    hourlyActivity: number[];
    /** Height of the component */
    height?: number;
}

/**
 * Get color intensity based on value
 */
function getIntensityColor(value: number, max: number): string {
    if (max === 0 || value === 0) return 'bg-gray-100 dark:bg-gray-800';

    const intensity = value / max;

    if (intensity < 0.2) return 'bg-blue-100 dark:bg-blue-900/30';
    if (intensity < 0.4) return 'bg-blue-200 dark:bg-blue-800/40';
    if (intensity < 0.6) return 'bg-blue-300 dark:bg-blue-700/50';
    if (intensity < 0.8) return 'bg-blue-400 dark:bg-blue-600/60';
    return 'bg-blue-500 dark:bg-blue-500/70';
}

/**
 * Format hour for display
 */
function formatHour(hour: number): string {
    if (hour === 0) return '12am';
    if (hour === 12) return '12pm';
    if (hour < 12) return `${hour}am`;
    return `${hour - 12}pm`;
}

/**
 * Format milliseconds to readable time
 */
function formatTime(ms: number): string {
    if (ms < 60000) return `${Math.round(ms / 1000)}s`;
    if (ms < 3600000) return `${Math.round(ms / 60000)}m`;
    const hours = Math.floor(ms / 3600000);
    const mins = Math.round((ms % 3600000) / 60000);
    return mins > 0 ? `${hours}h ${mins}m` : `${hours}h`;
}

/**
 * ActivityHeatmap
 * 
 * Displays hourly activity as a heatmap grid.
 */
export function ActivityHeatmap({
    hourlyActivity,
    height = 120,
}: ActivityHeatmapProps) {
    const { data, maxValue } = useMemo(() => {
        // Ensure we have 24 hours of data
        const normalized = Array(24).fill(0).map((_, i) => hourlyActivity[i] || 0);
        const max = Math.max(...normalized);
        return { data: normalized, maxValue: max };
    }, [hourlyActivity]);

    const currentHour = new Date().getHours();

    return (
        <div style={{ height }}>
            {/* Hour labels */}
            <div className="flex justify-between text-xs text-gray-500 dark:text-gray-400 mb-2">
                <span>12am</span>
                <span>6am</span>
                <span>12pm</span>
                <span>6pm</span>
                <span>11pm</span>
            </div>

            {/* Heatmap grid */}
            <div className="flex gap-0.5">
                {data.map((value, hour) => (
                    <div
                        key={hour}
                        className={`
              flex-1 h-12 rounded-sm transition-all cursor-pointer
              ${getIntensityColor(value, maxValue)}
              ${hour === currentHour ? 'ring-2 ring-blue-500 ring-offset-1' : ''}
              hover:ring-2 hover:ring-gray-400 hover:ring-offset-1
            `}
                        title={`${formatHour(hour)}: ${formatTime(value)}`}
                    />
                ))}
            </div>

            {/* Legend */}
            <div className="flex items-center justify-between mt-3">
                <span className="text-xs text-gray-500 dark:text-gray-400">Less</span>
                <div className="flex gap-1">
                    <div className="w-4 h-4 rounded-sm bg-gray-100 dark:bg-gray-800" />
                    <div className="w-4 h-4 rounded-sm bg-blue-100 dark:bg-blue-900/30" />
                    <div className="w-4 h-4 rounded-sm bg-blue-200 dark:bg-blue-800/40" />
                    <div className="w-4 h-4 rounded-sm bg-blue-300 dark:bg-blue-700/50" />
                    <div className="w-4 h-4 rounded-sm bg-blue-400 dark:bg-blue-600/60" />
                    <div className="w-4 h-4 rounded-sm bg-blue-500 dark:bg-blue-500/70" />
                </div>
                <span className="text-xs text-gray-500 dark:text-gray-400">More</span>
            </div>
        </div>
    );
}
