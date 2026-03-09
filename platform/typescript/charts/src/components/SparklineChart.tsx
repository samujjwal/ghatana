import * as React from 'react';
import { ResponsiveContainer, AreaChart, Area } from 'recharts';
import { useChartTheme } from '../hooks';
import type { ChartDataPoint, ChartMetricConfig } from '../types';

export interface SparklineChartProps {
  data: ChartDataPoint[];
  metric: ChartMetricConfig;
  height?: number;
  showGradient?: boolean;
  showActiveDot?: boolean;
}

export function SparklineChart({
  data,
  metric,
  height = 80,
  showGradient = true,
  showActiveDot = false,
}: SparklineChartProps) {
  const _theme = useChartTheme();
  const gradientId = React.useId();
  const stroke = metric.color ?? '#2563EB';

  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data} margin={{ top: 8, bottom: 8, left: 0, right: 0 }}>
        {showGradient && (
          <defs>
            <linearGradient id={`${gradientId}-spark`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor={stroke} stopOpacity={0.35} />
              <stop offset="95%" stopColor={stroke} stopOpacity={0} />
            </linearGradient>
          </defs>
        )}
        <Area
          type="monotone"
          dataKey={metric.id}
          stroke={stroke}
          strokeWidth={2}
          fill={showGradient ? `url(#${gradientId}-spark)` : 'transparent'}
          dot={false}
          activeDot={showActiveDot ? { r: 4 } : undefined}
          name={metric.label}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}
