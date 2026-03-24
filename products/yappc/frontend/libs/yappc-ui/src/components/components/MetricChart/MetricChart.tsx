/**
 * Metric Chart Component
 * 
 * Time-series chart for metrics visualization.
 * Supports line, area, and bar chart types.
 * 
 * @module ui/components
 */

import React, { useMemo } from 'react';

export interface DataPoint {
  timestamp: Date | string | number;
  value: number;
  label?: string;
}

export interface MetricChartProps {
  /** Data points */
  data: DataPoint[];
  /** Chart type */
  type?: 'line' | 'area' | 'bar';
  /** Chart title */
  title?: string;
  /** Y-axis label */
  yAxisLabel?: string;
  /** Chart color */
  color?: string;
  /** Height */
  height?: number;
  /** Show grid */
  showGrid?: boolean;
  /** Show tooltip */
  showTooltip?: boolean;
  /** Additional CSS classes */
  className?: string;
}

/**
 * Metric Chart Component
 * 
 * Simple SVG-based chart for metrics visualization.
 * 
 * @example
 * ```tsx
 * <MetricChart
 *   data={[
 *     { timestamp: '2024-01-01', value: 100 },
 *     { timestamp: '2024-01-02', value: 150 },
 *   ]}
 *   type="line"
 *   title="CPU Usage"
 *   yAxisLabel="%"
 * />
 * ```
 */
export const MetricChart: React.FC<MetricChartProps> = ({
  data,
  type = 'line',
  title,
  yAxisLabel,
  color = '#8b5cf6',
  height = 200,
  showGrid = true,
  className = '',
}) => {
  const chartData = useMemo(() => {
    if (data.length === 0) return { points: [], min: 0, max: 100, path: '' };

    const values = data.map(d => d.value);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min || 1;

    const padding = 40;
    const chartWidth = 100;
    const chartHeight = height - padding * 2;

    const points = data.map((d, i) => ({
      x: padding + (i / (data.length - 1 || 1)) * (chartWidth - padding * 2) * 10,
      y: padding + chartHeight - ((d.value - min) / range) * chartHeight,
      value: d.value,
      timestamp: d.timestamp,
    }));

    const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ');

    const areaPath = `${path} L ${points[points.length - 1]?.x || 0} ${height - padding} L ${padding} ${height - padding} Z`;

    return { points, min, max, path, areaPath };
  }, [data, height]);

  if (data.length === 0) {
    return (
      <div className={`rounded-lg border border-zinc-700 p-4 ${className}`}>
        {title && <h3 className="text-sm font-medium text-white mb-2">{title}</h3>}
        <div className="flex items-center justify-center text-zinc-500" style={{ height }}>
          No data available
        </div>
      </div>
    );
  }

  return (
    <div className={`rounded-lg border border-zinc-700 bg-zinc-900 p-4 ${className}`}>
      {/* Header */}
      {title && (
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-medium text-white">{title}</h3>
          <div className="flex items-center gap-2 text-xs text-zinc-400">
            <span>Min: {chartData.min.toFixed(1)}</span>
            <span>Max: {chartData.max.toFixed(1)}</span>
          </div>
        </div>
      )}

      {/* Chart */}
      <svg
        viewBox={`0 0 ${40 + (data.length - 1) * 10 + 40} ${height}`}
        className="w-full"
        style={{ height }}
      >
        {/* Grid */}
        {showGrid && (
          <g className="text-zinc-800">
            {[0, 0.25, 0.5, 0.75, 1].map((ratio, i) => (
              <line
                key={i}
                x1={40}
                y1={40 + (height - 80) * ratio}
                x2={40 + (data.length - 1) * 10}
                y2={40 + (height - 80) * ratio}
                stroke="currentColor"
                strokeDasharray="4 4"
              />
            ))}
          </g>
        )}

        {/* Area fill */}
        {type === 'area' && (
          <path
            d={chartData.areaPath}
            fill={color}
            fillOpacity={0.2}
          />
        )}

        {/* Line */}
        {(type === 'line' || type === 'area') && (
          <path
            d={chartData.path}
            fill="none"
            stroke={color}
            strokeWidth={2}
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        )}

        {/* Bars */}
        {type === 'bar' && chartData.points.map((point, i) => (
          <rect
            key={i}
            x={point.x - 4}
            y={point.y}
            width={8}
            height={height - 40 - point.y}
            fill={color}
            rx={2}
          />
        ))}

        {/* Data points */}
        {(type === 'line' || type === 'area') && chartData.points.map((point, i) => (
          <circle
            key={i}
            cx={point.x}
            cy={point.y}
            r={3}
            fill={color}
            className="hover:r-5 transition-all cursor-pointer"
          />
        ))}

        {/* Y-axis label */}
        {yAxisLabel && (
          <text
            x={10}
            y={height / 2}
            fill="#71717a"
            fontSize={10}
            textAnchor="middle"
            transform={`rotate(-90, 10, ${height / 2})`}
          >
            {yAxisLabel}
          </text>
        )}
      </svg>
    </div>
  );
};

export default MetricChart;
