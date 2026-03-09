/**
 * Line Chart Component
 * 
 * Optimized line chart for time-series data with:
 * - Automatic data sampling
 * - Smooth animations
 * - Interactive tooltips
 * - Export capabilities
 */

import React, { useMemo } from 'react';
import {
  LineChart as RechartsLineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { useTheme } from '@mui/material';
import type { BaseChartProps } from './BaseChart';
import { BaseChart } from './BaseChart';
import { formatTimestamp, formatValue } from '../utils';

export interface LineChartProps extends Omit<BaseChartProps, 'config'> {
  config?: BaseChartProps['config'];
  smooth?: boolean;
  showGrid?: boolean;
  showDots?: boolean;
}

/**
 * Line chart component for time-series visualization
 */
export const LineChart: React.FC<LineChartProps> = ({
  data,
  config = { type: 'line' },
  smooth = true,
  showGrid = true,
  showDots = false,
  maxDataPoints = 1000,
  ...baseProps
}) => {
  const theme = useTheme();

  // Transform data for Recharts
  const chartData = useMemo(() => {
    if (!data || data.length === 0) return [];

    // Find all unique timestamps
    const timestamps = new Set<number>();
    data.forEach(series => {
      series.data.forEach(point => timestamps.add(point.timestamp));
    });

    // Create data points for each timestamp
    return Array.from(timestamps)
      .sort((a, b) => a - b)
      .map(timestamp => {
        const point: Record<string, number | string> = {
          timestamp,
          time: formatTimestamp(timestamp),
        };

        // Add values from each series
        data.forEach(series => {
          const dataPoint = series.data.find(p => p.timestamp === timestamp);
          if (dataPoint) {
            point[series.id] = dataPoint.value;
          }
        });

        return point;
      });
  }, [data]);

  // Color palette
  const colors = useMemo(() => {
    const palette = [
      theme.palette.primary.main,
      theme.palette.secondary.main,
      theme.palette.success.main,
      theme.palette.warning.main,
      theme.palette.error.main,
      theme.palette.info.main,
    ];

    return data.map((series, index) => series.color || palette[index % palette.length]);
  }, [data, theme]);

  return (
    <BaseChart data={data} config={config} maxDataPoints={maxDataPoints} {...baseProps}>
      <ResponsiveContainer width="100%" height="100%">
        <RechartsLineChart
          data={chartData}
          margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
        >
          {showGrid && (
            <CartesianGrid
              strokeDasharray="3 3"
              stroke={theme.palette.divider}
            />
          )}
          <XAxis
            dataKey="time"
            stroke={theme.palette.text.secondary}
            tick={{ fill: theme.palette.text.secondary }}
            label={config.xAxis?.label ? { value: config.xAxis.label, position: 'insideBottom', offset: -5 } : undefined}
          />
          <YAxis
            stroke={theme.palette.text.secondary}
            tick={{ fill: theme.palette.text.secondary }}
            label={config.yAxis?.label ? { value: config.yAxis.label, angle: -90, position: 'insideLeft' } : undefined}
            tickFormatter={(value: unknown) => config.yAxis?.format ? config.yAxis.format(Number(value)) : formatValue(Number(value))}
          />
          <Tooltip
            contentStyle={{
              backgroundColor: theme.palette.background.paper,
              border: `1px solid ${theme.palette.divider}`,
              borderRadius: theme.shape.borderRadius,
            }}
            formatter={(value: number) => formatValue(value)}
          />
          {config.legend?.show !== false && (
            <Legend
              wrapperStyle={{ paddingTop: '10px' }}
              iconType="line"
            />
          )}
          {data.map((series, index) => (
            <Line
              key={series.id}
              type={smooth ? 'monotone' : 'linear'}
              dataKey={series.id}
              name={series.name}
              stroke={colors[index]}
              strokeWidth={2}
              dot={showDots}
              activeDot={{ r: 6 }}
              animationDuration={config.animation !== false ? 300 : 0}
            />
          ))}
        </RechartsLineChart>
      </ResponsiveContainer>
    </BaseChart>
  );
};

LineChart.displayName = 'LineChart';

export default LineChart;
