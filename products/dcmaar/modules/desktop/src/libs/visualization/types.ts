/**
 * Visualization Type Definitions
 * 
 * Core types for visualization components following DCMaar standards.
 */

import type { ReactNode } from 'react';

/**
 * Chart data point
 */
export interface DataPoint {
  timestamp: number;
  value: number;
  label?: string;
  metadata?: Record<string, unknown>;
}

/**
 * Time series data
 */
export interface TimeSeriesData {
  id: string;
  name: string;
  data: DataPoint[];
  color?: string;
  unit?: string;
}

/**
 * Chart configuration
 */
export interface ChartConfig {
  type: 'line' | 'bar' | 'pie' | 'area' | 'scatter';
  title?: string;
  subtitle?: string;
  xAxis?: AxisConfig;
  yAxis?: AxisConfig;
  legend?: LegendConfig;
  tooltip?: TooltipConfig;
  animation?: boolean;
  responsive?: boolean;
}

/**
 * Axis configuration
 */
export interface AxisConfig {
  label?: string;
  unit?: string;
  min?: number;
  max?: number;
  format?: (value: number) => string;
}

/**
 * Legend configuration
 */
export interface LegendConfig {
  show?: boolean;
  position?: 'top' | 'bottom' | 'left' | 'right';
  align?: 'start' | 'center' | 'end';
}

/**
 * Tooltip configuration
 */
export interface TooltipConfig {
  show?: boolean;
  format?: (data: DataPoint) => string;
}

/**
 * Dashboard widget
 */
export interface Widget {
  id: string;
  type: 'chart' | 'metric' | 'table' | 'custom';
  title: string;
  description?: string;
  position: WidgetPosition;
  size: WidgetSize;
  config: WidgetConfig;
  refreshInterval?: number;
}

/**
 * Widget position
 */
export interface WidgetPosition {
  x: number;
  y: number;
}

/**
 * Widget size
 */
export interface WidgetSize {
  width: number;
  height: number;
  minWidth?: number;
  minHeight?: number;
  maxWidth?: number;
  maxHeight?: number;
}

/**
 * Widget configuration
 */
export interface WidgetConfig {
  dataSource?: string;
  chartConfig?: ChartConfig;
  customRenderer?: (data: unknown) => ReactNode;
  [key: string]: unknown;
}

/**
 * Dashboard layout
 */
export interface DashboardLayout {
  id: string;
  name: string;
  description?: string;
  widgets: Widget[];
  columns: number;
  rowHeight: number;
  gap: number;
}

/**
 * Historical data query
 */
export interface HistoricalQuery {
  metric: string;
  startTime: number;
  endTime: number;
  aggregation?: 'avg' | 'sum' | 'min' | 'max' | 'count';
  interval?: number;
  filters?: Record<string, unknown>;
}

/**
 * Historical data result
 */
export interface HistoricalResult {
  query: HistoricalQuery;
  data: TimeSeriesData[];
  metadata: {
    totalPoints: number;
    samplingRate?: number;
    cacheHit?: boolean;
  };
}

/**
 * Export format
 */
export type ExportFormat = 'png' | 'svg' | 'csv' | 'json' | 'pdf';

/**
 * Export options
 */
export interface ExportOptions {
  format: ExportFormat;
  filename?: string;
  quality?: number;
  includeMetadata?: boolean;
}
