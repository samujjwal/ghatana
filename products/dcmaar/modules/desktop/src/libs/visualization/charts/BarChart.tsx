/**
 * Bar Chart Component
 * 
 * Optimized bar chart for categorical and comparative data with:
 * - Horizontal and vertical orientations
 * - Stacked and grouped modes
 * - Interactive tooltips
 * - Export capabilities
 */

import React, { useMemo } from 'react';
import {
  BarChart as RechartsBarChart,
  Bar,
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

export interface BarChartProps extends Omit<BaseChartProps, 'config'> {
  config?: BaseChartProps['config'];
  orientation?: 'vertical' | 'horizontal';
  stacked?: boolean;
  showGrid?: boolean;
}

/**
 * Bar chart component for categorical data visualization
 */
export const BarChart: React.FC<BarChartProps> = ({
  data,
  config = { type: 'bar' },
  orientation = 'vertical',
  stacked = false,
  showGrid = true,
  maxDataPoints = 1000,
  ...baseProps
}) => {
  const theme = useTheme();

  // Transform data for Recharts
  const chartData = useMemo(() => {
    if (!data || data.length === 0) return [];

    // Find all unique timestamps/categories
    const categories = new Set<number>();
    data.forEach(series => {
      series.data.forEach(point => categories.add(point.timestamp));
    });

    // Create data points for each category
    return Array.from(categories)
      .sort((a, b) => a - b)
      .map(timestamp => {
        const point: Record<string, number | string> = {
          timestamp,
          category: formatTimestamp(timestamp),
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

  const isHorizontal = orientation === 'horizontal';

  return (
    <BaseChart data={data} config={config} maxDataPoints={maxDataPoints} {...baseProps}>
      <ResponsiveContainer width="100%" height="100%">
        <RechartsBarChart
          data={chartData}
          layout={isHorizontal ? 'horizontal' : 'vertical'}
          margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
        >
          {showGrid && (
            <CartesianGrid
              strokeDasharray="3 3"
              stroke={theme.palette.divider}
            />
          )}
          {isHorizontal ? (
            <>
              <XAxis
                type="number"
                stroke={theme.palette.text.secondary}
                tick={{ fill: theme.palette.text.secondary }}
                tickFormatter={(value: unknown) => config.xAxis?.format ? config.xAxis.format(Number(value)) : formatValue(Number(value))}
              />
              <YAxis
                type="category"
                dataKey="category"
                stroke={theme.palette.text.secondary}
                tick={{ fill: theme.palette.text.secondary }}
              />
            </>
          ) : (
            <>
              <XAxis
                type="category"
                dataKey="category"
                stroke={theme.palette.text.secondary}
                tick={{ fill: theme.palette.text.secondary }}
                label={config.xAxis?.label ? { value: config.xAxis.label, position: 'insideBottom', offset: -5 } : undefined}
              />
              <YAxis
                type="number"
                stroke={theme.palette.text.secondary}
                tick={{ fill: theme.palette.text.secondary }}
                label={config.yAxis?.label ? { value: config.yAxis.label, angle: -90, position: 'insideLeft' } : undefined}
                tickFormatter={(value: unknown) => config.yAxis?.format ? config.yAxis.format(Number(value)) : formatValue(Number(value))}
              />
            </>
          )}
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
            />
          )}
          {data.map((series, index) => (
            <Bar
              key={series.id}
              dataKey={series.id}
              name={series.name}
              fill={colors[index]}
              stackId={stacked ? 'stack' : undefined}
              animationDuration={config.animation !== false ? 300 : 0}
            />
          ))}
        </RechartsBarChart>
      </ResponsiveContainer>
    </BaseChart>
  );
};

BarChart.displayName = 'BarChart';

export default BarChart;
