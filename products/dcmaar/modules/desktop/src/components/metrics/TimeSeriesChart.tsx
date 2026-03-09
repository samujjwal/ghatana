import React from 'react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Box, Typography, useTheme } from '@mui/material';

export interface TimeSeriesChartProps {
  title: string;
  description?: string;
  data: Array<{ timestamp: string;[key: string]: number | string }>;
  series: Array<{ key: string; label: string; color: string }>;
  height?: number;
}

export const TimeSeriesChart: React.FC<TimeSeriesChartProps> = ({
  title,
  description,
  data,
  series,
  height = 360,
}) => {
  const theme = useTheme();

  return (
    <Box>
      <Typography variant="h6" fontWeight={600}>
        {title}
      </Typography>
      {description ? (
        <Typography variant="body2" color="text.secondary" mb={2}>
          {description}
        </Typography>
      ) : null}
      <Box sx={{ height, minHeight: 200, minWidth: 0, width: '100%' }}>
        <ResponsiveContainer width="100%" height={height}>
          <AreaChart data={data}>
            <defs>
              {series.map((item) => (
                <linearGradient
                  key={item.key}
                  id={`gradient-${item.key}`}
                  x1="0"
                  y1="0"
                  x2="0"
                  y2="1"
                >
                  <stop offset="5%" stopColor={item.color} stopOpacity={0.6} />
                  <stop offset="95%" stopColor={item.color} stopOpacity={0} />
                </linearGradient>
              ))}
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(148, 163, 184, 0.14)" />
            <XAxis
              dataKey="timestamp"
              tickFormatter={(value) => new Date(value).toLocaleTimeString()}
              tick={{ fill: 'rgba(148, 163, 184, 0.6)' }}
            />
            <YAxis tick={{ fill: 'rgba(148, 163, 184, 0.6)' }} />
            <Tooltip
              contentStyle={{
                backgroundColor: theme.palette.background.paper,
                borderRadius: 12,
                border: `1px solid ${theme.palette.divider}`,
              }}
              labelFormatter={(value) => new Date(value).toLocaleString()}
            />
            <Legend />
            {series.map((item) => (
              <Area
                key={item.key}
                type="monotone"
                dataKey={item.key}
                stroke={item.color}
                fill={`url(#gradient-${item.key})`}
                strokeWidth={2}
                dot={false}
                name={item.label}
              />
            ))}
          </AreaChart>
        </ResponsiveContainer>
      </Box>
    </Box>
  );
};

export default TimeSeriesChart;
