/**
 * @fileoverview Metric Chart Component
 *
 * Flexible chart component for visualizing metrics with thresholds,
 * tooltips, axis labels, and interactive legends.
 *
 * @module ui/components/metrics
 * @since 2.0.0
 */

import React, {
  useMemo,
  useState,
  useCallback,
  useRef,
  type MouseEvent,
} from 'react';
import { Card } from '@ghatana/dcmaar-shared-ui-tailwind';
import type { ProcessedMetrics } from '../../../analytics/AnalyticsPipeline';
import {
  getMetricGlossaryEntry,
  type MetricGlossaryEntry,
} from '../../../analytics/metrics/MetricGlossary';
import { MetricTooltip } from '../common/MetricTooltip';

const DEFAULT_COLORS = [
  '#3b82f6', // blue-500
  '#8b5cf6', // violet-500
  '#10b981', // emerald-500
  '#f59e0b', // amber-500
  '#ef4444', // red-500
  '#6b7280', // gray-500
];

const DEFAULT_ZONE_COLORS = {
  good: '#d1fae5', // emerald-100
  warning: '#fef3c7', // amber-100
  critical: '#fee2e2', // rose-100
};

type MetricDirection = 'higher-is-better' | 'lower-is-better';

export interface MetricThresholdConfig {
  good: number;
  poor: number;
  colors?: {
    good?: string;
    warning?: string;
    critical?: string;
  };
}

export type MetricThresholdMap = Record<string, MetricThresholdConfig>;

interface ChartPoint {
  timestamp: number;
  values: Record<string, number>;
}

interface HoverState {
  x: number;
  y: number;
  timestamp: number;
  formattedTime: string;
  data: {
    metric: string;
    value: number;
    color: string;
    status?: 'good' | 'warning' | 'poor';
    entry?: MetricGlossaryEntry;
  }[];
}

export interface MetricChartProps {
  title: string;
  description?: string;
  data: ProcessedMetrics[];
  metrics: string[];
  type: 'line' | 'bar' | 'area' | 'sparkline';
  height?: number;
  width?: number;
  colors?: string[];
  showLegend?: boolean;
  showGrid?: boolean;
  interactive?: boolean;
  thresholds?: MetricThresholdMap;
  yAxisLabel?: string;
  xAxisLabel?: string;
  enableMetricToggle?: boolean;
  onMetricClick?: (metric: string, timestamp: number) => void;
  loading?: boolean;
  error?: string;
}

const formatValueWithUnit = (value: number, unit?: string, precision = 0): string => {
  if (!Number.isFinite(value)) {
    return '—';
  }

  if (unit === 'ms') {
    return `${Math.round(value)}ms`;
  }

  if (unit === '%') {
    return `${value.toFixed(1)}%`;
  }

  if (unit === 'KB') {
    const kb = value / 1024;
    if (kb > 1024) {
      return `${(kb / 1024).toFixed(1)}MB`;
    }
    return `${kb.toFixed(1)}KB`;
  }

  if (!unit || unit.length === 0) {
    const decimals = precision > 0 ? precision : Math.abs(value) < 10 ? 2 : 0;
    return value.toFixed(decimals);
  }

  return `${value.toFixed(precision || 1)}${unit}`;
};

const deriveThresholdConfig = (
  metric: string,
  entry?: MetricGlossaryEntry,
  explicit?: MetricThresholdConfig
): {
  thresholds?: MetricThresholdConfig & { direction: MetricDirection };
  direction: MetricDirection;
} => {
  if (!entry && !explicit) {
    return { thresholds: undefined, direction: 'lower-is-better' };
  }

  const direction = entry?.direction ?? 'lower-is-better';
  if (explicit) {
    return {
      thresholds: {
        ...explicit,
        colors: {
          good: explicit.colors?.good ?? DEFAULT_ZONE_COLORS.good,
          warning: explicit.colors?.warning ?? DEFAULT_ZONE_COLORS.warning,
          critical: explicit.colors?.critical ?? DEFAULT_ZONE_COLORS.critical,
        },
        direction,
      },
      direction,
    };
  }

  if (entry) {
    return {
      thresholds: {
        good: entry.goodThreshold,
        poor: entry.poorThreshold,
        colors: {
          good: DEFAULT_ZONE_COLORS.good,
          warning: DEFAULT_ZONE_COLORS.warning,
          critical: DEFAULT_ZONE_COLORS.critical,
        },
        direction,
      },
      direction,
    };
  }

  return { thresholds: undefined, direction };
};

const computeStatus = (
  value: number,
  thresholds?: MetricThresholdConfig & { direction: MetricDirection }
): 'good' | 'warning' | 'poor' | undefined => {
  if (!thresholds || !Number.isFinite(value)) return undefined;

  const { good, poor, direction } = thresholds;
  if (direction === 'lower-is-better') {
    if (value <= good) return 'good';
    if (value <= poor) return 'warning';
    return 'poor';
  }
  // higher is better
  if (value >= good) return 'good';
  if (value >= poor) return 'warning';
  return 'poor';
};

const getStatusLabel = (status?: 'good' | 'warning' | 'poor'): string => {
  if (!status) return 'Unknown';
  return status === 'good'
    ? 'Good'
    : status === 'warning'
    ? 'Warning'
    : 'Poor';
};

export const MetricChart: React.FC<MetricChartProps> = ({
  title,
  description,
  data,
  metrics,
  type,
  height = 320,
  width = 800,
  colors = DEFAULT_COLORS,
  showLegend = true,
  showGrid = true,
  interactive = true,
  thresholds,
  yAxisLabel,
  xAxisLabel,
  enableMetricToggle = true,
  onMetricClick,
  loading = false,
  error,
}) => {
  const metricEntries = useMemo(
    () =>
      metrics.map((metric, index) => {
        const entry = getMetricGlossaryEntry(metric);
        const { thresholds: mergedThresholds, direction } = deriveThresholdConfig(
          metric,
          entry,
          thresholds?.[metric]
        );
        return {
          key: metric,
          color: colors[index % colors.length],
          entry,
          thresholds: mergedThresholds,
          direction,
        };
      }),
    [metrics, colors, thresholds]
  );

  const initialActiveMetrics = useMemo(
    () => new Set(metrics),
    [metrics]
  );

  const [activeMetrics, setActiveMetrics] = useState<Set<string>>(initialActiveMetrics);

  const activeMetricEntries = useMemo(() => {
    const active = metrics.filter((metric) => activeMetrics.has(metric));
    if (active.length === 0 && metrics.length > 0) {
      // Guarantee at least the first metric is visible to avoid empty charts
      return metricEntries.filter((item) => item.key === metrics[0]);
    }
    return metricEntries.filter((item) => active.includes(item.key));
  }, [metrics, metricEntries, activeMetrics]);

  const chartPoints: ChartPoint[] = useMemo(() => {
    return data.map((entry) => {
      const values: Record<string, number> = {};
      metrics.forEach((metric) => {
        values[metric] = entry.summary[metric] ?? NaN;
      });
      return {
        timestamp: entry.timestamp,
        values,
      };
    });
  }, [data, metrics]);

  const hasData = chartPoints.length > 0 && activeMetricEntries.length > 0;

  const resolvedDescription = useMemo(() => {
    if (description) return description;
    if (activeMetricEntries.length === 1 && activeMetricEntries[0]?.entry) {
      return activeMetricEntries[0].entry.description;
    }
    const names = activeMetricEntries
      .filter((item) => item.entry)
      .map((item) => item.entry!.fullName);
    return names.length ? `Tracks ${names.join(', ')}` : undefined;
  }, [description, activeMetricEntries]);

  const resolvedYAxisLabel = useMemo(() => {
    if (yAxisLabel) return yAxisLabel;
    const firstEntry = activeMetricEntries[0]?.entry;
    if (!firstEntry) return 'Value';
    const unitSuffix = firstEntry.unit ? ` (${firstEntry.unit})` : '';
    return `${firstEntry.fullName}${unitSuffix}`;
  }, [yAxisLabel, activeMetricEntries]);

  const resolvedXAxisLabel = xAxisLabel ?? 'Time';

  const svgRef = useRef<SVGSVGElement | null>(null);
  const [hoverState, setHoverState] = useState<HoverState | null>(null);

  const {
    padding,
    chartWidth,
    chartHeight,
    xScale,
    yScale,
    yTicks,
    xTicks,
    minValue,
    maxValue,
    thresholdZones,
  } = useMemo(() => {
    const padding = {
      top: 32,
      right: 24,
      bottom: 56,
      left: 70,
    };
    const chartWidth = Math.max(0, width - padding.left - padding.right);
    const chartHeight = Math.max(0, height - padding.top - padding.bottom);

    const activeKeys = activeMetricEntries.map((item) => item.key);

    const thresholdsValues: number[] = [];
    activeMetricEntries.forEach((item) => {
      if (item.thresholds) {
        thresholdsValues.push(item.thresholds.good, item.thresholds.poor);
      }
    });

    const allValues = chartPoints.flatMap((point) =>
      activeKeys.map((key) => point.values[key]).filter((value) => Number.isFinite(value))
    );

    const combined = [...allValues, ...thresholdsValues].filter((value) =>
      Number.isFinite(value)
    );

    const minValue = combined.length ? Math.min(...combined) : 0;
    const maxValue = combined.length ? Math.max(...combined) : 1;
    const rangePadding = (maxValue - minValue) * 0.1;

    const adjustedMin = minValue - rangePadding;
    const adjustedMax = maxValue + rangePadding || 1;

    const valueRange = adjustedMax - adjustedMin || 1;

    const xScale = (index: number) => {
      if (chartPoints.length <= 1) return padding.left;
      return (
        padding.left +
        (index / (chartPoints.length - 1)) * chartWidth
      );
    };

    const yScale = (value: number) => {
      if (!Number.isFinite(value)) return padding.top + chartHeight;
      return (
        padding.top +
        (1 - (value - adjustedMin) / valueRange) * chartHeight
      );
    };

    const yTicks = Array.from({ length: 5 }, (_, i) => {
      const value = adjustedMin + (i * valueRange) / 4;
      return { value, y: yScale(value) };
    });

    const xTicks = chartPoints.length
      ? chartPoints
          .filter((_, index) => {
            const step = Math.ceil(chartPoints.length / 5);
            return index % step === 0;
          })
          .map((point, index) => ({
            timestamp: point.timestamp,
            x: xScale(chartPoints.indexOf(point)),
            key: `${point.timestamp}-${index}`,
          }))
      : [];

    const firstThresholds = activeMetricEntries.find((item) => item.thresholds)?.thresholds;
    const zoneColors = firstThresholds?.colors ?? DEFAULT_ZONE_COLORS;

    const thresholdZones = firstThresholds
      ? (() => {
          const { good, poor, direction } = firstThresholds;
          const goodY = yScale(good);
          const poorY = yScale(poor);

          if (direction === 'lower-is-better') {
            return [
              {
                label: 'critical',
                y: padding.top,
                height: Math.max(0, Math.min(chartHeight, poorY - padding.top)),
                color: zoneColors.critical,
              },
              {
                label: 'warning',
                y: Math.min(poorY, padding.top + chartHeight),
                height: Math.max(0, Math.min(chartHeight, goodY - poorY)),
                color: zoneColors.warning,
              },
              {
                label: 'good',
                y: Math.min(goodY, padding.top + chartHeight),
                height: Math.max(
                  0,
                  padding.top + chartHeight - Math.min(goodY, padding.top + chartHeight)
                ),
                color: zoneColors.good,
              },
            ];
          }

          // higher-is-better
          return [
            {
              label: 'critical',
              y: Math.min(poorY, padding.top + chartHeight),
              height: Math.max(
                0,
                padding.top + chartHeight - Math.min(poorY, padding.top + chartHeight)
              ),
              color: zoneColors.critical,
            },
            {
              label: 'warning',
              y: Math.min(goodY, poorY),
              height: Math.max(0, Math.abs(poorY - goodY)),
              color: zoneColors.warning,
            },
            {
              label: 'good',
              y: padding.top,
              height: Math.max(0, Math.min(chartHeight, goodY - padding.top)),
              color: zoneColors.good,
            },
          ];
        })()
      : [];

    return {
      padding,
      chartWidth,
      chartHeight,
      xScale,
      yScale,
      yTicks,
      xTicks,
      minValue: adjustedMin,
      maxValue: adjustedMax,
      thresholdZones,
    };
  }, [width, height, chartPoints, activeMetricEntries]);

  const handleLegendToggle = useCallback(
    (metric: string) => {
      if (!enableMetricToggle) return;
      setActiveMetrics((prev) => {
        const next = new Set(prev);
        if (next.has(metric)) {
          next.delete(metric);
        } else {
          next.add(metric);
        }
        return next;
      });
    },
    [enableMetricToggle]
  );

  const formatTimestamp = (timestamp: number): string => {
    return new Date(timestamp).toLocaleString([], {
      hour: '2-digit',
      minute: '2-digit',
      month: 'short',
      day: 'numeric',
    });
  };

  const handleMouseMove = useCallback(
    (event: MouseEvent<SVGSVGElement>) => {
      if (!interactive || !svgRef.current || chartPoints.length === 0) {
        return;
      }

      const rect = svgRef.current.getBoundingClientRect();
      const offsetX = event.clientX - rect.left - padding.left;

      if (offsetX < 0 || offsetX > chartWidth) {
        setHoverState(null);
        return;
      }

      const ratio =
        chartPoints.length > 1
          ? offsetX / chartWidth
          : 0;
      const index = Math.min(
        chartPoints.length - 1,
        Math.max(0, Math.round(ratio * (chartPoints.length - 1)))
      );

      const point = chartPoints[index];
      if (!point) {
        setHoverState(null);
        return;
      }

      const entries = activeMetricEntries.map((item) => {
        const thresholdsForMetric = item.thresholds;
        const value = point.values[item.key];
        const status = computeStatus(value, thresholdsForMetric);
        return {
          metric: item.key,
          value,
          color: item.color,
          status,
          entry: item.entry,
        };
      });

      setHoverState({
        x: xScale(index),
        y: padding.top,
        timestamp: point.timestamp,
        formattedTime: formatTimestamp(point.timestamp),
        data: entries,
      });
    },
    [interactive, chartPoints, activeMetricEntries, xScale, padding.left, chartWidth, padding.top]
  );

  const handleMouseLeave = useCallback(() => {
    setHoverState(null);
  }, []);

  const latestSummary = data[data.length - 1]?.summary ?? {};

  const renderChart = () => {
    if (!hasData) {
      return (
        <div className="flex h-full flex-col items-center justify-center text-sm text-slate-500">
          No metrics enabled. Select at least one metric to visualize.
        </div>
      );
    }

    return (
      <svg
        ref={svgRef}
        width={width}
        height={height}
        className="w-full"
        onMouseMove={handleMouseMove}
        onMouseLeave={handleMouseLeave}
      >
        {/* Background zones */}
        {thresholdZones.map((zone, index) => (
          <rect
            key={`${zone.label}-${index}`}
            x={padding.left}
            y={zone.y}
            width={chartWidth}
            height={zone.height}
            fill={zone.color}
            opacity={0.35}
          />
        ))}

        {/* Grid lines */}
        {showGrid && (
          <g>
            {yTicks.map((tick, index) => (
              <line
                key={`grid-y-${index}`}
                x1={padding.left}
                y1={tick.y}
                x2={padding.left + chartWidth}
                y2={tick.y}
                stroke="#e2e8f0"
                strokeWidth={1}
                strokeDasharray="4 6"
              />
            ))}
            {xTicks.map((tick, index) => (
              <line
                key={`grid-x-${index}`}
                x1={tick.x}
                y1={padding.top}
                x2={tick.x}
                y2={padding.top + chartHeight}
                stroke="#e2e8f0"
                strokeWidth={1}
                strokeDasharray="4 6"
              />
            ))}
          </g>
        )}

        {/* Threshold lines */}
        {activeMetricEntries.map((item) => {
          if (!item.thresholds) return null;
          const { good, poor } = item.thresholds;
          return (
            <g key={`threshold-${item.key}`}>
              <line
                x1={padding.left}
                x2={padding.left + chartWidth}
                y1={yScale(good)}
                y2={yScale(good)}
                stroke="#059669"
                strokeDasharray="6 6"
                strokeWidth={1}
              />
              <line
                x1={padding.left}
                x2={padding.left + chartWidth}
                y1={yScale(poor)}
                y2={yScale(poor)}
                stroke="#dc2626"
                strokeDasharray="6 6"
                strokeWidth={1}
              />
            </g>
          );
        })}

        {/* Metric paths */}
        {activeMetricEntries.map((item) => {
          const points = chartPoints.map((point, index) => {
            const value = point.values[item.key];
            return {
              x: xScale(index),
              y: yScale(value),
              value,
              timestamp: point.timestamp,
            };
          });

          if (type === 'area') {
            const path = points
              .map((point, index) =>
                `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`
              )
              .join(' ');
            const areaPath = `${path} L ${padding.left + chartWidth} ${
              padding.top + chartHeight
            } L ${padding.left} ${padding.top + chartHeight} Z`;
            return (
              <g key={`metric-area-${item.key}`}>
                <path
                  d={areaPath}
                  fill={item.color}
                  fillOpacity={0.1}
                />
                <path
                  d={path}
                  fill="none"
                  stroke={item.color}
                  strokeWidth={2}
                />
              </g>
            );
          }

          if (type === 'bar') {
            const groupWidth =
              chartPoints.length > 0 ? chartWidth / chartPoints.length : chartWidth;
            const metricIndex = activeMetricEntries.findIndex((entry) => entry.key === item.key);
            const barWidth =
              groupWidth / Math.max(1, activeMetricEntries.length);
            return (
              <g key={`metric-bar-${item.key}`}>
                {points.map((point, index) => (
                  <rect
                    key={`${item.key}-bar-${index}`}
                    x={
                      padding.left +
                      index * groupWidth +
                      metricIndex * barWidth +
                      barWidth * 0.1
                    }
                    y={point.y}
                    width={barWidth * 0.8}
                    height={padding.top + chartHeight - point.y}
                    fill={item.color}
                    opacity={0.8}
                  />
                ))}
              </g>
            );
          }

          // default to line
          const path = points
            .map((point, index) =>
              `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`
            )
            .join(' ');

          return (
            <path
              key={`metric-line-${item.key}`}
              d={path}
              fill="none"
              stroke={item.color}
              strokeWidth={2}
            />
          );
        })}

        {/* Axes */}
        {/* Y-axis */}
        <line
          x1={padding.left}
          y1={padding.top}
          x2={padding.left}
          y2={padding.top + chartHeight}
          stroke="#cbd5f5"
          strokeWidth={1}
        />
        {/* X-axis */}
        <line
          x1={padding.left}
          y1={padding.top + chartHeight}
          x2={padding.left + chartWidth}
          y2={padding.top + chartHeight}
          stroke="#cbd5f5"
          strokeWidth={1}
        />

        {/* Axis labels */}
        <g className="text-xs text-slate-500">
          {yTicks.map((tick, index) => (
            <text
              key={`y-tick-${index}`}
              x={padding.left - 8}
              y={tick.y + 4}
              textAnchor="end"
            >
              {formatValueWithUnit(tick.value, activeMetricEntries[0]?.entry?.unit, 1)}
            </text>
          ))}
        </g>

        <g className="text-xs text-slate-500">
          {xTicks.map((tick) => (
            <text
              key={tick.key}
              x={tick.x}
              y={padding.top + chartHeight + 16}
              textAnchor="middle"
            >
              {new Date(tick.timestamp).toLocaleTimeString([], {
                hour: '2-digit',
                minute: '2-digit',
              })}
            </text>
          ))}
        </g>

        {/* Axis titles */}
        <text
          x={padding.left + chartWidth / 2}
          y={height - 8}
          textAnchor="middle"
          className="text-xs font-medium text-slate-500"
        >
          {resolvedXAxisLabel}
        </text>
        <text
          transform={`translate(${18}, ${padding.top + chartHeight / 2}) rotate(-90)`}
          textAnchor="middle"
          className="text-xs font-medium text-slate-500"
        >
          {resolvedYAxisLabel}
        </text>

        {/* Hover marker */}
        {hoverState && (
          <g pointerEvents="none">
            <line
              x1={hoverState.x}
              y1={padding.top}
              x2={hoverState.x}
              y2={padding.top + chartHeight}
              stroke="#94a3b8"
              strokeDasharray="4 4"
            />
            <circle
              cx={hoverState.x}
              cy={padding.top}
              r={3}
              fill="#1e293b"
            />
          </g>
        )}
      </svg>
    );
  };

  const legend = showLegend ? (
    <div className="flex flex-wrap items-center justify-center gap-3">
      {metricEntries.map((item) => {
        const isActive = activeMetrics.has(item.key);
        return (
          <button
            key={item.key}
            type="button"
            onClick={() => handleLegendToggle(item.key)}
            className={`flex items-center gap-2 rounded-full px-3 py-1 text-xs transition ${
              isActive ? 'bg-slate-100 text-slate-700' : 'bg-white text-slate-400'
            } ${enableMetricToggle ? 'hover:bg-slate-200' : 'cursor-default'}`}
          >
            <span
              className="h-2 w-2 rounded-full"
              style={{ backgroundColor: item.color }}
            />
            <span className="font-medium">
              {item.entry?.shortName ?? item.key.toUpperCase()}
            </span>
            <span className="text-slate-400">
              {formatValueWithUnit(
                latestSummary[item.key],
                item.entry?.unit,
                1
              )}
            </span>
            {!isActive && enableMetricToggle && (
              <span className="text-slate-400">(hidden)</span>
            )}
          </button>
        );
      })}
    </div>
  ) : null;

  const tooltip = hoverState ? (
    <div
      className="pointer-events-none absolute rounded-lg border border-slate-200 bg-white p-3 text-xs shadow-lg"
      style={{
        left: Math.max(
          padding.left,
          Math.min(hoverState.x + 16, padding.left + chartWidth - 220)
        ),
        top: padding.top + 32,
        minWidth: 180,
      }}
    >
      <div className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-slate-500">
        {hoverState.formattedTime}
      </div>
      <div className="space-y-1">
        {hoverState.data.map((entry) => (
          <div key={entry.metric} className="flex items-center justify-between gap-3">
            <span className="flex items-center gap-2 text-slate-600">
              <span
                className="h-2 w-2 rounded-full"
                style={{ backgroundColor: entry.color }}
              />
              <span>{entry.entry?.shortName ?? entry.metric.toUpperCase()}</span>
            </span>
            <span className="text-right text-slate-500">
              {formatValueWithUnit(entry.value, entry.entry?.unit, 1)}{' '}
              <span
                className={`ml-1 text-[10px] uppercase ${
                  entry.status === 'good'
                    ? 'text-emerald-600'
                    : entry.status === 'warning'
                    ? 'text-amber-600'
                    : entry.status === 'poor'
                    ? 'text-rose-600'
                    : 'text-slate-400'
                }`}
              >
                {getStatusLabel(entry.status)}
              </span>
            </span>
          </div>
        ))}
      </div>
    </div>
  ) : null;

  return (
    <Card title={title} description={resolvedDescription}>
      {loading ? (
        <div className="flex items-center justify-center" style={{ height }}>
          <div className="h-8 w-8 animate-spin rounded-full border-b-2 border-slate-400" />
        </div>
      ) : error ? (
        <div className="flex items-center justify-center text-rose-600" style={{ height }}>
          {error}
        </div>
      ) : (
        <div className="relative space-y-4">
          <div className="relative">{renderChart()}</div>
          {legend}
          {tooltip}
        </div>
      )}
    </Card>
  );
};

export default MetricChart;
