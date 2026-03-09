import React from 'react';
import { Box, Typography, useTheme } from '@mui/material';
import {
  Area,
  AreaChart,
  Tooltip,
  ResponsiveContainer,
  XAxis,
  YAxis,
} from 'recharts';

export interface TrendPoint {
  timestamp: string;
  value: number;
}

export interface TrendSparklineProps {
  title: string;
  value: string;
  delta?: string;
  deltaColor?: 'success' | 'warning' | 'error' | 'info';
  data: TrendPoint[];
  unit?: string;
}

export const TrendSparkline: React.FC<TrendSparklineProps> = ({
  title,
  value,
  delta,
  deltaColor = 'success',
  data,
  unit,
}) => {
  const theme = useTheme();
  const gradientId = React.useId();
  const strokeColor = theme.palette.primary.main;

  return (
    <Box>
      <Typography variant="subtitle2" color="text.secondary">
        {title}
      </Typography>
      <Typography variant="h5" fontWeight={600} mt={0.5}>
        {value}
        {unit ? (
          <Typography component="span" variant="subtitle2" color="text.secondary" ml={1}>
            {unit}
          </Typography>
        ) : null}
      </Typography>
      {delta ? (
        <Typography variant="body2" color={`${deltaColor}.main`} mt={0.5}>
          {delta}
        </Typography>
      ) : null}
      <Box mt={1} sx={{ minWidth: 0, minHeight: 64 }}>
        <ResponsiveContainer width="100%" height={80}>
          <AreaChart data={data}>
            <defs>
              <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor={strokeColor} stopOpacity={0.6} />
                <stop offset="95%" stopColor={strokeColor} stopOpacity={0} />
              </linearGradient>
            </defs>
            <XAxis dataKey="timestamp" hide />
            <YAxis hide />
            <Tooltip
              contentStyle={{
                background: theme.palette.background.paper,
                borderRadius: theme.shape.borderRadius,
                border: `1px solid ${theme.palette.divider}`
              }}
              labelFormatter={(label) => new Date(label).toLocaleString()}
            />
            <Area
              type="monotone"
              dataKey="value"
              stroke={strokeColor}
              strokeWidth={2}
              fill={`url(#${gradientId})`}
            />
          </AreaChart>
        </ResponsiveContainer>
      </Box>
    </Box>
  );
};

export default TrendSparkline;
