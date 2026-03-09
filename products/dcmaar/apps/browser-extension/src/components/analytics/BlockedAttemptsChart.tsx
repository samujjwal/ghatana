/**
 * @fileoverview Blocked Attempts Chart
 * 
 * Bar chart showing blocked requests by category.
 */

import React, { useMemo } from 'react';
import {
    BarChart,
    Bar,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
    Cell,
} from 'recharts';

export interface BlockedAttemptData {
    category: string;
    count: number;
    color?: string;
}

export interface BlockedAttemptsChartProps {
    data: BlockedAttemptData[];
    height?: number;
}

/**
 * Category colors
 */
const CATEGORY_COLORS: Record<string, string> = {
    social: '#ef4444',
    gaming: '#f97316',
    streaming: '#eab308',
    adult: '#dc2626',
    shopping: '#ec4899',
    entertainment: '#f59e0b',
    other: '#6b7280',
};

/**
 * Custom tooltip
 */
function CustomTooltip({ active, payload, label }: any) {
    if (!active || !payload || !payload.length) return null;

    return (
        <div className="bg-white dark:bg-gray-800 p-3 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700">
            <p className="font-medium text-gray-900 dark:text-white capitalize">{label}</p>
            <p className="text-sm text-red-600 dark:text-red-400">
                {payload[0].value} blocked attempts
            </p>
        </div>
    );
}

/**
 * BlockedAttemptsChart
 * 
 * Displays blocked attempts by category as a bar chart.
 */
export function BlockedAttemptsChart({
    data,
    height = 250,
}: BlockedAttemptsChartProps) {
    const chartData = useMemo(() => {
        return data
            .filter((item) => item.count > 0)
            .map((item) => ({
                ...item,
                color: item.color || CATEGORY_COLORS[item.category] || CATEGORY_COLORS.other,
            }))
            .sort((a, b) => b.count - a.count)
            .slice(0, 8); // Top 8 categories
    }, [data]);

    if (chartData.length === 0) {
        return (
            <div className="flex items-center justify-center h-48 bg-gray-50 dark:bg-gray-800 rounded-lg">
                <div className="text-center">
                    <div className="text-4xl mb-2">🎉</div>
                    <p className="text-gray-500 dark:text-gray-400">No blocked attempts!</p>
                </div>
            </div>
        );
    }

    return (
        <div className="w-full" style={{ height }}>
            <ResponsiveContainer width="100%" height="100%">
                <BarChart data={chartData} layout="vertical" margin={{ top: 5, right: 30, left: 80, bottom: 5 }}>
                    <CartesianGrid strokeDasharray="3 3" className="stroke-gray-200 dark:stroke-gray-700" />
                    <XAxis type="number" tick={{ fontSize: 12 }} />
                    <YAxis
                        type="category"
                        dataKey="category"
                        tick={{ fontSize: 12 }}
                        className="capitalize"
                    />
                    <Tooltip content={<CustomTooltip />} />
                    <Bar dataKey="count" radius={[0, 4, 4, 0]}>
                        {chartData.map((entry, index) => (
                            <Cell key={`cell-${index}`} fill={entry.color} />
                        ))}
                    </Bar>
                </BarChart>
            </ResponsiveContainer>
        </div>
    );
}
