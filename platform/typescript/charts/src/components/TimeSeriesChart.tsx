import * as React from 'react';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from 'recharts';
import { useChartTheme } from '../hooks';
import type { ChartDataPoint, ChartMetricConfig } from '../types';

export interface TimeSeriesChartProps {
  data: ChartDataPoint[];
  series: ChartMetricConfig[];
  height?: number;
  showLegend?: boolean;
  xAxisFormatter?: (label: string) => string;
  yAxisFormatter?: (value: number) => string;
}

export function TimeSeriesChart({
  data,
  series,
  height = 280,
  showLegend = true,
  xAxisFormatter,
  yAxisFormatter,
}: TimeSeriesChartProps) {
  const theme = useChartTheme();

  const lines = series.map((metric, index) => {
    const color =
      metric.color ??
      ['#1976d2', '#10B981', '#F59E0B', '#EF4444', '#6366F1'][index % 5];
    const dataKey = metric.id;

    return (
      <Line
        key={metric.id}
        type="monotone"
        dataKey={dataKey}
        name={metric.label}
        stroke={color}
        strokeWidth={2}
        dot={false}
        activeDot={{ r: 5 }}
      />
    );
  });

  const formatValue = (metric: ChartMetricConfig, value: number) =>
    metric.formatter ? metric.formatter(value) : value;

  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={data} margin={{ top: 16, right: 32, left: 0, bottom: 8 }}>
        <CartesianGrid strokeDasharray="3 3" stroke={theme.gridColor} vertical={false} />
        <XAxis
          dataKey="label"
          tick={{ fontFamily: theme.fontFamily, fill: theme.textColor }}
          tickFormatter={xAxisFormatter}
          axisLine={{ stroke: theme.gridColor }}
          tickLine={{ stroke: theme.gridColor }}
        />
        <YAxis
          tick={{ fontFamily: theme.fontFamily, fill: theme.textColor }}
          axisLine={{ stroke: theme.gridColor }}
          tickLine={{ stroke: theme.gridColor }}
          tickFormatter={yAxisFormatter}
        />
        <Tooltip
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
            return [formatValue(metric, value), metric.label];
          }}
        />
        {showLegend ? (
          <Legend
            verticalAlign="top"
            height={32}
            wrapperStyle={{
              fontFamily: theme.fontFamily,
              color: theme.textColor,
            }}
          />
        ) : null}
        {lines}
      </LineChart>
    </ResponsiveContainer>
  );
}
