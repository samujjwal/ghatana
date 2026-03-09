/**
 * @fileoverview Category Distribution Chart
 * 
 * Donut chart showing time spent by website category.
 */

import React, { useMemo } from 'react';
import {
    PieChart,
    Pie,
    Cell,
    ResponsiveContainer,
    Legend,
    Tooltip,
} from 'recharts';

export interface CategoryData {
    category: string;
    time: number;
    color?: string;
}

export interface CategoryDistributionChartProps {
    data: CategoryData[];
    height?: number;
    showLegend?: boolean;
}

/**
 * Category colors
 */
const CATEGORY_COLORS: Record<string, string> = {
    social: '#ef4444',      // red
    gaming: '#f97316',      // orange
    streaming: '#eab308',   // yellow
    education: '#22c55e',   // green
    productivity: '#3b82f6', // blue
    communication: '#8b5cf6', // purple
    shopping: '#ec4899',    // pink
    news: '#06b6d4',        // cyan
    entertainment: '#f59e0b', // amber
    adult: '#dc2626',       // dark red
    other: '#6b7280',       // gray
};

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
 * Custom tooltip
 */
function CustomTooltip({ active, payload }: any) {
    if (!active || !payload || !payload.length) return null;

    const data = payload[0].payload;
    return (
        <div className="bg-white dark:bg-gray-800 p-3 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700">
            <p className="font-medium text-gray-900 dark:text-white capitalize">
                {data.category}
            </p>
            <p className="text-sm text-gray-600 dark:text-gray-400">
                {formatTime(data.time)} ({data.percentage}%)
            </p>
        </div>
    );
}

/**
 * Custom legend
 */
function CustomLegend({ payload }: any) {
    return (
        <div className="flex flex-wrap justify-center gap-3 mt-4">
            {payload.map((entry: any, index: number) => (
                <div key={index} className="flex items-center gap-1.5">
                    <div
                        className="w-3 h-3 rounded-full"
                        style={{ backgroundColor: entry.color }}
                    />
                    <span className="text-xs text-gray-600 dark:text-gray-400 capitalize">
                        {entry.value}
                    </span>
                </div>
            ))}
        </div>
    );
}

/**
 * CategoryDistributionChart
 * 
 * Displays time distribution by category as a donut chart.
 */
export function CategoryDistributionChart({
    data,
    height = 300,
    showLegend = true,
}: CategoryDistributionChartProps) {
    const chartData = useMemo(() => {
        const total = data.reduce((sum, item) => sum + item.time, 0);
        return data
            .filter((item) => item.time > 0)
            .map((item) => ({
                ...item,
                color: item.color || CATEGORY_COLORS[item.category] || CATEGORY_COLORS.other,
                percentage: total > 0 ? Math.round((item.time / total) * 100) : 0,
            }))
            .sort((a, b) => b.time - a.time);
    }, [data]);

    if (chartData.length === 0) {
        return (
            <div className="flex items-center justify-center h-64 bg-gray-50 dark:bg-gray-800 rounded-lg">
                <p className="text-gray-500 dark:text-gray-400">No category data available</p>
            </div>
        );
    }

    return (
        <div className="w-full" style={{ height }}>
            <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                    <Pie
                        data={chartData}
                        cx="50%"
                        cy="50%"
                        innerRadius={60}
                        outerRadius={100}
                        paddingAngle={2}
                        dataKey="time"
                        nameKey="category"
                    >
                        {chartData.map((entry, index) => (
                            <Cell key={`cell-${index}`} fill={entry.color} />
                        ))}
                    </Pie>
                    <Tooltip content={<CustomTooltip />} />
                    {showLegend && <Legend content={<CustomLegend />} />}
                </PieChart>
            </ResponsiveContainer>
        </div>
    );
}
