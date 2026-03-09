import * as React from 'react';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Legend,
  Tooltip,
} from 'recharts';

import { useChartTheme } from '../hooks';
import type { ChartDataPoint, ChartMetricConfig } from '../types';

export interface CategoryBarChartProps {
  data: ChartDataPoint[];
  series: ChartMetricConfig[];
  height?: number;
  stacked?: boolean;
  xAxisFormatter?: (value: string) => string;
  yAxisFormatter?: (value: number) => string;
  layout?: 'horizontal' | 'vertical';
}

const defaultPalette = ['#2563EB', '#10B981', '#F97316', '#EC4899', '#6366F1', '#14B8A6'];

export function CategoryBarChart({
  data,
  series,
  height = 320,
  stacked = false,
  xAxisFormatter,
  yAxisFormatter,
  layout = 'horizontal' as const,
}: CategoryBarChartProps) {
  const theme = useChartTheme();

  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} layout={layout} margin={{ top: 16, right: 24, bottom: 24, left: 16 }}>
        <CartesianGrid strokeDasharray="3 3" stroke={theme.gridColor} />
        {layout === 'horizontal' ? (
          <XAxis
            dataKey="label"
            tick={{ fontFamily: theme.fontFamily, fill: theme.textColor, fontSize: 12 }}
            axisLine={{ stroke: theme.gridColor }}
            tickLine={{ stroke: theme.gridColor }}
            tickFormatter={xAxisFormatter}
          />
        ) : (
          <XAxis
            type="number"
            tick={{ fontFamily: theme.fontFamily, fill: theme.textColor, fontSize: 12 }}
            axisLine={{ stroke: theme.gridColor }}
            tickLine={{ stroke: theme.gridColor }}
            tickFormatter={yAxisFormatter}
          />
        )}

        {layout === 'horizontal' ? (
          <YAxis
            tick={{ fontFamily: theme.fontFamily, fill: theme.textColor, fontSize: 12 }}
            axisLine={{ stroke: theme.gridColor }}
            tickLine={{ stroke: theme.gridColor }}
            tickFormatter={yAxisFormatter}
          />
        ) : (
          <YAxis
            dataKey="label"
            type="category"
            tick={{ fontFamily: theme.fontFamily, fill: theme.textColor, fontSize: 12 }}
            axisLine={{ stroke: theme.gridColor }}
            tickLine={{ stroke: theme.gridColor }}
            tickFormatter={xAxisFormatter}
          />
        )}

        <Tooltip
          wrapperStyle={{ outline: 'none' }}
          contentStyle={{
            background: theme.tooltipBackground,
            border: `1px solid ${theme.tooltipBorder}`,
            borderRadius: 8,
            fontFamily: theme.fontFamily,
          }}
          labelStyle={{ color: theme.textColor }}
          formatter={(value: number, key: string) => {
            const metric = series.find((m) => m.id === key);
            if (!metric) {
              return [value, key];
            }
            return [metric.formatter ? metric.formatter(value) : value, metric.label];
          }}
        />

        <Legend
          wrapperStyle={{ fontFamily: theme.fontFamily, color: theme.textColor }}
        />

        {series.map((metric, index) => (
          <Bar
            key={metric.id}
            dataKey={metric.id}
            name={metric.label}
            stackId={stacked ? 'stack' : metric.id}
            fill={metric.color ?? defaultPalette[index % defaultPalette.length]}
            radius={layout === 'horizontal' ? [4, 4, 0, 0] : [0, 4, 4, 0]}
          />
        ))}
      </BarChart>
    </ResponsiveContainer>
  );
}
