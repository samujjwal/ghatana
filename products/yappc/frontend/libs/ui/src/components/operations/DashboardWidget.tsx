/**
 * DashboardWidget Component
 *
 * @description Configurable widget container for custom dashboards with
 * support for various chart types, metrics, and data visualizations.
 *
 * @doc.type component
 * @doc.purpose Custom dashboard building blocks
 * @doc.layer presentation
 * @doc.phase 4
 */

import React, { useState, useMemo } from 'react';
import { cn } from '@ghatana/ui';

// ============================================================================
// Types
// ============================================================================

export type WidgetType =
  | 'line_chart'
  | 'bar_chart'
  | 'pie_chart'
  | 'gauge'
  | 'stat'
  | 'table'
  | 'text'
  | 'log_stream'
  | 'alert_list'
  | 'heatmap';

export type WidgetSize = 'small' | 'medium' | 'large' | 'full';

export interface WidgetDataPoint {
  label: string;
  value: number;
  color?: string;
  timestamp?: string;
}

export interface WidgetSeries {
  name: string;
  data: WidgetDataPoint[];
  color?: string;
}

export interface WidgetConfig {
  id: string;
  type: WidgetType;
  title: string;
  description?: string;
  size: WidgetSize;
  refreshInterval?: number;
  thresholds?: { warning: number; critical: number };
  query?: string;
}

export interface DashboardWidgetProps {
  config: WidgetConfig;
  data?: WidgetSeries[] | WidgetDataPoint[] | number | string;
  isLoading?: boolean;
  error?: string;
  isEditing?: boolean;
  onEdit?: (widgetId: string) => void;
  onDelete?: (widgetId: string) => void;
  onRefresh?: (widgetId: string) => void;
  onResize?: (widgetId: string, size: WidgetSize) => void;
  className?: string;
}

// ============================================================================
// Utility Functions
// ============================================================================

const getWidgetTypeConfig = (type: WidgetType) => {
  const configs: Record<WidgetType, { icon: string; label: string }> = {
    line_chart: { icon: '📈', label: 'Line Chart' },
    bar_chart: { icon: '📊', label: 'Bar Chart' },
    pie_chart: { icon: '🥧', label: 'Pie Chart' },
    gauge: { icon: '🎯', label: 'Gauge' },
    stat: { icon: '🔢', label: 'Stat' },
    table: { icon: '📋', label: 'Table' },
    text: { icon: '📝', label: 'Text' },
    log_stream: { icon: '📜', label: 'Log Stream' },
    alert_list: { icon: '🚨', label: 'Alert List' },
    heatmap: { icon: '🗺️', label: 'Heatmap' },
  };
  return configs[type];
};

const getSizeConfig = (size: WidgetSize) => {
  const configs: Record<WidgetSize, { width: string; minHeight: string }> = {
    small: { width: '25%', minHeight: '150px' },
    medium: { width: '50%', minHeight: '200px' },
    large: { width: '75%', minHeight: '250px' },
    full: { width: '100%', minHeight: '300px' },
  };
  return configs[size];
};

const formatStatValue = (value: number | string): string => {
  if (typeof value === 'string') return value;
  if (value >= 1e9) return `${(value / 1e9).toFixed(2)}B`;
  if (value >= 1e6) return `${(value / 1e6).toFixed(2)}M`;
  if (value >= 1e3) return `${(value / 1e3).toFixed(1)}K`;
  return value.toLocaleString();
};

// ============================================================================
// Chart Renderers
// ============================================================================

interface ChartProps {
  data: WidgetSeries[] | WidgetDataPoint[];
  width?: number;
  height?: number;
  thresholds?: { warning: number; critical: number };
}

const SimpleLineChart: React.FC<ChartProps> = ({ data, height = 120 }) => {
  const series = Array.isArray(data) && data[0] && 'data' in data[0]
    ? (data as WidgetSeries[])
    : [{ name: 'default', data: data as WidgetDataPoint[] }];

  const allValues = series.flatMap((s) => s.data.map((d) => d.value));
  const minVal = Math.min(...allValues);
  const maxVal = Math.max(...allValues);
  const range = maxVal - minVal || 1;

  return (
    <svg width="100%" height={height} className="simple-chart">
      {series.map((s, si) => {
        const points = s.data.map((d, i) => {
          const x = (i / (s.data.length - 1)) * 100;
          const y = height - ((d.value - minVal) / range) * (height - 20) - 10;
          return `${x}%,${y}`;
        }).join(' ');

        return (
          <polyline
            key={si}
            points={points}
            fill="none"
            stroke={s.color || '#3B82F6'}
            strokeWidth={2}
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        );
      })}
    </svg>
  );
};

const SimpleBarChart: React.FC<ChartProps> = ({ data, height = 120 }) => {
  const points = Array.isArray(data) && data[0] && 'data' in data[0]
    ? (data as WidgetSeries[])[0].data
    : (data as WidgetDataPoint[]);

  const maxVal = Math.max(...points.map((d) => d.value));
  const barWidth = 100 / points.length;

  return (
    <svg width="100%" height={height} className="simple-chart">
      {points.map((point, i) => {
        const barHeight = (point.value / maxVal) * (height - 30);
        return (
          <g key={i}>
            <rect
              x={`${i * barWidth + barWidth * 0.1}%`}
              y={height - barHeight - 20}
              width={`${barWidth * 0.8}%`}
              height={barHeight}
              fill={point.color || '#3B82F6'}
              rx={4}
            />
            <text
              x={`${i * barWidth + barWidth / 2}%`}
              y={height - 5}
              textAnchor="middle"
              fontSize={10}
              fill="#6B7280"
            >
              {point.label.slice(0, 3)}
            </text>
          </g>
        );
      })}
    </svg>
  );
};

const SimpleGauge: React.FC<{ value: number; max?: number; thresholds?: { warning: number; critical: number } }> = ({
  value,
  max = 100,
  thresholds,
}) => {
  const percentage = Math.min((value / max) * 100, 100);
  const angle = (percentage / 100) * 180;

  let color = '#10B981';
  if (thresholds) {
    if (value >= thresholds.critical) color = '#EF4444';
    else if (value >= thresholds.warning) color = '#F59E0B';
  }

  return (
    <div className="gauge-container">
      <svg viewBox="0 0 100 60" className="gauge-svg">
        {/* Background arc */}
        <path
          d="M 10 50 A 40 40 0 0 1 90 50"
          fill="none"
          stroke="#E5E7EB"
          strokeWidth={8}
          strokeLinecap="round"
        />
        {/* Value arc */}
        <path
          d="M 10 50 A 40 40 0 0 1 90 50"
          fill="none"
          stroke={color}
          strokeWidth={8}
          strokeLinecap="round"
          strokeDasharray={`${(angle / 180) * 126} 126`}
        />
      </svg>
      <div className="gauge-value">
        <span className="gauge-number" style={{ color }}>
          {formatStatValue(value)}
        </span>
        <span className="gauge-label">/ {formatStatValue(max)}</span>
      </div>
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const DashboardWidget: React.FC<DashboardWidgetProps> = ({
  config,
  data,
  isLoading = false,
  error,
  isEditing = false,
  onEdit,
  onDelete,
  onRefresh,
  onResize,
  className,
}) => {
  const [showMenu, setShowMenu] = useState(false);
  const typeConfig = getWidgetTypeConfig(config.type);
  const sizeConfig = getSizeConfig(config.size);

  // Render content based on widget type
  const renderContent = useMemo(() => {
    if (isLoading) {
      return (
        <div className="widget-loading">
          <div className="loading-spinner" />
        </div>
      );
    }

    if (error) {
      return (
        <div className="widget-error">
          <span className="error-icon">⚠️</span>
          <span className="error-text">{error}</span>
        </div>
      );
    }

    if (data === undefined || data === null) {
      return (
        <div className="widget-empty">
          <span className="empty-icon">{typeConfig.icon}</span>
          <span className="empty-text">No data</span>
        </div>
      );
    }

    switch (config.type) {
      case 'stat':
        return (
          <div className="widget-stat">
            <span className="stat-value">{formatStatValue(data as number)}</span>
          </div>
        );

      case 'line_chart':
        return (
          <SimpleLineChart
            data={data as WidgetSeries[] | WidgetDataPoint[]}
            thresholds={config.thresholds}
          />
        );

      case 'bar_chart':
        return (
          <SimpleBarChart
            data={data as WidgetSeries[] | WidgetDataPoint[]}
            thresholds={config.thresholds}
          />
        );

      case 'gauge':
        return (
          <SimpleGauge
            value={data as number}
            thresholds={config.thresholds}
          />
        );

      case 'text':
        return (
          <div className="widget-text">
            <p>{data as string}</p>
          </div>
        );

      case 'table':
        const tableData = data as WidgetDataPoint[];
        return (
          <div className="widget-table">
            <table>
              <tbody>
                {tableData.slice(0, 5).map((row, i) => (
                  <tr key={i}>
                    <td className="table-label">{row.label}</td>
                    <td className="table-value">{formatStatValue(row.value)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        );

      default:
        return (
          <div className="widget-placeholder">
            <span>{typeConfig.icon}</span>
            <span>{typeConfig.label}</span>
          </div>
        );
    }
  }, [config.type, config.thresholds, data, isLoading, error, typeConfig]);

  return (
    <div
      className={cn(
        'dashboard-widget',
        `dashboard-widget--${config.size}`,
        isEditing && 'dashboard-widget--editing',
        className
      )}
      style={{ minHeight: sizeConfig.minHeight }}
    >
      {/* Header */}
      <div className="widget-header">
        <div className="widget-title-row">
          <span className="widget-icon">{typeConfig.icon}</span>
          <h4 className="widget-title">{config.title}</h4>
        </div>
        <div className="widget-actions">
          {onRefresh && (
            <button
              type="button"
              className="widget-action-btn"
              onClick={() => onRefresh(config.id)}
              aria-label="Refresh widget"
            >
              🔄
            </button>
          )}
          {(onEdit || onDelete || onResize) && (
            <div className="widget-menu-container">
              <button
                type="button"
                className="widget-action-btn"
                onClick={() => setShowMenu(!showMenu)}
                aria-label="Widget menu"
              >
                ⋮
              </button>
              {showMenu && (
                <div className="widget-menu">
                  {onEdit && (
                    <button
                      type="button"
                      className="menu-item"
                      onClick={() => {
                        onEdit(config.id);
                        setShowMenu(false);
                      }}
                    >
                      ✏️ Edit
                    </button>
                  )}
                  {onResize && (
                    <>
                      <div className="menu-divider" />
                      {(['small', 'medium', 'large', 'full'] as WidgetSize[]).map((size) => (
                        <button
                          key={size}
                          type="button"
                          className={cn('menu-item', config.size === size && 'menu-item--active')}
                          onClick={() => {
                            onResize(config.id, size);
                            setShowMenu(false);
                          }}
                        >
                          Size: {size}
                        </button>
                      ))}
                    </>
                  )}
                  {onDelete && (
                    <>
                      <div className="menu-divider" />
                      <button
                        type="button"
                        className="menu-item menu-item--danger"
                        onClick={() => {
                          onDelete(config.id);
                          setShowMenu(false);
                        }}
                      >
                        🗑️ Delete
                      </button>
                    </>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Description */}
      {config.description && (
        <p className="widget-description">{config.description}</p>
      )}

      {/* Content */}
      <div className="widget-content">{renderContent}</div>

      {/* Editing Overlay */}
      {isEditing && (
        <div className="widget-edit-overlay">
          <span className="edit-icon">✏️</span>
          <span className="edit-label">Drag to move</span>
        </div>
      )}

      <style>{`
        .dashboard-widget {
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 12px;
          padding: 1rem;
          display: flex;
          flex-direction: column;
          position: relative;
          transition: all 0.2s ease;
        }

        .dashboard-widget:hover {
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
        }

        .dashboard-widget--editing {
          border-style: dashed;
          border-color: #3B82F6;
        }

        .widget-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 0.5rem;
        }

        .widget-title-row {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .widget-icon {
          font-size: 1rem;
        }

        .widget-title {
          margin: 0;
          font-size: 0.875rem;
          font-weight: 600;
          color: #111827;
        }

        .widget-actions {
          display: flex;
          gap: 0.25rem;
        }

        .widget-action-btn {
          background: transparent;
          border: none;
          font-size: 0.875rem;
          padding: 0.25rem;
          cursor: pointer;
          border-radius: 4px;
          opacity: 0.6;
        }

        .widget-action-btn:hover {
          opacity: 1;
          background: #F3F4F6;
        }

        .widget-menu-container {
          position: relative;
        }

        .widget-menu {
          position: absolute;
          top: 100%;
          right: 0;
          z-index: 10;
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 8px;
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
          min-width: 140px;
          padding: 0.25rem;
        }

        .menu-item {
          display: block;
          width: 100%;
          text-align: left;
          font-size: 0.8125rem;
          padding: 0.5rem 0.75rem;
          background: transparent;
          border: none;
          border-radius: 6px;
          cursor: pointer;
          color: #374151;
        }

        .menu-item:hover {
          background: #F3F4F6;
        }

        .menu-item--active {
          color: #3B82F6;
          font-weight: 500;
        }

        .menu-item--danger {
          color: #EF4444;
        }

        .menu-divider {
          height: 1px;
          background: #E5E7EB;
          margin: 0.25rem 0;
        }

        .widget-description {
          margin: 0 0 0.5rem 0;
          font-size: 0.75rem;
          color: #6B7280;
        }

        .widget-content {
          flex: 1;
          display: flex;
          align-items: center;
          justify-content: center;
          min-height: 80px;
        }

        .widget-loading {
          display: flex;
          align-items: center;
          justify-content: center;
        }

        .loading-spinner {
          width: 24px;
          height: 24px;
          border: 2px solid #E5E7EB;
          border-top-color: #3B82F6;
          border-radius: 50%;
          animation: spin 0.8s linear infinite;
        }

        @keyframes spin {
          to { transform: rotate(360deg); }
        }

        .widget-error {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 0.25rem;
          color: #EF4444;
        }

        .error-icon {
          font-size: 1.5rem;
        }

        .error-text {
          font-size: 0.75rem;
        }

        .widget-empty {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 0.25rem;
          color: #9CA3AF;
        }

        .empty-icon {
          font-size: 1.5rem;
          opacity: 0.5;
        }

        .empty-text {
          font-size: 0.75rem;
        }

        .widget-stat {
          text-align: center;
        }

        .stat-value {
          font-size: 2.5rem;
          font-weight: 700;
          color: #111827;
        }

        .widget-text {
          font-size: 0.875rem;
          color: #374151;
          line-height: 1.5;
        }

        .widget-text p {
          margin: 0;
        }

        .widget-table {
          width: 100%;
          overflow-x: auto;
        }

        .widget-table table {
          width: 100%;
          border-collapse: collapse;
        }

        .widget-table tr {
          border-bottom: 1px solid #F3F4F6;
        }

        .widget-table td {
          padding: 0.375rem 0;
          font-size: 0.8125rem;
        }

        .table-label {
          color: #6B7280;
        }

        .table-value {
          text-align: right;
          font-weight: 600;
          color: #111827;
        }

        .widget-placeholder {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 0.5rem;
          color: #9CA3AF;
          font-size: 0.875rem;
        }

        .simple-chart {
          width: 100%;
        }

        .gauge-container {
          display: flex;
          flex-direction: column;
          align-items: center;
        }

        .gauge-svg {
          width: 120px;
          height: 72px;
        }

        .gauge-value {
          margin-top: -0.5rem;
          text-align: center;
        }

        .gauge-number {
          font-size: 1.5rem;
          font-weight: 700;
        }

        .gauge-label {
          font-size: 0.75rem;
          color: #9CA3AF;
          margin-left: 0.25rem;
        }

        .widget-edit-overlay {
          position: absolute;
          inset: 0;
          background: rgba(59, 130, 246, 0.05);
          border-radius: 12px;
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          gap: 0.5rem;
          cursor: move;
          opacity: 0;
          transition: opacity 0.15s ease;
        }

        .dashboard-widget--editing:hover .widget-edit-overlay {
          opacity: 1;
        }

        .edit-icon {
          font-size: 1.5rem;
        }

        .edit-label {
          font-size: 0.75rem;
          color: #3B82F6;
          font-weight: 500;
        }

        @media (prefers-color-scheme: dark) {
          .dashboard-widget {
            background: #1F2937;
            border-color: #374151;
          }

          .dashboard-widget:hover {
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
          }

          .widget-title {
            color: #F9FAFB;
          }

          .widget-action-btn:hover {
            background: #374151;
          }

          .widget-menu {
            background: #1F2937;
            border-color: #374151;
          }

          .menu-item {
            color: #D1D5DB;
          }

          .menu-item:hover {
            background: #374151;
          }

          .widget-description {
            color: #9CA3AF;
          }

          .stat-value {
            color: #F9FAFB;
          }

          .widget-text {
            color: #E5E7EB;
          }

          .widget-table tr {
            border-bottom-color: #374151;
          }

          .table-value {
            color: #F9FAFB;
          }
        }
      `}</style>
    </div>
  );
};

DashboardWidget.displayName = 'DashboardWidget';

export default DashboardWidget;
