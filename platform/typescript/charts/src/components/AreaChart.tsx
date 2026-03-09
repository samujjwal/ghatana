/**
 * AreaChart Component
 * 
 * A simple area chart component wrapper for the Ghatana platform.
 * 
 * @doc.type component
 * @doc.purpose Area chart visualization
 * @doc.layer shared
 */

import React from 'react';
import { ChartDataPoint } from '../types';

export interface AreaChartProps {
    data?: ChartDataPoint[];
    width?: number;
    height?: number;
    className?: string;
    xKey?: string;
    yKey?: string;
    xField?: string;
    yField?: string;
    color?: string;
    [key: string]: unknown;
}

export function AreaChart({
    data = [],
    width = 400,
    height = 300,
    className = '',
    xKey = 'x',
    yKey = 'y',
    xField,
    yField,
    color
}: AreaChartProps) {
    return (
        <div
            className={`border border-gray-200 rounded p-4 bg-white ${className}`}
            style={{ width, height }}
        >
            <div className="text-sm text-gray-500 text-center flex items-center justify-center h-full">
                Area Chart
                {data.length > 0 && ` (${data.length} points)`}
            </div>
        </div>
    );
}
