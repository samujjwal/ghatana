/**
 * BurndownChart Component
 *
 * @description Displays sprint burndown as a line chart showing
 * ideal vs actual remaining work over time.
 *
 * @doc.phase 3
 * @doc.component BurndownChart
 */

import React, { useMemo } from 'react';

// ============================================================================
// Types
// ============================================================================

export interface BurndownDataPoint {
  date: Date;
  remaining: number;
  ideal: number;
}

export interface BurndownChartProps {
  data: BurndownDataPoint[];
  totalPoints: number;
  sprintDays: number;
  currentDay?: number;
  height?: number;
  className?: string;
}

// ============================================================================
// Helper Functions
// ============================================================================

const formatDate = (date: Date): string => {
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
  });
};

const interpolatePath = (
  points: { x: number; y: number }[]
): string => {
  if (points.length < 2) return '';

  let path = `M ${points[0].x} ${points[0].y}`;
  for (let i = 1; i < points.length; i++) {
    path += ` L ${points[i].x} ${points[i].y}`;
  }
  return path;
};

// ============================================================================
// Main Component
// ============================================================================

export const BurndownChart: React.FC<BurndownChartProps> = ({
  data,
  totalPoints,
  sprintDays,
  currentDay,
  height = 300,
  className = '',
}) => {
  // Calculate chart dimensions
  const chartWidth = 100;
  const chartHeight = 100;
  const padding = { top: 10, right: 10, bottom: 20, left: 10 };

  // Calculate scale
  const maxY = useMemo(() => {
    const maxRemaining = Math.max(...data.map((d) => d.remaining), totalPoints);
    return maxRemaining * 1.1;
  }, [data, totalPoints]);

  // Calculate points for ideal line
  const idealPoints = useMemo(() => {
    return Array.from({ length: sprintDays + 1 }, (_, i) => {
      const x =
        padding.left +
        (i / sprintDays) * (chartWidth - padding.left - padding.right);
      const y =
        padding.top +
        (1 - (totalPoints - (totalPoints * i) / sprintDays) / maxY) *
          (chartHeight - padding.top - padding.bottom);
      return { x, y };
    });
  }, [sprintDays, totalPoints, maxY, chartWidth, chartHeight, padding]);

  // Calculate points for actual burndown
  const actualPoints = useMemo(() => {
    return data.map((d, i) => {
      const x =
        padding.left +
        (i / (data.length - 1 || 1)) *
          (chartWidth - padding.left - padding.right);
      const y =
        padding.top +
        (1 - d.remaining / maxY) * (chartHeight - padding.top - padding.bottom);
      return { x, y };
    });
  }, [data, maxY, chartWidth, chartHeight, padding]);

  // Calculate status
  const status = useMemo(() => {
    if (data.length === 0) return 'on-track';
    const lastActual = data[data.length - 1];
    const deviation = lastActual.remaining - lastActual.ideal;
    const percentDeviation = (deviation / totalPoints) * 100;

    if (percentDeviation > 20) return 'at-risk';
    if (percentDeviation > 10) return 'behind';
    if (percentDeviation < -10) return 'ahead';
    return 'on-track';
  }, [data, totalPoints]);

  // Stats
  const stats = useMemo(() => {
    if (data.length === 0) {
      return { remaining: totalPoints, completed: 0, percentComplete: 0 };
    }
    const remaining = data[data.length - 1].remaining;
    const completed = totalPoints - remaining;
    const percentComplete = Math.round((completed / totalPoints) * 100);
    return { remaining, completed, percentComplete };
  }, [data, totalPoints]);

  // Status colors
  const statusColors: Record<string, { bg: string; text: string }> = {
    'on-track': { bg: '#ECFDF5', text: '#059669' },
    ahead: { bg: '#DBEAFE', text: '#2563EB' },
    behind: { bg: '#FEF3C7', text: '#D97706' },
    'at-risk': { bg: '#FEE2E2', text: '#DC2626' },
  };

  const statusLabels: Record<string, string> = {
    'on-track': 'On Track',
    ahead: 'Ahead',
    behind: 'Behind',
    'at-risk': 'At Risk',
  };

  if (data.length === 0) {
    return (
      <div className={`burndown-chart burndown-chart--empty ${className}`}>
        <p className="empty-message">No burndown data available</p>
      </div>
    );
  }

  return (
    <div className={`burndown-chart ${className}`}>
      {/* Stats Header */}
      <div className="chart-header">
        <div className="chart-stats">
          <div className="stat-item">
            <span className="stat-value">{stats.remaining}</span>
            <span className="stat-label">Points Remaining</span>
          </div>
          <div className="stat-item">
            <span className="stat-value">{stats.completed}</span>
            <span className="stat-label">Completed</span>
          </div>
          <div className="stat-item">
            <span className="stat-value">{stats.percentComplete}%</span>
            <span className="stat-label">Progress</span>
          </div>
        </div>
        <div
          className="status-badge"
          style={{
            background: statusColors[status].bg,
            color: statusColors[status].text,
          }}
        >
          {statusLabels[status]}
        </div>
      </div>

      {/* Chart */}
      <div className="chart-container" style={{ height }}>
        <svg
          viewBox={`0 0 ${chartWidth} ${chartHeight}`}
          preserveAspectRatio="none"
          className="chart-svg"
        >
          {/* Grid Lines */}
          {[0, 25, 50, 75, 100].map((percent) => {
            const y =
              padding.top +
              (percent / 100) * (chartHeight - padding.top - padding.bottom);
            return (
              <line
                key={percent}
                x1={padding.left}
                y1={y}
                x2={chartWidth - padding.right}
                y2={y}
                stroke="#E5E7EB"
                strokeWidth="0.5"
              />
            );
          })}

          {/* Today Marker */}
          {currentDay !== undefined && currentDay < sprintDays && (
            <line
              x1={
                padding.left +
                (currentDay / sprintDays) *
                  (chartWidth - padding.left - padding.right)
              }
              y1={padding.top}
              x2={
                padding.left +
                (currentDay / sprintDays) *
                  (chartWidth - padding.left - padding.right)
              }
              y2={chartHeight - padding.bottom}
              stroke="#6366F1"
              strokeWidth="1"
              strokeDasharray="2,2"
            />
          )}

          {/* Ideal Line */}
          <path
            d={interpolatePath(idealPoints)}
            fill="none"
            stroke="#9CA3AF"
            strokeWidth="1.5"
            strokeDasharray="4,4"
          />

          {/* Actual Burndown Line */}
          <path
            d={interpolatePath(actualPoints)}
            fill="none"
            stroke="#3B82F6"
            strokeWidth="2"
          />

          {/* Data Points */}
          {actualPoints.map((point, i) => (
            <circle
              key={i}
              cx={point.x}
              cy={point.y}
              r="2"
              fill="#3B82F6"
              stroke="#fff"
              strokeWidth="1"
            />
          ))}

          {/* Area under actual line */}
          <path
            d={`${interpolatePath(actualPoints)} L ${
              actualPoints[actualPoints.length - 1].x
            } ${chartHeight - padding.bottom} L ${padding.left} ${
              chartHeight - padding.bottom
            } Z`}
            fill="url(#burndown-gradient)"
            opacity="0.3"
          />

          {/* Gradient Definition */}
          <defs>
            <linearGradient
              id="burndown-gradient"
              x1="0%"
              y1="0%"
              x2="0%"
              y2="100%"
            >
              <stop offset="0%" stopColor="#3B82F6" stopOpacity="0.6" />
              <stop offset="100%" stopColor="#3B82F6" stopOpacity="0" />
            </linearGradient>
          </defs>
        </svg>

        {/* Y-Axis Labels */}
        <div className="y-axis">
          <span className="y-label">{Math.round(maxY)}</span>
          <span className="y-label">{Math.round(maxY / 2)}</span>
          <span className="y-label">0</span>
        </div>
      </div>

      {/* X-Axis Labels */}
      <div className="x-axis">
        {data.length > 0 && (
          <>
            <span className="x-label">{formatDate(data[0].date)}</span>
            {data.length > 2 && (
              <span className="x-label">
                {formatDate(data[Math.floor(data.length / 2)].date)}
              </span>
            )}
            <span className="x-label">
              {formatDate(data[data.length - 1].date)}
            </span>
          </>
        )}
      </div>

      {/* Legend */}
      <div className="chart-legend">
        <div className="legend-item">
          <div className="legend-line legend-line--ideal" />
          <span className="legend-text">Ideal Burndown</span>
        </div>
        <div className="legend-item">
          <div className="legend-line legend-line--actual" />
          <span className="legend-text">Actual Burndown</span>
        </div>
        {currentDay !== undefined && (
          <div className="legend-item">
            <div className="legend-line legend-line--today" />
            <span className="legend-text">Today</span>
          </div>
        )}
      </div>

      {/* CSS-in-JS Styles */}
      <style>{`
        .burndown-chart {
          background: #fff;
          border-radius: 12px;
          padding: 1.5rem;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
        }

        .burndown-chart--empty {
          display: flex;
          align-items: center;
          justify-content: center;
          min-height: 200px;
        }

        .empty-message {
          color: #6B7280;
          font-size: 0.875rem;
        }

        .chart-header {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          margin-bottom: 1.5rem;
          padding-bottom: 1rem;
          border-bottom: 1px solid #E5E7EB;
        }

        .chart-stats {
          display: flex;
          gap: 2rem;
        }

        .stat-item {
          display: flex;
          flex-direction: column;
        }

        .stat-value {
          font-size: 1.5rem;
          font-weight: 700;
          color: #111827;
        }

        .stat-label {
          font-size: 0.75rem;
          color: #6B7280;
          text-transform: uppercase;
          letter-spacing: 0.05em;
        }

        .status-badge {
          padding: 0.375rem 0.75rem;
          border-radius: 9999px;
          font-size: 0.75rem;
          font-weight: 600;
          text-transform: uppercase;
          letter-spacing: 0.05em;
        }

        .chart-container {
          position: relative;
          margin-bottom: 0.5rem;
        }

        .chart-svg {
          width: 100%;
          height: 100%;
        }

        .y-axis {
          position: absolute;
          left: -30px;
          top: 0;
          bottom: 20px;
          display: flex;
          flex-direction: column;
          justify-content: space-between;
        }

        .y-label {
          font-size: 0.625rem;
          color: #6B7280;
          text-align: right;
        }

        .x-axis {
          display: flex;
          justify-content: space-between;
          padding: 0 10px;
        }

        .x-label {
          font-size: 0.625rem;
          color: #6B7280;
        }

        .chart-legend {
          display: flex;
          justify-content: center;
          gap: 1.5rem;
          margin-top: 1rem;
          padding-top: 1rem;
          border-top: 1px solid #E5E7EB;
        }

        .legend-item {
          display: flex;
          align-items: center;
          gap: 0.375rem;
        }

        .legend-line {
          width: 20px;
          height: 2px;
        }

        .legend-line--ideal {
          background: #9CA3AF;
          background-image: repeating-linear-gradient(
            90deg,
            #9CA3AF,
            #9CA3AF 4px,
            transparent 4px,
            transparent 8px
          );
        }

        .legend-line--actual {
          background: #3B82F6;
        }

        .legend-line--today {
          background: #6366F1;
          background-image: repeating-linear-gradient(
            90deg,
            #6366F1,
            #6366F1 2px,
            transparent 2px,
            transparent 4px
          );
        }

        .legend-text {
          font-size: 0.75rem;
          color: #6B7280;
        }
      `}</style>
    </div>
  );
};

BurndownChart.displayName = 'BurndownChart';

export default BurndownChart;
