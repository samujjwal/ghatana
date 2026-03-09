/**
 * PieChart Component
 * 
 * A simple pie chart component wrapper for the Ghatana platform.
 * 
 * @doc.type component
 * @doc.purpose Pie chart visualization
 * @doc.layer shared
 */

import React from 'react';
import { ChartDataPoint } from '../types';

export interface PieChartProps {
    data?: ChartDataPoint[];
    width?: number;
    height?: number;
    className?: string;
    nameField?: string;
    valueField?: string;
    [key: string]: unknown;
}

export function PieChart({
    data = [],
    width = 400,
    height = 300,
    className = '',
    nameField = 'name',
    valueField = 'value'
}: PieChartProps) {
    return (
        <div
            className={`border border-gray-200 rounded p-4 bg-white ${className}`}
            style={{ width, height }}
        >
            <div className="text-sm text-gray-500 text-center flex items-center justify-center h-full">
                Pie Chart
                {data.length > 0 && ` (${data.length} segments)`}
            </div>
        </div>
    );
}
