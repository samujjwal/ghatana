/**
 * Pie Chart Component
 * 
 * Optimized pie/donut chart for proportional data with:
 * - Donut mode support
 * - Interactive segments
 * - Percentage labels
 * - Export capabilities
 */

import React, { useMemo } from 'react';
import {
  PieChart as RechartsPieChart,
  Pie,
  Cell,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { useTheme } from '@mui/material';
import type { BaseChartProps } from './BaseChart';
import { BaseChart } from './BaseChart';
import { formatValue } from '../utils';

export interface PieChartProps extends Omit<BaseChartProps, 'config'> {
  config?: BaseChartProps['config'];
  donut?: boolean;
  innerRadius?: number;
  showLabels?: boolean;
  showPercentage?: boolean;
}

/**
 * Pie chart component for proportional data visualization
 */
export const PieChart: React.FC<PieChartProps> = ({
  data,
  config = { type: 'pie' },
  donut = false,
  innerRadius = 60,
  showLabels = true,
  showPercentage = true,
  ...baseProps
}) => {
  const theme = useTheme();

  // Transform data for Recharts
  const chartData = useMemo(() => {
    if (!data || data.length === 0) return [];

    // Calculate total for percentages
    const total = data.reduce((sum, series) => {
      return sum + series.data.reduce((s, point) => s + point.value, 0);
    }, 0);

    // Transform to pie chart format
    return data.flatMap(series =>
      series.data.map(point => ({
        name: point.label || series.name,
        value: point.value,
        percentage: total > 0 ? (point.value / total) * 100 : 0,
        color: series.color,
      }))
    );
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
      theme.palette.primary.light,
      theme.palette.secondary.light,
    ];

    return chartData.map((item, index) => item.color || palette[index % palette.length]);
  }, [chartData, theme]);

  // Custom label renderer
  const renderLabel = (props: any) => {
    if (!showLabels) return null;
    const entry = chartData.find((item: any) => item.name === props.name);
    if (!entry) return null;
    if (showPercentage) {
      return `${entry.name}: ${entry.percentage.toFixed(1)}%`;
    }
    return entry.name;
  };

  return (
    <BaseChart data={data} config={config} {...baseProps}>
      <ResponsiveContainer width="100%" height="100%">
        <RechartsPieChart>
          <Pie
            data={chartData}
            cx="50%"
            cy="50%"
            labelLine={showLabels}
            label={renderLabel}
            outerRadius={80}
            innerRadius={donut ? innerRadius : 0}
            fill={theme.palette.primary.main}
            dataKey="value"
            animationDuration={config.animation !== false ? 300 : 0}
          >
            {chartData.map((entry, index) => (
              <Cell key={`cell-${index}`} fill={colors[index]} />
            ))}
          </Pie>
          <Tooltip
            contentStyle={{
              backgroundColor: theme.palette.background.paper,
              border: `1px solid ${theme.palette.divider}`,
              borderRadius: theme.shape.borderRadius,
            }}
            formatter={(value: number, name: string, props: any) => [
              `${formatValue(value)} (${props?.payload?.percentage ? props.payload.percentage.toFixed(1) : '0.0'}%)`,
              name,
            ]}
          />
          {config.legend?.show !== false && (
            <Legend
              wrapperStyle={{ paddingTop: '10px' }}
            />
          )}
        </RechartsPieChart>
      </ResponsiveContainer>
    </BaseChart>
  );
};

PieChart.displayName = 'PieChart';

export default PieChart;
