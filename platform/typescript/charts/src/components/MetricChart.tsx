import * as React from 'react';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from 'recharts';
import { useChartTheme } from '../hooks';
import type { ChartDataPoint, ChartMetricConfig } from '../types';

export interface MetricChartProps {
  data: ChartDataPoint[];
  primary: ChartMetricConfig;
  secondary?: ChartMetricConfig;
  height?: number;
  showGrid?: boolean;
  xAxisFormatter?: (label: string) => string;
}

export function MetricChart({
  data,
  primary,
  secondary,
  height = 240,
  showGrid = true,
  xAxisFormatter,
}: MetricChartProps) {
  const theme = useChartTheme();
  const gradientId = React.useId();

  const primaryColor = primary.color ?? '#1976d2';
  const secondaryColor = secondary?.color ?? '#10B981';

  const formatValue = (metric: ChartMetricConfig, value: number) =>
    metric.formatter ? metric.formatter(value) : new Intl.NumberFormat().format(value);

  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data} margin={{ top: 16, right: 16, left: 8, bottom: 16 }}>
        <defs>
          <linearGradient id={`${gradientId}-primary`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor={primaryColor} stopOpacity={0.35} />
            <stop offset="95%" stopColor={primaryColor} stopOpacity={0} />
          </linearGradient>
          {secondary ? (
            <linearGradient id={`${gradientId}-secondary`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor={secondaryColor} stopOpacity={0.3} />
              <stop offset="95%" stopColor={secondaryColor} stopOpacity={0} />
            </linearGradient>
          ) : null}
        </defs>

        {showGrid ? (
          <CartesianGrid stroke={theme.gridColor} strokeDasharray="3 3" vertical={false} />
        ) : null}

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
          tickFormatter={(value: number) => formatValue(primary, value)}
        />

        <Tooltip
          cursor={{ stroke: primaryColor, strokeWidth: 1, strokeDasharray: '4 4' }}
          contentStyle={{
            background: theme.tooltipBackground,
            border: `1px solid ${theme.tooltipBorder}`,
            boxShadow: '0 4px 20px rgba(15, 23, 42, 0.12)',
            borderRadius: 8,
            fontFamily: theme.fontFamily,
          }}
          labelStyle={{ color: theme.textColor }}
          formatter={(value: number, key: string) => {
            if (key === 'value') {
              return [formatValue(primary, value), primary.label];
            }
            if (key === 'secondaryValue' && secondary) {
              return [formatValue(secondary, value), secondary.label];
            }
            return [value, key];
          }}
        />

        <Area
          type="monotone"
          dataKey="value"
          stroke={primaryColor}
          strokeWidth={2.5}
          fillOpacity={1}
          fill={`url(#${gradientId}-primary)`}
          dot={{ stroke: primaryColor, strokeWidth: 1, r: 3 }}
          activeDot={{ r: 5 }}
          name={primary.label}
        />

        {secondary ? (
          <Area
            type="monotone"
            dataKey="secondaryValue"
            stroke={secondaryColor}
            strokeWidth={2}
            fillOpacity={1}
            fill={`url(#${gradientId}-secondary)`}
            dot={{ stroke: secondaryColor, strokeWidth: 1, r: 3 }}
            activeDot={{ r: 5 }}
            name={secondary.label}
          />
        ) : null}
      </AreaChart>
    </ResponsiveContainer>
  );
}
