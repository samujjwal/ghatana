/**
 * ChartWidget Component
 *
 * Data visualization widget supporting line, bar, and area chart types via Recharts.
 *
 * @doc.type component
 * @doc.purpose Render interactive data charts
 * @doc.layer product
 * @doc.pattern Component
 */
import React from 'react';
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';

type ChartType = 'line' | 'bar' | 'area';

interface ChartWidgetProps {
  type: ChartType;
  title: string;
  data: Record<string, unknown>[];
  xKey: string;
  yKeys: string[];
  showLegend?: boolean;
  isLoading?: boolean;
  width?: number;
  height?: number;
  colors?: string[];
}

const DEFAULT_COLORS = ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#3b82f6'];

/**
 * ChartWidget renders a responsive data chart using Recharts.
 */
export const ChartWidget: React.FC<ChartWidgetProps> = ({
  type,
  title,
  data,
  xKey,
  yKeys,
  showLegend = false,
  isLoading = false,
  width,
  height = 300,
  colors = DEFAULT_COLORS,
}) => {
  if (isLoading) {
    return <div data-testid="chart-skeleton" className="animate-pulse rounded-lg bg-surface-muted" style={{ height }} />;
  }

  if (!data || data.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border p-6" style={{ height }}>
        <p className="text-sm text-fg-muted">No data available</p>
      </div>
    );
  }

  const containerStyle = width != null ? { width, height } : undefined;

  const commonProps = {
    data,
    margin: { top: 5, right: 30, left: 20, bottom: 5 },
  };

  function renderLines() {
    return yKeys.map((key, idx) => (
      <Line
        key={key}
        type="monotone"
        dataKey={key}
        stroke={colors[idx] ?? DEFAULT_COLORS[idx % DEFAULT_COLORS.length]}
        dot
      />
    ));
  }

  function renderBars() {
    return yKeys.map((key, idx) => (
      <Bar
        key={key}
        dataKey={key}
        fill={colors[idx] ?? DEFAULT_COLORS[idx % DEFAULT_COLORS.length]}
      />
    ));
  }

  function renderAreas() {
    return yKeys.map((key, idx) => (
      <Area
        key={key}
        type="monotone"
        dataKey={key}
        stroke={colors[idx] ?? DEFAULT_COLORS[idx % DEFAULT_COLORS.length]}
        fill={`${colors[idx] ?? DEFAULT_COLORS[idx % DEFAULT_COLORS.length]}33`}
      />
    ));
  }

  function renderChart() {
    if (type === 'bar') {
      return (
        <div data-testid="recharts-bar-chart">
          <BarChart {...commonProps}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey={xKey} />
            <YAxis />
            <Tooltip />
            {showLegend && <Legend />}
            {renderBars()}
          </BarChart>
        </div>
      );
    }
    if (type === 'area') {
      return (
        <div data-testid="recharts-area-chart">
          <AreaChart {...commonProps}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey={xKey} />
            <YAxis />
            <Tooltip />
            {showLegend && <Legend />}
            {renderAreas()}
          </AreaChart>
        </div>
      );
    }
    // default: line
    return (
      <div data-testid="recharts-line-chart">
        <LineChart {...commonProps}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey={xKey} />
          <YAxis />
          <Tooltip />
          {showLegend && <Legend />}
          {renderLines()}
        </LineChart>
      </div>
    );
  }

  return (
    <div>
      <h3 className="mb-2 text-base font-semibold">{title}</h3>
      {containerStyle ? (
        <div className="recharts-responsive-container" style={containerStyle}>
          {renderChart()}
        </div>
      ) : (
        <ResponsiveContainer width="100%" height={height}>
          {renderChart()}
        </ResponsiveContainer>
      )}
    </div>
  );
};

export default ChartWidget;
