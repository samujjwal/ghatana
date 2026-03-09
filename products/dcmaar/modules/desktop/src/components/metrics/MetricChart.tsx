import React from 'react';
import { Box } from '../../ui/tw-compat';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { Metric } from '../../services/daemon';

interface MetricChartProps {
  metrics: Metric[];
}

export default function MetricChart({ metrics }: MetricChartProps) {
  // Group metrics by name for the chart
  const chartData = metrics.reduce<Record<string, any>>((acc, metric) => {
    const timestamp = new Date(metric.timestamp).toLocaleTimeString();
    if (!acc[timestamp]) {
      acc[timestamp] = { timestamp };
    }
    acc[timestamp][metric.name] = metric.value;
    return acc;
  }, {});

  const data = Object.values(chartData);
  const metricNames = [...new Set(metrics.map(m => m.name))];

  return (
    <Box sx={{ height: 400 }}>
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="timestamp" />
          <YAxis />
          <Tooltip />
          <Legend />
          {metricNames.map(name => (
            <Line
              key={name}
              type="monotone"
              dataKey={name}
              stroke={`#${Math.floor(Math.random()*16777215).toString(16)}`}
              activeDot={{ r: 8 }}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </Box>
  );
}
