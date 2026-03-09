import * as React from 'react';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { useChartTheme } from '../hooks';
import type { ChartDataPoint, ChartMetricConfig } from '../types';

export interface DonutChartProps {
  data: ChartDataPoint[];
  metric: ChartMetricConfig;
  height?: number;
  innerRadius?: number;
  outerRadius?: number;
  showLegend?: boolean;
}

const defaultPalette = ['#1976d2', '#10B981', '#F97316', '#6366F1', '#EC4899', '#14B8A6'];

export function DonutChart({
  data,
  metric,
  height = 280,
  innerRadius = 64,
  outerRadius = 110,
  showLegend = true,
}: DonutChartProps) {
  const theme = useChartTheme();

  const formatValue = (value: number) =>
    metric.formatter ? metric.formatter(value) : new Intl.NumberFormat().format(value);

  return (
    <ResponsiveContainer width="100%" height={height}>
      <PieChart>
        <Pie
          data={data}
          dataKey="value"
          nameKey="label"
          innerRadius={innerRadius}
          outerRadius={outerRadius}
          paddingAngle={4}
          stroke={theme.background}
        >
          {data.map((entry, index) => (
            <Cell
              key={entry.label}
              fill={entry.color ?? defaultPalette[index % defaultPalette.length]}
            />
          ))}
        </Pie>
        <Tooltip
          formatter={(value: number, key: string, payload?: { payload?: { label?: string } }) => {
            return [formatValue(value), payload?.payload?.label ?? key];
          }}
          contentStyle={{
            background: theme.tooltipBackground,
            border: `1px solid ${theme.tooltipBorder}`,
            borderRadius: 8,
            fontFamily: theme.fontFamily,
          }}
        />
        {showLegend ? (
          <Legend
            layout="vertical"
            align="right"
            verticalAlign="middle"
            wrapperStyle={{
              fontFamily: theme.fontFamily,
              color: theme.textColor,
            }}
          />
        ) : null}
      </PieChart>
    </ResponsiveContainer>
  );
}
