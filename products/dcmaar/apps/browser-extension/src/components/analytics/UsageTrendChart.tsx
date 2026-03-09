/**
 * @fileoverview Usage Trend Chart
 * 
 * Line chart showing screen time trends over 7/30 days.
 */

import React, { useMemo } from 'react';
import {
    LineChart,
    Line,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
    Legend,
} from 'recharts';

export interface UsageTrendDataPoint {
    date: string;
    totalTime: number;
    productiveTime?: number;
    blockedAttempts?: number;
}

export interface UsageTrendChartProps {
    data: UsageTrendDataPoint[];
    period?: '7d' | '30d';
    showProductiveTime?: boolean;
    height?: number;
}

/**
 * Format milliseconds to hours for display
 */
function formatHours(ms: number): string {
    const hours = ms / 3600000;
    return hours.toFixed(1);
}

/**
 * Custom tooltip for the chart
 */
function CustomTooltip({ active, payload, label }: any) {
    if (!active || !payload || !payload.length) return null;

    return (
        <div className="bg-white dark:bg-gray-800 p-3 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700">
            <p className="font-medium text-gray-900 dark:text-white mb-2">{label}</p>
            {payload.map((entry: any, index: number) => (
                <p key={index} className="text-sm" style={{ color: entry.color }}>
                    {entry.name}: {formatHours(entry.value)}h
                </p>
            ))}
        </div>
    );
}

/**
 * UsageTrendChart
 * 
 * Displays screen time trends as a line chart.
 */
export function UsageTrendChart({
    data,
    // period is reserved for future filtering
    period: _period = '7d',
    showProductiveTime = false,
    height = 300,
}: UsageTrendChartProps) {
    void _period; // Suppress unused warning
    const chartData = useMemo(() => {
        return data.map((point) => ({
            ...point,
            totalTimeHours: point.totalTime / 3600000,
            productiveTimeHours: (point.productiveTime || 0) / 3600000,
        }));
    }, [data]);

    if (data.length === 0) {
        return (
            <div className="flex items-center justify-center h-64 bg-gray-50 dark:bg-gray-800 rounded-lg">
                <p className="text-gray-500 dark:text-gray-400">No usage data available</p>
            </div>
        );
    }

    return (
        <div className="w-full" style={{ height }}>
            <ResponsiveContainer width="100%" height="100%">
                <LineChart data={chartData} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
                    <CartesianGrid strokeDasharray="3 3" className="stroke-gray-200 dark:stroke-gray-700" />
                    <XAxis
                        dataKey="date"
                        tick={{ fontSize: 12 }}
                        className="text-gray-600 dark:text-gray-400"
                    />
                    <YAxis
                        tickFormatter={(value) => `${value}h`}
                        tick={{ fontSize: 12 }}
                        className="text-gray-600 dark:text-gray-400"
                    />
                    <Tooltip content={<CustomTooltip />} />
                    <Legend />
                    <Line
                        type="monotone"
                        dataKey="totalTimeHours"
                        name="Total Screen Time"
                        stroke="#3b82f6"
                        strokeWidth={2}
                        dot={{ r: 4 }}
                        activeDot={{ r: 6 }}
                    />
                    {showProductiveTime && (
                        <Line
                            type="monotone"
                            dataKey="productiveTimeHours"
                            name="Productive Time"
                            stroke="#10b981"
                            strokeWidth={2}
                            dot={{ r: 4 }}
                            activeDot={{ r: 6 }}
                        />
                    )}
                </LineChart>
            </ResponsiveContainer>
        </div>
    );
}
