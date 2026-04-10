/**
 * VelocityChart Component
 *
 * @description Displays sprint velocity over time as a bar chart.
 * Shows committed vs completed points per sprint with trend line.
 *
 * @doc.phase 3
 * @doc.component VelocityChart
 */

import React, { useMemo } from 'react';

// ============================================================================
// Types
// ============================================================================

export interface SprintVelocityData {
  sprintId: string;
  sprintName: string;
  committed: number;
  completed: number;
  startDate: Date;
  endDate: Date;
}

export interface VelocityChartProps {
  data: SprintVelocityData[];
  averageVelocity?: number;
  showTrendLine?: boolean;
  height?: number;
  className?: string;
}

// ============================================================================
// Helper Functions
// ============================================================================

const calculateTrendLine = (
  data: SprintVelocityData[]
): { start: number; end: number } | null => {
  if (data.length < 2) return null;

  const n = data.length;
  const sumX = (n * (n - 1)) / 2;
  const sumY = data.reduce((sum, d) => sum + d.completed, 0);
  const sumXY = data.reduce((sum, d, i) => sum + i * d.completed, 0);
  const sumX2 = (n * (n - 1) * (2 * n - 1)) / 6;

  const slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
  const intercept = (sumY - slope * sumX) / n;

  return {
    start: intercept,
    end: intercept + slope * (n - 1),
  };
};

// ============================================================================
// Main Component
// ============================================================================

export const VelocityChart: React.FC<VelocityChartProps> = ({
  data,
  averageVelocity,
  showTrendLine = true,
  height = 300,
  className = '',
}) => {
  // Calculate max value for scaling
  const maxValue = useMemo(() => {
    const values = data.flatMap((d) => [d.committed, d.completed]);
    return Math.max(...values, 1) * 1.1; // 10% padding
  }, [data]);

  // Calculate average if not provided
  const calculatedAverage = useMemo(() => {
    if (averageVelocity !== undefined) return averageVelocity;
    if (data.length === 0) return 0;
    const total = data.reduce((sum, d) => sum + d.completed, 0);
    return Math.round(total / data.length);
  }, [data, averageVelocity]);

  // Calculate trend line
  const trendLine = useMemo(() => {
    if (!showTrendLine) return null;
    return calculateTrendLine(data);
  }, [data, showTrendLine]);

  // Stats
  const stats = useMemo(() => {
    if (data.length === 0) {
      return { avgCommitted: 0, avgCompleted: 0, completionRate: 0 };
    }
    const totalCommitted = data.reduce((sum, d) => sum + d.committed, 0);
    const totalCompleted = data.reduce((sum, d) => sum + d.completed, 0);
    return {
      avgCommitted: Math.round(totalCommitted / data.length),
      avgCompleted: Math.round(totalCompleted / data.length),
      completionRate:
        totalCommitted > 0
          ? Math.round((totalCompleted / totalCommitted) * 100)
          : 0,
    };
  }, [data]);

  // Bar width calculation
  const barWidth = useMemo(() => {
    const totalWidth = 100;
    const gaps = data.length + 1;
    const gapSize = 2;
    const availableWidth = totalWidth - gapSize * gaps;
    return availableWidth / data.length / 2; // /2 because committed + completed
  }, [data.length]);

  if (data.length === 0) {
    return (
      <div className={`velocity-chart velocity-chart--empty ${className}`}>
        <p className="empty-message">No velocity data available</p>
      </div>
    );
  }

  return (
    <div className={`velocity-chart ${className}`}>
      {/* Stats Summary */}
      <div className="chart-stats">
        <div className="stat-item">
          <span className="stat-value">{stats.avgCompleted}</span>
          <span className="stat-label">Avg Velocity</span>
        </div>
        <div className="stat-item">
          <span className="stat-value">{stats.avgCommitted}</span>
          <span className="stat-label">Avg Committed</span>
        </div>
        <div className="stat-item">
          <span className="stat-value">{stats.completionRate}%</span>
          <span className="stat-label">Completion Rate</span>
        </div>
      </div>

      {/* Chart Container */}
      <div className="chart-container" style={{ height }}>
        {/* Y-Axis Labels */}
        <div className="y-axis">
          <span className="y-label">{Math.round(maxValue)}</span>
          <span className="y-label">{Math.round(maxValue / 2)}</span>
          <span className="y-label">0</span>
        </div>

        {/* Chart Area */}
        <div className="chart-area">
          {/* Grid Lines */}
          <div className="grid-lines">
            <div className="grid-line" style={{ bottom: '0%' }} />
            <div className="grid-line" style={{ bottom: '25%' }} />
            <div className="grid-line" style={{ bottom: '50%' }} />
            <div className="grid-line" style={{ bottom: '75%' }} />
            <div className="grid-line" style={{ bottom: '100%' }} />
          </div>

          {/* Average Line */}
          {calculatedAverage > 0 && (
            <div
              className="average-line"
              style={{ bottom: `${(calculatedAverage / maxValue) * 100}%` }}
            >
              <span className="average-label">
                Avg: {calculatedAverage} pts
              </span>
            </div>
          )}

          {/* Trend Line */}
          {trendLine && (
            <svg className="trend-line-svg" preserveAspectRatio="none">
              <line
                x1="0%"
                y1={`${100 - (trendLine.start / maxValue) * 100}%`}
                x2="100%"
                y2={`${100 - (trendLine.end / maxValue) * 100}%`}
                stroke="#6366F1"
                strokeWidth="2"
                strokeDasharray="5,5"
              />
            </svg>
          )}

          {/* Bars */}
          <div className="bars-container">
            {data.map((sprint, index) => {
              const committedHeight = (sprint.committed / maxValue) * 100;
              const completedHeight = (sprint.completed / maxValue) * 100;

              return (
                <div
                  key={sprint.sprintId}
                  className="bar-group"
                  style={{ width: `${barWidth * 2}%` }}
                >
                  <div className="bars">
                    <div
                      className="bar bar--committed"
                      style={{ height: `${committedHeight}%` }}
                      title={`Committed: ${sprint.committed} pts`}
                    >
                      <span className="bar-value">{sprint.committed}</span>
                    </div>
                    <div
                      className="bar bar--completed"
                      style={{ height: `${completedHeight}%` }}
                      title={`Completed: ${sprint.completed} pts`}
                    >
                      <span className="bar-value">{sprint.completed}</span>
                    </div>
                  </div>
                  <span className="bar-label">{sprint.sprintName}</span>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Legend */}
      <div className="chart-legend">
        <div className="legend-item">
          <span className="legend-color legend-color--committed" />
          <span className="legend-text">Committed</span>
        </div>
        <div className="legend-item">
          <span className="legend-color legend-color--completed" />
          <span className="legend-text">Completed</span>
        </div>
        {showTrendLine && (
          <div className="legend-item">
            <span className="legend-color legend-color--trend" />
            <span className="legend-text">Trend</span>
          </div>
        )}
      </div>

      {/* CSS-in-JS Styles */}
      <style>{`
        .velocity-chart {
          background: #fff;
          border-radius: 12px;
          padding: 1.5rem;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
        }

        .velocity-chart--empty {
          display: flex;
          align-items: center;
          justify-content: center;
          min-height: 200px;
        }

        .empty-message {
          color: #6B7280;
          font-size: 0.875rem;
        }

        .chart-stats {
          display: flex;
          gap: 2rem;
          margin-bottom: 1.5rem;
          padding-bottom: 1rem;
          border-bottom: 1px solid #E5E7EB;
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

        .chart-container {
          display: flex;
          gap: 0.5rem;
          position: relative;
        }

        .y-axis {
          display: flex;
          flex-direction: column;
          justify-content: space-between;
          width: 40px;
          padding-bottom: 24px;
        }

        .y-label {
          font-size: 0.625rem;
          color: #6B7280;
          text-align: right;
        }

        .chart-area {
          flex: 1;
          position: relative;
          padding-bottom: 24px;
        }

        .grid-lines {
          position: absolute;
          inset: 0;
          bottom: 24px;
        }

        .grid-line {
          position: absolute;
          left: 0;
          right: 0;
          height: 1px;
          background: #E5E7EB;
        }

        .average-line {
          position: absolute;
          left: 0;
          right: 0;
          height: 2px;
          background: #F59E0B;
          z-index: 10;
        }

        .average-label {
          position: absolute;
          right: 0;
          top: -18px;
          font-size: 0.625rem;
          font-weight: 500;
          color: #F59E0B;
          background: #fff;
          padding: 0 0.25rem;
        }

        .trend-line-svg {
          position: absolute;
          inset: 0;
          bottom: 24px;
          z-index: 5;
        }

        .bars-container {
          display: flex;
          justify-content: space-around;
          align-items: flex-end;
          height: calc(100% - 24px);
          gap: 1rem;
        }

        .bar-group {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 0.5rem;
        }

        .bars {
          display: flex;
          align-items: flex-end;
          gap: 2px;
          height: 100%;
        }

        .bar {
          width: 24px;
          min-height: 4px;
          border-radius: 4px 4px 0 0;
          position: relative;
          transition: height 0.3s ease;
        }

        .bar--committed {
          background: #93C5FD;
        }

        .bar--completed {
          background: #3B82F6;
        }

        .bar-value {
          position: absolute;
          top: -18px;
          left: 50%;
          transform: translateX(-50%);
          font-size: 0.625rem;
          font-weight: 500;
          color: #374151;
          white-space: nowrap;
        }

        .bar-label {
          font-size: 0.625rem;
          color: #6B7280;
          text-align: center;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          max-width: 60px;
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

        .legend-color {
          width: 12px;
          height: 12px;
          border-radius: 2px;
        }

        .legend-color--committed {
          background: #93C5FD;
        }

        .legend-color--completed {
          background: #3B82F6;
        }

        .legend-color--trend {
          background: transparent;
          border: 2px dashed #6366F1;
        }

        .legend-text {
          font-size: 0.75rem;
          color: #6B7280;
        }
      `}</style>
    </div>
  );
};

VelocityChart.displayName = 'VelocityChart';

export default VelocityChart;
